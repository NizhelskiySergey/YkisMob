package com.ykis.mob.ui.screens.chat

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ykis.mob.ui.components.UserImage
import com.ykis.mob.ui.theme.YkisPAMTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun historyFormatTime(timestamp: Long): String {
  val currentTime = System.currentTimeMillis()
  val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L // 7 days in milliseconds

  val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
  val sdfDayOfWeek = SimpleDateFormat("E", Locale.getDefault())
  val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

  return when {
    // If today, show time like "21:17"
    timestamp >= currentTime - currentTime % (24 * 60 * 60 * 1000) && timestamp <= currentTime ->
      sdfTime.format(Date(timestamp))

    // If within the last 7 days, show shortened day of the week like "Fri", "Tue"
    timestamp > currentTime - sevenDaysInMillis ->
      sdfDayOfWeek.format(Date(timestamp))

    // Otherwise, show full date like "01.07.2024"
    else ->
      sdfDate.format(Date(timestamp))
  }
}
fun formatTime24H(timestamp: Long): String {

  val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

  return sdfTime.format(Date(timestamp))
}
@Composable
fun UserListItem(
  modifier: Modifier = Modifier,
  it: UserEntity,
  onUserClick: (UserEntity) -> Unit,
  lastMessage: MessageEntity,
  currentUid: String = ""
) {
  // Разрезаем строку "Адрес | Фамилия"
  val parts = remember(it.displayName) { it.displayName?.split("|") ?: emptyList() }
  val displayAddress = parts.getOrNull(0)?.trim() ?: it.displayName ?: "Нет адреса"
  val residentName = parts.getOrNull(1)?.trim() ?: ""

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onUserClick(it) }
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      UserImage(
        modifier = Modifier.size(52.dp),
        photoUrl = it.photoUrl.toString()
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
      ) {
        // 1. ПЕРВАЯ СТРОКА: АДРЕС И ВРЕМЯ
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            modifier = Modifier.weight(1f),
            text = displayAddress,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          if (lastMessage.timestamp > 0) {
            Text(
              text = formatTime24H(lastMessage.timestamp), // Используем твой формат
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
          }
        }

        // 2. ВТОРАЯ СТРОКА: ФИО ЖИЛЬЦА
        if (residentName.isNotEmpty()) {
          Text(
            text = residentName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1
          )
        }

        // 3. ТРЕТЬЯ СТРОКА: ПОСЛЕДНЕЕ СООБЩЕНИЕ
        val prefix = if (lastMessage.senderUid == currentUid) "Вы: " else ""
        val displayText = when {
          lastMessage.text.isNotBlank() -> "$prefix${lastMessage.text}"
          lastMessage.imageUrl != null -> "$prefix📷 Фотография"
          else -> "Нет сообщений"
        }

        Text(
          text = displayText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(start = 76.dp, end = 16.dp),
      thickness = 0.5.dp,
      color = MaterialTheme.colorScheme.outlineVariant
    )
  }
}






@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
private fun PreviewUserListItem() {
  YkisPAMTheme {
    UserListItem(it = UserEntity(
      displayName = "Кирило Блідний"
    ),
      onUserClick = {},
      lastMessage = MessageEntity(
        text = "Привіт чувак!"
      )
    )
  }
}
