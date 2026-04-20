/*
 * Copyright 2022-2024 Google LLC
 * Адаптировано для проекта YkisPam
 */

package com.ykis.mob.ui

import android.content.res.Resources
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.core.snackbar.SnackbarMessage.Companion.toMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Класс управления состоянием UI приложения.
 * Аннотация @Stable говорит Compose, что состояние объекта отслеживается и
 * изменения приведут к корректной рекомпозиции.
 */
@Stable
class YkisPamAppState(
  val snackbarHostState: SnackbarHostState,
  private val snackbarManager: SnackbarManager,
  private val resources: Resources,
  val coroutineScope: CoroutineScope
) {
  init {
    coroutineScope.launch {
      snackbarManager.snackbarMessages
        .filterNotNull()
        .collect { snackbarMessage ->
          // 1. Формируем текст
          val text = snackbarMessage.toMessage(resources)

          // 2. Показываем (это suspend функция, она "висит" пока снэкбар на экране)
          snackbarHostState.showSnackbar(text)

          // 3. КРИТИЧЕСКИЙ ФИКС: Сразу после показа очищаем менеджер,
          // чтобы при смене конфигурации или рекомпозиции сообщение не вылетело снова.
          snackbarManager.clearMessage()
          Log.d("YkisLog", "AppState: Snackbar показан и очищен из очереди")
        }
    }
  }
}
