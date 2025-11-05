package com.simplexray.an.game

import com.simplexray.an.common.AppLogger
import java.net.InetAddress
import java.util.regex.Pattern

/**
 * GameMatcher - Fast game server classifier with suffix/prefix/CIDR matching.
 * 
 * Features:
 * - Fast suffix/prefix domain matching
 * - Simple CIDR set for known game provider IP ranges
 * - Immutable data structures for thread safety
 * - Supports future external list injection
 */
object GameMatcher {
    private const val TAG = "GameMatcher"
    
    // Known game domains (suffixes and prefixes)
    private val gameDomainSuffixes = setOf(
        // Riot Games (Valorant, League of Legends)
        ".riotgames.com",
        ".riotcdn.net",
        ".rcdn.net",
        ".riotcdn.com",
        // Valve (CS2, Dota 2, Steam)
        ".steampowered.com",
        ".steamcontent.com",
        ".steamstatic.com",
        ".valve.net",
        ".valvesoftware.com",
        // Epic Games (Fortnite, Unreal Engine)
        ".epicgames.com",
        ".epicgames.net",
        ".unrealtournament.com",
        // EA (Apex Legends, Battlefield, FIFA)
        ".ea.com",
        ".eaaccess.com",
        ".origin.com",
        ".battlefield.com",
        // Activision (Call of Duty, Overwatch)
        ".activision.com",
        ".callofduty.com",
        ".blizzard.com",
        ".battle.net",
        // Ubisoft
        ".ubisoft.com",
        ".ubisoftconnect.com",
        // Mobile games (PUBG, Genshin Impact, etc.)
        ".pubg.com",
        ".tencent.com",
        ".mihoyo.com",
        ".hoyoverse.com",
        ".supercell.com",
        // Other popular games
        ".minecraft.net",
        ".mojang.com",
        ".rockstargames.com",
        ".take2games.com"
    )
    
    // Known game domain prefixes
    private val gameDomainPrefixes = setOf(
        "game.",
        "games.",
        "multiplayer.",
        "matchmaking.",
        "match.",
        "lobby.",
        "server."
    )
    
    // Known game provider CIDR ranges (simplified - can be expanded)
    // Format: "IP/CIDR" -> provider name
    private val gameProviderCidr = mapOf(
        // Riot Games IP ranges (example - should be expanded)
        "185.40.64.0/22" to "riot",
        "185.40.64.0/24" to "riot",
        // Valve/Steam IP ranges (example)
        "146.75.0.0/16" to "valve",
        // Add more as needed
    )
    
    // Compiled patterns for fast matching
    private val gameSuffixPattern: Pattern = Pattern.compile(
        gameDomainSuffixes.joinToString("|") { Pattern.quote(it) },
        Pattern.CASE_INSENSITIVE
    )
    
    /**
     * Check if host or IP is a game server
     * 
     * @param hostOrIp Hostname or IP address
     * @return True if matches game server pattern
     */
    fun isGameHost(hostOrIp: String): Boolean {
        if (hostOrIp.isBlank()) return false
        
        // Check domain suffix
        if (gameDomainSuffixes.any { hostOrIp.lowercase().endsWith(it) }) {
            return true
        }
        
        // Check domain prefix
        if (gameDomainPrefixes.any { hostOrIp.lowercase().startsWith(it) }) {
            return true
        }
        
        // Check if it's an IP and matches CIDR
        if (isIpAddress(hostOrIp)) {
            return matchesCidr(hostOrIp)
        }
        
        return false
    }
    
    /**
     * Get game ID for host or IP (if known)
     * 
     * @param hostOrIp Hostname or IP address
     * @return GameId if known, null otherwise
     */
    fun gameIdFor(hostOrIp: String): GameId? {
        if (hostOrIp.isBlank()) return null
        
        val lower = hostOrIp.lowercase()
        
        // Riot Games
        if (lower.contains("riot") || lower.contains("valorant") || lower.contains("league")) {
            return GameId.RIOT_VALORANT
        }
        
        // Valve/Steam
        if (lower.contains("steam") || lower.contains("valve") || lower.contains("cs2") || lower.contains("dota")) {
            return GameId.VALVE_CS2
        }
        
        // Epic Games
        if (lower.contains("epic") || lower.contains("fortnite") || lower.contains("unreal")) {
            return GameId.EPIC_FORTNITE
        }
        
        // EA
        if (lower.contains("ea") || lower.contains("apex") || lower.contains("battlefield") || lower.contains("origin")) {
            return GameId.EA_APEX
        }
        
        // Activision/Blizzard
        if (lower.contains("activision") || lower.contains("callofduty") || lower.contains("blizzard") || lower.contains("battle.net")) {
            return GameId.ACTIVISION_COD
        }
        
        // PUBG Mobile
        if (lower.contains("pubg") || lower.contains("tencent")) {
            return GameId.PUBG_MOBILE
        }
        
        // Mobile games
        if (lower.contains("mihoyo") || lower.contains("hoyoverse") || lower.contains("genshin")) {
            return GameId.MOBILE_GENSHIN
        }
        
        return null
    }
    
    /**
     * Check if string is an IP address
     */
    private fun isIpAddress(str: String): Boolean {
        return try {
            val parts = str.split(".")
            parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if IP matches any CIDR range
     */
    private fun matchesCidr(ip: String): Boolean {
        try {
            val ipAddr = InetAddress.getByName(ip)
            val ipBytes = ipAddr.address
            
            for ((cidr, _) in gameProviderCidr) {
                val (network, prefixLen) = parseCidr(cidr)
                if (network != null && matchesNetwork(ipBytes, network, prefixLen)) {
                    return true
                }
            }
        } catch (e: Exception) {
            AppLogger.d("$TAG: Error checking CIDR for $ip", e)
        }
        
        return false
    }
    
    /**
     * Parse CIDR notation (e.g., "192.168.1.0/24")
     */
    private fun parseCidr(cidr: String): Pair<ByteArray?, Int> {
        val parts = cidr.split("/")
        if (parts.size != 2) return Pair(null, 0)
        
        val prefixLen = parts[1].toIntOrNull() ?: return Pair(null, 0)
        
        return try {
            val network = InetAddress.getByName(parts[0])
            Pair(network.address, prefixLen)
        } catch (e: Exception) {
            Pair(null, 0)
        }
    }
    
    /**
     * Check if IP matches network with prefix length
     */
    private fun matchesNetwork(ipBytes: ByteArray, networkBytes: ByteArray, prefixLen: Int): Boolean {
        if (ipBytes.size != networkBytes.size) return false
        
        val fullBytes = prefixLen / 8
        val remainingBits = prefixLen % 8
        
        // Check full bytes
        for (i in 0 until fullBytes) {
            if (ipBytes[i] != networkBytes[i]) return false
        }
        
        // Check remaining bits
        if (remainingBits > 0 && fullBytes < ipBytes.size) {
            val mask = (0xFF shl (8 - remainingBits)) and 0xFF
            val ipByte = ipBytes[fullBytes].toInt() and 0xFF
            val netByte = networkBytes[fullBytes].toInt() and 0xFF
            if ((ipByte and mask) != (netByte and mask)) return false
        }
        
        return true
    }
    
    /**
     * Game ID enumeration
     */
    enum class GameId {
        RIOT_VALORANT,
        VALVE_CS2,
        EPIC_FORTNITE,
        EA_APEX,
        ACTIVISION_COD,
        PUBG_MOBILE,
        MOBILE_GENSHIN,
        UNKNOWN
    }
}

