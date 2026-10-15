package dev.mosaicobot

/** IPv4 only. Automatic probes never leave the selected, private on-link subnet. */
data class LanSubnet(val address: String, val prefix: Int) {
    private val ip = number(address)
    private val mask = if (prefix in 1..32) (0xffffffffL shl (32 - prefix)) and 0xffffffffL else 0L
    val valid = ip != null && isPrivate(address) && prefix in 1..30
    val label get() = "$address/$prefix"
    fun contains(host: String): Boolean {
        val other = number(host) ?: return false
        if (!valid || !isPrivate(host) || other == ip) return false
        val base = ip!! and mask
        val broadcast = base or (mask xor 0xffffffffL)
        return (other and mask) == base && other != base && other != broadcast
    }
    // Bounded fallback for legacy firmware with no mDNS; never sweep a /8 or /16.
    fun nearbyHosts(): List<String> {
        if (!valid) return emptyList()
        val scanMask = if (prefix < 24) 0xffffff00L else mask
        val first = ip!! and scanMask
        val last = first or (scanMask xor 0xffffffffL)
        return (first + 1 until last).map(::text).filter(::contains)
    }
    companion object {
        fun number(host: String): Long? {
            val parts = host.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            if (nums.any { it !in 0..255 }) return null
            return nums.fold(0L) { result, n -> (result shl 8) or n.toLong() }
        }
        fun text(ip: Long) = (3 downTo 0).joinToString(".") { ((ip shr (it * 8)) and 255).toString() }
        fun isPrivate(host: String): Boolean {
            val n = number(host) ?: return false
            return n shr 24 == 10L || n shr 20 == 0xac1L || n shr 16 == 0xc0a8L
        }
    }
}
