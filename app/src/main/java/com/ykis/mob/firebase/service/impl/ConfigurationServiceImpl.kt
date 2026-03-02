/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.ykis.mob.firebase.service.impl

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.get
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.ykis.mob.BuildConfig
import com.ykis.mob.core.trace
import com.ykis.mob.firebase.service.repo.ConfigurationService
import kotlinx.coroutines.tasks.await
import com.ykis.mob.R.xml as AppConfig

class ConfigurationServiceImpl () : ConfigurationService {
    private val remoteConfig
        get() = Firebase.remoteConfig

    init {
    if (BuildConfig.DEBUG) {
      val configSettings = remoteConfigSettings { minimumFetchIntervalInSeconds = 0 }
      remoteConfig.setConfigSettingsAsync(configSettings)
    }

        remoteConfig.setDefaultsAsync(AppConfig.remote_config_defaults)
    }

    override suspend fun fetchConfiguration(): Boolean =
        trace(FETCH_CONFIG_TRACE) { remoteConfig.fetchAndActivate().await() }



    override val isWiFiCheckConfig: Boolean
        get() = remoteConfig[LOADING_FROM_WIFI].asBoolean()

    override val isMobileCheckConfig: Boolean
        get() = remoteConfig[LOADING_FROM_MOBILE].asBoolean()
    override val agreementTitle: String
        get() = remoteConfig[AGREEMENT_TITLE].asString()

    override val agreementText: String
        get() = remoteConfig[AGREEMENT_TEXT].asString()

    companion object {
        private const val LOADING_FROM_WIFI = "loading_from_wifi"
        private const val LOADING_FROM_MOBILE = "loading_from_mobile"
        private const val AGREEMENT_TITLE = "agreement_title"
        private const val AGREEMENT_TEXT = "agreement_text"

        private const val FETCH_CONFIG_TRACE = "fetchConfig"
    }
}
