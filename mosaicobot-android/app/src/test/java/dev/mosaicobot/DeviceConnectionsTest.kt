package dev.mosaicobot

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceConnectionsTest {
    private fun device(ip: String) = DeviceInfo("http://" + ip,"/nand",setOf("display","delay","system"))
    private class FakeNetwork(override val key: String, val result: DeviceInfo? = null) : DiscoveryNetwork {
        override val label = key
        var calls = 0
        var preferred = ""
        var late: ((DeviceInfo) -> Unit)? = null
        var wait = false
        override suspend fun discover(preferred: String, found: (DeviceInfo) -> Unit) {
            calls++; this.preferred = preferred; late = found
            result?.let(found)
            if (wait) awaitCancellation()
        }
        override suspend fun inspect(address: String) = result ?: error("not a Claw device")
    }
    @Test fun foregroundAndNetworkAvailabilityTriggerAutoConnection() = runTest {
        val expected = device("192.168.1.3")
        val saved = mutableListOf<String>()
        val c = DeviceConnections(this,"http://192.168.1.3",true,saved::add)
        c.foreground(true)
        val net = FakeNetwork("wifi-1",expected)
        c.networkChanged(net)
        advanceUntilIdle()
        assertEquals(expected,c.state.value.device)
        assertEquals(listOf(expected.address),saved)
        assertEquals(expected.address,net.preferred)
        assertFalse(c.state.value.scanning)
    }
    @Test fun unchangedCallbacksDoNotStartScanStorm() = runTest {
        val net = FakeNetwork("wifi-1",device("192.168.1.3"))
        val c = DeviceConnections(this,"",true,{})
        c.foreground(true); c.networkChanged(net)
        repeat(10) { c.networkChanged(FakeNetwork("wifi-1")) }
        advanceUntilIdle()
        assertEquals(1,net.calls)
    }
    @Test fun lostNetworkClearsConnectionAndRejectsLateResults() = runTest {
        val net = FakeNetwork("wifi-1",device("192.168.1.3")).apply { wait=true }
        val c = DeviceConnections(this,"",true,{})
        c.foreground(true); c.networkChanged(net); advanceTimeBy(701); runCurrent()
        assertNotNull(c.state.value.device)
        c.networkChanged(null)
        net.late!!(device("192.168.1.99"))
        runCurrent()
        assertNull(c.state.value.device); assertTrue(c.state.value.found.isEmpty()); assertFalse(c.state.value.scanning)
    }
    @Test fun networkSwitchAcceptsOnlyNewNetworkResults() = runTest {
        val old = FakeNetwork("old",device("192.168.1.3")).apply { wait=true }
        val next = FakeNetwork("new",device("192.168.2.5"))
        val c = DeviceConnections(this,"",true,{})
        c.foreground(true);c.networkChanged(old);advanceTimeBy(701);runCurrent()
        c.networkChanged(next);old.late!!(device("192.168.1.99"));advanceUntilIdle()
        assertEquals(next.result,c.state.value.device)
        assertEquals(listOf(next.result),c.state.value.found)
    }
    @Test fun manualScanningWorksWithAutomaticDisabled() = runTest {
        val net = FakeNetwork("wifi",device("192.168.1.3"))
        val c = DeviceConnections(this,"",false,{})
        c.foreground(true);c.networkChanged(net);advanceUntilIdle()
        assertEquals(0,net.calls)
        c.scan();advanceUntilIdle()
        assertEquals(net.result,c.state.value.device)
    }
    @Test fun stopAndBackgroundCancelPendingDiscovery() = runTest {
        val net = FakeNetwork("wifi").apply { wait=true }
        val c = DeviceConnections(this,"",true,{})
        c.foreground(true);c.networkChanged(net);advanceTimeBy(701);runCurrent()
        c.stop();net.late!!(device("192.168.1.3"));runCurrent()
        assertNull(c.state.value.device);assertFalse(c.state.value.scanning)
        c.scan();runCurrent();c.foreground(false);net.late!!(device("192.168.1.4"));runCurrent()
        assertNull(c.state.value.device);assertFalse(c.state.value.scanning)
    }
    @Test fun duplicateDiscoveryDoesNotSwitchAutoSelectedDevice() = runTest {
        val first = device("192.168.1.3")
        val net = FakeNetwork("wifi",first).apply { wait=true }
        val c = DeviceConnections(this,"",true,{})
        c.foreground(true);c.networkChanged(net);advanceTimeBy(701);runCurrent()
        net.late!!(first);net.late!!(device("192.168.1.4"))
        assertEquals(2,c.state.value.found.size);assertEquals(first,c.state.value.device)
        c.stop()
    }
    @Test fun manualConnectionFailureNeverClaimsConnected() = runTest {
        val c = DeviceConnections(this,"",false,{})
        c.foreground(true);c.networkChanged(FakeNetwork("wifi"));c.connect("192.168.1.99");advanceUntilIdle()
        assertNull(c.state.value.device);assertFalse(c.state.value.scanning)
    }
}
