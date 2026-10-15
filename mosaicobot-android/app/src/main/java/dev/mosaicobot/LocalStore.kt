package dev.mosaicobot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val worksFile = AtomicFile(File(context.filesDir, "works.json"))
    private val centerFile = AtomicFile(File(context.filesDir, "app-center.json"))
    private val alias = "mosaicobot-agent-key-v1"
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun config(): AgentConfig = AgentConfig(
        preferences.getString("base", null) ?: AgentConfig().baseUrl,
        preferences.getString("model", null) ?: AgentConfig().model,
        // A lost/invalid Keystore entry must not hide works or prevent entering a replacement key.
        runCatching { decrypt(preferences.getString("encrypted-key", "") ?: "") }.getOrDefault(""),
        if (BuildConfig.DEBUG) preferences.getInt("debug-proxy-port", 0).takeIf { it in 1..65535 } else null,
    )
    fun saveConfig(config: AgentConfig) {
        require(preferences.edit().putString("base", config.baseUrl).putString("model", config.model)
            .putInt("debug-proxy-port", if (BuildConfig.DEBUG) config.debugProxyPort ?: 0 else 0)
            .putString("encrypted-key", encrypt(config.apiKey)).commit()) { "模型配置保存失败" }
    }
    fun address() = preferences.getString("device-address", "") ?: ""
    fun saveAddress(address: String) { preferences.edit().putString("device-address", address).apply() }
    fun automaticScan() = preferences.getBoolean("automatic-device-scan", true)
    fun saveAutomaticScan(enabled: Boolean) { preferences.edit().putBoolean("automatic-device-scan", enabled).apply() }
    fun readWorks(): List<Work> {
        if (!worksFile.baseFile.exists()) return emptyList()
        val array = JSONArray(String(worksFile.readFully(), Charsets.UTF_8))
        return (0 until array.length()).map { Work.fromJson(array.getJSONObject(it)) }
    }
    fun saveWorks(works: List<Work>) {
        val array = JSONArray().apply { works.forEach { put(it.json()) } }
        val stream = worksFile.startWrite()
        try { stream.write(array.toString().toByteArray()); worksFile.finishWrite(stream) }
        catch (e: Exception) { worksFile.failWrite(stream); throw e }
    }
    fun readCenter(): List<CenterApp> = if (centerFile.baseFile.exists()) AppCenter.decode(String(centerFile.readFully(), Charsets.UTF_8)) else emptyList()
    fun saveCenter(apps: List<CenterApp>) {
        val content=AppCenter.encode(apps).toByteArray(Charsets.UTF_8)
        val stream=centerFile.startWrite()
        try { stream.write(content); centerFile.finishWrite(stream) }
        catch (e: Exception) { centerFile.failWrite(stream); throw e }
    }
    // Debug-only provisioning via adb run-as. No exported receiver/provider and no APK secret.
    fun importDebugBootstrap() {
        if (!BuildConfig.DEBUG) return
        val file = File(context.filesDir, "bootstrap.local.json")
        if (!file.exists()) return
        try {
            val j = JSONObject(file.readText())
            saveConfig(AgentConfig(j.getString("baseUrl"), j.getString("model"), j.getString("apiKey"),
                j.optInt("debugProxyPort", 0).takeIf { it in 1..65535 }))
        } finally { check(file.delete()) { "临时配置清理失败" } }
    }
}
