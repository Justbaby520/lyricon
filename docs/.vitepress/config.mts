import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Lyricon Bridge',
  description: 'Provider 与 Subscriber 接入文档',
  base: '/lyricon/',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', href: '/lyricon/logo.svg' }]
  ],
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'Lyricon Bridge',
    nav: [
      { text: '首页', link: '/' },
      { text: 'Provider', link: '/provider/' },
      { text: 'Subscriber', link: '/subscriber/' },
      { text: 'GitHub', link: 'https://github.com/tomakino/lyricon' }
    ],
    sidebar: [
      {
        text: '开始',
        items: [
          { text: '文档首页', link: '/' },
          { text: 'Bridge 概览', link: '/README' }
        ]
      },
      {
        text: 'Provider',
        collapsed: false,
        items: [
          { text: '概览', link: '/provider/' },
          { text: '快速开始', link: '/provider/quick-start' },
          { text: 'Manifest 配置', link: '/provider/manifest' },
          { text: '连接生命周期', link: '/provider/connection' },
          { text: '播放器控制', link: '/provider/player-control' },
          { text: '歌词数据结构', link: '/provider/lyrics-model' },
          { text: '本地测试', link: '/provider/local-testing' },
          { text: '常见问题', link: '/provider/faq' }
        ]
      },
      {
        text: 'Subscriber',
        collapsed: false,
        items: [
          { text: '概览', link: '/subscriber/' },
          { text: '快速开始', link: '/subscriber/quick-start' },
          { text: '连接生命周期', link: '/subscriber/connection' },
          { text: '活跃播放器', link: '/subscriber/active-player' },
          { text: '回调说明', link: '/subscriber/callbacks' },
          { text: '常见问题', link: '/subscriber/faq' }
        ]
      }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/tomakino/lyricon' }
    ],
    search: {
      provider: 'local'
    },
    outline: {
      level: [2, 3],
      label: '页面导航'
    },
    docFooter: {
      prev: '上一页',
      next: '下一页'
    },
    lastUpdated: {
      text: '最后更新',
      formatOptions: {
        dateStyle: 'short',
        timeStyle: 'medium'
      }
    },
    editLink: {
      pattern: 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
      text: '在 GitHub 上编辑此页'
    },
    footer: {
      message: 'Released under the Apache-2.0 License.',
      copyright: 'Copyright © 2026 Proify, Tomakino'
    }
  }
})
