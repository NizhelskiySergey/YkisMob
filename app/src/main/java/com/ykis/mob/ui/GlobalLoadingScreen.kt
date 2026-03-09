package com.ykis.mob.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun GlobalLoadingBarrier(isVisible: Boolean) {
  if (isVisible) {
    androidx.compose.ui.window.Dialog(
      onDismissRequest = { /* Не закрываем при клике мимо */ },
      properties = androidx.compose.ui.window.DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false
      )
    ) {
      androidx.compose.foundation.layout.Box(
        modifier = Modifier
          .fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
      ) {
        androidx.compose.material3.CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 4.dp
        )
      }
    }
  }
}

//
//@Composable
//fun GlobalLoadingBarrier(isShowing: Boolean) {
//  if (isShowing) {
//    // Box на весь экран, блокирующий ввод
//    Box(
//      modifier = Modifier
//        .fillMaxSize()
//        .background(Color.Black.copy(alpha = 0.4f)) // Затемнение
//        .pointerInput(Unit) {}, // Блокировка кликов
//      contentAlignment = Alignment.Center
//    ) {
//      Card(
//        shape = RoundedCornerShape(12.dp),
//        elevation = CardDefaults.cardElevation(8.dp)
//      ) {
//        Column(
//          modifier = Modifier.padding(24.dp),
//          horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
//          Spacer(modifier = Modifier.height(16.dp))
//          Text(text = "Загрузка...", style = MaterialTheme.typography.bodyMedium)
//        }
//      }
//    }
//  }
//}
