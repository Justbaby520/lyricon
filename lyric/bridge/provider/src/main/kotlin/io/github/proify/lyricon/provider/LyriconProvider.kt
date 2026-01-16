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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.proify.lyricon.provider.ProviderBinder.OnRegistrationCallback
import io.github.proify.lyricon.provider.remote.ConnectionStatus
import io.github.proify.lyricon.provider.remote.RemoteService
import io.github.proify.lyricon.provider.remote.RemoteServiceProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 歌词提供者类，用于向中心服务注册并提供歌词服务。
 *
 * 该类负责：
 * - 管理与中心服务的连接和注册状态；
 * - 提供远程调用接口 [service]；
 * - 管理提供者信息 [providerInfo]；
 * - 支持资源释放及生命周期管理。
 *
 * @property context 上下文对象
 * @property providerPackageName 提供者包名
 * @property playerPackageName 播放器包名（默认为 providerPackageName）
 * @property logo 播放器 Logo，可为空
 * @property metadata 提供者元数据，可为空
 */
class LyriconProvider(
    context: Context,
    providerPackageName: String,
    playerPackageName: String = providerPackageName,
    logo: ProviderLogo? = null,
    metadata: ProviderMetadata? = null
) {

    private companion object {
        private const val TAG = "LyriconProvider"
        private const val CONNECTION_TIMEOUT_MS = 4000L
    }

    /** 应用级上下文 */
    private val appContext: Context = context.applicationContext

    /** 本地提供者服务实例 */
    private val providerService = ProviderService()

    /** 远程服务代理，用于与中心服务交互 */
    private val serviceProxy = RemoteServiceProxy(this)

    /** 提供者绑定器，负责注册回调和广播交互 */
    private val binder = ProviderBinder(this, providerService, serviceProxy)

    /** 协程作用域，用于超时处理等异步操作 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 注册连接超时任务 */
    @Volatile
    private var connectionTimeoutJob: Job? = null

    /** 实例销毁标记 */
    private val destroyed = AtomicBoolean(false)

    /** 监听中心服务启动/重启事件 */
    private val centralServiceListener = object : CentralServiceReceiver.ServiceListener {
        override fun onServiceBootCompleted() {
            if (serviceProxy.connectionStatus == ConnectionStatus.DISCONNECTED_REMOTE) {
                Log.d(TAG, "Central service restarted, attempting re-registration")
                register()
            }
        }
    }

    /** 提供者信息对象，包含包名、Logo、元数据等 */
    val providerInfo: ProviderInfo = ProviderInfo(
        providerPackageName = providerPackageName,
        playerPackageName = playerPackageName,
        logo = logo,
        metadata = metadata
    )

    /** 远程服务接口 */
    val service: RemoteService = serviceProxy

    init {
        CentralServiceReceiver.initialize(appContext)
        CentralServiceReceiver.addServiceListener(centralServiceListener)
    }

    /**
     * 向中心服务发起注册请求。
     *
     * 注册流程：
     * - 若当前未连接或未注册，启动注册流程；
     * - 若已连接或正在连接，直接返回 false。
     *
     * @return true 表示已发起注册，false 表示已连接或正在连接
     * @throws IllegalStateException 实例已被销毁
     */
    @Synchronized
    fun register(): Boolean {
        if (destroyed.get()) error("Provider has been destroyed")

        return when (serviceProxy.connectionStatus) {
            ConnectionStatus.CONNECTED,
            ConnectionStatus.CONNECTING -> false

            else -> {
                performRegistration()
                true
            }
        }
    }

    /**
     * 执行注册流程：
     * - 设置连接状态为 CONNECTING；
     * - 启动超时任务；
     * - 向中心服务发送注册广播；
     * - 注册回调监听注册成功事件。
     */
    @SuppressLint("MemberExtensionConflict")
    private fun performRegistration() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null

        val registrationCallback = object : OnRegistrationCallback {
            override fun onRegistered() {
                connectionTimeoutJob?.cancel()
                connectionTimeoutJob = null
                binder.removeRegistrationCallback(this)
            }
        }

        serviceProxy.connectionStatus = ConnectionStatus.CONNECTING

        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (serviceProxy.connectionStatus == ConnectionStatus.CONNECTING) {
                serviceProxy.connectionStatus = ConnectionStatus.DISCONNECTED
                binder.removeRegistrationCallback(registrationCallback)

                serviceProxy.connectionListeners.forEach {
                    runCatching { it.onConnectTimeout(this@LyriconProvider) }
                }
            }
        }

        binder.addRegistrationCallback(registrationCallback)

        val bundle = Bundle().apply {
            putBinder(Constants.EXTRA_BINDER, binder)
        }

        val intent = Intent(Constants.ACTION_REGISTER_PROVIDER).apply {
            setPackage(Constants.CENTRAL_PACKAGE_NAME)
            putExtra(Constants.EXTRA_BUNDLE, bundle)
        }

        appContext.sendBroadcast(intent)
    }

    /**
     * 注销提供者，释放连接资源。
     *
     * @throws IllegalStateException 实例已被销毁
     */
    @Synchronized
    fun unregister() {
        if (destroyed.get()) error("Provider has been destroyed")
        unregisterByUser()
    }

    /**
     * 用户主动注销实现：
     * - 取消注册超时任务；
     * - 断开远程服务连接。
     */
    @SuppressLint("MemberExtensionConflict")
    private fun unregisterByUser() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
        serviceProxy.disconnect(RemoteServiceProxy.DisconnectType.USER)
    }

    /**
     * 销毁实例，释放所有资源。
     *
     * 销毁后对象不可再次使用。
     */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return

        scope.cancel()
        unregisterByUser()
        CentralServiceReceiver.removeServiceListener(centralServiceListener)
    }
}