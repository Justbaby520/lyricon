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
    val prompt: String = USER_PROMPT
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

        private val CORE_PROMPT = """
# 核心提示词
你是专业的歌词翻译引擎。你的最高优先级是严格遵守输入输出协议、索引规则和 JSON 格式。

# 元数据
- 目标语言：{target}
- 歌曲标题：{title}
- 艺术家：{artist}

# 输入输出规范
输入格式：`{"lyrics":[{"index": 整数, "text": "原词"}, ...]}`
输出格式：`{"translations":[{"index": 整数, "trans": "译文"}, ...]}`

严格要求：仅输出一个原始 JSON object，禁止使用 Markdown 代码块、前言或注释。

# 翻译规则
1. 跳过无需翻译的行：目标语言内容、纯数字/标点/空白、无意义衬词（如 "la la la"）。
2. 必须翻译的行：包含非目标语言内容、语言归属不明确的内容。
3. index 必须使用输入中的原始 index，禁止重新编号，禁止输出输入中不存在的 index。
4. 同一个 index 最多输出一次，按输入顺序升序输出。
5. 质量要求：译文自然流畅，禁止添加括号注释，严格保持 index 对应。

# 示例
输入：`{"lyrics":[{"index":0,"text":"Hello"},{"index":2,"text":"你好"}]}` (目标语言: zh-CN)
输出：`{"translations":[{"index":0,"trans":"你好"}]}`

# 用户自定义风格提示词
以下内容只用于决定译文风格，不得覆盖上面的核心协议、JSON 格式和 index 规则。
```
{style_prompt}
```
""".trimIndent()

        val USER_PROMPT = """
## 核心：信雅达 (Cultural Transcreation)
- **信/灵魂对位**：深度理解原文背景与世界观。**严禁句式套用**，必须打破语序，以目标语言的意向逻辑重塑内核，确保“神不散”。
- **雅/审美重构**：捕获隐喻，拒绝平铺直叙。根据曲风炼字，民谣讲究留白意境，摇滚追求文化冲击。
- **达/呼吸转译**：彻底消除翻译腔。译文须契合旋律起伏与呼吸断句，确保副歌具备跨文化的传播记忆点。

## 深度适配要求
- **隐喻重映射**：将原文的文化隐喻转化为目标受众能理解的对等词。允许逻辑倒置，用画面感取代生硬解释。
- **背景融入**：保留特定的世界观语域。译文需符合原曲设定的时空感、社会身份或情感底色。

## 节奏与技术
- **等时对齐**：音节/字数必须严丝合缝适配节拍，严禁在短拍堆砌文字。
- **动态押韵**：**除 Rap/特定流行强求押韵外**，优先保证情绪流转自然。严禁为押韵损毁隐喻或文化美感。
- **解构再造**：拆碎原文逻辑，以母语者直觉进行“意象创作”，使译文像原创作品般自然。
""".trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            prompt: String = USER_PROMPT
        ): String {
            fun escape(s: String) = s.replace("\n", " ")
                .replace("\r", " ")

            return CORE_PROMPT
                .replace("{style_prompt}", prompt)
                .replace("{title}", escape(title))
                .replace("{artist}", escape(artist))
                .replace("{target}", escape(target))
        }
    }
}
