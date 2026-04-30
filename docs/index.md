---
layout: home

hero:
  name: Lyricon
  text: Android 状态栏歌词增强工具
  tagline: 在系统状态栏显示当前播放歌词，支持逐字歌词、翻译显示和可视化调整。
  image:
    src: /logo.svg
    alt: Lyricon
  actions:
    - theme: brand
      text: 使用文档
      link: /app/
    - theme: alt
      text: 下载应用
      link: https://github.com/tomakino/lyricon/releases

features:
  - icon: 🎤
    title: 状态栏歌词
    details: 将当前播放歌词显示在 Android 状态栏中，切换应用时也能查看。
    link: /app/
  - icon: 🎨
    title: 显示自定义
    details: 调整位置、宽度、字体、Logo 和动画，适配不同设备状态栏。
    link: /app/#界面配置
  - icon: 🧩
    title: 播放器插件
    details: 通过 LyricProvider 插件适配不同音乐播放器的歌词来源。
    link: /app/#安装歌词插件
---

## 快速开始

1. 从 [Releases](https://github.com/tomakino/lyricon/releases) 下载并安装 Lyricon。
2. 在 LSPosed 中启用 Lyricon，并勾选 **系统界面 (System UI)** 作用域。
3. 重启 System UI 或重启设备。
4. 安装对应播放器的 [LyricProvider](https://github.com/tomakino/LyricProvider) 插件。
5. 播放音乐，打开 Lyricon 调整显示位置和样式。

## 主要功能

| 功能   | 说明                    |
|:-----|:----------------------|
| 逐字歌词 | 根据歌词来源显示动态逐字进度        |
| 翻译显示 | 支持展示翻译歌词              |
| 对唱模式 | 支持次要歌词或对唱歌词展示         |
| 视觉配置 | 支持位置、宽度、字体、Logo 和动画调整 |

## 更多内容

| 内容                       | 入口                                               |
|:-------------------------|:-------------------------------------------------|
| App 安装、配置和排障             | [App 使用文档](./app/README.md)                      |
| Provider / Subscriber 接入 | [开发者文档](./lyric/bridge/README.md)                |
| 源码与问题反馈                  | [GitHub 仓库](https://github.com/tomakino/lyricon) |
