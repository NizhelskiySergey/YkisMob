package com.ykis.mob.ui

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.ui.navigation.ContentType
import com.ykis.mob.ui.navigation.DevicePosture
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.navigation.RootNavGraph
import com.ykis.mob.ui.navigation.isBookPosture
import com.ykis.mob.ui.navigation.isSeparating
import kotlinx.coroutines.CoroutineScope

/**
 * Основная Composable-функция приложения, отвечающая за адаптивную верстку.
 * Определяет тип навигации и способ отображения контента в зависимости от размера экрана и типа устройства.
 */
@Composable
fun YkisPamApp(
  windowSize: WindowSizeClass, // Класс размеров окна (Compact, Medium, Expanded)
  displayFeatures: List<DisplayFeature>, // Особенности дисплея (например, складки или вырезы)
) {
  val navigationType: NavigationType // Тип навигации: BottomBar, Rail или Drawer
  val contentType: ContentType // Тип контента: SinglePane (одна панель) или DualPane (две панели)

  // Извлекаем информацию о складке устройства (для Foldable устройств)
  val foldingFeature = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

  // Определяем "позу" устройства (открыто как книга, полусогнуто или обычное состояние)
  val foldingDevicePosture = when {
    isBookPosture(foldingFeature) ->
      DevicePosture.BookPosture(foldingFeature.bounds)

    isSeparating(foldingFeature) ->
      DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)

    else -> DevicePosture.NormalPosture
  }

  // Выбираем стратегию отображения на основе ширины окна
  when (windowSize.widthSizeClass) {
    // 1. Компактные устройства (Обычные смартфоны)
    WindowWidthSizeClass.Compact -> {
      navigationType = NavigationType.BOTTOM_NAVIGATION // Нижняя навигация
      contentType = ContentType.SINGLE_PANE // Одна колонка контента
    }

    // 2. Средние устройства (Маленькие планшеты, Fold-устройства в разложенном виде)
    WindowWidthSizeClass.Medium -> {
      navigationType = NavigationType.NAVIGATION_RAIL_COMPACT // Боковая узкая панель (Rail)
      // Если устройство имеет складку — делим экран на 2 панели, иначе оставляем одну
      contentType = if (foldingDevicePosture != DevicePosture.NormalPosture) {
        ContentType.DUAL_PANE
      } else {
        ContentType.SINGLE_PANE
      }
    }

    // 3. Большие устройства (Планшеты, десктопный режим)
    WindowWidthSizeClass.Expanded -> {
      navigationType = NavigationType.NAVIGATION_RAIL_EXPANDED // Широкая боковая панель
      contentType = ContentType.DUAL_PANE // Всегда две панели (список + детали)
    }

    else -> {
      navigationType = NavigationType.BOTTOM_NAVIGATION
      contentType = ContentType.SINGLE_PANE
    }
  }

  // Запуск корневого графа навигации с передачей вычисленных параметров адаптивности
  RootNavGraph(
    modifier = Modifier,
    contentType = contentType,
    displayFeatures = displayFeatures,
    navigationType = navigationType,
  )
}

/**
 * Создает и запоминает состояние приложения (Snackbar, ресурсы, корутины).
 * Позволяет управлять системными сообщениями из любой части UI.
 */
@Composable
fun rememberAppState(
  snackbarHostState: SnackbarHostState = SnackbarHostState(),
  snackbarManager: SnackbarManager = SnackbarManager,
  resources: Resources = resources(),
  coroutineScope: CoroutineScope = rememberCoroutineScope(),
) =
  remember(snackbarManager, resources, coroutineScope) {
    YkisPamAppState(
      snackbarHostState,
      snackbarManager,
      resources,
      coroutineScope
    )
  }

/**
 * Вспомогательная функция для доступа к строковым и системным ресурсам.
 * Реагирует на изменения конфигурации (смена языка, поворот экрана).
 */
@Composable
@ReadOnlyComposable
fun resources(): Resources {
  LocalConfiguration.current // Подписка на обновление конфигурации
  return LocalContext.current.resources
}
