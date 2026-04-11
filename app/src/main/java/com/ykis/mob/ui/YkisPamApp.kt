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
 * Добавлен параметр initialChatId для обработки переходов из Push-уведомлений.
 */
@Composable
fun YkisPamApp(
  windowSize: WindowSizeClass,
  displayFeatures: List<DisplayFeature>,
  initialChatId: String? = null // Получаем ID чата из MainActivity
) {
  // Находим складную особенность экрана
  val foldingFeature = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

  // Определяем физическую позу устройства
  val foldingDevicePosture = when {
    isBookPosture(foldingFeature) -> DevicePosture.BookPosture(foldingFeature.bounds)
    isSeparating(foldingFeature) -> DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)
    else -> DevicePosture.NormalPosture
  }

  // Решаем, какую навигацию и тип контента показать
  val (navigationType, contentType) = when (windowSize.widthSizeClass) {
    WindowWidthSizeClass.Compact -> {
      NavigationType.BOTTOM_NAVIGATION to ContentType.SINGLE_PANE
    }
    WindowWidthSizeClass.Medium -> {
      val nav = NavigationType.NAVIGATION_RAIL_COMPACT
      val content = if (foldingDevicePosture != DevicePosture.NormalPosture) {
        ContentType.DUAL_PANE
      } else {
        ContentType.SINGLE_PANE
      }
      nav to content
    }
    WindowWidthSizeClass.Expanded -> {
      val nav = if (foldingDevicePosture is DevicePosture.BookPosture) {
        NavigationType.NAVIGATION_RAIL_EXPANDED
      } else {
        NavigationType.PERMANENT_NAVIGATION_DRAWER
      }
      nav to ContentType.DUAL_PANE
    }
    else -> NavigationType.BOTTOM_NAVIGATION to ContentType.SINGLE_PANE
  }

  // Запускаем основной граф навигации
  RootNavGraph(
    modifier = Modifier,
    contentType = contentType,
    displayFeatures = displayFeatures,
    navigationType = navigationType,
    initialChatId = initialChatId // Пробрасываем ID дальше в граф
  )
}

/**
 * В RootNavGraph (или там, где инициализируется NavHost),
 * нужно добавить примерно такую логику:
 *
 * LaunchedEffect(initialChatId) {
 *    if (initialChatId != null) {
 *        // Логика перехода в конкретный чат
 *        navController.navigate("chat_route/$initialChatId")
 *    }
 * }
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

@Composable
@ReadOnlyComposable
fun resources(): Resources {
  LocalConfiguration.current
  return LocalResources.current
}

