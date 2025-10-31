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

## CI/CD & Telegram Notifications

SimpleXray includes GitHub Actions workflows that automatically build APKs and send them to Telegram when builds succeed.

### Setting Up Telegram Notifications

1. **Create a Telegram Bot**
   - Open Telegram and search for [@BotFather](https://t.me/BotFather)
   - Send `/newbot` and follow the instructions
   - Save the **Bot Token** (looks like: `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`)

2. **Get Your Chat ID** (Choose easiest method)

   **Method 1 (Easiest):**
   - Open [@userinfobot](https://t.me/userinfobot) in Telegram
   - Send `/start`
   - Copy the number shown as "Id:" (e.g., `123456789`)

   **Method 2:**
   - Open [@myidbot](https://t.me/myidbot) in Telegram
   - Send `/getid`
   - Copy "Your user ID" number

   **Method 3 (Manual):**
   - Send a message to your bot
   - Visit: `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
   - Find `"chat":{"id":` and copy the number after it

3. **Add Secrets to GitHub Repository**
   - Go to your GitHub repository
   - Navigate to **Settings** → **Secrets and variables** → **Actions**
   - Click **New repository secret** and add:
     - **Name**: `TELEGRAM_BOT_TOKEN`
     - **Value**: Your bot token from step 1
   - Click **New repository secret** again and add:
     - **Name**: `TELEGRAM_CHAT_ID`
     - **Value**: Your chat ID from step 2

4. **Trigger a Build**
   - Push to main branch or manually trigger workflow
   - Go to **Actions** tab to monitor build progress
   - Once build succeeds, you'll receive APK in Telegram!

### Notification Content

The Telegram message includes:
- ✅ Build status
- 📦 APK filename
- 📊 File size
- 🔨 Commit hash
- 🌿 Branch name
- 👤 GitHub username
- 🔗 Commit link
- 📎 APK file attachment

### Manual Workflow Trigger

You can also manually trigger builds:
1. Go to **Actions** tab
2. Select **Build** workflow
3. Click **Run workflow**
4. Select branch and click **Run workflow**

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
