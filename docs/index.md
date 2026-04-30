---
layout: home

hero:
  name: Lyricon Bridge
  text: App 与 Lyric 文档
  tagline: 覆盖 Lyricon 主体应用、歌词模型、Bridge 接入和第三方集成能力。
  image:
    src: /logo.svg
    alt: Lyricon
  actions:
    - theme: brand
      text: App 文档
      link: /app/
    - theme: alt
      text: Lyric 文档
      link: /lyric/
    - theme: alt
      text: GitHub
      link: https://github.com/tomakino/lyricon

features:
  - icon: 🎤
    title: App
    details: Lyricon 主体应用的安装、配置和使用说明。
    link: /app/
  - icon: 📡
    title: Lyric
    details: 歌词数据模型、Bridge 接入和显示相关能力文档。
    link: /lyric/
  - icon: 🧪
    title: Bridge
    details: Provider 可使用 LocalCentralService 在无 LSPosed 环境下完成基础调试。
    link: /lyric/bridge/provider/local-testing
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
