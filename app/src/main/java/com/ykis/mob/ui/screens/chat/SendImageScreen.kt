package com.ykis.mob.ui.screens.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ykis.mob.ui.components.ZoomableImage
import com.ykis.mob.ui.theme.YkisPAMTheme

@Composable
fun SendImageScreen(
  modifier: Modifier = Modifier,
  imageUri: Uri,
  navigateBack: () -> Unit,
  messageText: String,
  onMessageTextChanged: (String) -> Unit,
  onSent: () -> Unit,
  isLoadingAfterSending: Boolean,
  chatViewModel: ChatViewModel
) {
  val context = LocalContext.current
  val aiAssistantResponse by chatViewModel.assistantResponse.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize()
  ) {
    Box(
      modifier = modifier
        .weight(1f)
        .align(Alignment.CenterHorizontally),
      contentAlignment = Alignment.Center
    ) {
      ZoomableImage(imageUri = imageUri)
      IconButton(
        modifier = modifier
          .padding(8.dp)
          .align(Alignment.TopStart),
        onClick = { navigateBack() }
      ) {
        Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          null
        )
      }
    }
    AnimatedVisibility(visible = !aiAssistantResponse.isNullOrBlank()) {
      Surface(
        modifier = Modifier.padding(8.dp).clickable {
          chatViewModel.onMessageTextChanged(aiAssistantResponse!!)
          chatViewModel.clearAiSuggestion()
        },
        color = MaterialTheme.colorScheme.primaryContainer
      ) {
        Text(text = aiAssistantResponse ?: "", modifier = Modifier.padding(12.dp))
      }
    }

    ComposeMessageBox(
      onSent = { onSent() },
      onImageSent = {},
      onAiClick = {chatViewModel.analyzePhotoWithGemini(imageUri, context) },
      text = messageText,
      showAttachIcon = false,
      onTextChanged = onMessageTextChanged,
      onCameraClick = {},
      isLoading = isLoadingAfterSending,
      canSend = true
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSendImageScreen() {
  YkisPAMTheme {
    SendImageScreen(
      imageUri = Uri.EMPTY,
      messageText = "",
      onMessageTextChanged = {},
      navigateBack = {},
      onSent = {},
      isLoadingAfterSending = false,
      chatViewModel = viewModel()
    )
  }
}
