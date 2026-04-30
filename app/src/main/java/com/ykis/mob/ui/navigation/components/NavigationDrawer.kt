import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
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
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.RaionEntity
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.RaionDropdownSelector
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.appartment.ListMode
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
  val methodName = "DrawerContent"
  val keyboardController = LocalSoftwareKeyboardController.current
  val searchQuery by apartmentViewModel.searchQuery.collectAsStateWithLifecycle()

  // ПОДПИСКИ (Золотой фонд)
  val houses by apartmentViewModel.drawerHouses.collectAsStateWithLifecycle()
  val drawerApartments by apartmentViewModel.drawerApartments.collectAsStateWithLifecycle()
  val filteredResults by apartmentViewModel.filteredApartments.collectAsStateWithLifecycle()

  val isUserAdmin = baseUIState.userRole != UserRole.StandardUser
  val isOrgAdmin = baseUIState.userRole != UserRole.StandardUser && baseUIState.userRole != UserRole.OsbbUser
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  val listMode = baseUIState.listMode

  // ПАРСИНГ БЕЙДЖЕЙ
  val apartmentBadges = remember(unreadCounts) {
    unreadCounts.map { (fullKey, count) ->
      val parts = fullKey.split("_")
      val addressId = parts.getOrNull(parts.size - 2) ?: ""
      addressId to count
    }.filter { it.first.isNotEmpty() }
      .groupBy({ it.first }, { it.second })
      .mapValues { it.value.sum() }
  }

  ModalDrawerSheet(
    modifier = modifier.width(320.dp),
    drawerContainerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 1. ШАПКА: ПОИСК ИЛИ ДОБАВЛЕНИЕ
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (isUserAdmin) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = {
              Log.d("YkisLog", "$methodName: [SEARCH_INPUT] $it")
              apartmentViewModel.onSearchQueryChanged(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Пошук адреси чи о/р", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { apartmentViewModel.onSearchQueryChanged("") }) {
                  Icon(Icons.Default.Close, null)
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
          )
        } else {
          Text(
            text = stringResource(id = R.string.list_apartment),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = {
              Log.d("YkisLog", "$methodName: [ADD_CLICK]")
              onMenuClick();
              navigateToDestination(AddApartmentScreen.route)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.AddHome, null)
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.add_appartment))
          }
        }
      }

      HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

      // 2. ДИНАМИЧЕСКИЙ СПИСОК (Уровни навигации)
      Column(modifier = Modifier.weight(1f)) {

        // Кнопка НАЗАД (только если мы не в корне и поиск пуст)
        if (listMode != ListMode.RAIONS && isOrgAdmin && searchQuery.isEmpty()) {
          NavigationDrawerItem(
            label = { Text("Назад", fontWeight = FontWeight.Bold) },
            selected = false,
            icon = { Icon(Icons.Default.ArrowBackIosNew, null, Modifier.size(18.dp)) },
            onClick = {
              Log.d("YkisLog", "$methodName: [BACK_LEVEL] Текущий: $listMode")
              apartmentViewModel.goBackLevel()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
          )
          HorizontalDivider()
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {

          if (searchQuery.isNotEmpty()) {
            // --- РЕЖИМ ПОИСКА ---
            items(filteredResults, key = { "search_${it.addressId}" }) { item ->
              DrawerItemContent(
                apartment = item,
                isSelected = baseUIState.addressId == item.addressId,
                listMode = listMode,
                badgeCount = 0,
                onClick = {
                  keyboardController?.hide()
                  if (listMode == ListMode.HOUSES) {
                    Log.d("YkisLog", "$methodName: [SEARCH_SELECT_HOUSE] ID: ${item.addressId}")
                    apartmentViewModel.onHouseSelected(item.addressId)
                  } else {
                    Log.d("YkisLog", "$methodName: [SEARCH_SELECT_APT] ID: ${item.addressId}")
                    navigateToApartment(item.addressId)
                    onMenuClick()
                  }
                }
              )
            }
          } else {
            // --- РЕЖИМ УРОВНЕЙ ---
            when (listMode) {
              ListMode.RAIONS -> {
                items(baseUIState.raions, key = { "r_${it.raionId}" }) { raion ->
                  NavigationDrawerItem(
                    label = { Text(raion.raion ?: "") },
                    selected = baseUIState.selectedRegionId == raion.raionId,
                    icon = { Icon(Icons.Default.Map, null) },
                    onClick = {
                      Log.d("YkisLog", "$methodName: [SELECT_RAION] ${raion.raion}")
                      apartmentViewModel.onRaionSelected(raion)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                  )
                }
              }
              ListMode.HOUSES -> {
                items(houses, key = { "h_${it.houseId}" }) { house ->
                  NavigationDrawerItem(
                    label = { Text(house.house ?: "") },
                    selected = baseUIState.selectedHouseId == house.houseId,
                    icon = { Icon(Icons.Default.Domain, null) },
                    onClick = {
                      Log.d("YkisLog", "$methodName: [SELECT_HOUSE] ${house.house}")
                      apartmentViewModel.onHouseSelected(house.houseId)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                  )
                }
              }
              ListMode.APARTMENTS -> {
                val aptList = if (isOrgAdmin) drawerApartments else baseUIState.apartments
                items(aptList, key = { "a_${it.addressId}" }) { apt ->
                  DrawerItemContent(
                    apartment = apt,
                    isSelected = baseUIState.addressId == apt.addressId,
                    listMode = ListMode.APARTMENTS,
                    badgeCount = apartmentBadges[apt.addressId.toString()] ?: 0,
                    onClick = {
                      Log.d("YkisLog", "$methodName: [SELECT_APT] Final Choice: ${apt.addressId}")
                      keyboardController?.hide()
                      // Вызывает переход на Info и очистку стека
                      navigateToApartment(apt.addressId)
                      onMenuClick()
                    }
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


@Composable
fun DrawerItemContent(
  apartment: ApartmentEntity,
  isSelected: Boolean,
  listMode: ListMode,
  badgeCount: Int,
  onClick: () -> Unit
) {
  NavigationDrawerItem(
    label = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.Start) {
            Text(apartment.address, fontWeight = FontWeight.Bold)
            Text("| о/р ${apartment.addressId}", style = MaterialTheme.typography.labelSmall)
          }

          if (listMode == ListMode.APARTMENTS) {
            apartment.nanim.let { Text(it, style = MaterialTheme.typography.labelSmall) }
          }
        }
        if (badgeCount > 0) {
          Badge(containerColor = MaterialTheme.colorScheme.error) { Text(badgeCount.toString()) }
        }
      }
    },
    selected = isSelected,
    onClick = onClick,
    icon = { Icon(if (listMode == ListMode.HOUSES) Icons.Default.Domain else Icons.Default.Home, null) },
    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
  )
}





