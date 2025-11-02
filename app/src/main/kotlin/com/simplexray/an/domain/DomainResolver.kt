package com.simplexray.an.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DomainResolver(private val scope: CoroutineScope) {
    private val cache = ConcurrentHashMap<String, String?>()

    fun resolveAsync(ip: String, onResult: (String, String?) -> Unit) {
        if (cache.containsKey(ip)) {
            onResult(ip, cache[ip])
            return
        }
        scope.launch(Dispatchers.IO) {
            val host = try {
                val addr = InetAddress.getByName(ip)
                val name = addr.canonicalHostName
                if (name != null && name != ip) name else null
            } catch (_: Throwable) { null }
            cache[ip] = host
            onResult(ip, host)
        }
    }
}

