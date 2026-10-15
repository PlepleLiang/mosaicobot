package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test

class LanSubnetTest {
    @Test fun privateIpv4Only() {
        listOf("10.2.3.4", "172.16.0.2", "172.31.255.254", "192.168.137.67").forEach { assertTrue(LanSubnet.isPrivate(it)) }
        listOf("172.15.1.1", "172.32.1.1", "8.8.8.8", "127.0.0.1", "169.254.1.2", "1.2.3", "256.1.1.1", "192.168.1.-1", "::1").forEach { assertFalse(LanSubnet.isPrivate(it)) }
    }
    @Test fun scanExcludesSelfNetworkBroadcastAndOtherSubnets() {
        val lan = LanSubnet("192.168.137.67", 24)
        assertEquals(253, lan.nearbyHosts().size)
        assertTrue(lan.nearbyHosts().contains("192.168.137.246"))
        listOf("192.168.137.0", "192.168.137.255", "192.168.137.67", "192.168.1.2").forEach { assertFalse(lan.contains(it)) }
    }
    @Test fun narrowSubnetRemainsNarrow() {
        val lan = LanSubnet("10.1.2.5", 30)
        assertEquals(listOf("10.1.2.6"), lan.nearbyHosts())
        assertFalse(lan.contains("10.1.2.7"))
    }
    @Test fun largeSubnetUsesBoundedFallbackButAcceptsOnLinkMdns() {
        val lan = LanSubnet("10.2.3.4", 16)
        assertEquals(253, lan.nearbyHosts().size)
        assertTrue(lan.nearbyHosts().all { it.startsWith("10.2.3.") })
        assertTrue(lan.contains("10.2.9.7"))
        assertFalse(lan.contains("10.3.9.7"))
    }
    @Test fun unsupportedNetworksDoNotProduceTargets() {
        listOf(LanSubnet("8.8.8.8",24),LanSubnet("10.1.1.2",0),LanSubnet("10.1.1.2",33),LanSubnet("::1",64)).forEach { assertTrue(it.nearbyHosts().isEmpty()) }
    }
}
