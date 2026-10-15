package dev.mosaicobot

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryWorkStatusTest {
    private val work = ArtifactTest.work()
    private val device = DeviceInfo("http://192.168.1.80", "/nand", setOf("display", "delay", "system"))
    private fun state(status: String) = WorkRuntime(device.address, work.packageId, status)

    @Test fun matchingDeviceRuntimeShowsActualState() {
        mapOf("running" to "设备运行中", "queued" to "设备正在启动", "stopping" to "设备正在停止",
            "stopped" to "已安装", "failed" to "启动失败 · 可重试", "not_found" to "待发布到 Works").forEach { (status, label) ->
            assertEquals(label, libraryWorkStatus(work, device, state(status)))
        }
    }
    @Test fun transferFlagAloneNeverClaimsInstalledOrRunning() {
        assertEquals("已保存 · 待部署", libraryWorkStatus(work, device, null))
        assertEquals("已传输 · 状态待确认", libraryWorkStatus(work.copy(uploadedTo=device.address), device, null))
        assertEquals("已保存 · 待部署", libraryWorkStatus(work.copy(uploadedTo="http://192.168.1.90"), device, null))
    }
    @Test fun otherVersionOrDeviceRuntimeIsIgnored() {
        assertEquals("已保存 · 待部署", libraryWorkStatus(work, device, state("running").copy(id="another-version")))
        assertEquals("已保存 · 待部署", libraryWorkStatus(work, device, state("running").copy(address="http://192.168.1.90")))
    }
    @Test fun disconnectDoesNotLeaveStaleRunningLabel() {
        assertEquals("已传输 · 设备未连接", libraryWorkStatus(work.copy(uploadedTo=device.address), null, state("running")))
        assertEquals("已保存 · 待部署", libraryWorkStatus(work, null, null))
    }
}
