# SimpleXray - 基于 Xray-core 的 Android 客户端

## 项目概述

SimpleXray 项目致力于构建一个基于 **[Xray-core](https://github.com/XTLS/Xray-core)** 的高性能、稳定且用户友好的 Android 平台代理解决方案。

本应用充分利用 Xray-core 提供的强大网络处理能力，并针对 Android 环境进行了深度优化，旨在为终端用户提供卓越的网络代理体验。

## 核心特性

*   **卓越性能**: 充分发挥 Xray-core 的性能优势，确保快速稳定的网络连接。

*   **操作简便**: 提供直观的用户界面及精简的配置流程，降低用户上手难度。

*   **显著稳定性**: 相较于传统依赖 JNI 调用 Xray-core 动态链接库 (.so 文件) 的方式，
    **SimpleXray 创新性地采用了直接执行 Xray-core 二进制文件来启动和管理代理进程的策略**。

    此种实现方式有效地实现了核心逻辑与应用层的解耦，显著提升了客户端的长期运行稳定性，规避了库调用可能引发的各类潜在问题。

*   **高效 Tun2socks 实现**: 集成了高性能的 **[@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)** 库作为 Tun2socks 的实现，
    进一步优化了数据转发效率与整体性能。

## 与同类项目比较

当前诸多基于 Xray-core 的 Android 客户端，通常选择将核心模块编译为动态链接库 (.so 文件)，
并通过 JNI (Java Native Interface) 在应用层进行调用以控制代理进程的启动与运行。

尽管此方法在开发集成层面可能更为便捷，但易于引入一系列稳定性挑战，例如 JNI 调用开销、内存管理复杂性上升以及核心库崩溃对整个应用的直接影响。

**SimpleXray 的核心区别在于其选择了直接执行 Xray-core 官方发布的、未经修改的二进制文件。**

在应用安装部署阶段，内嵌的 Xray-core 二进制文件（libxray.so）会被解压至应用库目录。

当用户发起代理连接请求时，应用层并非调用库函数，而是以子进程形式直接执行此二进制文件，并通过标准的进程间通信机制实施控制。

此模式最大限度地保留了 Xray-core 原生二进制的稳定性和性能特性，同时实现了核心进程与主应用进程的有效隔离。

geoip.dat和geosite.dat位于/storage/emulated/0/Android/data/com.simplexray.an/files/目录，默认为精简版，若有需要请自行替换。

精简版仅包含 "geoip:private" "geoip:cn" "geosite:gfw"，项目地址: [@lhear/v2ray-rules-dat](https://github.com/lhear/v2ray-rules-dat) 。

## 快速入门指南

1.  确保您的操作系统版本不低于 Android 10。

2.  **获取应用**: 请从 [Release 页面](https://github.com/lhear/SimpleXray/releases) 下载最新版本的 APK 文件。

3.  **安装部署**: 将 APK 文件安装至您的 Android 设备。

4.  **配置设定**: 启动应用，导入或手动配置您的代理服务器参数。

5.  **建立连接**: 选择一个配置，点击连接按钮启动代理服务。

## 项目构建指南

1.  **环境配置**: 请确认您已安装 [Android Studio](https://developer.android.com/studio) 及相应的 Android SDK。

2.  **代码克隆**: 执行命令 `git clone --recursive https://github.com/lhear/SimpleXray` 获取项目源代码。

3.  **导入项目**: 在 Android Studio 中打开已克隆的项目文件夹。

4.  **核心导入**: 将对应架构的 xray 内核二进制文件置于 `jniLibs` 目录下。

5.  **执行构建**: 同步 Gradle 项目并运行构建任务以生成 APK 文件。

## 贡献途径

欢迎以任何形式参与项目贡献，包括但不限于提交 Issue、完善文档等。

## 许可证信息

本项目依照 **[Mozilla Public License Version 2.0](LICENSE)** 发布。
