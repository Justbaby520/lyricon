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

package io.github.proify.lyricon.provider.service

import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.ConnectionStatus
import io.github.proify.lyricon.provider.IRemoteService
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.RemotePlayer
import io.github.proify.lyricon.provider.player.CachedRemotePlayer
import io.github.proify.lyricon.provider.player.RemotePlayerProxy
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 远程服务代理。
 *
 * 管理与远程服务 [IRemoteService] 的连接生命周期，包括：
 * - 服务绑定与断开
 * - 播放器代理管理
 * - 连接状态通知
 * - 服务死亡处理
 *
 * @param provider 本地 [LyriconProvider] 实例
 */
internal class RemoteServiceProxy(
    private val provider: LyriconProvider
) : RemoteService, RemoteServiceBinder<IRemoteService?> {

    /** 断开类型，用于区分断开来源 */
    enum class DisconnectType {
        DEFAULT, // 默认断开
        USER,    // 用户主动断开
        REMOTE   // 远程服务死亡断开
    }

    companion object {
        private const val TAG = "ProviderServiceImpl"
    }

    private val playerProxy = RemotePlayerProxy()
    override val player: RemotePlayer = CachedRemotePlayer(playerProxy)

    /** 当前连接状态 */
    override var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        set(value) {
            field = value
            playerProxy.isConnected = value == ConnectionStatus.CONNECTED
        }

    /** 当前绑定的远程服务实例，可能为 null */
    private var remoteService: IRemoteService? = null

    /** 已注册的连接状态监听器集合 */
    val connectionListeners = CopyOnWriteArraySet<ConnectionListener>()

    /** 是否曾成功连接过远程服务 */
    private var hasConnectedHistory = false

    /** 服务死亡回调 */
    private val deathRecipient = IBinder.DeathRecipient {
        Log.d(TAG, "Service is dead")
        disconnect(DisconnectType.REMOTE)
    }

    /**
     * 绑定远程服务实例。
     *
     * 会初始化播放器代理，注册服务死亡回调，并通知连接监听器。
     *
     * @param service 远程服务实例，可为 null
     */
    override fun bindRemoteService(service: IRemoteService?) {
        Log.d(TAG, "Bind service")
        disconnect(DisconnectType.DEFAULT)

        if (service == null) {
            Log.w(TAG, "Service is null")
            return
        }

        val binder = service.asBinder()
        if (!binder.isBinderAlive) {
            Log.w(TAG, "Service is not alive")
            return
        }

        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to link death", e)
            return
        }

        remoteService = service
        playerProxy.bindRemoteService(service.player)

        connectionStatus = ConnectionStatus.CONNECTED
        connectionListeners.forEach {
            if (hasConnectedHistory) {
                it.onReconnected(provider)
            } else {
                it.onConnected(provider)
            }
        }
        hasConnectedHistory = true
    }

    /** 检查远程服务是否仍然可用 */
    override val isActivated: Boolean
        get() = remoteService?.asBinder()?.isBinderAlive ?: false

    /**
     * 断开远程服务连接。
     *
     * 会更新连接状态，清理播放器代理，并通知监听器。
     *
     * @param disconnectType 断开来源类型
     */
    fun disconnect(disconnectType: DisconnectType) {
        connectionStatus = when (disconnectType) {
            DisconnectType.USER -> ConnectionStatus.DISCONNECTED_USER
            DisconnectType.REMOTE -> ConnectionStatus.DISCONNECTED_REMOTE
            DisconnectType.DEFAULT -> ConnectionStatus.DISCONNECTED
        }
        if (ProviderConstants.isDebug()) Log.d(TAG, "Disconnect $disconnectType")

        playerProxy.bindRemoteService(null)

        remoteService?.let { service ->
            runCatching { service.asBinder().unlinkToDeath(deathRecipient, 0) }
                .onFailure { Log.w(TAG, "Failed to unlink death", it) }
            runCatching { service.disconnect() }
                .onFailure { Log.e(TAG, "Failed to disconnect service", it) }
            remoteService = null
            connectionListeners.forEach { it.onDisconnected(provider) }
        }
    }

    /** 注册连接状态监听器 */
    override fun addConnectionListener(listener: ConnectionListener) =
        connectionListeners.add(listener)

    /** 移除连接状态监听器 */
    override fun removeConnectionListener(listener: ConnectionListener) =
        connectionListeners.remove(listener)
}