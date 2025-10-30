package com.simplexray.an.protocol.routing

/**
 * Advanced routing with split tunneling 2.0
 */
class AdvancedRouter {

    /**
     * Routing rule with multiple match criteria
     */
    data class RoutingRule(
        val id: String,
        val name: String,
        val enabled: Boolean = true,
        val priority: Int = 0, // Higher = evaluated first
        val action: RoutingAction,
        val matchers: List<RoutingMatcher>
    ) {
        fun matches(context: RoutingContext): Boolean {
            return matchers.all { it.matches(context) }
        }
    }

    /**
     * Routing action
     */
    sealed class RoutingAction {
        data object Proxy : RoutingAction()
        data object Direct : RoutingAction()
        data object Block : RoutingAction()
        data class CustomProxy(val proxyId: String) : RoutingAction()
    }

    /**
     * Routing matcher types
     */
    sealed class RoutingMatcher {
        abstract fun matches(context: RoutingContext): Boolean

        /**
         * Match by app package name
         */
        data class AppMatcher(val packages: List<String>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                return context.packageName in packages
            }
        }

        /**
         * Match by domain (with wildcard support)
         */
        data class DomainMatcher(val domains: List<String>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                val domain = context.domain ?: return false
                return domains.any { pattern ->
                    matchesDomain(domain, pattern)
                }
            }

            private fun matchesDomain(domain: String, pattern: String): Boolean {
                return when {
                    pattern.startsWith("*.") -> {
                        // Wildcard subdomain
                        val suffix = pattern.substring(2)
                        domain.endsWith(suffix) || domain == suffix.removePrefix(".")
                    }
                    pattern.startsWith("*") -> {
                        domain.contains(pattern.substring(1))
                    }
                    else -> domain.equals(pattern, ignoreCase = true)
                }
            }
        }

        /**
         * Match by IP address or CIDR range
         */
        data class IpMatcher(val ipRanges: List<IpRange>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                val ip = context.destinationIp ?: return false
                return ipRanges.any { it.contains(ip) }
            }
        }

        /**
         * Match by port or port range
         */
        data class PortMatcher(val ports: List<PortRange>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                val port = context.destinationPort ?: return false
                return ports.any { it.contains(port) }
            }
        }

        /**
         * Match by protocol
         */
        data class ProtocolMatcher(val protocols: List<Protocol>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                return context.protocol in protocols
            }
        }

        /**
         * Match by GeoIP country
         */
        data class GeoIpMatcher(val countries: List<String>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                val country = context.geoCountry ?: return false
                return country in countries
            }
        }

        /**
         * Match by time range
         */
        data class TimeMatcher(
            val startHour: Int,
            val endHour: Int,
            val daysOfWeek: List<Int>? = null // 1=Monday, 7=Sunday
        ) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                val calendar = java.util.Calendar.getInstance()
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

                val hourMatches = hour in startHour until endHour
                val dayMatches = daysOfWeek == null || dayOfWeek in daysOfWeek

                return hourMatches && dayMatches
            }
        }

        /**
         * Match by network type
         */
        data class NetworkTypeMatcher(val networkTypes: List<NetworkType>) : RoutingMatcher() {
            override fun matches(context: RoutingContext): Boolean {
                return context.networkType in networkTypes
            }

            enum class NetworkType {
                WIFI,
                CELLULAR,
                ETHERNET,
                VPN
            }
        }
    }

    /**
     * Routing context for rule evaluation
     */
    data class RoutingContext(
        val packageName: String?,
        val domain: String?,
        val destinationIp: String?,
        val destinationPort: Int?,
        val protocol: Protocol,
        val geoCountry: String? = null,
        val networkType: RoutingMatcher.NetworkTypeMatcher.NetworkType? = null
    )

    /**
     * Protocol type
     */
    enum class Protocol {
        TCP,
        UDP,
        ICMP,
        ANY
    }

    /**
     * IP range (CIDR notation)
     */
    data class IpRange(val cidr: String) {
        private val ipAddress: String
        private val prefixLength: Int

        init {
            val parts = cidr.split("/")
            ipAddress = parts[0]
            prefixLength = parts.getOrNull(1)?.toIntOrNull() ?: 32
        }

        fun contains(ip: String): Boolean {
            return try {
                val rangeBytes = ipToBytes(ipAddress)
                val testBytes = ipToBytes(ip)

                if (rangeBytes.size != testBytes.size) return false

                val mask = createMask(prefixLength, rangeBytes.size)

                rangeBytes.indices.all { i ->
                    (rangeBytes[i] and mask[i]) == (testBytes[i] and mask[i])
                }
            } catch (e: Exception) {
                false
            }
        }

        private fun ipToBytes(ip: String): ByteArray {
            return ip.split(".").map { it.toInt().toByte() }.toByteArray()
        }

        private fun createMask(prefixLength: Int, byteCount: Int): ByteArray {
            val mask = ByteArray(byteCount)
            var remaining = prefixLength

            for (i in mask.indices) {
                mask[i] = when {
                    remaining >= 8 -> 0xFF.toByte()
                    remaining > 0 -> (0xFF shl (8 - remaining)).toByte()
                    else -> 0x00.toByte()
                }
                remaining = maxOf(0, remaining - 8)
            }

            return mask
        }
    }

    /**
     * Port range
     */
    data class PortRange(val start: Int, val end: Int = start) {
        fun contains(port: Int): Boolean {
            return port in start..end
        }

        companion object {
            fun single(port: Int) = PortRange(port, port)
        }
    }

    /**
     * Policy-based routing engine
     */
    class RoutingEngine {
        private val rules = mutableListOf<RoutingRule>()

        fun addRule(rule: RoutingRule) {
            rules.add(rule)
            rules.sortByDescending { it.priority }
        }

        fun removeRule(ruleId: String) {
            rules.removeAll { it.id == ruleId }
        }

        fun updateRule(rule: RoutingRule) {
            val index = rules.indexOfFirst { it.id == rule.id }
            if (index != -1) {
                rules[index] = rule
                rules.sortByDescending { it.priority }
            }
        }

        fun findMatchingRule(context: RoutingContext): RoutingRule? {
            return rules
                .filter { it.enabled }
                .firstOrNull { it.matches(context) }
        }

        fun getAllRules(): List<RoutingRule> = rules.toList()

        fun clearRules() {
            rules.clear()
        }

        /**
         * Get routing decision
         */
        fun route(context: RoutingContext): RoutingAction {
            val matchedRule = findMatchingRule(context)
            return matchedRule?.action ?: RoutingAction.Proxy // Default to proxy
        }
    }

    /**
     * Pre-defined rule templates
     */
    object RuleTemplates {
        /**
         * Bypass China mainland IPs
         */
        fun bypassChinaMainland(): RoutingRule {
            return RoutingRule(
                id = "bypass_china",
                name = "Bypass China Mainland",
                action = RoutingAction.Direct,
                matchers = listOf(
                    RoutingMatcher.GeoIpMatcher(listOf("CN"))
                )
            )
        }

        /**
         * Bypass private IPs (LAN)
         */
        fun bypassPrivateIps(): RoutingRule {
            return RoutingRule(
                id = "bypass_lan",
                name = "Bypass LAN",
                action = RoutingAction.Direct,
                matchers = listOf(
                    RoutingMatcher.IpMatcher(
                        listOf(
                            IpRange("10.0.0.0/8"),
                            IpRange("172.16.0.0/12"),
                            IpRange("192.168.0.0/16"),
                            IpRange("127.0.0.0/8")
                        )
                    )
                )
            )
        }

        /**
         * Block ads and trackers
         */
        fun blockAds(): RoutingRule {
            return RoutingRule(
                id = "block_ads",
                name = "Block Ads",
                action = RoutingAction.Block,
                matchers = listOf(
                    RoutingMatcher.DomainMatcher(
                        listOf(
                            "*.doubleclick.net",
                            "*.googleadservices.com",
                            "*.googlesyndication.com",
                            "*.google-analytics.com",
                            "*facebook.com/tr*",
                            "*.ads.*.com"
                        )
                    )
                )
            )
        }

        /**
         * Gaming apps direct connection
         */
        fun gamingDirect(gamePackages: List<String>): RoutingRule {
            return RoutingRule(
                id = "gaming_direct",
                name = "Gaming Direct",
                action = RoutingAction.Direct,
                matchers = listOf(
                    RoutingMatcher.AppMatcher(gamePackages),
                    RoutingMatcher.ProtocolMatcher(listOf(Protocol.UDP))
                )
            )
        }

        /**
         * Work hours policy (9-5, weekdays only proxy)
         */
        fun workHoursOnly(): RoutingRule {
            return RoutingRule(
                id = "work_hours",
                name = "Work Hours Only",
                action = RoutingAction.Proxy,
                matchers = listOf(
                    RoutingMatcher.TimeMatcher(
                        startHour = 9,
                        endHour = 17,
                        daysOfWeek = listOf(2, 3, 4, 5, 6) // Mon-Fri
                    )
                )
            )
        }

        /**
         * Streaming platforms via proxy
         */
        fun streamingViaProxy(): RoutingRule {
            return RoutingRule(
                id = "streaming_proxy",
                name = "Streaming via Proxy",
                action = RoutingAction.Proxy,
                matchers = listOf(
                    RoutingMatcher.DomainMatcher(
                        listOf(
                            "*.netflix.com",
                            "*.youtube.com",
                            "*.twitch.tv",
                            "*.primevideo.com",
                            "*.disneyplus.com"
                        )
                    )
                )
            )
        }
    }

    /**
     * GeoIP database interface
     */
    interface GeoIpDatabase {
        fun lookupCountry(ip: String): String?
        fun lookupCity(ip: String): String?
        fun lookupContinent(ip: String): String?
    }
}
