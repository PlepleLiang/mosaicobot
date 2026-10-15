package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GeneratedGamePolicyTest {
    companion object {
        // Only checked-in, handwritten source; no model code is executed by this lint.
        fun dual(key:String="tank")="-- interaction: game-dual\n"+
            (File("src/main/assets/app-center/game-common.lua").readText()+"\n"+
                File("src/main/assets/app-center/$key.lua").readText()).replace("padState","input")
        val touch get()=ArtifactTest.lua.replace("-- interaction: app","-- interaction: game-touch")+
            "\nlocal touches=screen:touch().points\nscreen:text(10,10,\"EXIT\")"
    }
    private fun rejected(lua:String)=assertThrows(IllegalArgumentException::class.java) {GeneratedGamePolicy.validate(lua)}

    @Test fun defaultGamesAndToolsAreAccepted() {
        for(key in listOf("tank","flight","racer")) {
            val lua=dual(key)
            ArtifactValidator.validateLua(lua)
            GeneratedGamePolicy.validate(lua)
        }
        GeneratedGamePolicy.validate(ArtifactTest.lua)
    }
    @Test fun discreteGameDoesNotRequireHeldOrAnalogInputs() {
        GeneratedGamePolicy.validate(dual().replace("input.held","{}")
            .replace("input.lx","0").replace("input.ly","0").replace("input.rx","0").replace("input.ry","0"))
    }
    @Test fun explicitTouchOnlyDoesNotNeedPadModule() {GeneratedGamePolicy.validate(touch)}
    @Test fun generationNeedsDeclarationButStoredLegacyWorksDoNot() {
        val legacy=ArtifactTest.lua.removePrefix("-- interaction: app\n")
        rejected(legacy)
        assertEquals(legacy,ArtifactValidator.parse(ArtifactTest.response(legacy),"旧作品").lua)
    }
    @Test fun unknownKindCannotSilentlySkipControlChecks() {
        rejected(dual().replace("game-dual","game"))
        rejected(dual().replace("game-dual","app"))
        rejected(dual().replace("game-dual","game-touch"))
    }
    @Test fun defaultGameWithoutPadIsRejected() {
        rejected(touch.replace("game-touch","game-dual"))
        rejected(dual().replace("local pad = require(\"gamepad\")","-- local pad = require(\"gamepad\")"))
    }
    @Test fun missingTouchOrExitIsRejected() {
        rejected(dual().replace("screen:touch()","touchSnapshot"))
        rejected(dual().replace("\"EXIT\"","\"DONE\""))
        rejected(touch.replace("screen:touch()","touchSnapshot"))
    }
    @Test fun lifecycleAndDisconnectHandlingAreRequired() {
        for(call in listOf("pad.open()","pad.poll()","pad.close()")) rejected(dual().replace(call,"nil"))
        rejected(dual().replace("input.connected","false"))
        rejected(dual().replace("input.input_active","nil"))
        rejected(dual().replace("input=pad.poll()","ignored=pad.poll()"))
    }
    @Test fun pollWithoutActionOrStartReferencesIsRejected() {
        rejected(dual().replace("input.held","other.held").replace("input.pressed","other.pressed"))
        rejected(dual().replace("\"start\"","\"select\""))
        rejected(dual()+"\npad.approve(true)")
    }
    @Test fun commentsAndQuotedExamplesCannotFakeExecutableCalls() {
        val missing=dual().replace("screen:touch()","touchSnapshot")
        for(example in listOf("-- screen:touch()", "--[=[ screen:touch() ]=]",
            "local example=\"screen:touch()\"", "local example=[=[screen:touch()]=]")) rejected(missing+"\n"+example)
        val noPoll=dual().replace("pad.poll()","nil")
        rejected(noPoll+"\n-- input=pad.poll()\nlocal example='input=pad.poll()'")
    }
    @Test fun commentsBetweenTokensAndSingleQuotedImportsAreSupported() {
        GeneratedGamePolicy.validate(dual().replace("require(\"gamepad\")","require('gamepad')")
            .replace("pad.open()","pad --[=[ note ]=]\n . open ( )"))
    }
}
