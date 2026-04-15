package com.ykis.mob.ui.screens.chat

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
@Composable
internal fun ComposeMessageBox(
  onSent: () -> Unit,
  onImageSent: (Uri) -> Unit,
  onCameraClick: () -> Unit,
  onAiClick: () -> Unit,
  text: String,
  onTextChanged: (String) -> Unit,
  showAttachIcon: Boolean = true,
  isLoading: Boolean,
  canSend: Boolean
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface)

  // Расширяем лаунчер и добавляем логирование
  val openDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      Log.d("YkisLog", "ComposeMessageBox: [FILE_SELECTED] Uri: $uri")
      onImageSent(uri) // Это вызывает setSelectedImageUri во ViewModel и навигацию
    } else {
      Log.d("YkisLog", "ComposeMessageBox: [CANCELLED] Выбор файла отменен пользователем")
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (showAttachIcon) {
      IconButton(onClick = onAiClick, enabled = !isLoading) {
        Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
      }

      IconButton(onClick = {
        Log.d("YkisLog", "ComposeMessageBox: [CLICK] Нажата кнопка прикрепить")
        openDocumentLauncher.launch(
          arrayOf(
            "image/*",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain"
          )
        )
      }) {
        Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Прикріпити")
      }

      IconButton(onClick = {
        Log.d("YkisLog", "ComposeMessageBox: [CLICK] Нажата кнопка камеры")
        onCameraClick()
      }) {
        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Камера")
      }
    }

    BasicTextField(
      value = text,
      onValueChange = onTextChanged,
      modifier = Modifier.weight(1f),
      textStyle = textStyle,
      decorationBox = { innerTextField ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
          if (text.isEmpty()) {
            Text("Повідомлення", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
          }
          innerTextField()
        }
      }
    )

    Crossfade(isLoading, label = "send_state") { loading ->
      if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp).padding(12.dp))
      } else {
        IconButton(
          onClick = {
            Log.d("YkisLog", "ComposeMessageBox: [CLICK] Нажата кнопка ОТПРАВИТЬ")
            onSent()
            keyboardController?.hide()
          },
          enabled = canSend
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Відправити",
            tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
          )
        }
      }
    }
  }
}


