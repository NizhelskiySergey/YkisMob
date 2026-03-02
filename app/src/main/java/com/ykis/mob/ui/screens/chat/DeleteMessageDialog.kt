package com.ykis.mob.ui.screens.chat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ykis.mob.R

@Composable
fun DeleteMessageDialog(
  modifier: Modifier = Modifier,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertDialog(
    modifier = modifier,
    onDismissRequest = onDismiss,
    // Поверхность с небольшим подъемом, чтобы выделяться на фоне чата
    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
    icon = {
      Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error // Красный акцент для опасного действия
      )
    },
    title = {
      Text(
        text = "Видалити повідомлення?",
        style = MaterialTheme.typography.headlineSmall
      )
    },
    text = {
      Text(
        text = "Повідомлення буде видалено безповоротно!",
        style = MaterialTheme.typography.bodyMedium
      )
    },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        // Текст кнопки удаления тоже делаем красным
        colors = ButtonDefaults.textButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        )
      ) {
        Text("Видалити", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Відмінити")
      }
    }
  )
}
