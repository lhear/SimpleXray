package com.simplexray.an.ui.gaming

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamingScreen(
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gaming Optimization") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Gaming Optimizations",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            val games = listOf(
                "PUBG Mobile" to "Ultra-low latency, UDP optimization",
                "Free Fire" to "Ping stabilization, jitter reduction",
                "Call of Duty Mobile" to "Fast path routing, QoS priority",
                "Mobile Legends" to "Lag compensation, packet prioritization",
                "Genshin Impact" to "Stable connection, bandwidth management",
                "Valorant Mobile" to "Low latency mode, anti-jitter",
                "Fortnite" to "Network smoothing, tick rate optimization",
                "Arena of Valor" to "Real-time sync optimization",
                "Clash of Clans" to "Connection stability focus"
            )

            items(games) { (game, description) ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(game, style = MaterialTheme.typography.titleMedium)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
