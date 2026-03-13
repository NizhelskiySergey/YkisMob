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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ykis.mob.R
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.NAV_RAIL_DESTINATIONS

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
  baseUIState: BaseUIState,
  selectedDestination: String,
  navigateToDestination: (String) -> Unit = {},
  isRailExpanded: Boolean,
  onMenuClick: () -> Unit,
  navigateToApartment: (Int) -> Unit = {},
  railWidth: Dp,
  maxApartmentListHeight: Dp,
  isApartmentsEmpty: Boolean,
  modifier: Modifier = Modifier
) {
  var showApartmentList by rememberSaveable { mutableStateOf(true) }

  // Упрощаем анимации для мгновенного отклика текста
  val transition = updateTransition(targetState = isRailExpanded, label = "RailExpansion")
  val contentAlpha by transition.animateFloat(label = "Alpha") { if (it) 1f else 0f }
  val listHeight by transition.animateDp(label = "ListHeight") { if (it) maxApartmentListHeight else 0.dp }

  val rotationIcon by animateFloatAsState(
    targetValue = if (showApartmentList) 180f else 0f,
    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
    label = "IconRotation"
  )

  CustomNavigationRail(
    modifier = modifier,
    currentWidth = railWidth,
    header = {
      IconButton(
        onClick = onMenuClick,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
      ) {
        Icon(Icons.Default.Menu, contentDescription = "Menu")
      }

      FloatingActionButton(
        onClick = { navigateToDestination(AddApartmentScreen.route) },
        modifier = Modifier
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(0.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = if (isRailExpanded) Arrangement.Start else Arrangement.Center
        ) {
          Icon(Icons.Default.AddHome, contentDescription = null)
          if (isRailExpanded) {
            Text(
              text = stringResource(id = R.string.add_appartment),
              modifier = Modifier.padding(start = 12.dp),
              maxLines = 1,
              style = MaterialTheme.typography.labelLarge,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  ) {
    // --- Список квартир ---
    if (!isApartmentsEmpty) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(contentAlpha)
          .heightIn(max = listHeight)
      ) {
        Row(
          modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .clickable { showApartmentList = !showApartmentList }
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(id = R.string.list_apartment),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotationIcon)
          )
        }

        // Для мгновенного появления списка используем обычный if вместо AnimatedVisibility
        if (showApartmentList && isRailExpanded) {
          ApartmentList(
            apartmentList = baseUIState.apartments,
            onClick = navigateToApartment,
            currentAddressId = baseUIState.addressId
          )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
      }
    }

    // --- Главное меню ---
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(top = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      NAV_RAIL_DESTINATIONS.forEach { destination ->
        if (destination.alwaysVisible || !isApartmentsEmpty) {
          val isSelected = selectedDestination.substringBefore("/") == destination.route.substringBefore("/")

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
              Icon(
                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
              )

              // ВАЖНО: Убираем IF. Текст всегда в дереве Compose.
              // Если он не виден — значит isRailExpanded равно false.
              Text(
                text = stringResource(destination.labelId),
                modifier = Modifier
                  .padding(start = 12.dp)
                  // Если текст все равно не виден, временно поставь тут 1f для теста
                  .alpha(if (isRailExpanded) 1f else 0f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible // Разрешаем выходить за границы во время анимации
              )
            }
          }
        }
      }

    }
  }
}
