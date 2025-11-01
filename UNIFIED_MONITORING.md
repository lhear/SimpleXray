# Unified Monitoring System - SimpleXray

## Genel Bakış

SimpleXray için **Unified Monitoring System** (Birleşik İzleme Sistemi), **Performance Monitoring** ve **Network Visualization** özelliklerini tek bir sistemde birleştirerek Xray core'dan gerçek zamanlı veri sağlar.

## Özellikler

### ✅ Gerçek Zamanlı Veri
- **Xray Core Integration**: `CoreStatsClient` kullanarak Xray core'dan doğrudan trafik verileri
- **Otomatik Fallback**: Xray core erişilemezse sistem geneli network istatistiklerine düşer
- **1 saniye güncelleme aralığı**: Anında performans değişikliklerini gösterir

### 📊 Performance Monitoring
- Upload/Download hızları (gerçek zamanlı)
- CPU ve Memory kullanımı
- Latency ve jitter ölçümleri
- Connection quality skorları
- Bottleneck tespiti ve önerileri

### 🌐 Network Visualization
- Real-time network topology görselleştirmesi
- Latency grafikler (son 60 saniye)
- Bandwidth grafikler (upload/download)
- Connection status göstergeleri

## Mimari

```
┌─────────────────────────────────────────┐
│   UnifiedMonitoringViewModel           │
│  (Unified monitoring orchestrator)     │
└────────────┬────────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌───────────────┐  ┌──────────────────┐
│ Performance   │  │ Network          │
│ Monitor       │  │ Visualization    │
│               │  │                  │
│ - Metrics     │  │ - Topology       │
│ - History     │  │ - Charts         │
│ - Bottlenecks │  │ - Real-time data │
└───────┬───────┘  └────────┬─────────┘
        │                   │
        └─────────┬─────────┘
                  │
                  ▼
        ┌─────────────────┐
        │ CoreStatsClient │
        │  (Xray gRPC)    │
        └─────────────────┘
```

## Kullanım

### 1. ViewModel Oluşturma

```kotlin
// Application context ile ViewModel oluştur
val viewModel = UnifiedMonitoringViewModel(
    application = application,
    coreStatsClient = null // İsteğe bağlı, daha sonra ayarlanabilir
)

// Xray core bağlandığında CoreStatsClient'i ayarla
val statsClient = CoreStatsClient.create("127.0.0.1", apiPort)
viewModel.setCoreStatsClient(statsClient)
```

### 2. UI'da Kullanım

```kotlin
@Composable
fun MyMonitoringScreen(viewModel: UnifiedMonitoringViewModel) {
    val currentMetrics by viewModel.currentMetrics.collectAsState()
    val metricsHistory by viewModel.metricsHistory.collectAsState()
    val bottlenecks by viewModel.bottlenecks.collectAsState()
    val topology by viewModel.topology.collectAsState()
    val latencyHistory by viewModel.latencyHistory.collectAsState()
    val bandwidthHistory by viewModel.bandwidthHistory.collectAsState()

    UnifiedMonitoringScreen(
        currentMetrics = currentMetrics,
        metricsHistory = metricsHistory,
        bottlenecks = bottlenecks,
        topology = topology,
        latencyHistory = latencyHistory,
        bandwidthHistory = bandwidthHistory,
        onRefreshTopology = { viewModel.refreshTopology() },
        onBackClick = { /* navigate back */ }
    )
}
```

### 3. Standalone Performance Monitor

Performance Monitor'u tek başına kullanmak isterseniz:

```kotlin
val performanceMonitor = PerformanceMonitor(
    context = context,
    updateInterval = 1000, // 1 saniye
    coreStatsClient = statsClient
)

// Monitoring'i başlat
performanceMonitor.start()

// Metrics'leri dinle
lifecycleScope.launch {
    performanceMonitor.currentMetrics.collect { metrics ->
        // Metrics'leri kullan
        val downloadSpeed = metrics.downloadSpeed
        val uploadSpeed = metrics.uploadSpeed
        val latency = metrics.latency
    }
}

// Monitoring'i durdur
performanceMonitor.stop()
```

### 4. Standalone Network Visualization

Network Visualization'ı tek başına kullanmak isterseniz:

```kotlin
val networkViewModel = NetworkVisualizationViewModel(
    application = application,
    coreStatsClient = statsClient
)

// Monitoring'i başlat (otomatik başlar)
networkViewModel.startMonitoring()

// Topology'yi güncelle
networkViewModel.refreshTopology()

// Monitoring'i durdur
networkViewModel.stopMonitoring()
```

## API Referansı

### UnifiedMonitoringViewModel

#### Properties (StateFlow)
- `currentMetrics: StateFlow<PerformanceMetrics>` - Anlık performans metrikleri
- `metricsHistory: StateFlow<MetricsHistory>` - Geçmiş metrikler
- `bottlenecks: StateFlow<List<Bottleneck>>` - Tespit edilen performans sorunları
- `topology: StateFlow<NetworkTopology>` - Network topology
- `latencyHistory: StateFlow<List<TimeSeriesData>>` - Latency geçmişi (grafik için)
- `bandwidthHistory: StateFlow<List<TimeSeriesData>>` - Bandwidth geçmişi (grafik için)
- `isMonitoring: StateFlow<Boolean>` - Monitoring durumu

#### Methods
- `setCoreStatsClient(client: CoreStatsClient?)` - Xray core client'i ayarla
- `startMonitoring()` - Monitoring'i başlat
- `stopMonitoring()` - Monitoring'i durdur
- `refreshTopology()` - Network topology'yi yenile

### PerformanceMonitor

#### Methods
- `start()` - Monitoring'i başlat
- `stop()` - Monitoring'i durdur
- `setCoreStatsClient(client: CoreStatsClient?)` - Xray core client'i ayarla
- `onConnectionEstablished()` - Bağlantı kurulduğunda çağır
- `onConnectionLost()` - Bağlantı kesildiğinde çağır
- `recordLatency(latencyMs: Int)` - Latency kaydı ekle

### NetworkVisualizationViewModel

#### Methods
- `setCoreStatsClient(client: CoreStatsClient?)` - Xray core client'i ayarla
- `startMonitoring()` - Monitoring'i başlat
- `stopMonitoring()` - Monitoring'i durdur
- `refreshTopology()` - Topology'yi yenile

## Data Models

### PerformanceMetrics
```kotlin
data class PerformanceMetrics(
    val uploadSpeed: Long,          // bytes/second
    val downloadSpeed: Long,        // bytes/second
    val totalUpload: Long,          // total bytes
    val totalDownload: Long,        // total bytes
    val latency: Int,               // milliseconds
    val jitter: Int,                // milliseconds
    val packetLoss: Float,          // percentage
    val connectionCount: Int,
    val activeConnectionCount: Int,
    val cpuUsage: Float,           // percentage
    val memoryUsage: Long,         // bytes
    val nativeMemoryUsage: Long,   // bytes
    val connectionStability: Float, // 0-100
    val overallQuality: Float,     // 0-100
    val timestamp: Long
)
```

### NetworkTopology
```kotlin
data class NetworkTopology(
    val nodes: List<NetworkNode>,
    val connections: List<NetworkConnection>
)
```

### TimeSeriesData
```kotlin
data class TimeSeriesData(
    val name: String,
    val dataPoints: List<GraphDataPoint>,
    val unit: String,
    val color: Long // ARGB color
)
```

## Xray Core Integration

### CoreStatsClient Kullanımı

```kotlin
// Client oluştur
val statsClient = CoreStatsClient.create("127.0.0.1", apiPort)

// Traffic verilerini al
val traffic = statsClient.getTraffic()
println("Upload: ${traffic?.uplink}")
println("Download: ${traffic?.downlink}")

// System stats'ları al
val sysStats = statsClient.getSystemStats()
println("Goroutines: ${sysStats?.numGoroutine}")
println("Memory: ${sysStats?.alloc}")

// Client'i kapat
statsClient.close()
```

### gRPC Configuration

Xray core'un API portunu yapılandırmak için:

```json
{
  "api": {
    "tag": "api",
    "services": [
      "StatsService"
    ]
  },
  "policy": {
    "levels": {
      "0": {
        "statsUserUplink": true,
        "statsUserDownlink": true
      }
    },
    "system": {
      "statsInboundUplink": true,
      "statsInboundDownlink": true,
      "statsOutboundUplink": true,
      "statsOutboundDownlink": true
    }
  },
  "inbounds": [
    {
      "listen": "127.0.0.1",
      "port": 10085,
      "protocol": "dokodemo-door",
      "settings": {
        "address": "127.0.0.1"
      },
      "tag": "api"
    }
  ],
  "routing": {
    "rules": [
      {
        "inboundTag": ["api"],
        "outboundTag": "api",
        "type": "field"
      }
    ]
  }
}
```

## Performance Optimizations

1. **Update Interval**: Varsayılan 1000ms (1 saniye) - daha düşük interval daha fazla CPU kullanır
2. **History Size**: Son 60 data point saklanır (1 dakika)
3. **Coroutine Dispatchers**: I/O işlemleri için `Dispatchers.IO`, hesaplamalar için `Dispatchers.Default`
4. **Suspend Functions**: Network çağrıları suspend fonksiyonlar olarak implemente edildi

## Troubleshooting

### Realtime Monitor Çalışmıyor?

1. **CoreStatsClient Ayarlandı mı?**
   ```kotlin
   viewModel.setCoreStatsClient(statsClient)
   ```

2. **Xray Core API Portu Açık mı?**
   - Preferences'da `apiPort` değerini kontrol edin
   - Xray config'de API inbound var mı kontrol edin

3. **Stats Policy Aktif mi?**
   - Xray config'de `statsUserUplink/Downlink` aktif olmalı
   - System stats da aktif olmalı

### Veriler Sıfır Gösteriyor?

1. **Fallback Mode**: CoreStatsClient yoksa sistem `/proc/net/dev`'e düşer
2. **VPN Aktif mi?**: Xray çalışıyor olmalı
3. **İzinler**: App network istatistiklerine erişebilmeli

### Performans Sorunları?

1. **Update Interval'i artırın**: 1000ms → 2000ms
2. **History size'ı azaltın**: 60 → 30
3. **Monitoring'i durdurun**: Ekrandan çıkıldığında `stopMonitoring()` çağırın

## Örnekler

### Tam Entegrasyon Örneği

```kotlin
class MonitoringActivity : ComponentActivity() {
    private lateinit var viewModel: UnifiedMonitoringViewModel
    private var statsClient: CoreStatsClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewModel oluştur
        viewModel = UnifiedMonitoringViewModel(application)

        // VPN bağlandığında stats client oluştur
        lifecycleScope.launch {
            // VPN durumunu dinle
            vpnStateFlow.collect { isConnected ->
                if (isConnected) {
                    statsClient = CoreStatsClient.create("127.0.0.1", apiPort)
                    viewModel.setCoreStatsClient(statsClient)
                } else {
                    statsClient?.close()
                    statsClient = null
                    viewModel.setCoreStatsClient(null)
                }
            }
        }

        setContent {
            MyMonitoringScreen(viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopMonitoring()
        statsClient?.close()
    }
}
```

## Sonuç

Unified Monitoring System, SimpleXray için gerçek zamanlı, Xray core entegreli, profesyonel bir izleme çözümüdür. Performance monitoring ve network visualization'ı tek bir sistemde birleştirerek kullanıcılara kapsamlı network insight'ları sağlar.

### Ana Avantajlar
✅ Xray core'dan gerçek veriler
✅ Otomatik fallback mekanizması
✅ Modern Compose UI
✅ Düşük kaynak tüketimi
✅ Kolay entegrasyon
✅ Genişletilebilir mimari

### Katkıda Bulunma
Bu sistem açık kaynaklıdır ve katkılara açıktır. Issues ve pull request'ler memnuniyetle karşılanır!
