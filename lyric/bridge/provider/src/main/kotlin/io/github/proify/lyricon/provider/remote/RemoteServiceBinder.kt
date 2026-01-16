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

package io.github.proify.lyricon.provider.remote

/**
 * 远程服务绑定器接口。
 *
 * 用于管理远程服务实例的绑定操作。
 *
 * @param T 远程服务类型
 */
internal interface RemoteServiceBinder<T> {

    /**
     * 绑定远程服务实例。
     *
     * @param service 远程服务实例
     */
    fun bindRemoteService(service: T)
}