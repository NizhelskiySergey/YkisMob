package com.ykis.mob.ui.screens.appartment

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import com.ykis.mob.R
import com.ykis.mob.core.composable.DialogCancelButton
import com.ykis.mob.core.composable.DialogConfirmButton
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.YkisPamAppState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.ContentType
import com.ykis.mob.ui.navigation.INFO_APARTMENT_TAB_ITEM
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.bti.BtiPanelContent
import com.ykis.mob.ui.screens.family.FamilyContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoApartmentScreen(
  modifier: Modifier = Modifier,
  contentType: ContentType,
  displayFeatures: List<DisplayFeature>,
  baseUIState: BaseUIState,
  apartmentViewModel: ApartmentViewModel,
  appState: YkisPamAppState,
  deleteApartment: () -> Unit,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
) {
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var showWarningDialog by remember { mutableStateOf(false) }

  // 1. Диалог подтверждения удаления
  if (showWarningDialog) {
    AlertDialog(
      onDismissRequest = { showWarningDialog = false },
      icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
      title = { Text(stringResource(R.string.title_delete_appartment)) },
      text = { Text("Вы действительно хотите удалить эту квартиру? Данные нельзя будет восстановить.") },
      dismissButton = { DialogCancelButton(R.string.cancel) { showWarningDialog = false } },
      confirmButton = {
        DialogConfirmButton(R.string.title_delete_appartment) {
          deleteApartment()
          showWarningDialog = false
        }
      }
    )
  }

  // 2. Логика загрузки данных
  LaunchedEffect(baseUIState.addressId, baseUIState.apartments) {
    val targetId = when {
      baseUIState.addressId != 0 -> baseUIState.addressId
      baseUIState.apartments.isNotEmpty() -> baseUIState.apartments.firstOrNull()?.addressId
      else -> null
    }
    targetId?.let { apartmentViewModel.getApartment(it) }
  }

  Scaffold(
    topBar = {
      DefaultAppBar(
        title = baseUIState.address,
        canNavigateBack = false,
        onDrawerClick = onDrawerClicked,
        navigationType = navigationType,
        actionButton = {
          IconButton(onClick = { showWarningDialog = true }) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = stringResource(id = R.string.delete_appartment),
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      )
    }

  ) { innerPadding ->
    Column(
      modifier = modifier
        .padding(innerPadding)
        .fillMaxSize()
    ) {
      if (contentType == ContentType.DUAL_PANE) {
        // Планшетный режим (Две панели рядом)
        InfoScreenDualPanelContent(
          baseUIState = baseUIState,
          apartmentViewModel = apartmentViewModel
        )
      } else {
        // Мобильный режим (Вкладки)
        PrimaryTabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface,
          divider = { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant) },
          indicator = {
            // Исправлено: используем tabIndicatorOffset из TabIndicatorScope
            TabRowDefaults.PrimaryIndicator(
              modifier = Modifier.tabIndicatorOffset(selectedTab),
              width = 64.dp,
              shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
            )
          }
        ) {
          INFO_APARTMENT_TAB_ITEM.forEachIndexed { index, tabItem ->
            LeadingIconTab(
              selected = selectedTab == index,
              onClick = { selectedTab = index },
              text = {
                Text(
                  text = stringResource(tabItem.titleId),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                )
              },
              icon = {
                Icon(
                  imageVector = if (index == selectedTab) tabItem.selectedIcon else tabItem.unselectedIcon,
                  contentDescription = null
                )
              }
            )
          }
        }

        // Анимированный контент вкладок
        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            if (targetState > initialState) {
              (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn())
                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut())
            } else {
              (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn())
                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut())
            }.using(SizeTransform(clip = false))
          },
          label = "TabContentAnimation"
        ) { targetIndex ->
          Box(modifier = Modifier.fillMaxSize()) {
            when (targetIndex) {
              0 -> BtiPanelContent(baseUIState = baseUIState, viewModel = apartmentViewModel)
              else -> FamilyContent(baseUIState = baseUIState)
            }
          }
        }
      }
    }
  }
}

@Composable
fun InfoScreenDualPanelContent(
  baseUIState: BaseUIState,
  apartmentViewModel: ApartmentViewModel
) {
  // Реализация Dual Pane через стандартный Row (стабильно и без ошибок доступа)
  Row(
    modifier = Modifier.fillMaxSize(),
    verticalAlignment = Alignment.Top
  ) {
    // Левая панель: БТИ
    Surface(
      modifier = Modifier
        .weight(0.45f)
        .fillMaxHeight(),
      color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) {
      Column {
        DualPaneHeader(Icons.Default.Home, stringResource(R.string.bti))
        BtiPanelContent(baseUIState = baseUIState, viewModel = apartmentViewModel)
      }
    }

    // Тонкий вертикальный разделитель
    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

    // Правая панель: Состав семьи
    Column(
      modifier = Modifier
        .weight(0.55f)
        .fillMaxHeight()
    ) {
      // Используем текст напрямую или ресурс, если он есть
      DualPaneHeader(Icons.Default.People, "Состав семьи")
      FamilyContent(baseUIState = baseUIState)
    }
  }
}

@Composable
private fun DualPaneHeader(icon: ImageVector, title: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold
    )
  }
  HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp)
}
