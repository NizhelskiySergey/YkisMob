package com.ykis.mob.ui.navigation.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.UserListScreen
import com.ykis.mob.ui.navigation.getNavDestinations
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.appartment.ListMode
import com.ykis.mob.ui.screens.chat.ChatViewModel

@Composable
fun CustomNavigationRail(
  currentWidth: Dp,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  contentColor: Color = contentColorFor(containerColor),
  header: @Composable (ColumnScope.() -> Unit)? = null,
  windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    color = containerColor,
    contentColor = contentColor,
    modifier = modifier.fillMaxHeight().width(currentWidth),
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(windowInsets)
        .padding(vertical = 4.dp)
        .selectableGroup(),
    ) {
      if (header != null) {
        header()
        Spacer(Modifier.height(8.dp))
      }
      content()
    }
  }
}

@Composable
fun ApartmentNavigationRail(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  selectedDestination: String,
  navigateToDestination: (String) -> Unit = {},
  isRailExpanded: Boolean,
  onMenuClick: () -> Unit,
  chatViewModel: ChatViewModel,
  apartmentViewModel: ApartmentViewModel,
  navigateToApartment: (Int) -> Unit = {},
  railWidth: Dp,
  isApartmentsEmpty: Boolean
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val searchQuery by apartmentViewModel.searchQuery.collectAsStateWithLifecycle()
  val apartments by apartmentViewModel.filteredApartments.collectAsStateWithLifecycle()
  val isUserAdmin = baseUIState.userRole != UserRole.StandardUser
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  val listMode = baseUIState.listMode
  val isOrgAdmin = baseUIState.userRole != UserRole.StandardUser && baseUIState.userRole != UserRole.OsbbUser
  val raions = baseUIState.raions // Просто берем из уже готового стейта

  // 1. ПОДПИСЫВАЕМСЯ НА ВСЕ ТРИ СПИСКА
  val houses by apartmentViewModel.drawerHouses.collectAsStateWithLifecycle() // Список домов (новый)
  val drawerApartments by apartmentViewModel.drawerApartments.collectAsStateWithLifecycle() // Список квартир (новый)
  // 2. РЕАКТИВНЫЙ ПЕРЕСЧЕТ: Как только мапа unreadCounts меняется,
  // эти переменные пересчитаются автоматически
  val totalUnread = remember(unreadCounts) { unreadCounts.values.sum() }
  LaunchedEffect(totalUnread) {
    Log.d("YkisLog", "RailMenu: Total sum calculated: $totalUnread")
  }
  // Внутри ApartmentNavigationRail
  val apartmentBadges = remember(unreadCounts) {
    unreadCounts.map { (fullKey, count) ->
      // Извлекаем ID: разбиваем строку по "_" и берем ту часть, которая является числом
      // Для "OSBB_3_1336_izLMP..." -> части: [OSBB, 3, 1336, izLMP...]
      // Нам нужно 1336
      val parts = fullKey.split("_")
      // Обычно ID квартиры — это третья часть в ОСББ (индекс 2)
      // или вторая в службах. Чтобы не гадать, ищем первое длинное число:
      val addressId = parts.find { it.length >= 3 && it.all { char -> char.isDigit() } } ?: ""
      addressId to count
    }.filter { it.first.isNotEmpty() }
      .toMap()
  }
  CustomNavigationRail(
    modifier = modifier,
    currentWidth = railWidth,
    header = {
      // 1. Компактный Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onMenuClick) {
          Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
      }
      if (isRailExpanded) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
          if (isUserAdmin) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              // Поле поиска
              OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                  Log.d("YkisLog", "Rail: [SEARCH_INPUT] Query: $it")
                  apartmentViewModel.onSearchQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                placeholder = { Text("Пошук...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                      apartmentViewModel.onSearchQueryChanged("")
                    }) {
                      Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                    }
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
              )
            }
          } else {
            FloatingActionButton(
              onClick = { navigateToDestination(AddApartmentScreen.route) },
              modifier = Modifier.fillMaxWidth().height(40.dp),
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddHome, null, Modifier.size(18.dp))
                if (railWidth > 150.dp) {
                  Text(" Додати", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // --- 1. ВЕРХНЯЯ ЧАСТЬ (СПИСКИ + ПОИСК) ---
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        if (isRailExpanded) {
          // Кнопка назад (скрываем при активном поиске)
          if (listMode != ListMode.RAIONS && isOrgAdmin && searchQuery.isEmpty()) {
            TextButton(
              onClick = { apartmentViewModel.goBackLevel() },
              modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            ) {
              Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(8.dp))
              Text("Назад")
            }
          }
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
          ) {
            // ПРОВЕРКА: Если есть текст поиска — показываем фильтрованный список
            if (searchQuery.isNotEmpty()) {
              items(apartments, key = { "search_${it.addressId}" }) { item ->
                RailItemContent(
                  title = item.address,
                  subtitle = item.nanim,
                  extraInfo = "о/р ${item.addressId}",
                  icon = if (listMode == ListMode.HOUSES) Icons.Default.Domain else Icons.Default.Home,
                  isSelected = baseUIState.addressId == item.addressId,
                  onClick = {
                    keyboardController?.hide()
                    if (listMode == ListMode.HOUSES) {
                      apartmentViewModel.onHouseSelected(item.addressId)
                    } else {
                      navigateToApartment(item.addressId)
                    }
                  }
                )
              }
            } else {
              // ОБЫЧНЫЙ РЕЖИМ (Золотой фонд)
              when (listMode) {
                ListMode.RAIONS -> {
                  items(raions, key = { "r_${it.raionId}" }) { raion ->
                    RailItemContent(
                      title = raion.raion ?: "",
                      icon = Icons.Default.Map,
                      isSelected = baseUIState.selectedRegionId == raion.raionId,
                      onClick = { apartmentViewModel.onRaionSelected(raion) }
                    )
                  }
                }
                ListMode.HOUSES -> {
                  items(houses, key = { "h_${it.houseId}" }) { house ->
                    RailItemContent(
                      title = house.house ?: "",
                      icon = Icons.Default.Domain,
                      isSelected = baseUIState.selectedHouseId == house.houseId,
                      onClick = { apartmentViewModel.onHouseSelected(house.houseId) }
                    )
                  }
                }
                ListMode.APARTMENTS -> {
                  val aptList = if (isOrgAdmin) drawerApartments else baseUIState.apartments
                  items(aptList, key = { "a_${it.addressId}" }) { apartment ->
                    val isSelected = baseUIState.addressId == apartment.addressId
                    val badgeCount = apartmentBadges[apartment.addressId.toString()] ?: 0

                    RailItemContent(
                      title = "кв. ${apartment.address}",
                      subtitle = apartment.nanim,
                      extraInfo = "о/р ${apartment.addressId}",
                      icon = Icons.Default.Home,
                      isSelected = isSelected,
                      badgeCount = badgeCount,
                      onClick = {
                        keyboardController?.hide()
                        navigateToApartment(apartment.addressId)
                      }
                    )
                  }
                }
              }
            }
          }
        }
      }

  // --- 2. НИЖНЯЯ ЧАСТЬ (МЕНЮ) ---
      // Теперь блок всегда внизу, но элементы внутри фильтруются по твоему правилу
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(bottom = 16.dp)
      ) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

        // 1. Получаем динамический список дестинаций на основе роли
        val navDestinations = getNavDestinations(role = baseUIState.userRole)

        navDestinations.forEach { destination ->
          // Условие видимости: всегда видимые (настройки) или если есть квартиры/выбран адрес
          val shouldShow = destination.alwaysVisible || !isApartmentsEmpty

          if (shouldShow) {
            val isSelected = selectedDestination.substringBefore("/") == destination.route.substringBefore("/")

            NavigationRailItem(
              selected = isSelected,
              onClick = {
                Log.d("YkisLog", "Rail: [CLICK] Target: ${destination.route} | Role: ${baseUIState.userRole}")

                // Если жилец нажимает на чат, сбрасываем выбор службы для чистого входа
                if (destination.route == "service_selector") {
                  chatViewModel.setSelectedService(null)
                }

                navigateToDestination(destination.route)
              },
              icon = {
                BadgedBox(
                  badge = {
                    // 2. УНИВЕРСАЛЬНАЯ ПРОВЕРКА БЕЙДЖА
                    val isChatRoute = destination.route == "service_selector" ||
                      destination.route == UserListScreen.route

                    if (isChatRoute && totalUnread > 0) {
                      Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                      ) {
                        Text(text = if (totalUnread > 9) "9+" else totalUnread.toString())
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = stringResource(destination.labelId)
                  )
                }
              },
              label = if (isRailExpanded) {
                { Text(stringResource(destination.labelId), fontSize = 11.sp) }
              } else null,
              alwaysShowLabel = false
            )
          }
        }
      }

    }


    }
}

@Composable
fun RailItemContent(
  title: String,
  subtitle: String? = null,
  extraInfo: String? = null,
  icon: ImageVector,
  isSelected: Boolean,
  badgeCount: Int = 0,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
      .clickable { onClick() }
      .padding(8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
          if (extraInfo != null) {
            Text(" $extraInfo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
          }
        }
        subtitle?.let {
          Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
      }
      if (badgeCount > 0) {
        Badge { Text(badgeCount.toString()) }
      }
    }
  }
}

