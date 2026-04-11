package com.ykis.mob.ui.navigation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.R
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
  val totalUnread = remember(unreadCounts) { unreadCounts.values.sum() }
  val searchQuery by apartmentViewModel.searchQuery.collectAsStateWithLifecycle()
  val apartments by apartmentViewModel.filteredApartments.collectAsStateWithLifecycle()
  val isUserAdmin = baseUIState.userRole != UserRole.StandardUser

  CustomNavigationRail(
    modifier = modifier,
    currentWidth = railWidth,
    header = {
      // 1. Кнопка меню (бургер) - Всегда сверху
      IconButton(
        onClick = onMenuClick,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
      ) {
        Icon(Icons.Default.Menu, contentDescription = "Menu")
      }

      // 2. ДИНАМИЧЕСКИЙ HEADER
      if (isRailExpanded) {
        if (isUserAdmin) {
          // --- ПОИСК ДЛЯ АДМИНА ---
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { apartmentViewModel.onSearchQueryChanged(it) },
            modifier = Modifier
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .fillMaxWidth(),
            placeholder = { Text("Поиск кв.", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, modifier = Modifier.size(18.dp), contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { apartmentViewModel.onSearchQueryChanged("") }) {
                  Icon(Icons.Default.Close, modifier = Modifier.size(16.dp), contentDescription = null)
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall
          )
        } else {
          // --- КНОПКА ДОБАВИТЬ ДЛЯ ЖИЛЬЦА ---
          FloatingActionButton(
            onClick = { navigateToDestination(AddApartmentScreen.route) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Start
            ) {
              Icon(Icons.Default.AddHome, contentDescription = null)
              Text(
                text = stringResource(id = R.string.add_appartment),
                modifier = Modifier.padding(start = 12.dp),
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge
              )
            }
          }
        }
      }
    }
  ) {
    Column(modifier = Modifier.fillMaxSize()) {

      // --- СПИСОК КВАРТИР ---
      if (!isApartmentsEmpty && isRailExpanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            // Для админа список тянется (weight), для жильца - по контенту
            .then(if (isUserAdmin) Modifier.weight(1f) else Modifier.wrapContentHeight())
        ) {
          Text(
            text = stringResource(id = R.string.list_apartment),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
          )

          // Используем filteredApartments только для админа, для жильца можно все
          val listToDisplay = if (isUserAdmin) apartments else baseUIState.apartments

          ApartmentList(
            apartmentList = listToDisplay,
            onClick = { id ->
              keyboardController?.hide() // <--- СКРЫВАЕМ КЛАВИАТУРУ
              navigateToApartment(id)
            },
            currentAddressId = baseUIState.addressId
          )
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
      }

      // --- ГЛАВНОЕ МЕНЮ (НАВИГАЦИЯ) ---
      Column(
        modifier = Modifier
          .fillMaxWidth()
          // Если список жильца не занимает weight, то меню прижимается к нему сверху
          .then(if (!isUserAdmin) Modifier.weight(1f) else Modifier.wrapContentHeight())
          .verticalScroll(rememberScrollState())
          .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // ... (цикл NAV_RAIL_DESTINATIONS без изменений) ...

        NAV_RAIL_DESTINATIONS.forEach { destination ->
          if (destination.alwaysVisible || !isApartmentsEmpty) {
            val isSelected =
              selectedDestination.substringBefore("/") == destination.route.substringBefore("/")

            Box(
              modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable { navigateToDestination(destination.route) }
                .padding(horizontal = 16.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
              ) {
                // ВНЕДРЯЕМ BADGE ДЛЯ ИКОНКИ ЧАТА
                BadgedBox(
                  badge = {
                    // Если это пункт "Чат" и есть непрочитанные
                    if (destination.route == UserListScreen.route && totalUnread > 0) {
                      Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                      ) {
                        Text(text = if (totalUnread > 99) "99+" else totalUnread.toString())
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Text(
                  text = stringResource(destination.labelId),
                  modifier = Modifier
                    .padding(start = 12.dp)
                    .alpha(if (isRailExpanded) 1f else 0f),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Visible
                )
              }
            }
          }
        }
      }
    }
  }
}

