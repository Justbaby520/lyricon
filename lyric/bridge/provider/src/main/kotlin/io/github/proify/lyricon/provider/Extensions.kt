/*
 * Copyright 2026 Proify, Tomakino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.proify.lyricon.provider

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

internal val json: Json = Json {
    coerceInputValues = true     // 尝试转换类型
    ignoreUnknownKeys = true     // 忽略未知字段
    isLenient = true             // 宽松的 JSON 语法
    explicitNulls = false        // 不序列化 null
    encodeDefaults = false       // 不序列化默认值
}

/**
 * 使用 ZLIB 压缩字节数组
 *
 * @param level 压缩等级
 * @param bufferSize 缓冲区大小，默认为 4KB
 * @return 压缩后的字节数组
 */
internal fun ByteArray.deflate(
    level: Int = Deflater.DEFAULT_COMPRESSION,
    bufferSize: Int = 4096
): ByteArray {
    if (isEmpty()) return byteArrayOf()

    val deflater = Deflater(level)
    // 预估输出大小为原大小的一半，减少 ByteArrayOutputStream 的扩容频率
    val outputStream = ByteArrayOutputStream(size / 2)

    return try {
        deflater.setInput(this)
        deflater.finish()

        val buffer = ByteArray(bufferSize)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.toByteArray()
    } finally {
        deflater.end()
    }
}