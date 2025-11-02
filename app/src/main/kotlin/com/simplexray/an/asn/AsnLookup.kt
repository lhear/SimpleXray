package com.simplexray.an.asn

import android.content.Context
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

data class AsnInfo(val asn: Int, val org: String)

class AsnLookup(private val context: Context) {
    private val cache = ConcurrentHashMap<String, AsnInfo?>()
    @Volatile private var loaded = false
    private val ipv4Table = mutableListOf<Ipv4Net>()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                context.assets.open("asn_v4.csv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val t = line.trim()
                        if (t.isEmpty() || t.startsWith("#")) return@forEach
                        val parts = t.split(',')
                        if (parts.size < 3) return@forEach
                        val prefix = parts[0].trim()
                        val asn = parts[1].trim().toIntOrNull() ?: return@forEach
                        val org = parts.subList(2, parts.size).joinToString(",").trim()
                        parseIpv4(prefix)?.let { net ->
                            ipv4Table.add(Ipv4Net(net.first, net.second, AsnInfo(asn, org)))
                        }
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
            loaded = true
        }
    }

    fun lookup(ip: String): AsnInfo? {
        cache[ip]?.let { return it }
        ensureLoaded()
        val addr = parseIpv4Address(ip) ?: return null.also { cache[ip] = null }
        val res = ipv4Table.firstOrNull { it.contains(addr) }?.info
        cache[ip] = res
        return res
    }

    private data class Ipv4Net(val network: Int, val mask: Int, val info: AsnInfo) {
        fun contains(addr: Int): Boolean = (addr and mask) == network
    }

    private fun parseIpv4(cidr: String): Pair<Int, Int>? {
        val parts = cidr.split('/')
        if (parts.size != 2) return null
        val addr = parseIpv4Address(parts[0]) ?: return null
        val pfxLen = parts[1].toIntOrNull() ?: return null
        if (pfxLen !in 0..32) return null
        val mask = if (pfxLen == 0) 0 else (-0x1 shl (32 - pfxLen))
        val net = addr and mask
        return net to mask
    }

    private fun parseIpv4Address(ip: String): Int? {
        return try {
            val a = InetAddress.getByName(ip)
            if (a is Inet4Address) {
                val b = a.address
                ((b[0].toInt() and 0xFF) shl 24) or
                    ((b[1].toInt() and 0xFF) shl 16) or
                    ((b[2].toInt() and 0xFF) shl 8) or
                    (b[3].toInt() and 0xFF)
            } else null
        } catch (_: Throwable) {
            null
        }
    }
}

