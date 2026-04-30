/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.aitrans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TranslationRequestItem(val index: Int, val text: String)

@Serializable
internal data class TranslationRequest(val lyrics: List<TranslationRequestItem>)

@Serializable
data class TranslationItem(val index: Int, val trans: String)

@Serializable
internal data class TranslationResponse(val translations: List<TranslationItem>)

@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class ResponseFormat(
    val type: String
)

@Serializable
internal data class OpenAiChatResponse(
    val choices: List<Choice>
)

@Serializable
internal data class Choice(
    val message: ChatMessage
)
