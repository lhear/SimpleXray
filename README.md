# SimpleXray

<img src="https://raw.githubusercontent.com/lhear/SimpleXray/main/metadata/en-US/images/icon.png" alt="icon" width="150">

[![GitHub Release](https://img.shields.io/github/v/release/halibiram/SimpleXray)](https://github.com/halibiram/SimpleXray/releases)
[![GitHub License](https://img.shields.io/github/license/halibiram/SimpleXray)](LICENSE)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/halibiram/SimpleXray/.github%2Fworkflows%2Fbuild.yml)](https://github.com/halibiram/SimpleXray/actions)

SimpleXray is a high-performance proxy client for Android, **built upon the robust Xray-core ([@XTLS/Xray-core](https://github.com/XTLS/Xray-core))**.

It features an **innovative approach**: **directly executing the official Xray-core binary**, unlike traditional JNI methods.

## Recent Improvements (2025)

### Q1 2025 
- Testing Infrastructure: JUnit, MockK, Compose UI tests
- Unit Tests: 23+ test cases for config parsing
- Error Handling: Type-safe error system
- Turkish Localization: Full support (127+ strings)
- CI/CD: Automated testing in GitHub Actions

### Q2 2025
- Enhanced ProGuard Rules: Comprehensive R8 configuration
- Documentation: Enhanced README
- Code Quality: Better maintainability


### Q3 2025 ✅
- Home Screen Widget: Quick VPN toggle
- Battery Optimization: Background service reliability
- Multi-language Widget Support

### Q4 2025 🚀
- **Performance Optimization System**: Complete performance management framework
- **5 Performance Profiles**: Turbo, Balanced, Battery Saver, Gaming, Streaming modes
- **Real-time Monitoring**: CPU, memory, bandwidth, latency metrics
- **Adaptive Auto-Tuning**: Automatic performance adjustments based on network conditions
- **Smart Connection Manager**: Automatic failover and server health monitoring
- **QoS System**: Per-app bandwidth control and traffic prioritization
- **Traffic Shaping**: Rate limiting, fair queuing, and bandwidth management
- **Advanced Caching**: DNS and HTTP caching for improved performance
- **Benchmark Tools**: Built-in speed testing and network diagnostics
- **Connection Quality Analysis**: Real-time bottleneck detection and recommendations
- **Network Type Detection**: Adaptive settings for Wi-Fi, 5G, 4G, 3G
- **Performance Dashboard**: Comprehensive monitoring UI with real-time graphs



### Q5 2025 (Advanced) 🌟
- **Protocol Optimizations**: HTTP/3 (QUIC), TLS 1.3, Brotli/HPACK compression
- **Geo-Routing System**: Nearest server selection, latency-based routing, multi-CDN support
- **Gaming Optimizations**: 9 popular game profiles (PUBG, Free Fire, COD, ML, etc.)
- **UDP Optimization**: Jitter buffer, ping stabilization, fast path routing
- **Streaming Enhancements**: Adaptive bitrate for YouTube, Netflix, Twitch, Spotify
- **Platform Detection**: Auto-optimize for streaming services
- **Advanced Split Tunneling 2.0**: Domain + IP + App + Protocol + Time-based rules
- **Policy-Based Routing**: GeoIP tables, custom routing rules, rule templates
- **Network Visualization**: Real-time topology maps, geographic server maps
- **Performance Graphs**: Interactive charts with historical data


## Key Features

*   **Enhanced Stability**: Independent child process execution
*   **High Performance**: Native Xray-core speed
*   **Modern Architecture**: 100% Kotlin + Jetpack Compose
*   **Type-Safe Error Handling**: User-friendly error messages
*   **Comprehensive Testing**: Unit + UI tests
*   **Multi-Language**: English, Turkish, Indonesian, Russian, Chinese

## Quick Start

1.  **Requirement**: Android 10 or higher
2.  **Download**: Get APK from [Releases](https://github.com/halibiram/SimpleXray/releases)
3.  **Install**: Install on your device
4.  **Configure**: Import or add server details
5.  **Connect**: Select config and connect

## Build Guide

### Prerequisites
- Android Studio (latest)
- JDK 17
- Android SDK (API 35)
- NDK r28c

### Building

```bash
git clone --recursive https://github.com/halibiram/SimpleXray
cd SimpleXray
./gradlew assembleDebug
```

### Running Tests

```bash
# Unit tests
./gradlew test

# UI tests
./gradlew connectedAndroidTest
```

## Contributing

Contributions welcome! Please:
- Write tests for new features
- Follow Kotlin conventions
- Use conventional commits
- Ensure CI/CD passes

## Roadmap

## License

**[Mozilla Public License Version 2.0](LICENSE)**

## Acknowledgments

- [@XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- Original: [@lhear/SimpleXray](https://github.com/lhear/SimpleXray)
