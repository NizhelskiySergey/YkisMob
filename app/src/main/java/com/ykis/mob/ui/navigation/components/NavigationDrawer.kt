import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.components.ApartmentNavigationRail
import com.ykis.mob.ui.screens.chat.ChatViewModel

@Composable
fun ModalNavigationDrawerContent(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  selectedDestination: String,
  navigateToDestination: (String) -> Unit,
  onMenuClick: () -> Unit = {},
  navigateToApartment: (Int) -> Unit,
  isApartmentsEmpty: Boolean,
  chatViewModel: ChatViewModel // 1. Добавляем в параметры
) {
  ModalDrawerSheet(
    modifier = modifier.width(320.dp)
  ) {
    ApartmentNavigationRail(
      baseUIState = baseUIState,
      selectedDestination = selectedDestination,
      navigateToDestination = navigateToDestination,
      isRailExpanded = true, // В Drawer меню всегда развернуто
      onMenuClick = onMenuClick,
      chatViewModel = chatViewModel, // 2. Передаем правильную ссылку
      navigateToApartment = navigateToApartment,
      railWidth = 320.dp,
      maxApartmentListHeight = 400.dp, // Можно увеличить высоту списка для Drawer
      isApartmentsEmpty = isApartmentsEmpty,
      modifier = Modifier.fillMaxHeight()
    )
  }
}

