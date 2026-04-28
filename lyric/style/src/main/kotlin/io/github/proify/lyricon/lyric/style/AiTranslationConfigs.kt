/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class AiTranslationConfigs(
    val provider: String? = null,
    val targetLanguage: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val baseUrl: String? = null,
    val prompt: String = BASE_PROMPT
) : Parcelable {

    @IgnoredOnParcel
    val isUsable by lazy {
        !provider.isNullOrBlank()
                && !targetLanguage.isNullOrBlank()
                && !apiKey.isNullOrBlank()
                && !model.isNullOrBlank()
                && !baseUrl.isNullOrBlank()
    }

    override fun toString(): String {
        return "AiTranslationConfigs(baseUrl=$baseUrl, provider=$provider, targetLanguage=$targetLanguage, apiKey=${
            apiKey.orEmpty().take(6)
        }..., model=$model prompt=${
            prompt.take(30)
        }..., isUsable=$isUsable)"
    }

    companion object {

        private val BASE_PROMPT = """
# 角色
你是专业的歌词翻译引擎。输入带序号的歌词 JSON 数组，输出仅含译文的 JSON 数组。

# 元数据
- 目标语言：{target}
- 歌曲标题：{title}
- 艺术家：{artist}

# 风格指南
```
{user_prompt}
```

# 输入输出规范
输入格式：`[{"index": 整数, "text": "原词"}, ...]`
输出格式：`[{"index": 整数, "trans": "译文"}, ...]`

严格要求：index从0开始。仅输出原始 JSON，禁止使用 Markdown 代码块、前言或注释。

# 翻译规则
1. 跳过无需翻译的行：目标语言内容、纯数字/标点/空白、无意义衬词（如 "la la la"）。
2. 必须翻译的行：包含非目标语言内容、语言归属不明确的内容。
3. 质量要求：译文自然流畅，禁止添加括号注释，严格保持 index 对应和升序。

# 示例
输入：`[{"index":0,"text":"Hello"},{"index":1,"text":"你好"}]` (目标语言: zh-CN)
输出：`[{"index":0,"trans":"你好"}]`
""".trimIndent()

        val USER_PROMPT = """
## 一、隐性语境建模
1. 世界观对齐：结合歌曲背景（如游戏、ACG、特定历史），译文用词需匹配其语域特征。
2. 风格决策：
   - Pop/Indie：情感优先，深度意译。
   - Rap/Hip-Hop：行末押韵，可调整语序。
   - 抒情/民谣：文学性意象，保持呼吸感与留白。

## 二、歌词重构标准
- 可唱性：译文音节数匹配原句节奏，实现“以歌译歌”。
- 语义去壳：本地化，摆脱源语语法，用目标语言原生表达重写，杜绝翻译腔。
- 意象等效：将文化特有隐喻转换为目标文化中同等意境的表达。

## 三、输出约束
- 严格禁止：分析过程、括号说明、文化注释、纯数字/标点/空白/纯语气词、无意义衬词。
- 最终输出：纯净、可直接分发的目标语言歌词译文。
""".trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            prompt: String = USER_PROMPT
        ): String {
            fun escape(s: String) = s.replace("\n", " ").replace("\r", " ")

            return BASE_PROMPT
                .replace("{user_prompt}", prompt)
                .replace("{title}", escape(title))
                .replace("{artist}", escape(artist))
                .replace("{target}", escape(target))
        }

        fun cleanLlmOutput(raw: String): String {
            val regex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            return regex.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
        }
    }
}