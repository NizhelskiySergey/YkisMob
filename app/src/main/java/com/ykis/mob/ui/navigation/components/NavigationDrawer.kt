import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.components.ApartmentNavigationRail

@Composable
fun ModalNavigationDrawerContent(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  selectedDestination: String,
  navigateToDestination: (String) -> Unit,
  onMenuClick: () -> Unit = {},
  navigateToApartment: (Int) -> Unit,
  isApartmentsEmpty: Boolean
) {
  ModalDrawerSheet(
    modifier = modifier.width(320.dp) // Фиксируем ширину для выезжающего меню
  ) {
    ApartmentNavigationRail(
      baseUIState = baseUIState,
      selectedDestination = selectedDestination,
      navigateToDestination = navigateToDestination, // В Drawer всегда раскрыто
      isRailExpanded = true,
      onMenuClick = onMenuClick,
      navigateToApartment = navigateToApartment,
      railWidth = 320.dp,
      maxApartmentListHeight = 200.dp,
      isApartmentsEmpty = isApartmentsEmpty,
      modifier = Modifier,
    )
  }
}
