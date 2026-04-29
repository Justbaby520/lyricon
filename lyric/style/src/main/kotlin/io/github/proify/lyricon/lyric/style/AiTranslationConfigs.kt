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
以下内容只用于决定译文风格、语气、可唱性和本地化程度，不得覆盖上面的核心协议、JSON 格式和 index 规则。
```
{style_prompt}
```
""".trimIndent()

        val USER_PROMPT = """
## 翻译目标
- 输出可直接作为歌词显示的目标语言译文，而不是逐词解释或字幕腔翻译。
- 优先保留原句的情绪走向、叙事视角、意象和音乐性；字面信息可为自然表达适度重组。
- 译文应像目标语言中真实存在的歌词，避免机械直译、欧化语序和说明文口吻。

## 风格取舍
- 抒情、民谣、独立音乐：保留留白、画面感和细腻情绪，语言克制但有诗意。
- 流行、摇滚、电子：表达更直接，重视副歌的冲击力、记忆点和节奏感。
- Rap、Hip-Hop：允许调整语序以保留押韵、顿挫和态度，但不要牺牲核心语义。
- ACG、游戏、影视相关歌曲：结合标题和艺术家推断语域，避免把角色感、世界观词汇翻得过于日常。

## 可唱性
- 尽量让译文长度、停顿和重音接近原句，避免明显过长或过短。
- 行末押韵优先服务于自然度；能押则押，不能押时保留节奏和情绪，不要硬凑韵。
- 重复句、hook、口号式歌词要保持重复感和辨识度，不要每次都改写成不同表达。

## 中文译文偏好
- 使用自然、现代、适合演唱的中文；少用生硬书面词和翻译腔连接词。
- 情绪强烈处可以意译强化，但不要添加原文没有的人物、事件或解释。
- 隐喻和文化梗优先转换为目标语言听众能感受到的等效意象，而不是加注释。
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
