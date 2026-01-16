/*
 * Copyright (c) 2026 Proify
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

val json: Json = Json { ignoreUnknownKeys = true }

/**
 * 对字节数组进行 DEFLATE 压缩。
 *
 * @param level 压缩级别，默认使用 [Deflater.DEFAULT_COMPRESSION]
 * @param bufferSize 压缩缓冲区大小，默认使用 [DEFAULT_BUFFER_SIZE]
 * @return 压缩后的字节数组，如果原数组为空则返回空数组
 */
internal fun ByteArray.deflate(
    level: Int = Deflater.DEFAULT_COMPRESSION,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): ByteArray {
    if (isEmpty()) return byteArrayOf()

    val deflater = Deflater(level)
    return try {
        deflater.setInput(this)
        deflater.finish()

        ByteArrayOutputStream(maxOf(bufferSize, size / 2)).use { output ->
            val buffer = ByteArray(bufferSize)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            output.toByteArray()
        }
    } finally {
        deflater.end()
    }
}