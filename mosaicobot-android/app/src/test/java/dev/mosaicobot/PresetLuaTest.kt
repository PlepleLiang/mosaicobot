package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

/** Executes ONLY our six checked-in presets with fake APIs, never a model response/user upload. */
@RunWith(Parameterized::class)
class PresetLuaTest(private val key:String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun apps()=listOf("tank","flight","racer","pixelpet","lightboard","quiettime").map { arrayOf(it) }
    }
    private val source get()=File("src/main/assets/app-center/"+(if(key in setOf("tank","flight","racer")) "game-common.lua" else "common.lua")).readText()+"\n"+File("src/main/assets/app-center/$key.lua").readText()
    private fun execute(failFrame:Int=0,frames:Int=400): org.luaj.vm2.Globals {
        val g=JsePlatform.standardGlobals()
        g.set("failFrame",failFrame);g.set("exitFrame",frames)
        g.load("""
            frame=0;begins=0;presents=0;closed=0;drawing=false;elapsed=0
            local function integer(v) assert(type(v)=='number' and v==math.floor(v),'noninteger drawing') end
            local function region(x,y,w,h)
              integer(x);integer(y);integer(w);integer(h)
              assert(w>0 and h>0 and x>=0 and y>=0 and x+w<=480 and y+h<=480,'drawing outside screen')
            end
            local s={}
            function s:info() return {width=480,height=480,touch_available=true} end
            function s:begin(o) assert(not drawing);drawing=true;begins=begins+1 end
            function s:present() assert(drawing);drawing=false;presents=presents+1 end
            function s:fill_rect(x,y,w,h,c) assert(drawing);region(x,y,w,h) end
            function s:fill_round_rect(x,y,w,h,r,c) assert(drawing);region(x,y,w,h);integer(r) end
            function s:stroke_circle(x,y,r,c) assert(drawing);integer(x);integer(y);integer(r) end
            function s:measure_text(t,o) return #t*o.font_size/2,o.font_size end
            function s:text(x,y,t,o)
              assert(drawing);integer(x);integer(y);assert(o.font_size>=8 and o.font_size<=64)
              for i=1,#t do assert(string.byte(t,i)<128,'non ASCII device text') end
            end
            function s:close() closed=closed+1 end
            function s:touch()
              if failFrame>0 and frame==failFrame then error('injected touch failure') end
              local p=nil
              if frame==2 then p={x=235,y=285}
              elseif frame==20 or frame==25 then p={x=330,y=25}
              elseif frame==40 then p={x=420,y=440}
              elseif frame==42 then p={x=120,y=280}
              elseif frame>45 and frame%4==0 then p={x=40+(frame*31)%400,y=90+(frame*13)%360} end
              if frame>=exitFrame then p={x=425,y=25} end
              return {points=p and {p} or {}}
            end
            package.loaded.gamepad={open=function() end,close=function() end,
              poll=function() return {connected=false,held={},pressed={},events={},lx=0,ly=0,rx=0,ry=0} end}
            package.loaded.display={open=function() return s end}
            package.loaded.system={millis=function() return elapsed end}
            package.loaded.delay={delay_ms=function(ms)
              assert(ms>=34,'unbounded frame rate');elapsed=elapsed+ms;frame=frame+1
              assert(frame<exitFrame+5,'exit did not release the main loop')
            end}
        """.trimIndent(),"fake-device").call()
        val result=runCatching {g.load(source,"preset-$key").call()}
        if(failFrame>0) assertTrue(result.isFailure) else result.getOrThrow()
        return g
    }
    @Test fun syntaxAndCapabilityContract() { ArtifactValidator.validateLua(source) }
    @Test(timeout=10000) fun framesTouchPauseAndExitFollowDisplayContract() {
        val g=execute();assertEquals(1,g.get("closed").toint())
        assertEquals(g.get("begins").toint(),g.get("presents").toint())
        assertTrue(g.get("presents").toint()>300);assertFalse(g.get("drawing").toboolean())
    }
    @Test(timeout=10000) fun errorsAlsoReleaseScreen() {assertEquals(1,execute(failFrame=7).get("closed").toint())}
}
