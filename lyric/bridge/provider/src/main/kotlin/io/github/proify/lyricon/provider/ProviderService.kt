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

import android.content.Intent
import android.os.Bundle

/**
 * 提供者服务接口，用于处理远程调用。
 *
 * 实现该接口的类将用于处理来自中心服务的远程调用，并返回结果。
 *
 * **目前未实现相关功能，仅作为未来扩展。**
 */
interface ProviderService {

    /**
     * 由远程调用，处理相关命令
     *
     * @return 返回值
     */
    fun onRunCommand(intent: Intent?): Bundle?
}