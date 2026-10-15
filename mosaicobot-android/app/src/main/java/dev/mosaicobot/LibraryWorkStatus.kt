package dev.mosaicobot

internal fun libraryWorkStatus(work: Work, device: DeviceInfo?, runtime: WorkRuntime?): String {
    if (device == null) return if (work.uploadedTo == null) "已保存 · 待部署" else "已传输 · 设备未连接"
    val current = runtime?.takeIf { it.id == work.packageId && it.address == device.address }
    return when (current?.status) {
        "running" -> "设备运行中"
        "queued" -> "设备正在启动"
        "stopping" -> "设备正在停止"
        "stopped" -> "已安装"
        "failed" -> "启动失败 · 可重试"
        "not_found" -> "待发布到 Works"
        else -> if (work.uploadedTo == device.address) "已传输 · 状态待确认" else "已保存 · 待部署"
    }
}
