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

enum class ConnectionStatus {
    /** 未连接 */
    DISCONNECTED,

    /** 已断开连接（服务器主动触发） */
    DISCONNECTED_REMOTE,

    /** 已断开连接（用户主动触发） */
    DISCONNECTED_USER,

    /** 连接中 */
    CONNECTING,

    /** 已连接 */
    CONNECTED,
}