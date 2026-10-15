package dev.mosaicobot

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Called only after the user confirms running this version and switching phone-created works. */
internal suspend fun DeviceClient.launchWork(
    device: DeviceInfo,
    work: Work,
    stage: (String) -> Unit,
    onPrepared: suspend (Work) -> Unit,
): WorkRuntime? {
    stage("检查设备上已有的作品版本")
    val current = runtime(device, work) // Fail before writing when Works is unavailable.
    if (current.status != "not_found" || work.uploadedTo == device.address) {
        stage("核对已部署版本，不重复上传")
        verifyWork(device, work)
    } else {
        uploadWork(device, work, stage) // Reuses matching DATA files if publication was interrupted.
    }
    onPrepared(work.copy(uploadedTo = device.address))
    if (current.status == "running") return current
    if (current.status == "queued") return awaitWorkState({ runtime(device, work) }, "running")
    if (current.status == "not_found") {
        stage("注册新作品到 Works")
        command(device, work, "publish")
    }
    if (current.status == "stopping") {
        stage("等待此作品停止后再次运行")
        check(awaitWorkIdle { runtime(device, work) }) { "作品尚未停止，未发送新的启动请求" }
    }
    stage("检查屏幕占用，准备切换作品")
    val active = withTimeoutOrNull(20_000) { activePhoneWorks(device, work.packageId) }
        ?: error("检查设备当前作品超时，未停止其他作品或发送启动请求")
    for (other in active) {
        stage("停止之前的手机作品，等待屏幕释放")
        if (other.status != "stopping") commandById(device, other.id, "stop")
        check(awaitWorkIdle { runtimeById(device, other.id) }) {
            "之前的作品尚未停止，未启动新作品；请在设备上确认，不会强制终止或重复部署"
        }
    }
    stage("启动作品，等待设备运行状态")
    command(device, work, "start") // A race/native work may still conflict; never blindly retry writes.
    return awaitWorkState({ runtime(device, work) }, "running")
}

internal suspend fun awaitWorkIdle(read: suspend () -> WorkRuntime): Boolean = withTimeoutOrNull(20_000) {
    var state = read()
    while (state.active) {
        delay(700)
        state = read()
    }
    check(state.status in setOf("stopped", "failed")) { "作品停止状态未知，未继续启动新作品" }
    true
} ?: false
