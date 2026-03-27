package com.ykis.mob.ui.screens.chat

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
/**
 * Элемент списка пользователей (чата) для админ-панели.
 * Визуально выделяет непрочитанные сообщения и отображает превью последнего сообщения.
 */
@Composable
fun UserListItem(
  modifier: Modifier = Modifier,
  user: UserEntity, // Данные собеседника (жильца)
  onUserClick: (UserEntity) -> Unit, // Действие при клике на чат
  lastMessage: MessageEntity, // Объект последнего сообщения в этом чате
  currentUserId: String // UID текущего пользователя (админа) для логики "свой/чужой"
) {
  // ЛОГИКА ИНДИКАТОРА:
  // Сообщение считается непрочитанным, если:
  // 1. Отправитель — НЕ текущий пользователь (админ).
  // 2. Флаг isRead в объекте сообщения равен false.
  val isUnread = lastMessage.senderUid != currentUserId && !lastMessage.isRead

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onUserClick(user) }
      // Если сообщение не прочитано, подсвечиваем фон элемента легким основным цветом
      .background(
        if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        else Color.Transparent
      )
      .padding(horizontal = 16.dp)
  ) {
    Row(
      modifier = Modifier.padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Аватар пользователя
      UserImage(photoUrl = user.photoUrl.toString())

      Column(
        modifier = Modifier
          .padding(start = 12.dp)
          .weight(1f)
      ) {
        // ВЕРХНЯЯ СТРОКА: Имя и Время
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            modifier = Modifier.weight(1f),
            text = user.displayName ?: user.email.toString(),
            // Если не прочитано — выделяем имя жирным шрифтом
            style = if (isUnread) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          // Время последнего сообщения (подсвечиваем основным цветом, если новое)
          Text(
            text = historyFormatTime(lastMessage.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isUnread) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
          )
        }

        // НИЖНЯЯ СТРОКА: Текст сообщения и Индикатор-точка
        Row(
          modifier = Modifier.padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            modifier = Modifier.weight(1f),
            // Если текст пуст (например, прислали только фото), пишем заглушку
            text = lastMessage.text.ifEmpty { "Фотография" },
            style = MaterialTheme.typography.bodyMedium,
            // Непрочитанный текст делаем темнее и контрастнее
            color = if (isUnread) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          // СИНЯЯ ТОЧКА (Маркер нового сообщения)
          if (isUnread) {
            Surface(
              modifier = Modifier
                .padding(start = 8.dp)
                .size(10.dp),
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary
            ) {}
          }
        }
      }
    }
    // Тонкий разделитель между чатами
    HorizontalDivider(
      thickness = 0.5.dp,
      color = MaterialTheme.colorScheme.outlineVariant
    )
  }
}



@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, name = "Обычное сообщение")
@Composable
private fun PreviewUserListItem() {
  YkisPAMTheme {
    UserListItem(
      user = UserEntity(
        displayName = "Кирило Блідний",
        photoUrl = null
      ),
      onUserClick = {},
      // Пример прочитанного сообщения (isRead = true)
      lastMessage = MessageEntity(
        text = "Привіт чувак!",
        senderUid = "another_uid",
        isRead = true,
        timestamp = System.currentTimeMillis()
      ),
      currentUserId = "my_uid"
    )
  }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, name = "Непрочитанное сообщение")
@Composable
private fun PreviewUnreadUserListItem() {
  YkisPAMTheme {
    UserListItem(
      user = UserEntity(
        displayName = "Олег Скрипка",
        email = "vopli@email.com"
      ),
      onUserClick = {},
      // Пример НЕПРОЧИТАННОГО сообщения от другого пользователя
      lastMessage = MessageEntity(
        text = "Коли буде гаряча вода?",
        senderUid = "another_uid",
        isRead = false,
        timestamp = System.currentTimeMillis()
      ),
      currentUserId = "my_uid" // Наш ID (админа)
    )
  }
}

