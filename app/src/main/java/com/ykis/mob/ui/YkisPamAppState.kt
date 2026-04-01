/*
 * Copyright 2022-2024 Google LLC
 * Адаптировано для проекта YkisPam
 */

package com.ykis.mob.ui

import android.content.res.Resources
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
  // Состояние хоста Snackbar (через него физически выводятся сообщения)
  val snackbarHostState: SnackbarHostState,
  // Глобальный синглтон-менеджер сообщений (бизнес-логика)
  private val snackbarManager: SnackbarManager,
  // Доступ к ресурсам для перевода строк (strings.xml)
  private val resources: Resources,
  // Область видимости корутин (обычно привязана к жизненному циклу экрана)
  val coroutineScope: CoroutineScope
) {
  init {
    // Запускаем бесконечный цикл прослушивания сообщений при создании объекта
    coroutineScope.launch {
      // Подписываемся на поток сообщений из SnackbarManager
      snackbarManager.snackbarMessages
        .filterNotNull() // Пропускаем пустые значения
        .collect { snackbarMessage ->
          // 1. Преобразуем объект сообщения в готовую строку (с учетом локализации)
          val text = snackbarMessage.toMessage(resources)

          // 2. Вызываем системную функцию отображения всплывающего уведомления
          // Это приостанавливающая функция (suspend), она дождется закрытия уведомления
          snackbarHostState.showSnackbar(text)
        }
    }
  }
}
