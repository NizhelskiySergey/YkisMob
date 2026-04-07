
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ykis.mob.ui.components.UserImage
import com.ykis.mob.ui.screens.chat.MessageEntity
import com.ykis.mob.ui.screens.chat.formatTime24H
import com.ykis.mob.ui.theme.YkisPAMTheme
import kotlinx.coroutines.Dispatchers
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageListItem(
  modifier: Modifier = Modifier,
  uid: String,               // Свой UID
  isUserAdmin: Boolean,      // Флаг: является ли текущий пользователь админом
  messageEntity: MessageEntity,
  onLongClick: () -> Unit,
  onClick: () -> Unit
) {
  val isFromMe = remember(uid, messageEntity.senderUid) { uid == messageEntity.senderUid }

  val shape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = if (isFromMe) 16.dp else 4.dp,
    bottomEnd = if (isFromMe) 4.dp else 16.dp
  )

  val containerColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
  val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp, horizontal = 12.dp),
    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Bottom
  ) {
    if (!isFromMe) {
      UserImage(
        modifier = Modifier.size(34.dp).padding(bottom = 2.dp),
        photoUrl = messageEntity.senderLogoUrl.toString()
      )
      Spacer(modifier = Modifier.width(8.dp))
    }

    Column(
      modifier = Modifier
        .weight(1f, fill = false)
        .widthIn(max = 280.dp)
        .clip(shape)
        .background(containerColor)
        .combinedClickable(
          onClick = { if (messageEntity.imageUrl != null) onClick() },
          // УДАЛЕНИЕ: разрешаем удалять только свои сообщения
          onLongClick = {
            if (isFromMe) {
              Log.d("YkisLog", "MessageListItem: Long click detected on msg ${messageEntity.id}")
              onLongClick()
            }
          }
        )
        .padding(8.dp)
    ) {
      // ЛОГИКА ЗАГОЛОВКОВ
      if (!isFromMe) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = messageEntity.senderDisplayedName, // Используем уже очищенное имя из БД
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          // Пометка "Диспетчер", если сообщение пришло не от жильца (в имени жильца всегда есть '|')
          // Если мы пишем чистый Nickname админа, то разделителя там нет.
          if (!messageEntity.senderAddress.contains("|")) {
            Text(
              text = " • Диспетчер",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
              modifier = Modifier.padding(start = 4.dp)
            )
          }
        }
      }

      if (messageEntity.imageUrl != null) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(messageEntity.imageUrl)
            .crossfade(true)
            .build(),
          contentDescription = null,
          modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .fillMaxWidth(),
          contentScale = ContentScale.FillWidth
        )
      }

      if (messageEntity.text.isNotBlank()) {
        Text(
          text = messageEntity.text,
          style = MaterialTheme.typography.bodyLarge,
          color = contentColor,
          modifier = Modifier.padding(vertical = 2.dp)
        )
      }

      Row(
        modifier = Modifier.align(Alignment.End),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = formatTime24H(messageEntity.timestamp),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = contentColor.copy(alpha = 0.7f),
          modifier = Modifier.padding(top = 2.dp)
        )

        // Иконка "прочитано" для ваших сообщений
        if (isFromMe) {
          Icon(
            imageVector = if (messageEntity.read) Icons.Default.DoneAll else Icons.Default.Done,
            contentDescription = null,
            modifier = Modifier.size(14.dp).padding(start = 4.dp),
            tint = if (messageEntity.read) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.5f)
          )
        }
      }
    }
  }
}



