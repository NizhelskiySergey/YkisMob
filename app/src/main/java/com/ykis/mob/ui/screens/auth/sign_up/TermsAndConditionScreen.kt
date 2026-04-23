package com.ykis.mob.ui.screens.auth.sign_up

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ykis.mob.R
import com.ykis.mob.firebase.service.repo.FirebaseService

@Composable
fun TermsAndConditionScreen(
  termsText: String,
  firebaseService: FirebaseService,// ПРИНИМАЕМ ТЕКСТ
  onAccept: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      // 1. Защита от статус-бара (сверху) и системного меню (снизу)
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.agreement_title),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.primary
    )

    Spacer(Modifier.height(16.dp))

    // 2. Текст делаем прокручиваемым, чтобы он не выталкивал кнопку
    Box(modifier = Modifier.weight(1f)) {
      Text(
        text = termsText,
        modifier = Modifier.verticalScroll(rememberScrollState()),
        style = MaterialTheme.typography.bodyMedium
      )
    }

    Spacer(Modifier.height(16.dp))

    // 3. Кнопка теперь точно будет выше системных кнопок Android
    Button(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      onClick = {
        Log.d("YkisLog", "Terms: Нажата кнопка ПРИНЯТЬ")
        onAccept()
      },
      shape = RoundedCornerShape(12.dp)
    ) {
      Text(stringResource(R.string.agreement_check))
    }
  }
}

