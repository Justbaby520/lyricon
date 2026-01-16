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
@file:Suppress("unused")

package io.github.proify.lyricon.provider

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 提供者元数据封装类。
 *
 * 使用键值对存储提供者相关信息
 *
 * @property map 内部存储的键值对
 */
@Parcelize
@Serializable
class ProviderMetadata(
    private val map: Map<String, String?> = emptyMap()
) : Map<String, String?> by map, Parcelable

/**
 * 创建 [ProviderMetadata] 的简便方法。
 *
 * @param pairs 键值对数组
 * @return 对应的 [ProviderMetadata] 实例
 */
fun providerMetadataOf(vararg pairs: Pair<String, String?>): ProviderMetadata =
    ProviderMetadata(mapOf(*pairs))