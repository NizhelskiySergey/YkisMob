package com.ykis.mob.ui.navigation.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.R
import com.ykis.mob.core.ext.truncate
import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.RaionEntity
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.NAV_RAIL_DESTINATIONS
import com.ykis.mob.ui.navigation.RaionDropdownSelector
import com.ykis.mob.ui.navigation.UserListScreen
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
            // КРИТИЧЕСКИЙ ФИКС: Используем Column, чтобы Raion не налезал на Поиск
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

              // 1. Выбор района
              RaionDropdownSelector(
                raions = baseUIState.raions,
                onRaionSelected = { raion ->
                  Log.d("YkisLog", "Rail: [SELECT_RAION] Name: ${raion.raion}, ID: ${raion.raionId}")
                  apartmentViewModel.onRaionSelected(raion)
                }
              )

              HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
              )

              // 2. Поле поиска
              OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                  Log.d("YkisLog", "Rail: [SEARCH_INPUT] Query: $it")
                  apartmentViewModel.onSearchQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                placeholder = { Text("Пошук кв.", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                      Log.d("YkisLog", "Rail: [SEARCH_CLEAR]")
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
            // Компактная кнопка добавления для жильца
            FloatingActionButton(
              onClick = {
                Log.d("YkisLog", "Rail: [ADD_APARTMENT_CLICK]")
                navigateToDestination(AddApartmentScreen.route)
              },
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

      // --- ДИНАМИЧЕСКИЙ СПИСОК (Районы / Дома / Квартиры) ---
      val listMode = baseUIState.listMode
      val isOrgAdmin = baseUIState.userRole != UserRole.StandardUser && baseUIState.userRole != UserRole.OsbbUser

// Определяем, что именно мы сейчас показываем в списке
      val listToDisplay: List<Any> = when {
        listMode == ListMode.RAIONS && isOrgAdmin -> baseUIState.raions
        isUserAdmin -> apartments // Твой список для админа ОСББ
        else -> baseUIState.apartments
      }

      if (listToDisplay.isNotEmpty() && isRailExpanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          // Динамический заголовок
          Text(
            text = when(listMode) {
              ListMode.RAIONS -> "Оберіть район міста"
              ListMode.HOUSES -> "Оберіть будинок"
              else -> stringResource(id = R.string.list_apartment)
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary
          )

          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp)
          ) {
            items(listToDisplay) { item ->
              // Определяем тип элемента для корректного отображения
              val isRaion = item is RaionEntity
              val apartment = item as? ApartmentEntity

              val label = if (isRaion) (item as RaionEntity).raion else apartment?.address ?: ""
              val id = if (isRaion) (item as RaionEntity).raionId else apartment?.addressId ?: 0
              val isSelected = !isRaion && baseUIState.addressId == id

              // Логика Badge (только для квартир)
              val badgeCount = if (!isRaion) apartmentBadges[id.toString()] ?: 0 else 0

              if (unreadCounts.isNotEmpty() && !isRaion) {
                Log.d("YkisLog", "RailList: Render ID: $id | Badge: $badgeCount")
              }

              Box(
                modifier = Modifier
                  .padding(horizontal = 8.dp, vertical = 1.dp)
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                  .clickable {
                    keyboardController?.hide()
                    Log.d("YkisLog", "RailList: [CLICK] Mode: $listMode, ID: $id")
                    if (item is RaionEntity) {
                      // 1. Если это район — передаем String в метод выбора района
                      Log.d("YkisLog", "Rail: Выбран район ${item.raionId}")
                      apartmentViewModel.onRaionSelected(item)
                    } else if (item is ApartmentEntity) {
                      // 2. Если это квартира — передаем Int в навигацию
                      Log.d("YkisLog", "Rail: Переход в квартиру ${item.addressId}")
                      navigateToApartment(item.addressId)
                    }
                  }
                  .padding(6.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  // Динамическая иконка
                  Icon(
                    imageVector = when {
                      isRaion -> Icons.Default.Map
                      listMode == ListMode.HOUSES -> Icons.Default.Domain
                      else -> Icons.Default.Home
                    },
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(Modifier.width(8.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    // Название района или Адрес
                    Text(
                      text = label,
                      fontWeight = if (isRaion) FontWeight.Medium else FontWeight.Bold,
                      style = MaterialTheme.typography.bodyMedium
                    )

                    // Вторая строка (только для квартир)
                    if (!isRaion && apartment != null) {
                      Row {
                        Text(
                          text = " о/р $id",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        apartment.nanim?.let {
                          Text(
                            text = " | $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                          )
                        }
                      }
                    }
                  }

                  // Показываем Badge только для квартир
                  if (badgeCount > 0) {
                    Badge(
                      modifier = Modifier.padding(start = 4.dp),
                      containerColor = MaterialTheme.colorScheme.error,
                      contentColor = MaterialTheme.colorScheme.onError
                    ) {
                      Text(badgeCount.toString())
                    }
                  }
                }
              }
            }
          }
        }
      }


      // --- НИЖНЕЕ МЕНЮ (Минимальная высота) ---
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight() // Не занимает лишнего места
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))


        NAV_RAIL_DESTINATIONS.forEach { destination ->
          Log.d("YkisLog", "RailCheck: Current route is '${destination.route}'")

          if (destination.alwaysVisible || !isApartmentsEmpty) {
            val isSelected = selectedDestination.substringBefore("/") == destination.route.substringBefore("/")

            // Компактный пункт навигации (высота 40dp вместо 56dp)
            Box(
              modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable { navigateToDestination(destination.route) }
                .padding(horizontal = 12.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Log.d("YkisLog", "RailCheck: Current route is '${destination.route}'")

                BadgedBox(
                  badge = {
                    // Проверяем: если это экран списка пользователей (админский чат)
                    // ИЛИ любой другой роут, содержащий "chat"
                    val isChatDestination = destination.route == "UserListScreen" ||
                      destination.route.contains("chat", ignoreCase = true)

                    if (isChatDestination && totalUnread > 0) {
                      Badge {
                        Text(text = totalUnread.toString())
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = destination.selectedIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                if (isRailExpanded) {
                  Text(
                    text = stringResource(destination.labelId),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    maxLines = 1,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}



