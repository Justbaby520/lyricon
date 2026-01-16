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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 中央服务广播接收器。
 *
 * 负责监听中央服务启动完成广播，并通知注册的 [ServiceListener]。
 *
 * 提供初始化注册、监听器管理及广播分发功能。
 */
internal object CentralServiceReceiver {

    /** 是否已初始化并注册广播接收器 */
    private var isInitialized = false

    /** 已注册的服务监听器集合 */
    private val listeners = CopyOnWriteArraySet<ServiceListener>()

    /** 添加服务启动监听器 */
    fun addServiceListener(listener: ServiceListener) = listeners.add(listener)

    /** 移除服务启动监听器 */
    fun removeServiceListener(listener: ServiceListener) = listeners.remove(listener)

    /**
     * 初始化广播接收器，注册监听中央服务启动完成事件。
     *
     * @param context 应用上下文
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        ContextCompat.registerReceiver(
            context.applicationContext,
            ServiceReceiver,
            IntentFilter(Constants.ACTION_CENTRAL_BOOT_COMPLETED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /** 通知所有监听器中央服务已启动完成 */
    fun notifyServiceBootCompleted() = listeners.forEach {
        it.onServiceBootCompleted()
    }

    /** 内部广播接收器，接收中央服务启动完成广播 */
    object ServiceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_CENTRAL_BOOT_COMPLETED) {
                notifyServiceBootCompleted()
            }
        }
    }

    /** 服务监听器接口 */
    interface ServiceListener {
        /** 当中央服务启动完成时回调 */
        fun onServiceBootCompleted()
    }
}