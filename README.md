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

### Q3 2025 (Planned)
- Onboarding flow
- Widget support
- Enhanced split tunneling
- Additional protocols

## License

**[Mozilla Public License Version 2.0](LICENSE)**

## Acknowledgments

- [@XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- Original: [@lhear/SimpleXray](https://github.com/lhear/SimpleXray)
