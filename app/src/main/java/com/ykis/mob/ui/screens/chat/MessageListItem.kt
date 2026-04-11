
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ykis.mob.R
import com.ykis.mob.ui.components.UserImage
import com.ykis.mob.ui.screens.chat.MessageEntity
import com.ykis.mob.ui.screens.chat.formatTime24H

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageListItem(
  modifier: Modifier = Modifier,
  uid: String,
  isUserAdmin: Boolean,
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

    Column(
      modifier = Modifier
        .weight(1f, fill = false)
        .widthIn(max = 280.dp)
        .clip(shape)
        .background(containerColor)
        .combinedClickable(
          onClick = { if (messageEntity.imageUrl != null) onClick() },
          onLongClick = { if (isFromMe) onLongClick() }
        )
        .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
      // 1. ПЕРЕСЛАНО
      if (messageEntity.isForwarded) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            modifier = Modifier.size(12.dp).graphicsLayer(scaleX = -1f),
            tint = contentColor.copy(alpha = 0.6f)
          )
          Text(
            text = stringResource(R.string.forwarded),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp)
          )
        }
      }

      // 2. ЗАГОЛОВОК (Имя отправителя)
      if (!isFromMe) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = messageEntity.senderDisplayedName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (!messageEntity.senderAddress.contains("|")) {
            Text(
              text = " • ${stringResource(R.string.dispatcher)}",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
              modifier = Modifier.padding(start = 4.dp)
            )
          }
        }
      }

      // 3. ИЗОБРАЖЕНИЕ
      if (messageEntity.imageUrl != null) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(messageEntity.imageUrl)
            .crossfade(true)
            .build(),
          contentDescription = null,
          modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .fillMaxWidth(),
          contentScale = ContentScale.FillWidth
        )
      }

      // 4. ТЕКСТ И ПОДВАЛ (Время + Галочки)
      // Используем Box или Column с выравниванием элементов подвала
      Column(modifier = Modifier.fillMaxWidth()) {
        if (messageEntity.text.isNotBlank()) {
          Text(
            text = messageEntity.text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
          )
        }

        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (messageEntity.edited) {
            Text(
              text = stringResource(R.string.izm),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = contentColor.copy(alpha = 0.5f),
              modifier = Modifier.padding(end = 4.dp)
            )
          }

          Text(
            text = formatTime24H(messageEntity.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = contentColor.copy(alpha = 0.6f)
          )

          if (isFromMe) {
            // ДВЕ СИНИЕ ГАЛОЧКИ
            Icon(
              imageVector = if (messageEntity.read) Icons.Default.DoneAll else Icons.Default.Done,
              contentDescription = null,
              modifier = Modifier.size(15.dp).padding(start = 4.dp),
              // Если прочитано — ярко-синий (DodgerBlue), если нет — серый
              tint = if (messageEntity.read) Color(0xFF02C1FF) else contentColor.copy(alpha = 0.4f)
            )
          }
        }
      }
    }
  }
}




