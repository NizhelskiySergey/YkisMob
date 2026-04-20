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

package com.ykis.mob.core.snackbar

import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SnackbarManager {
  private val messages: MutableStateFlow<SnackbarMessage?> = MutableStateFlow(null)

  val snackbarMessages: StateFlow<SnackbarMessage?>
    get() = messages.asStateFlow()

  fun showMessage(@StringRes message: Int) {
    Log.d("YkisLog", "Snackbar: [SHOW_RES] ID: $message")
    messages.value = SnackbarMessage.ResourceSnackbar(message)
  }

  fun showMessage(message: String) {
    // Защита от пустых сообщений
    if (message.isBlank()) return

    Log.d("YkisLog", "Snackbar: [SHOW_STR] Text: $message")
    messages.value = SnackbarMessage.StringSnackbar(message)
  }

  fun showMessage(message: SnackbarMessage) {
    Log.d("YkisLog", "Snackbar: [SHOW_MSG] Object: $message")
    messages.value = message
  }

  // КРИТИЧНО: Вызывай этот метод в LaunchedEffect сразу после showSnackbar
  fun clearMessage() {
    if (messages.value != null) {
      Log.d("YkisLog", "Snackbar: [CLEAR] Сообщение удалено из очереди")
      messages.value = null
    }
  }
}

