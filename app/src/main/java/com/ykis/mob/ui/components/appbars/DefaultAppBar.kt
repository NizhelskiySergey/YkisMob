package com.ykis.mob.ui.components.appbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ykis.mob.ui.navigation.NavigationType
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppBar(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String? = null,
  onBackClick: () -> Unit = {},
  onDrawerClick: () -> Unit = {},
  canNavigateBack: Boolean = true,
  navigationType: NavigationType? = null,
  actionButton: @Composable (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp
  ) {
    CenterAlignedTopAppBar( // 1. Центрирует всё содержимое title
      title = {
        Row(
          verticalAlignment = Alignment.Bottom, // Прижимаем адрес к базовой линии заголовка
          horizontalArrangement = Arrangement.Center
        ) {
          // Основной заголовок (Жирный)
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          // Адрес (Subtitle) - Меньшим шрифтом сразу за названием
          if (!subtitle.isNullOrBlank()) {
            Text(
              text = " | $subtitle",
              style = MaterialTheme.typography.labelSmall, // Шрифт как у ФИО
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.padding(start = 4.dp, bottom = 2.dp), // Смещение для визуального баланса
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      },
      navigationIcon = {
        if (!canNavigateBack && navigationType == NavigationType.BOTTOM_NAVIGATION) {
          IconButton(onClick = onDrawerClick) {
            Icon(Icons.Default.Menu, contentDescription = null)
          }
        } else if (canNavigateBack) {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        }
      },
      actions = {
        if (actionButton != null) actionButton()
      }
    )
  }
}



