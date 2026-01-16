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

import io.github.proify.lyricon.provider.LyriconProvider

/**
 * 远程服务接口，定义与中心服务的交互。
 *
 * 主要职责：
 * - 提供播放器控制接口 [player]；
 * - 暴露激活状态和连接状态；
 * - 注册和移除连接状态监听器。
 */
interface RemoteService {

    /** 播放器控制接口 */
    val player: RemotePlayer

    /** 当前服务是否激活 */
    val isActivated: Boolean

    /** 当前连接状态 */
    val connectionStatus: ConnectionStatus

    /**
     * 注册连接状态监听器
     *
     * @param listener 监听器实例
     * @return 是否成功添加
     */
    fun addConnectionListener(listener: ConnectionListener): Boolean

    /**
     * 移除已注册的连接状态监听器
     *
     * @param listener 之前注册的监听器实例
     * @return 是否成功移除
     */
    fun removeConnectionListener(listener: ConnectionListener): Boolean
}

/**
 * 构建连接状态监听器的便捷函数
 *
 * 使用 [ConnectionListenerBuilder] 定义各类事件回调。
 */
fun buildConnectionListener(block: ConnectionListenerBuilder.() -> Unit): ConnectionListener {
    val builder = ConnectionListenerBuilder().apply(block)
    return object : ConnectionListener {
        override fun onConnected(provider: LyriconProvider) {
            builder.onConnected?.invoke(provider)
        }

        override fun onReconnected(provider: LyriconProvider) {
            builder.onReconnected?.invoke(provider)
        }

        override fun onDisconnected(provider: LyriconProvider) {
            builder.onDisconnected?.invoke(provider)
        }

        override fun onConnectTimeout(provider: LyriconProvider) {
            builder.onConnectTimeout?.invoke(provider)
        }
    }
}

/**
 * 扩展函数，向 [RemoteService] 注册连接状态监听器
 *
 * @param block 使用 [ConnectionListenerBuilder] 定义回调
 * @return 注册的监听器实例
 */
fun RemoteService.addOnConnectionListener(block: ConnectionListenerBuilder.() -> Unit)
        : ConnectionListener {
    val listener = buildConnectionListener(block)
    addConnectionListener(listener)
    return listener
}

/**
 * 连接状态监听器构建器
 *
 * 用于按需设置各类连接状态回调。
 *
 * @property onConnected 服务首次连接回调
 * @property onReconnected 服务重连回调
 * @property onDisconnected 服务断开回调
 * @property onConnectTimeout 连接超时回调
 */
class ConnectionListenerBuilder(
    var onConnected: ((LyriconProvider) -> Unit)? = null,
    var onReconnected: ((LyriconProvider) -> Unit)? = null,
    var onDisconnected: ((LyriconProvider) -> Unit)? = null,
    var onConnectTimeout: ((LyriconProvider) -> Unit)? = null
) {
    fun onConnected(block: (LyriconProvider) -> Unit): ConnectionListenerBuilder =
        apply { onConnected = block }

    fun onReconnected(block: (LyriconProvider) -> Unit): ConnectionListenerBuilder =
        apply { onReconnected = block }

    fun onDisconnected(block: (LyriconProvider) -> Unit): ConnectionListenerBuilder =
        apply { onDisconnected = block }

    fun onConnectTimeout(block: (LyriconProvider) -> Unit): ConnectionListenerBuilder =
        apply { onConnectTimeout = block }
}