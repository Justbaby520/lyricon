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

package io.github.proify.lyricon.central

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.proify.lyricon.central.Constants.isDebug
import io.github.proify.lyricon.central.provider.ProviderManager
import io.github.proify.lyricon.central.provider.RemoteProvider
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 中央广播接收器。
 *
 * 监听来自播放器提供者的注册请求，并将其封装为 [RemoteProvider] 后
 * 注册到 [ProviderManager]。
 */
internal object CentralReceiver : BroadcastReceiver() {

    private const val TAG = "CentralReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Constants.ACTION_REGISTER_PROVIDER) {
            registerProvider(intent)
        }
    }

    /**
     * 从 Intent 中提取 AIDL Binder 并转换为指定类型。
     *
     * @return 对应的 Binder 实例，类型不匹配或不存在时返回 null
     */
    private inline fun <reified T> getBinder(intent: Intent): T? {
        val binder = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
            ?.getBinder(Constants.EXTRA_BINDER) ?: return null

        return when (T::class) {
            IProviderBinder::class -> IProviderBinder.Stub.asInterface(binder) as? T
            else -> {
                Log.e(TAG, "Unknown binder type")
                null
            }
        }
    }

    /**
     * 注册来自远程提供者的播放器服务。
     *
     * 主要流程：
     * 1. 获取 [IProviderBinder] 并解析 [ProviderInfo]；
     * 2. 校验信息有效性；
     * 3. 封装为 [RemoteProvider] 并注册到 [ProviderManager]；
     * 4. 调用远程回调通知注册完成。
     *
     * 出现异常时会自动撤销已注册的提供者。
     */
    private fun registerProvider(intent: Intent) {
        Log.d(TAG, "registerProvider")

        val binder = getBinder<IProviderBinder>(intent) ?: return
        var provider: RemoteProvider? = null

        try {
            val providerInfo = binder.providerInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(ProviderInfo.serializer(), it) }

            if (providerInfo?.providerPackageName.isNullOrBlank() ||
                providerInfo.playerPackageName.isBlank()
            ) {
                Log.e(TAG, "Provider info is invalid")
                return
            }

            provider = RemoteProvider(binder, providerInfo)
            ProviderManager.register(provider)

            binder.onRegistrationCallback(provider.service)
            if (isDebug()) {
                Log.d(
                    TAG,
                    "Provider registered:" + " ${providerInfo.providerPackageName} / "
                            + providerInfo.playerPackageName
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Provider registration failed", e)
            provider?.let { ProviderManager.unregister(it) }
        }
    }
}