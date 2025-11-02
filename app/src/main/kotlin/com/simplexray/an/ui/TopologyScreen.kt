package com.simplexray.an.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.simplexray.an.domain.DomainClassifier
import com.simplexray.an.topology.Edge
import com.simplexray.an.topology.Node
import com.simplexray.an.topology.TopologyViewModel
import com.simplexray.an.ui.components.NetworkGraphCanvas
import com.simplexray.an.asn.AsnLookup
import com.simplexray.an.asn.AsnCdnMap
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.simplexray.an.ui.components.Legend

@Composable
fun TopologyScreen(vm: TopologyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val pair = vm.graph.collectAsState().value
    val nodes = pair.first
    val edges = pair.second
    val ctx = LocalContext.current
    val classifier = remember(ctx) { DomainClassifier(ctx) }
    val asn = remember(ctx) { AsnLookup(ctx) }
    
    // Persistent selection: load from store and restore when nodes are available
    val savedSelectedId = remember(ctx) { 
        com.simplexray.an.topology.TopologySelectionStore.loadSelectedNodeId(ctx) 
    }
    var selected by remember { 
        mutableStateOf<Node?>(
            savedSelectedId?.let { id -> nodes.firstOrNull { it.id == id } }
        )
    }
    
    // Restore selection when nodes change
    androidx.compose.runtime.LaunchedEffect(nodes) {
        savedSelectedId?.let { id ->
            nodes.firstOrNull { it.id == id }?.let { node ->
                selected = node
            }
        }
    }
    
    var showLabels by remember { mutableStateOf(com.simplexray.an.topology.TopologySettingsStore.loadShowLabels(ctx)) }
    var resetKey by remember { mutableStateOf(0) }
    var viewResetKey by remember { mutableStateOf(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (nodes.isEmpty()) {
            Text("No topology data")
        } else {
            NetworkGraphCanvas(
                nodes = nodes,
                edges = edges,
                modifier = Modifier.fillMaxSize(),
                showLabels = showLabels,
                cdnBadge = { n ->
                    when (n.type) {
                        Node.Type.Domain -> classifier.isCdn(n.label)
                        Node.Type.IP -> AsnCdnMap.isCdn(asn.lookup(n.label))
                    }
                },
                onNodeClick = { n -> 
                    selected = n
                    // Persist selection
                    com.simplexray.an.topology.TopologySelectionStore.saveSelectedNodeId(ctx, n.id)
                },
                onBackgroundTap = { 
                    selected = null
                    // Clear persisted selection
                    com.simplexray.an.topology.TopologySelectionStore.clearSelection(ctx)
                },
                selectedNodeId = selected?.id,
                resetKey = resetKey,
                viewResetKey = viewResetKey
            )
            Legend(modifier = Modifier.padding(12.dp))
            Row(modifier = Modifier.padding(12.dp)) {
                Button(onClick = {
                    com.simplexray.an.topology.TopologyLayoutStore.clear(ctx)
                    resetKey++
                }) { Text("Reset layout") }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                Button(onClick = { showLabels = !showLabels; com.simplexray.an.topology.TopologySettingsStore.saveShowLabels(ctx, showLabels) }) { Text(if (showLabels) "Hide labels" else "Show labels") }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                Button(onClick = { viewResetKey++ }) { Text("Reset view") }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                Button(onClick = { 
                    // Fit to graph - reset view and zoom to show all nodes
                    viewResetKey++
                    com.simplexray.an.topology.TopologyViewStateStore.clear(ctx)
                }) { Text("Fit to graph") }
            }
            selected?.let { n ->
                val isCdn = n.type == Node.Type.Domain && classifier.isCdn(n.label)
                val asnInfo = if (n.type == Node.Type.IP) asn.lookup(n.label) else null
                Surface(modifier = Modifier.padding(12.dp)) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                        Text("${n.type}: ${n.label}")
                        if (asnInfo != null) {
                            Text("ASN ${asnInfo.asn} — ${asnInfo.org}")
                            val cdnName = AsnCdnMap.name(asnInfo)
                            if (cdnName != null) Text("CDN: $cdnName", color = androidx.compose.ui.graphics.Color(0xFFFFA000))
                        }
                        if (isCdn) Text("CDN matched", color = androidx.compose.ui.graphics.Color(0xFFFFA000))
                    }
                }
            }
        }
    }
}
