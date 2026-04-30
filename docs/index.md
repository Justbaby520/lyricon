---
layout: home

hero:
  name: Lyricon Bridge
  text: Provider 与 Subscriber 接入文档
  tagline: 为 Android 播放器、歌词插件和第三方应用提供统一的 Lyricon 数据桥接能力。
  image:
    src: /logo.svg
    alt: Lyricon
  actions:
    - theme: brand
      text: Provider 开发
      link: /provider/
    - theme: alt
      text: Subscriber 接入
      link: /subscriber/
    - theme: alt
      text: GitHub
      link: https://github.com/tomakino/lyricon

features:
  - icon: 🎤
    title: Provider
    details: 音乐播放器或歌词插件向 Lyricon 推送歌曲、歌词、播放状态和显示配置。
    link: /provider/
  - icon: 📡
    title: Subscriber
    details: 第三方应用订阅 Lyricon 当前活跃播放器、歌词、播放进度和显示状态。
    link: /subscriber/
  - icon: 🧪
    title: 本地测试
    details: Provider 可使用 LocalCentralService 在无 LSPosed 环境下完成基础调试。
    link: /provider/local-testing
---

## 选择接入方式

| 目标 | 应接入接口 |
|:---|:---|
| 让自己的播放器把歌词显示到 Lyricon | Provider |
| 为某个播放器开发歌词来源插件 | Provider |
| 在自己的应用里读取 Lyricon 当前歌词 | Subscriber |
| 监听当前活跃播放器和播放进度 | Subscriber |

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
