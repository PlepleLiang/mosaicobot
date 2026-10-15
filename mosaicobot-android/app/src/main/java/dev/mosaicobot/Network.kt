package dev.mosaicobot

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun networkClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(150, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

// Non-streaming code generation can take longer than a device file request. Still cancellable and bounded.
fun agentNetworkClient(): OkHttpClient = networkClient().newBuilder()
    .readTimeout(300, TimeUnit.SECONDS).callTimeout(360, TimeUnit.SECONDS).build()

// Debug USB CONNECT tunnel only. TLS and hostname checks remain end-to-end; device HTTP never uses it.
internal fun modelClient(client: OkHttpClient, config: AgentConfig): OkHttpClient {
    val port = config.debugProxyPort ?: return client
    require(BuildConfig.DEBUG && port in 1..65535) { "USB 开发代理配置无效" }
    val host = config.baseUrl.toHttpUrl().host
    require(host == "models-proxy.stepfun-inc.com") { "USB 开发代理仅用于配置的 StepGo 内网模型服务" }
    return client.newBuilder().proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", port))).build()
}

class HttpStatusException(val statusCode: Int, message: String) : IOException(message)

suspend fun OkHttpClient.fetch(request: Request, maxBytes: Int = 512 * 1024): String = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            val message = when (e) {
                is java.net.UnknownHostException -> "无法解析服务地址，请检查 DNS 或网络连接"
                is javax.net.ssl.SSLException -> "HTTPS 证书校验失败，请检查设备时间与证书；未绕过安全验证"
                is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "请求超时，未保存残缺作品；请检查网络或简化需求后重试"
                is java.net.ConnectException -> "无法连接服务，请检查地址、端口与局域网连接"
                else -> "网络连接中断，未自动重试；请检查网络后重试"
            }
            if (continuation.isActive) continuation.resumeWithException(IOException(message))
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                try {
                    if (!it.isSuccessful) {
                        // Never reflect the request, credential or arbitrary server payload into logs/UI.
                        throw HttpStatusException(it.code, when (it.code) {
                            401 -> "HTTP 401：Key 无效或已过期"
                            403 -> "HTTP 403：当前 Key 无权访问该模型或服务"
                            404 -> "HTTP 404：目标接口或目录不存在"
                            429 -> "HTTP 429：请求限流，请稍后重试"
                            else -> "服务返回 HTTP "+it.code
                        })
                    }
                    val body = it.body ?: throw IOException("服务返回空响应")
                    val bytes = body.byteStream().use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (out.size() <= maxBytes) {
                            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - out.size()))
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    }
                    require(bytes.size <= maxBytes) { "响应超过大小限制" }
                    if (continuation.isActive) continuation.resume(String(bytes, Charsets.UTF_8))
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        }
    })
}
