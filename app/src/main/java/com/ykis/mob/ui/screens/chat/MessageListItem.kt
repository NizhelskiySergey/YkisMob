
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
  uid: String,
  messageEntity: MessageEntity,
  onLongClick: () -> Unit,
  onClick: () -> Unit
) {
  val isFromMe = remember(uid, messageEntity) { uid == messageEntity.senderUid }

  // Форма бабла (Material 3 стиль)
  val shape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = if (isFromMe) 16.dp else 2.dp,
    bottomEnd = if (isFromMe) 2.dp else 16.dp
  )

  // Цвета из нашей M3 темы
  val containerColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
  val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp, horizontal = 12.dp),
    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Bottom
  ) {
    if (!isFromMe) {
      UserImage(
        modifier = Modifier.size(32.dp).padding(bottom = 2.dp),
        photoUrl = messageEntity.senderLogoUrl.toString()
      )
      Spacer(modifier = Modifier.width(8.dp))
    }

    // САМ БАБЛ
    Column(
      modifier = Modifier
        .weight(1f, fill = false)
        .widthIn(max = 280.dp) // Ограничиваем, чтобы текст не "лип" к краям
        .clip(shape)
        .background(containerColor)
        .combinedClickable(
          onClick = { if (messageEntity.imageUrl != null) onClick() },
          onLongClick = { if (isFromMe) onLongClick() }
        )
        .padding(if (messageEntity.imageUrl != null) 4.dp else 8.dp) // Меньше отступ для фото
    ) {
      if (!isFromMe) {
        Text(
          text = messageEntity.senderDisplayedName,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        if (messageEntity.senderAddress.isNotBlank()) {
          Text(
            text = messageEntity.senderAddress,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (messageEntity.imageUrl != null) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(messageEntity.imageUrl)
            .crossfade(true)
            .build(),
          contentDescription = null,
          modifier = Modifier.clip(RoundedCornerShape(12.dp)).fillMaxWidth(),
          contentScale = ContentScale.FillWidth
        )
      }

      if (messageEntity.text.isNotBlank()) {
        Text(
          text = messageEntity.text,
          style = MaterialTheme.typography.bodyLarge,
          color = contentColor,
          modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        )
      }

      // Время (прижато вправо внизу бабла)
      Text(
        text = formatTime24H(messageEntity.timestamp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = contentColor.copy(alpha = 0.6f),
        modifier = Modifier.align(Alignment.End).padding(top = 2.dp, end = 4.dp)
      )
    }
  }
}


@Preview(showBackground = true)
@Composable
private fun PreviewMessageListItem() {
    YkisPAMTheme {
        MessageListItem(uid = "1", messageEntity = MessageEntity(
            text = "Привіт чувак! aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            senderDisplayedName = "Кирило Блідний",
            senderAddress = "Миру 28/1"
        ),
            onLongClick = {},
            onClick = {}
        )
    }
}
