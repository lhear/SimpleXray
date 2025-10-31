package com.simplexray.an.ui.streaming

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
fun StreamingScreen(
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streaming Optimization") },
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
                    "Streaming Platforms",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            val platforms = listOf(
                "YouTube" to "Adaptive bitrate, buffer optimization",
                "Netflix" to "Quality adaptation, pre-buffering",
                "Twitch" to "Low-latency streaming, chat sync",
                "Disney+" to "CDN optimization, bandwidth management",
                "Amazon Prime" to "Multi-CDN routing, quality selection",
                "Spotify" to "Audio streaming optimization",
                "HBO Max" to "4K streaming support",
                "TikTok" to "Short-form video optimization"
            )

            items(platforms) { (platform, description) ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(platform, style = MaterialTheme.typography.titleMedium)
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
