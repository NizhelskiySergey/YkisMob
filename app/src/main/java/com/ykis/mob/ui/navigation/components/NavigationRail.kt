package com.ykis.mob.ui.navigation.components

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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
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
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.NAV_RAIL_DESTINATIONS
import com.ykis.mob.ui.navigation.UserListScreen
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
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
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  val searchQuery by apartmentViewModel.searchQuery.collectAsStateWithLifecycle()
  val apartments by apartmentViewModel.filteredApartments.collectAsStateWithLifecycle()
  val isUserAdmin = baseUIState.userRole != UserRole.StandardUser

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
            // Компактное поле поиска
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { apartmentViewModel.onSearchQueryChanged(it) },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              placeholder = { Text("Пошук кв.", fontSize = 11.sp) },
              leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { apartmentViewModel.onSearchQueryChanged("") }) {
                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(8.dp),
              textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
            )
          } else {
            // Компактная кнопка добавления
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

      // --- СПИСОК КВАРТИР (Занимает все свободное место) ---
      if (!isApartmentsEmpty && isRailExpanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f) // ОТДАЕМ ПРИОРИТЕТ СПИСКУ
        ) {
          Text(
            text = stringResource(id = R.string.list_apartment),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary
          )

          val listToDisplay = if (isUserAdmin) apartments else baseUIState.apartments

          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp)
          ) {
            items(listToDisplay, key = { it.addressId }) { apartment ->
              val isSelected = baseUIState.addressId == apartment.addressId

              // Компактный элемент списка (высота уменьшена)
              Box(
                modifier = Modifier
                  .padding(horizontal = 8.dp, vertical = 1.dp)
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                  .clickable {
                    keyboardController?.hide()
                    navigateToApartment(apartment.addressId)
                  }
                  .padding(6.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Default.Home, null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    // ПЕРВАЯ СТРОКА: Адрес + о/р
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      // Адрес (Жирный)
                      Text(
                        text = apartment.address,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                      )
                      // о/р (Шрифт как у фамилии)
                      Text(
                        text = " о/р ${apartment.addressId}",
                        style = MaterialTheme.typography.labelSmall, // Как у ФИО
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp)
                      )
                    }

                    // ВТОРАЯ СТРОКА: ФИО (как и было)
                    apartment.nanim?.let {
                      Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                      )
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
                BadgedBox(
                  badge = {
                    if (destination.route.contains("chat")) {
                      val totalUnread = unreadCounts.values.sum()
                      if (totalUnread > 0) Badge { Text(totalUnread.toString()) }
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



