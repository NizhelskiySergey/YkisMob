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
import com.ykis.mob.ui.navigation.NAV_BAR_DESTINATIONS
import com.ykis.mob.ui.navigation.UserListScreen
import com.ykis.mob.ui.screens.chat.ChatViewModel

@Composable
fun BottomNavigationBar(
  selectedDestination: String,
  onClick: (String) -> Unit,
  chatViewModel: ChatViewModel // 1. Добавляем ViewModel в параметры
) {
  // 2. Подписываемся на счетчики
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  // Считаем общую сумму непрочитанных
  val totalUnread = remember(unreadCounts) { unreadCounts.values.sum() }

  NavigationBar(
    modifier = Modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.surfaceContainer
  ) {
    NAV_BAR_DESTINATIONS.forEach { destination ->
      val isSelected = selectedDestination.substringBefore("/") == destination.route.substringBefore("/")

      NavigationBarItem(
        selected = isSelected,
        onClick = { onClick(destination.route) },
        icon = {
          // 3. Оборачиваем иконку в BadgedBox
          BadgedBox(
            badge = {
              // Показываем Badge только для иконки чата и если есть непрочитанные
              // Убедись, что роут в твоем Enum совпадает с UserListScreen.route
              if (destination.route == UserListScreen.route && totalUnread > 0) {
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
        alwaysShowLabel = false
      )
    }
  }
}

