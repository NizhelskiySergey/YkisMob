package com.ykis.mob.ui.navigation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.UserListScreen
import com.ykis.mob.ui.navigation.getNavDestinations
import com.ykis.mob.ui.screens.chat.ChatViewModel

@Composable
fun BottomNavigationBar(
  selectedDestination: String,
  onClick: (String) -> Unit,
  chatViewModel: ChatViewModel,
  baseUIState: BaseUIState // Добавляем стейт, чтобы знать роль
) {
  // 1. Получаем список дестинаций для текущей роли
  val navDestinations = getNavDestinations(role = baseUIState.userRole)

  // 2. Подписываемся на счетчики
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()

  // Считаем общую сумму непрочитанных
  val totalUnread = remember(unreadCounts) { unreadCounts.values.sum() }

  NavigationBar(
    modifier = Modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.surfaceContainer
  ) {
    navDestinations.forEach { destination ->
      val isSelected = selectedDestination.substringBefore("/") == destination.route.substringBefore("/")

      NavigationBarItem(
        selected = isSelected,
        onClick = { onClick(destination.route) },
        icon = {
          BadgedBox(
            badge = {
              // 3. УНИВЕРСАЛЬНАЯ ПРОВЕРКА:
              // Показываем Badge, если роут текущей иконки совпадает с роутом чата для этой роли
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
              contentDescription = stringResource(id = destination.labelId)
            )
          }
        },
        label = { Text(stringResource(id = destination.labelId)) },
        alwaysShowLabel = false
      )
    }
  }
}


