/*
 * Copyright 2022-2024 The Android Open Source Project
 * Адаптировано для проекта ykis.mob
 */

package com.ykis.mob.ui.navigation

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Описывает физическое состояние устройства (обычное, книга или разделение).
 */
sealed interface DevicePosture {
  // Обычное состояние (раскрыт полностью или обычный моноблок)
  data object NormalPosture : DevicePosture

  // Состояние "Книги" (вертикальный сгиб, полуоткрыт)
  data class BookPosture(
    val hingePosition: Rect
  ) : DevicePosture

  // Состояние разделения (например, Surface Duo, где два экрана физически разделены)
  data class Separating(
    val hingePosition: Rect,
    val orientation: FoldingFeature.Orientation // Изменил на val (неизменяемое)
  ) : DevicePosture
}

/**
 * Проверяет, находится ли устройство в режиме "книги".
 * Используется экспериментальный контракт, чтобы компилятор понимал: если true, то foldFeature не null.
 */
@OptIn(ExperimentalContracts::class)
fun isBookPosture(foldFeature: FoldingFeature?): Boolean {
  contract { returns(true) implies (foldFeature != null) }
  return foldFeature?.state == FoldingFeature.State.HALF_OPENED &&
    foldFeature.orientation == FoldingFeature.Orientation.VERTICAL
}

/**
 * Проверяет, разделено ли содержимое физическим препятствием (петлей).
 */
@OptIn(ExperimentalContracts::class)
fun isSeparating(foldFeature: FoldingFeature?): Boolean {
  contract { returns(true) implies (foldFeature != null) }
  return foldFeature?.state == FoldingFeature.State.FLAT && foldFeature.isSeparating
}

/**
 * Типы навигации в зависимости от размера экрана:
 * - BOTTOM_NAVIGATION: для телефонов (снизу)
 * - NAVIGATION_RAIL: боковая узкая панель (для планшетов)
 * - PERMANENT_DRAWER: широкая панель (для десктопов/больших экранов)
 */
enum class NavigationType {
  BOTTOM_NAVIGATION,
  NAVIGATION_RAIL_COMPACT,
  NAVIGATION_RAIL_EXPANDED,
  PERMANENT_NAVIGATION_DRAWER
}

/**
 * ContentType определяет, сколько колонок контента показывать:
 * - SINGLE_PANE: одна колонка (телефон)
 * - DUAL_PANE: две колонки (планшет/складной экран)
 */
enum class ContentType {
  SINGLE_PANE, DUAL_PANE
}

/**
 * Перечисление разделов приложения для детального отображения контента.
 */
enum class ContentDetail {
  STANDARD_USER,
  BTI,            // БТИ
  FAMILY,         // Состав семьи
  OSBB,           // ОСМД / ОСББ
  WATER_SERVICE,  // Водоканал
  WARM_SERVICE,   // Теплосеть
  GARBAGE_SERVICE,// Вывоз мусора
  WATER_METER,    // Водомеры (инфо)
  HEAT_METER,     // Теплосчетчики (инфо)
  WATER_READINGS, // Показания воды
  HEAT_READINGS,  // Показания тепла
  PAYMENT_LIST,   // Список платежей
  PAYMENT_CHOICE  // Выбор оплаты (исправил CHOICE вместо PAYMENT_CHOICE)
}

