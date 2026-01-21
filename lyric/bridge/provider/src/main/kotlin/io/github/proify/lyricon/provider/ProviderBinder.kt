/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider

import io.github.proify.lyricon.provider.service.RemoteServiceBinder
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 提供者绑定器，用于管理与中心服务的注册和回调。
 *
 * 主要职责：
 * - 提供 [IProviderBinder] 接口实现，用于中心服务调用；
 * - 维护提供者信息序列化数据；
 * - 管理注册回调 [OnRegistrationCallback] 集合；
 * - 与远程服务绑定和管理生命周期。
 *
 * @property provider 对应的 [LyriconProvider] 实例
 * @property providerService 本地服务实例
 * @property remoteServiceBinder 远程服务绑定器
 */
internal class ProviderBinder(
    val provider: LyriconProvider,
    private val providerService: LocalProviderService,
    private val remoteServiceBinder: RemoteServiceBinder<IRemoteService?>
) : IProviderBinder.Stub() {

    /** 序列化后的提供者信息字节数组，用于远程传输 */
    private val providerInfoByteArray by lazy {
        json.encodeToString<ProviderInfo>(provider.providerInfo).toByteArray()
    }

    /** 注册回调集合，用于通知注册完成事件 */
    private val registrationCallbacks = CopyOnWriteArraySet<OnRegistrationCallback>()

    /** 添加注册回调 */
    fun addRegistrationCallback(callback: OnRegistrationCallback) =
        registrationCallbacks.add(callback)

    /** 移除注册回调 */
    fun removeRegistrationCallback(callback: OnRegistrationCallback) =
        registrationCallbacks.remove(callback)

    /**
     * 当中心服务返回注册结果时调用：
     * - 绑定远程服务；
     * - 通知所有注册回调注册完成。
     */
    override fun onRegistrationCallback(remoteProviderService: IRemoteService?) {
        remoteServiceBinder.bindRemoteService(remoteProviderService)
        registrationCallbacks.forEach { it.onRegistered() }
    }

    /** 获取本地提供者服务实例 */
    override fun getProviderService(): IProviderService = providerService

    /** 获取序列化后的提供者信息 */
    override fun getProviderInfo(): ByteArray = providerInfoByteArray

    /** 注册回调接口，用于通知注册完成 */
    interface OnRegistrationCallback {
        /** 注册完成时调用 */
        fun onRegistered()
    }
}