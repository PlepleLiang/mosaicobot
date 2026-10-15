package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test
import org.luaj.vm2.Globals
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

/** Executes only the checked-in common loop with handwritten input fixtures, never model code. */
class GameInputRecoveryTest {
    private fun run(input:String, touch:String="return nil"):Globals {
        val g=JsePlatform.standardGlobals()
        g.load("""
            frame=0;updated={};labels={};closed=0
            local screen={}
            function screen:info() return {width=480,height=480,touch_available=true} end
            function screen:touch()
              local function point() $touch end
              local p=point()
              if frame==12 then p={x=420,y=24} end
              return {points=p and {p} or {}}
            end
            function screen:begin() labels[frame]={} end
            function screen:present() end
            function screen:fill_rect() end
            function screen:fill_round_rect() end
            function screen:measure_text(t,o) return #t*8,16 end
            function screen:text(x,y,t,o) labels[frame][t]=true end
            function screen:close() closed=closed+1 end
            package.loaded.display={open=function() return screen end}
            package.loaded.system={millis=function() return frame*34 end}
            package.loaded.delay={delay_ms=function() frame=frame+1;assert(frame<15) end}
            package.loaded.gamepad={open=function() end,close=function() end,poll=function()
              local input={connected=true,input_active=true,held={},pressed={},events={},lx=0,ly=0}
              $input
              return input
            end}
        """.trimIndent(),"handwritten-input-fixture").call()
        val common=File("src/main/assets/app-center/game-common.lua").readText()
        g.load(common+"\nrun('TEST',function(p,tap,dt) updated[frame]=true end,function() end)","preset-common").call()
        assertEquals(1,g.get("closed").toint())
        return g
    }
    private fun updated(g:Globals,frame:Int)=g.get("updated").get(frame).toboolean()
    private fun label(g:Globals,frame:Int,text:String)=g.get("labels").get(frame).get(text).toboolean()

    @Test fun initiallyDisconnectedAndLegacyFreshnessRemainPlayable() {
        for(fixture in listOf("input.connected=false;input.input_active=false","input.input_active=nil")) {
            val g=run(fixture)
            assertTrue(updated(g,1));assertTrue(updated(g,10))
        }
    }
    @Test fun shortStallFreezesWithoutPersistentPauseAndRecoversAutomatically() {
        val g=run("if frame>=3 and frame<=5 then input.input_active=false end")
        assertFalse(updated(g,4));assertTrue(label(g,4,"PAD SIGNAL WEAK"))
        assertFalse(label(g,4,"PAUSED"));assertTrue(updated(g,6))
    }
    @Test fun recoveryDoesNotResumeAnExplicitPause() {
        val g=run("if frame==2 or frame==8 then input.pressed.start=true end; if frame>=3 and frame<=5 then input.input_active=false end")
        assertTrue(label(g,6,"PAUSED"));assertFalse(updated(g,6));assertTrue(updated(g,8))
    }
    @Test fun realDisconnectPausesOnceAndTouchResumeWorksOffline() {
        val g=run("if frame>=3 then input.connected=false;input.input_active=false end",
            "if frame==6 then return {x=330,y=24} end")
        assertTrue(label(g,4,"PAUSED"));assertTrue(updated(g,6));assertTrue(updated(g,9))
    }
    @Test fun touchCanTakeOverDuringStaleInput() {
        val g=run("if frame>=3 then input.input_active=false end", "if frame==4 then return {x=200,y=350} end")
        assertFalse(updated(g,3));assertTrue(updated(g,4));assertTrue(updated(g,5))
        assertFalse(label(g,5,"PAD SIGNAL WEAK"))
    }
    @Test fun simultaneousTouchAndStartToggleOnlyOnce() {
        val g=run("if frame==2 then input.pressed.start=true end", "if frame==2 then return {x=330,y=24} end")
        assertTrue(label(g,2,"PAUSED"));assertFalse(updated(g,2))
    }
}
