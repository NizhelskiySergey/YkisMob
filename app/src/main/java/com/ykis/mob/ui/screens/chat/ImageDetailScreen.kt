package com.ykis.mob.ui.screens.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ykis.mob.ui.components.ZoomableImage
@Composable
fun ImageDetailScreen(
  modifier: Modifier = Modifier,
  navigateUp: () -> Unit,
  messageEntity: MessageEntity
) {
  // Используем Scaffold или Box с черным фоном для эффекта погружения
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Основное изображение (занимает всё пространство)
      ZoomableImage(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        imageUri = messageEntity.imageUrl?.toUri() ?: Uri.EMPTY
      )

      // Подпись к фото (если есть текст)
      if (messageEntity.text.isNotBlank()) {

        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = Color.Black.copy(alpha = 0.6f) // Полупрозрачный фон для текста
        ) {
          Text(
            modifier = Modifier
              .navigationBarsPadding() // Отступ от системных кнопок снизу
              .padding(16.dp),
            text = messageEntity.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 3, // Увеличим до 3 линий, вдруг там описание поломки
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    // Кнопка закрытия (на фоне полупрозрачного круга, чтобы всегда была видна)
    IconButton(
      modifier = Modifier
        .statusBarsPadding() // Отступ от статус-бара сверху
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
      onClick = navigateUp
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = Color.White
      )
    }
  }
}
