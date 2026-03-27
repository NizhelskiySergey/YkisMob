package com.ykis.mob.ui.components.appbars

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ykis.mob.R
import com.ykis.mob.ui.navigation.NavigationType
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppBar(
  modifier: Modifier = Modifier,
  title: String,
  onBackClick: () -> Unit = {},
  onDrawerClick: () -> Unit = {},
  canNavigateBack: Boolean = true,
  // Устанавливаем BOTTOM_NAVIGATION по умолчанию для безопасности
  navigationType: NavigationType = NavigationType.BOTTOM_NAVIGATION,
  actionButton: @Composable (() -> Unit)? = null,
) {
  // Используем CenterAlignedTopAppBar, чтобы TextAlign.Center имел смысл
  CenterAlignedTopAppBar(
    modifier = modifier, // Используем проброшенный параметр
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
      containerColor = Color.Transparent
    ),
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    },
    navigationIcon = {
      // Логика: показываем Меню, если мы на главном экране ТЕЛЕФОНА
      if (!canNavigateBack && navigationType == NavigationType.BOTTOM_NAVIGATION) {
        IconButton(onClick = onDrawerClick) {
          Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(id = R.string.driver_menu),
          )
        }
      }
      // Логика: показываем Назад, если это вложенный экран
      else if (canNavigateBack) {
        IconButton(onClick = onBackClick) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(id = R.string.back_button),
          )
        }
      }
    },
    actions = {
      actionButton?.invoke()
    }
  )
}

