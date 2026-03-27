package com.ykis.mob.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ykis.mob.R
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.service.list.TotalDebtState
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import com.ykis.mob.ui.screens.service.list.assembleServiceList
/**
 * Экран управления чатами.
 * Логика разделена на две части:
 * 1. Для АДМИНИСТРАТОРА (OSBB, Vodokanal и т.д.) — отображается список активных чатов с жильцами.
 * 2. Для ЖИТЕЛЯ (StandardUser) — отображается меню выбора службы для начала нового диалога.
 */
/**
 * Экран управления чатами (UserListScreen).
 * Разделяет интерфейс для Админа (список жильцов) и Жителя (выбор службы).
 */
@Composable
fun UserListScreen(
  modifier: Modifier = Modifier,
  userList: List<UserEntity>,
  baseUIState: BaseUIState,
  onUserClicked: (UserEntity) -> Unit,
  onServiceClick: (TotalServiceDebt) -> Unit,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
  chatViewModel: ChatViewModel
) {
  Column(modifier = modifier.fillMaxSize()) {
    // Верхняя панель управления
    DefaultAppBar(
      title = stringResource(id = R.string.chat),
      onDrawerClick = onDrawerClicked,
      canNavigateBack = false,
      navigationType = navigationType
    )

    // ЛОГИКА ОТОБРАЖЕНИЯ ПО РОЛИ
    if (baseUIState.userRole != UserRole.StandardUser) {
      // --- РЕЖИМ АДМИНИСТРАТОРА ---
      // Отображаем список чатов с жильцами (с индикацией сообщений и сортировкой)
      UserList(
        modifier = Modifier.weight(1f),
        userList = userList,
        baseUIState = baseUIState,
        onUserClick = onUserClicked,
        chatViewModel = chatViewModel
      )
    } else {
      // --- РЕЖИМ ЖИТЕЛЯ ---
      // Отображаем меню выбора организации для начала диалога
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier.width(IntrinsicSize.Max),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Формируем список доступных кнопок-служб
          assembleServiceList(
            totalDebtState = TotalDebtState(),
            baseUIState = baseUIState
          ).forEach { service ->
            Button(
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                // ВАЖНО: Сначала устанавливаем выбранную службу во ViewModel,
                // чтобы сформировать правильный chatUid (например, "osbb_UID")
                chatViewModel.setSelectedService(service)
                // Затем вызываем переход на экран чата
                onServiceClick(service)
              },
              shape = MaterialTheme.shapes.medium
            ) {
              Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = service.icon,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  modifier = Modifier.weight(1f),
                  text = service.name,
                  style = MaterialTheme.typography.labelLarge,
                  textAlign = TextAlign.Start
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Вспомогательная функция для формирования списка кнопок-служб.
 * Размещается внизу файла UserListScreen.kt вне Composable функций.
 */
/**
 * Формирует список доступных служб, используя существующий класс TotalServiceDebt.
 * Поля color и contentDetail помогут унифицировать дизайн кнопок с основным экраном услуг.
 */
fun assembleServiceList(
  totalDebtState: TotalDebtState, // Состояние из вьюмодели
  baseUIState: BaseUIState        // Профиль пользователя
): List<TotalServiceDebt> {
  val services = mutableListOf<TotalServiceDebt>()

  // 1. ОСББ (Добавляем, если есть адрес или ID организации)
  if (!baseUIState.address.isNullOrBlank() || baseUIState.osmdId != 0) {
    services.add(
      TotalServiceDebt(
        name = "ОСББ / Керуюча компанія",
        contentDetail = ContentDetail.OSBB,
        icon = Icons.Default.Home,
        color = Color(0xFF4CAF50), // Зеленый
        debt = 0.0 // Здесь можно подставить реальный долг из totalDebtState
      )
    )
  }

  // 2. ВОДОКАНАЛ
  services.add(
    TotalServiceDebt(
      name = "Водоканал",
      contentDetail = ContentDetail.WATER_SERVICE,
      icon = Icons.Default.WaterDrop,
      color = Color(0xFF2196F3), // Синий
      debt = 0.0
    )
  )

  // 3. ТБО (Мусор)
  services.add(
    TotalServiceDebt(
      name = "Вивіз сміття (ТБО)",
      contentDetail = ContentDetail.GARBAGE_SERVICE,
      icon = Icons.Default.Delete,
      color = Color(0xFF795548), // Коричневый
      debt = 0.0
    )
  )

  // 4. ЮТКЕ (Теплосеть)
  services.add(
    TotalServiceDebt(
      name = "Тепломережа (ЮТКЕ)",
      contentDetail = ContentDetail.WARM_SERVICE,
      icon = Icons.Default.Whatshot,
      color = Color(0xFFFF5722), // Оранжевый
      debt = 0.0
    )
  )

  return services
}


