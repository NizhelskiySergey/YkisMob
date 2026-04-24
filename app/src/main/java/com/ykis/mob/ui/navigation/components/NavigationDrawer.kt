import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.R
import com.ykis.mob.core.ext.truncate
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.delay

@Composable
fun ModalNavigationDrawerContent(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  selectedDestination: String,
  navigateToDestination: (String) -> Unit,
  onMenuClick: () -> Unit = {},
  navigateToApartment: (Int) -> Unit,
  isApartmentsEmpty: Boolean,
  apartmentViewModel: ApartmentViewModel,
  chatViewModel: ChatViewModel
) {
  val keyboardController = LocalSoftwareKeyboardController.current

  val searchQuery by apartmentViewModel.searchQuery.collectAsStateWithLifecycle()
  val apartments by apartmentViewModel.filteredApartments.collectAsStateWithLifecycle()

  val isUserAdmin = baseUIState.userRole != UserRole.StandardUser
  // 1. Получаем сырую мапу из ViewModel
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()

// 2. Группируем и суммируем по ID квартиры (ЗОЛОТОЙ ПАРСИНГ)
  val apartmentBadges = remember(unreadCounts) {
    unreadCounts.map { (fullKey, count) ->
      // Извлекаем только числовой ID (напр. из OSBB_3_1336_UID вытащит 1336)
      val addressId = fullKey.split("_").find { part ->
        part.isNotEmpty() && part.all { it.isDigit() } && part.length >= 3
      } ?: ""
      addressId to count
    }
      .groupBy({ it.first }, { it.second }) // Группируем по ID
      .mapValues { it.value.sum() }         // Суммируем все сообщения этой квартиры
  }


//
  ModalDrawerSheet(
    modifier = modifier.width(320.dp),
    drawerContainerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(modifier = Modifier.fillMaxSize()) {

      // 1. ШАПКА: Заголовок + (Поиск для Админа ИЛИ Кнопка для Жильца)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 16.dp)
      ) {
        Text(
          text = stringResource(id = R.string.list_apartment),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isUserAdmin) {
          // --- ПОИСК ДЛЯ АДМИНА ---
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { apartmentViewModel.onSearchQueryChanged(it) },
            modifier = Modifier
              .fillMaxWidth(),
//              .focusRequester(focusRequester),
            placeholder = { Text("Поиск квартиры", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { apartmentViewModel.onSearchQueryChanged("") }) {
                  Icon(Icons.Default.Close, contentDescription = null)
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
          )
        } else {
          // --- КНОПКА ДОБАВИТЬ ДЛЯ ЖИЛЬЦА ---
          Button(
            onClick = {
              onMenuClick() // Закрываем шторку
              navigateToDestination(AddApartmentScreen.route)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(12.dp)
          ) {
            Icon(Icons.Default.AddHome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.add_appartment))
          }
        }
      }

      HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

      // 2. СПИСОК КВАРТИР
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          // Админу растягиваем список, жильцу - нет (если квартир 1-2)
          .then(if (isUserAdmin) Modifier.weight(1f) else Modifier.wrapContentHeight()),
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        // Для админа используем фильтр, для жильца - весь список
        val displayList = if (isUserAdmin) apartments else baseUIState.apartments

        // Внутри LazyColumn в ModalNavigationDrawerContent
        items(displayList, key = { it.addressId }) { apartment ->
          val methodName = "DrawerItem"
          val isSelected = baseUIState.addressId == apartment.addressId
          val addrIdStr = apartment.addressId.toString()

          // 1. ПОЛУЧЕНИЕ BADGE
          // Используем мапу badges, которая должна быть подготовлена через groupBy/sum
          val badgeCount = apartmentBadges[addrIdStr] ?: 0

          // ЛОГИРОВАНИЕ (Детальный аудит отрисовки каждого элемента)
          Log.d("YkisLog", "$methodName: [RENDER] ID: $addrIdStr | Badge: $badgeCount")

          if (unreadCounts.isNotEmpty()) {
            val hasRawKey = unreadCounts.keys.any { it.contains("_$addrIdStr") }
//            Log.d("YkisLog", "$methodName: [DEBUG] Check ID: $addrIdStr | Result: $badgeCount | RawMatchFound: $hasRawKey | AllKeys: ${unreadCounts.keys}")
          }

          NavigationDrawerItem(
            label = {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  // ПЕРВАЯ СТРОКА: Адрес + о/р
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = apartment.address,
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                      text = " о/р $addrIdStr",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                      modifier = Modifier.padding(start = 4.dp)
                    )
                  }

                  // ВТОРАЯ СТРОКА: ФИО
                  apartment.nanim?.let {
                    Text(
                      text = it,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                // 2. ОТОБРАЖЕНИЕ BADGE (Красный кружок)
                if (badgeCount > 0) {
                  Badge(
                    modifier = Modifier.padding(start = 8.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                  ) {
                    Text(badgeCount.toString())
                  }
                }
              }
            },
            selected = isSelected,
            onClick = {
              Log.d("YkisLog", "$methodName: [CLICK] Переход -> $addrIdStr")
              keyboardController?.hide()
              navigateToApartment(apartment.addressId)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            icon = {
              Icon(
                imageVector = if (isSelected) Icons.Filled.Home else Icons.Outlined.Home,
                contentDescription = null
              )
            }
          )
        }


      }

      // 3. НИЖНЯЯ ПАНЕЛЬ (Для админа)
      if (isUserAdmin) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        NavigationDrawerItem(
          label = {
            Text(
              text = "ВЕРНУТЬСЯ В РЕЖИМ АДМИНА",
              fontWeight = FontWeight.ExtraBold,
              style = MaterialTheme.typography.labelLarge
            )
          },
          selected = false,
          onClick = {
            onMenuClick() // Закрываем шторку
            apartmentViewModel.resetToAdminMode() // Вызываем новый метод сброса
          },
          icon = {
            Icon(
              Icons.Default.AdminPanelSettings,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary // Белая иконка на синем фоне
            )
          },
          modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
          // ВЫДЕЛЯЕМ ЦВЕТОМ
          colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.primary, // Яркий фон (синий)
            unselectedTextColor = MaterialTheme.colorScheme.onPrimary,   // Белый текст
            unselectedIconColor = MaterialTheme.colorScheme.onPrimary
          )
        )
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}




