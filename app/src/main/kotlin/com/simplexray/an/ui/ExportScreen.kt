package com.simplexray.an.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simplexray.an.export.ExportRepository
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun ExportScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(ctx) { ExportRepository(ctx) }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Export Traffic History")
        Button(onClick = {
            scope.launch {
                val uri = repo.exportJson(TimeUnit.HOURS.toMillis(24))
                uri?.let { share(ctx, it, "application/json") }
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Export last 24h (JSON)") }
        Button(onClick = {
            scope.launch {
                val uri = repo.exportCsv(TimeUnit.HOURS.toMillis(24))
                uri?.let { share(ctx, it, "text/csv") }
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Export last 24h (CSV)") }
    }
}

private fun share(ctx: android.content.Context, uri: android.net.Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share export"))
}
