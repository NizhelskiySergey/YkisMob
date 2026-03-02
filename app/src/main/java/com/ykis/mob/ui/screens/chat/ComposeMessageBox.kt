package com.ykis.mob.ui.screens.chat

import android.net.Uri
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
  onAiClick: () -> Unit, // ДОБАВЛЕНО: действие для ИИ
  text: String,
  onTextChanged: (String) -> Unit,
  showAttachIcon: Boolean = true,
  isLoading: Boolean,
  canSend: Boolean
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface)

  val openDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri -> uri?.let { onImageSent(it) } }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (showAttachIcon) {
      // КНОПКА ИИ (Робот)
      IconButton(
        onClick = onAiClick,
        enabled = !isLoading
      ) {
        Icon(
          imageVector = Icons.Default.SmartToy, // Иконка робота
          contentDescription = "AI Assistant",
          tint = MaterialTheme.colorScheme.primary // Выделим цветом бренда
        )
      }

      IconButton(onClick = { openDocumentLauncher.launch(arrayOf("image/*")) }) {
        Icon(imageVector = Icons.Default.AttachFile, contentDescription = null)
      }
      IconButton(onClick = onCameraClick) {
        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
      }
    }

    BasicTextField(
      value = text,
      onValueChange = { onTextChanged(it) },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Default
      ),
      modifier = Modifier.weight(1f),
      textStyle = textStyle,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      decorationBox = { innerTextField ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = 1.dp, // Сделал чуть тоньше (M3 стиль)
              color = MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(size = 24.dp) // Более округлое
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
          if (text.isEmpty()) {
            Text(
              text = "Повідомлення",
              style = textStyle,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
          }
          innerTextField()
        }
      }
    )

    Crossfade(isLoading, label = "send_state") { loading ->
      if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.size(32.dp).padding(6.dp),
          strokeWidth = 3.dp
        )
      } else {
        IconButton(
          onClick = {
            onSent()
            keyboardController?.hide()
          },
          enabled = canSend
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Відправити",
            tint = if (canSend) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
          )
        }
      }
    }
  }
}
