package com.simplexray.an.asn

object AsnCdnMap {
    private val cdnAsns = mapOf(
        13335 to "Cloudflare",
        54113 to "Fastly",
        15169 to "Google",
        8075 to "Microsoft",
        16509 to "Amazon"
    )

    fun isCdn(info: AsnInfo?): Boolean = info?.asn?.let { cdnAsns.containsKey(it) } == true
    fun name(info: AsnInfo?): String? = info?.asn?.let { cdnAsns[it] }
}

