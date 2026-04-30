# Lyricon 文档

Lyricon 文档分为两个顶层分区：

- **App**：Lyricon 主体应用的安装、配置和使用说明。
- **Lyric**：歌词数据模型、Bridge 接入和显示相关文档。

## 快速入口

- [App 文档](./app/README.md)
- [Lyric 文档](./lyric/README.md)
- [Provider 开发文档](./lyric/bridge/provider/README.md)
- [Subscriber 接入文档](./lyric/bridge/subscriber/README.md)

## 如何选择

| 目标                     | 应接入接口      |
|:-----------------------|:-----------|
| 让自己的播放器把歌词显示到 Lyricon  | Provider   |
| 为某个播放器开发歌词来源插件         | Provider   |
| 在自己的应用里读取 Lyricon 当前歌词 | Subscriber |
| 监听当前活跃播放器和播放进度         | Subscriber |

## 环境说明

- Provider 和 Subscriber 均面向 Android 应用。
- Provider/Subscriber Bridge 的最低运行版本由库实现决定，目前 Android 8.1 以下会返回空实现。
- 主体应用 README 中的 Lyricon 模块运行要求仍以项目根目录文档为准。
- Provider 可使用 LocalCentralService 在无 LSPosed 环境下进行基本测试。
- Subscriber 需要安装 Lyricon 核心服务。

## Maven 坐标

Provider：

```kotlin
implementation("io.github.proify.lyricon:provider:<version>")
```

Subscriber：

```kotlin
implementation("io.github.proify.lyricon:subscriber:<version>")
```

建议使用 Maven Central 上的最新版本。

## 相关链接

- [Lyricon Releases](https://github.com/tomakino/lyricon/releases)
- [LyricProvider 仓库](https://github.com/tomakino/LyricProvider)
- [Provider 源码](https://github.com/tomakino/lyricon/tree/master/lyric/bridge/provider)
- [Subscriber 源码](https://github.com/tomakino/lyricon/tree/master/lyric/bridge/subscriber)
