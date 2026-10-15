package dev.mosaicobot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentClientTest {
    private fun reply(content:String,finish:String="stop") = JSONObject().put("choices",JSONArray().put(
        JSONObject().put("finish_reason",finish).put("message",JSONObject().put("content",content)))).toString()
    private fun withService(test:(MockWebServer,AgentClient,AgentConfig)->Unit) {
        val certificate=HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(),false)
            val client=networkClient().newBuilder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager).build()
            test(server,AgentClient(client),AgentConfig(server.url("/v1").toString(),"test-model","fake-test-key"))
        }
    }
    @Test fun validResponseSavedWithCredentialOnlyInHeader() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        assertEquals("屏幕时钟",agent.generate(config,"contract","生成时钟",null,{}).title)
        val request=server.takeRequest()
        assertEquals("/v1/chat/completions",request.path)
        assertEquals("Bearer fake-test-key",request.getHeader("Authorization"))
        val body=request.body.readUtf8()
        assertFalse(body.contains("fake-test-key"));assertEquals("test-model",JSONObject(body).getString("model"))
        assertEquals(16384,JSONObject(body).getInt("max_tokens"))
        assertFalse(JSONObject(body).has("reasoning_effort"))
    } }
    @Test fun malformedArtifactRepairedOnce() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("not-json")))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        val stages=mutableListOf<String>()
        agent.generate(config,"contract","生成时钟",null,{stages.add(it)})
        assertEquals(2,server.requestCount);assertTrue(stages.any { it.contains("修复") })
        server.takeRequest();assertTrue(server.takeRequest().body.readUtf8().contains("Validation failed"))
    } }
    @Test fun defaultStepModelUsesLowReasoningBudget() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        agent.generate(config.copy(model="step-5-preview:stepfun"),"contract","生成贪食蛇",null,{})
        assertEquals("low",JSONObject(server.takeRequest().body.readUtf8()).getString("reasoning_effort"))
    } }
    @Test fun repeatedBadResponseDoesNotLoopForever() = withService { server,agent,config -> runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody(reply("not-json"))) }
        assertTrue(runCatching { agent.generate(config,"contract","x",null,{}) }.isFailure)
        assertEquals(3,server.requestCount)
    } }
    @Test fun refusalDoesNotTriggerRepairOrDeployment() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("""{"refusal":"相机不在授权范围"}""")))
        val result=runCatching { agent.generate(config,"contract","相机",null,{}) }
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException);assertEquals(1,server.requestCount)
    } }
    @Test fun completeValidatedArtifactAcceptedDespiteLengthFinishReason() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response(),"length")))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        assertEquals(1,server.requestCount)
    } }
    @Test fun truncatedJsonContinuesExactlyAcrossStringEscape() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.response()
        val split=full.indexOf("\\n")+1
        assertTrue(split>0)
        val prefix=full.take(split)
        server.enqueue(MockResponse().setBody(reply(prefix,"length")))
        server.enqueue(MockResponse().setBody(reply(full.drop(split))))
        val stages=mutableListOf<String>()
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","生成时钟",null,{stages.add(it)}).lua)
        assertEquals(2,server.requestCount)
        assertTrue(stages.any { it.contains("自动续写") })
        server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(prefix,messages.getJSONObject(2).getString("content"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("missing suffix"))
    } }
    @Test fun multipleContinuationsKeepOriginalIntentAndOnlyOneDraft() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.response()
        val a=full.length/3;val b=a*2
        server.enqueue(MockResponse().setBody(reply(full.take(a),"length")))
        server.enqueue(MockResponse().setBody(reply(full.substring(a,b),"length")))
        server.enqueue(MockResponse().setBody(reply(full.drop(b))))
        val intent="保留原功能。".repeat(400)+"最后必须增加暂停键"
        val previous=ArtifactTest.work()
        val work=agent.generate(config,"contract",intent,previous,{})
        assertEquals(previous.id,work.id);assertEquals(2,work.version)
        assertEquals(intent,work.intent)
        repeat(3) { index ->
            val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
            assertEquals(intent,messages.getJSONObject(2).getString("content"))
            assertTrue(messages.getJSONObject(1).getString("content").contains(previous.lua))
            assertEquals(if(index==0) 3 else 5,messages.length())
            if(index==2) assertEquals(full.take(b),messages.getJSONObject(3).getString("content"))
        }
    } }
    @Test fun modelMayRestartWithCompleteReplacementDuringContinuation() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("{\"lua\":\"local ","length")))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        assertEquals(2,server.requestCount)
    } }
    @Test fun assembledInvalidLuaTriggersFullRepair() = withService { server,agent,config -> runBlocking {
        val bad=ArtifactTest.response(ArtifactTest.lua+"\nif then")
        val split=bad.length/2
        server.enqueue(MockResponse().setBody(reply(bad.take(split),"length")))
        server.enqueue(MockResponse().setBody(reply(bad.drop(split))))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","保留完整功能",null,{}).lua)
        server.takeRequest();server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(bad,messages.getJSONObject(2).getString("content"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("COMPLETE RAW LUA"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("never drop features"))
    } }
    @Test fun truncatedRepairIsAlsoContinued() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.response();val split=full.length/2
        server.enqueue(MockResponse().setBody(reply("bad-json")))
        server.enqueue(MockResponse().setBody(reply(full.take(split),"length")))
        server.enqueue(MockResponse().setBody(reply(full.drop(split))))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        assertEquals(3,server.requestCount)
    } }
    @Test fun repeatedTruncationStopsAtTotalCallBudget() = withService { server,agent,config -> runBlocking {
        repeat(8) { server.enqueue(MockResponse().setBody(reply("{\"lua\":\"local ","length"))) }
        val result=runCatching { agent.generate(config,"contract","x",null,{}) }
        assertTrue(result.exceptionOrNull()?.message?.contains("8 次") == true)
        assertEquals(8,server.requestCount)
    } }
    @Test fun emptyTruncationRegeneratesInsteadOfAppendingEmptyAssistant() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("","length")))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(3,messages.length())
        assertTrue(messages.getJSONObject(2).getString("content").contains("COMPLETE RAW LUA"))
    } }
    @Test fun secondRepairCanRecoverAfterFirstRepairFails() = withService { server,agent,config -> runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody(reply("bad-json"))) }
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        assertEquals(3,server.requestCount)
    } }
    @Test fun refusalDuringContinuationStopsImmediately() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("{\"lua\":", "length")))
        server.enqueue(MockResponse().setBody(reply("{\"refusal\":\"能力不支持\"}")))
        val result=runCatching { agent.generate(config,"contract","x",null,{}) }
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(2,server.requestCount)
    } }
    @Test fun providerContentFilterIsNotRetriedEvenWithValidLookingArtifact() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response(),"content_filter")))
        val result=runCatching { agent.generate(config,"contract","x",null,{}) }
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(1,server.requestCount)
    } }
    @Test fun providerRefusalIsNotRetried() = withService { server,agent,config -> runBlocking {
        val body=JSONObject(reply(""))
        body.getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("refusal","not allowed")
        server.enqueue(MockResponse().setBody(body.toString()))
        assertTrue(runCatching { agent.generate(config,"contract","x",null,{}) }.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(1,server.requestCount)
    } }
    @Test fun cancellationBetweenCallsDoesNotBecomeRepair() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("{\"lua\":", "length")))
        val result=runCatching { agent.generate(config,"contract","x",null,{ stage ->
            if(stage.contains("自动续写")) throw CancellationException("user cancelled")
        }) }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1,server.requestCount)
    } }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun totalDeadlineCancelsPendingRequestWithoutRepair() = withService { server,agent,config -> runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task=async(start=CoroutineStart.UNDISPATCHED) {
            runCatching { agent.generate(config,"contract","x",null,{}) }
        }
        assertNotNull(server.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS))
        advanceTimeBy(12*60*1000L)
        runCurrent()
        val error=task.await().exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertTrue(error?.message?.contains("自动续写与修复超时") == true)
        assertEquals(1,server.requestCount)
    } }
    @Test fun userCancellationWhileWaitingStaysCancellation() = withService { server,agent,config -> runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task=async(start=CoroutineStart.UNDISPATCHED) { agent.generate(config,"contract","x",null,{}) }
        assertNotNull(server.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS))
        task.cancelAndJoin()
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1,server.requestCount)
    } }
    @Test fun authorizationFailureDuringContinuationDoesNotRetry() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("{\"lua\":", "length")))
        server.enqueue(MockResponse().setResponseCode(403).setBody("private payload"))
        val result=runCatching { agent.generate(config,"contract","x",null,{}) }
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
        assertFalse(result.exceptionOrNull()?.message?.contains("private payload") == true)
        assertEquals(2,server.requestCount)
    } }
    @Test fun trailingPartialSecondObjectIsRepairedNotSilentlyIgnored() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response()+"{\"lua\":")))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        assertEquals(2,server.requestCount)
    } }
    @Test fun onlineWeatherArtifactCanBeGeneratedAndValidated() = withService { server,agent,config -> runBlocking {
        val contract=java.io.File("src/main/assets/agent-contract.txt").readText()
        assertTrue(contract.contains("system.date"))
        assertTrue(contract.contains("capability.call(\"http_request\""))
        assertFalse(contract.contains("camera, microphone, audio, networking"))
        assertTrue(contract.contains("-- MOSAICOBOT_APP_V1"))
        val lua="-- interaction: app\n"+LuaNetworkPolicyTest.lua
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(lua))))
        val work=agent.generate(config,contract,"生成天气和时钟结合的壁纸应用",null,{})
        assertTrue(work.usesNetwork)
        assertEquals(setOf("api.open-meteo.com"),work.networkHosts)
        assertEquals(1,server.requestCount)
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(contract,messages.getJSONObject(0).getString("content"))
    } }
    @Test fun brokenJsonEscapingRecoversByRequestingRawLuaInsteadOfMoreJson() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("""{"title":"时钟","summary":"说明","lua":"local display = require("display")"}""")))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","保留完整需求",null,{}).lua)
        server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals("保留完整需求",messages.getJSONObject(1).getString("content"))
        val instruction=messages.getJSONObject(3).getString("content")
        assertTrue(instruction.contains("COMPLETE RAW LUA"))
        assertTrue(instruction.contains("not JSON"))
        assertTrue(instruction.contains("-- MOSAICOBOT_END"))
    } }
    @Test fun rawLuaContinuationPreservesEscapeAtSplitBoundary() = withService { server,agent,config -> runBlocking {
        val lua="-- interaction: app\n"+LuaNetworkPolicyTest.lua
        val full=ArtifactTest.rawResponse(lua)
        val split=full.indexOf("\\n")+1
        assertTrue(split>0)
        server.enqueue(MockResponse().setBody(reply(full.take(split),"length")))
        server.enqueue(MockResponse().setBody(reply(full.drop(split))))
        assertEquals(lua,agent.generate(config,"contract","天气时钟",null,{}).lua)
        server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(full.take(split),messages.getJSONObject(2).getString("content"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("Preserve raw Lua"))
    } }
    @Test fun missingEndMarkerContinuesEvenWhenProviderSaysStop() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.rawResponse()
        server.enqueue(MockResponse().setBody(reply(full.removeSuffix("\n-- MOSAICOBOT_END"))))
        server.enqueue(MockResponse().setBody(reply("\n-- MOSAICOBOT_END")))
        val stages=mutableListOf<String>()
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{stages.add(it)}).lua)
        assertEquals(2,server.requestCount)
        assertTrue(stages.any { it.contains("自动续写") })
        assertFalse(stages.any { it.contains("完整修复") })
    } }
    @Test fun restartedButTruncatedRawAppReplacesOldPrefix() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.rawResponse(); val split=full.length/2
        server.enqueue(MockResponse().setBody(reply("{\"lua\":\"old prefix", "length")))
        server.enqueue(MockResponse().setBody(reply(full.take(split),"length")))
        server.enqueue(MockResponse().setBody(reply(full.drop(split))))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        server.takeRequest();server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(full.take(split),messages.getJSONObject(2).getString("content"))
        assertFalse(messages.getJSONObject(2).getString("content").contains("old prefix"))
    } }
    @Test fun standaloneReplacementWithBadLuaIsRepairedWithoutOldPrefix() = withService { server,agent,config -> runBlocking {
        val bad=ArtifactTest.response(ArtifactTest.lua+"\nif then")
        server.enqueue(MockResponse().setBody(reply("{\"lua\":\"old prefix", "length")))
        server.enqueue(MockResponse().setBody(reply(bad)))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        server.takeRequest();server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(bad,messages.getJSONObject(2).getString("content"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("Lua 语法检查失败"))
    } }
    @Test fun repeatedRawPrefixWithNoProgressTriggersFullRepair() = withService { server,agent,config -> runBlocking {
        val prefix=ArtifactTest.rawResponse().take(150)
        repeat(2) { server.enqueue(MockResponse().setBody(reply(prefix,"length"))) }
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        server.takeRequest();server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(prefix,messages.getJSONObject(2).getString("content"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("COMPLETE RAW LUA"))
    } }
    @Test fun truncatedRawRepairAlsoContinues() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.rawResponse();val split=full.length/2
        server.enqueue(MockResponse().setBody(reply("bad packaging")))
        server.enqueue(MockResponse().setBody(reply(full.take(split),"length")))
        server.enqueue(MockResponse().setBody(reply(full.drop(split))))
        val previous=ArtifactTest.work()
        val work=agent.generate(config,"contract","保留并修改",previous,{})
        assertEquals(previous.id,work.id);assertEquals(2,work.version)
        assertEquals(ArtifactTest.lua,work.lua)
        assertEquals(3,server.requestCount)
    } }
    @Test fun duplicateRawAppsAreRewrittenNotSilentlySelected() = withService { server,agent,config -> runBlocking {
        val full=ArtifactTest.rawResponse()
        server.enqueue(MockResponse().setBody(reply(full+"\n"+full)))
        server.enqueue(MockResponse().setBody(reply(full)))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","x",null,{}).lua)
        assertEquals(2,server.requestCount)
    } }
    @Test fun rawContinuationRefusalStopsImmediately() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse().take(150),"length")))
        server.enqueue(MockResponse().setBody(reply("""{"refusal":"不支持此能力"}""")))
        assertTrue(runCatching { agent.generate(config,"contract","x",null,{}) }.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(2,server.requestCount)
    } }
    @Test fun revisionIncludesPreviousSource() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.response())))
        val previous=ArtifactTest.work()
        val revised=agent.generate(config,"contract","改成橙色",previous,{})
        assertEquals(previous.id,revised.id);assertEquals(2,revised.version)
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertTrue(messages.getJSONObject(1).getString("content").contains(previous.lua))
        assertEquals("改成橙色",messages.getJSONObject(2).getString("content"))
    } }
    @Test fun connectionCheckUsesSmallAuthenticatedRequest() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("OK")))
        agent.checkConnection(config)
        val request=server.takeRequest()
        assertEquals("Bearer fake-test-key",request.getHeader("Authorization"))
        val body=JSONObject(request.body.readUtf8())
        assertEquals(64,body.getInt("max_tokens"))
        assertEquals(1,body.getJSONArray("messages").length())
    } }
    @Test fun connectionCheckRejectsEmptyReply() = withService { server,agent,config -> runBlocking {
        server.enqueue(MockResponse().setBody(reply("")))
        assertTrue(runCatching { agent.checkConnection(config) }.isFailure)
    } }
    @Test fun freeCreationReceivesDefaultDualInputContract() = withService { server,agent,config -> runBlocking {
        val contract=java.io.File("src/main/assets/agent-contract.txt").readText()
        val lua=GeneratedGamePolicyTest.dual()
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(lua))))
        assertEquals(lua,agent.generate(config,contract,"做一个坦克游戏",null,{}).lua)
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(contract,messages.getJSONObject(0).getString("content"))
        assertTrue(contract.contains("EVERY new game by default"))
        assertTrue(contract.contains("Never wait for a phone"))
        assertTrue(contract.contains("ONE shared game state/action path"))
        assertEquals("做一个坦克游戏",messages.getJSONObject(1).getString("content"))
    } }
    @Test fun missingGamepadIsFullyRepairedEvenWithLengthFinishReason() = withService { server,agent,config -> runBlocking {
        val incompleteControls=GeneratedGamePolicyTest.touch.replace("game-touch","game-dual")
        val repaired=GeneratedGamePolicyTest.dual()
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(incompleteControls),"length")))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(repaired))))
        val stages=mutableListOf<String>()
        assertEquals(repaired,agent.generate(config,"contract","生成游戏",null,{stages.add(it)}).lua)
        assertEquals(2,server.requestCount)
        assertTrue(stages.any {it.contains("完整修复")});assertFalse(stages.any {it.contains("自动续写")})
        server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        val instruction=messages.getJSONObject(3).getString("content")
        assertTrue(instruction.contains("缺少手机手柄"));assertTrue(instruction.contains("COMPLETE RAW LUA"))
        assertTrue(instruction.contains("do not reclassify a game"))
        assertEquals("生成游戏",messages.getJSONObject(1).getString("content"))
    } }
    @Test fun missingTouchIsRepairedWithoutDiscardingExistingWork() = withService { server,agent,config -> runBlocking {
        val good=GeneratedGamePolicyTest.dual()
        val previous=ArtifactTest.work().copy(lua=good)
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(good.replace("screen:touch()","touchSnapshot")))))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(good))))
        val work=agent.generate(config,"contract","改成蓝色，保留玩法",previous,{})
        assertEquals(previous.id,work.id);assertEquals(2,work.version);assertEquals(good,work.lua)
        server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertTrue(messages.getJSONObject(1).getString("content").contains(good))
        assertTrue(messages.getJSONObject(4).getString("content").contains("screen:touch()"))
    } }
    @Test fun replacementDuringContinuationCannotBypassGameControlChecks() = withService { server,agent,config -> runBlocking {
        val good=ArtifactTest.rawResponse(GeneratedGamePolicyTest.dual())
        val bad=ArtifactTest.rawResponse(GeneratedGamePolicyTest.dual().replace("pad.close()",""))
        server.enqueue(MockResponse().setBody(reply(good.take(120),"length")))
        server.enqueue(MockResponse().setBody(reply(bad)))
        server.enqueue(MockResponse().setBody(reply(good)))
        assertEquals(GeneratedGamePolicyTest.dual(),agent.generate(config,"contract","坦克",null,{}).lua)
        assertEquals(3,server.requestCount)
        server.takeRequest();server.takeRequest()
        val messages=JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(bad,messages.getJSONObject(2).getString("content"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("pad.close()"))
    } }
    @Test fun explicitTouchOnlyGameIsAcceptedWithoutForcedController() = withService { server,agent,config -> runBlocking {
        val lua=GeneratedGamePolicyTest.touch
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(lua))))
        assertEquals(lua,agent.generate(config,"contract","做一个仅触屏游戏，不用手柄",null,{}).lua)
        assertEquals(1,server.requestCount)
    } }
    @Test fun missingInteractionDeclarationIsRepairedOnlyForGeneration() = withService { server,agent,config -> runBlocking {
        val legacy=ArtifactTest.lua.removePrefix("-- interaction: app\n")
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(legacy))))
        server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse())))
        assertEquals(ArtifactTest.lua,agent.generate(config,"contract","生成时钟",null,{}).lua)
        assertEquals(2,server.requestCount)
    } }
    @Test fun repeatedMissingControlsNeverReturnAnIncompleteGame() = withService { server,agent,config -> runBlocking {
        val bad=GeneratedGamePolicyTest.dual().replace("pad.close()","")
        repeat(3) {server.enqueue(MockResponse().setBody(reply(ArtifactTest.rawResponse(bad))))}
        val error=runCatching {agent.generate(config,"contract","游戏",null,{})}.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("未保存残缺应用")==true)
        assertEquals(3,server.requestCount)
    } }
    @Test fun insecureModelEndpointRejectedBeforeSending() = runBlocking {
        assertTrue(runCatching { AgentClient().generate(AgentConfig("http://localhost/v1","m","k"),"contract","x",null,{}) }.isFailure)
    }
}
