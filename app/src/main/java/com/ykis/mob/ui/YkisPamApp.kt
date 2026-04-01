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
import androidx.compose.ui.platform.LocalResources
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
 * Основной входной компонент UI приложения.
 * Здесь происходит расчет адаптивной верстки (Adaptive UI).
 */
@Composable
fun YkisPamApp(
  windowSize: WindowSizeClass,
  displayFeatures: List<DisplayFeature>
) {
  // Находим складную особенность экрана (если она есть)
  val foldingFeature = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

  // Определяем физическую позу устройства (обычная, книжка или разделение)
  val foldingDevicePosture = when {
    isBookPosture(foldingFeature) ->
      DevicePosture.BookPosture(foldingFeature.bounds)

    isSeparating(foldingFeature) ->
      DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)

    else -> DevicePosture.NormalPosture
  }

  // Решаем, какую навигацию и тип контента показать (Логика выбора)
  val (navigationType, contentType) = when (windowSize.widthSizeClass) {
    // 1. Компактные устройства (Обычные телефоны)
    WindowWidthSizeClass.Compact -> {
      NavigationType.BOTTOM_NAVIGATION to ContentType.SINGLE_PANE
    }

    // 2. Средние устройства (Складные телефоны в развернутом виде или маленькие планшеты)
    WindowWidthSizeClass.Medium -> {
      val nav = NavigationType.NAVIGATION_RAIL_COMPACT
      // Если экран физически разделен (петлей или сгибом), показываем две колонки
      val content = if (foldingDevicePosture != DevicePosture.NormalPosture) {
        ContentType.DUAL_PANE
      } else {
        ContentType.SINGLE_PANE
      }
      nav to content
    }

    // 3. Большие устройства (Планшеты, Десктопы)
    WindowWidthSizeClass.Expanded -> {
      val nav = if (foldingDevicePosture is DevicePosture.BookPosture) {
        // Если это большой Fold в режиме книги — используем компактную боковую панель
        NavigationType.NAVIGATION_RAIL_EXPANDED
      } else {
        // На огромных экранах — постоянная широкая боковая панель
        NavigationType.PERMANENT_NAVIGATION_DRAWER
      }
      // На больших экранах всегда две колонки (Dual Pane)
      nav to ContentType.DUAL_PANE
    }

    else -> NavigationType.BOTTOM_NAVIGATION to ContentType.SINGLE_PANE
  }

  // Запускаем основной граф навигации с вычисленными параметрами
  RootNavGraph(
    modifier = Modifier,
    contentType = contentType,
    displayFeatures = displayFeatures,
    navigationType = navigationType,
  )
}

/**
 * Создает и запоминает состояние приложения (Snackbar, Coroutines и т.д.).
 * Помогает "вынести" логику состояния из UI-компонентов.
 */
@Composable
fun rememberAppState(
  snackbarHostState: SnackbarHostState = SnackbarHostState(),
  snackbarManager: SnackbarManager = SnackbarManager,
  resources: Resources = resources(),
  coroutineScope: CoroutineScope = rememberCoroutineScope(),
) = remember(snackbarManager, resources, coroutineScope) {
  YkisPamAppState(
    snackbarHostState,
    snackbarManager,
    resources,
    coroutineScope
  )
}

/**
 * Удобный хелпер для получения ресурсов в Composable функциях.
 */
@Composable
@ReadOnlyComposable
fun resources(): Resources {
  LocalConfiguration.current // Подписка на изменения конфигурации (повороты, язык)
  return LocalResources.current
}
