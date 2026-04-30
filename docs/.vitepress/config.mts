import { defineConfig } from 'vitepress'

         export default defineConfig({
                                     title : 'Lyricon Docs',
                                     description : 'App 与 Lyric 文档',
                                     base : '/lyricon/',
                                            cleanUrls : true,
                                            lastUpdated : true,
                                     head : [
                                     ['link', { rel: 'icon', href: '/lyricon/logo.svg' }]
                                     ],
                                     themeConfig : {
                                     logo : '/logo.svg',
                                     siteTitle : 'Lyricon Docs',
                                     nav : [
                                     { text : '首页', link: '/' },
                                     { text: 'App', link: '/app/' },
                                           { text : 'Lyric', link: '/lyric/' },
                                           { text : 'Bridge', link: '/lyric/bridge/' },
                                     { text : 'GitHub', link: 'https://github.com/tomakino/lyricon' }
                                     ],
                                     sidebar : [
                                     {
                                     text : '开始',
                                     items : [
                                     { text : '文档首页', link: '/' },
                                     { text : '文档总览', link: '/README' }
                                     ]
                                     },
                                     {
                                     text : 'App',
                                     collapsed : false,
                                                 items : [
                                                 { text : '概览', link: '/app/' }
                                                          ]
                                                          },
                                                          {
                                                          text : 'Lyric',
                                                                 collapsed : false,
                                                                 items : [
                                                 { text : '概览', link: '/lyric/' },
                                                 { text : 'Bridge 概览', link: '/lyric/bridge/' }
                                                 ]
                                                 },
                                                 {
                                                 text : 'Lyric / Bridge / Provider',
                                                 collapsed : false,
                                                             items : [
                                                             { text : '概览', link: '/lyric/bridge/provider/' },
                                                                      { text : '快速开始', link: '/lyric/bridge/provider/quick-start' },
                                                                      { text : 'Manifest 配置', link: '/lyric/bridge/provider/manifest' },
                                                                      { text : '连接生命周期', link: '/lyric/bridge/provider/connection' },
                                                                        { text : '播放器控制', link: '/lyric/bridge/provider/player-control' },
                                                                        { text : '歌词数据结构', link: '/lyric/bridge/provider/lyrics-model' },
                                                                                 { text : '本地测试', link: '/lyric/bridge/provider/local-testing' },
                                                                                 { text : '常见问题', link: '/lyric/bridge/provider/faq' }
                                                                                 ]
                                                 },
                                                 {
                                                 text : 'Lyric / Bridge / Subscriber',
                                                 collapsed: false,
                                                          items : [
                                                          { text: '概览', link: '/lyric/bridge/subscriber/' },
                                                                { text : '快速开始', link: '/lyric/bridge/subscriber/quick-start' },
                                                                { text : '连接生命周期', link: '/lyric/bridge/subscriber/connection' },
                                                          { text : '活跃播放器', link: '/lyric/bridge/subscriber/active-player' },
                                                          { text : '回调说明', link: '/lyric/bridge/subscriber/callbacks' },
                                                          { text: '常见问题', link: '/lyric/bridge/subscriber/faq' }
                                                                ]
                                                                }
                                                                ],
                                                                socialLinks: [
                                                                           { icon : 'github', link: 'https://github.com/tomakino/lyricon' }
                                                                           ],
                                                          search : {
                                                          provider : 'local'
                                                          },
                                                          outline : {
                                                          level : [2, 3],
                                                                       label : '页面导航'
                                                                       },
                                                                       docFooter: {
                                                                                prev : '上一页',
                                                                                next : '下一页'
                                                                                },
                                                                                 lastUpdated : {
                                                                                 text : '最后更新',
                                                                                 formatOptions: {
                                                                                              dateStyle : 'short',
                                                                                              timeStyle : 'medium'
                                                                                              }
                                    },
                                    editLink : {
                                    pattern : 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
                                              text : '在 GitHub 上编辑此页'
                                              },
                                              footer : {
                                    message : 'Released under the Apache-2.0 License.',
                                    copyright : 'Copyright © 2026 Proify, Tomakino'
                                    }
                                    }
                                    })
