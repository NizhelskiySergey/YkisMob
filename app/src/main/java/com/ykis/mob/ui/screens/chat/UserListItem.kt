package com.ykis.mob.ui.screens.chat

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UserListItem(
  modifier: Modifier = Modifier,
  it : UserEntity,
  onUserClick : (UserEntity) -> Unit,
  lastMessage : MessageEntity
) {
  LaunchedEffect(key1 = lastMessage) {
    Log.d("time_test" , lastMessage.timestamp.toString())
  }
  Column(
    modifier = modifier.clickable {
      onUserClick(it)
    }
  ) {
    Row(
      modifier = modifier
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ){
      UserImage(photoUrl = it.photoUrl.toString())
      Column(
        modifier = modifier.padding(horizontal = 8.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ){
          Text(
            modifier = modifier.weight(1f),
            text = it.displayName ?: it.email.toString(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = historyFormatTime(lastMessage.timestamp),
            style = MaterialTheme.typography.bodySmall
          )
        }
        Text(
          text =  lastMessage.text
        )
      }
    }

    HorizontalDivider()
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
