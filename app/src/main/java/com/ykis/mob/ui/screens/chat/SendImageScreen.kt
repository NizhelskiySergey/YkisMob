package com.ykis.mob.ui.screens.chat

import android.R.attr.text
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ykis.mob.ui.components.ZoomableImage
import com.ykis.mob.ui.theme.YkisPAMTheme
import okhttp3.Address

@Composable
fun SendImageScreen(
  modifier: Modifier = Modifier,
  imageUri: Uri,
  navigateBack: () -> Unit,
  address: String,
  messageText: String,
  onMessageTextChanged: (String) -> Unit,
  onSent: () -> Unit,
  isLoadingAfterSending: Boolean,
  chatViewModel: ChatViewModel
) {
  val context = LocalContext.current
  val aiAssistantResponse by chatViewModel.assistantResponse.collectAsStateWithLifecycle()
  val messageText by chatViewModel.messageText.collectAsStateWithLifecycle()


  // 1. ОПРЕДЕЛЯЕМ ТИП КОНТЕНТА
  // [СТАБИЛЬНАЯ ВЕРСИЯ] определения типа в SendImageScreen
  val mimeType = remember(imageUri) { context.contentResolver.getType(imageUri) ?: "" }
  val isImage = remember(imageUri, mimeType) {
    mimeType.startsWith("image") ||
      imageUri.toString().lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.contains("camera")
      }
  }

  Log.d("YkisLog", "SendImageScreen: [TYPE_CHECK] Uri: $imageUri | isImage: $isImage")


  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding()
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentAlignment = Alignment.Center
    ) {
      // 2. УСЛОВНОЕ ОТОБРАЖЕНИЕ
      if (isImage) {
        Log.d("YkisLog", "SendImageScreen: Rendering as IMAGE")
        ZoomableImage(imageUri = imageUri)
      } else {
        Log.d("YkisLog", "SendImageScreen: Rendering as DOCUMENT")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = "Документ готовий до відправки",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = mimeType.substringAfter("/").uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline
          )
        }
      }

      IconButton(
        modifier = Modifier
          .padding(8.dp)
          .align(Alignment.TopStart),
        onClick = {
          Log.d("YkisLog", "SendImageScreen: Back pressed")
          navigateBack()
        }
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
      }
    }

    AnimatedVisibility(visible = !aiAssistantResponse.isNullOrBlank()) {
      Surface(
        modifier = Modifier.padding(8.dp).clickable {
          chatViewModel.onMessageTextChanged(aiAssistantResponse!!)
          chatViewModel.clearAiSuggestion()
        },
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
      ) {
        Text(text = aiAssistantResponse ?: "", modifier = Modifier.padding(12.dp))
      }
    }

    ComposeMessageBox(
    text = messageText, // Сюда прилетит ответ от ИИ
    onTextChanged = { chatViewModel.onMessageTextChanged(it) },

    onSent = {
        Log.d("YkisLog", "SendImageScreen: Send clicked")
        onSent()
      },
      onImageSent = {},
      // ИИ работает только с фото
      onAiClick = {
        if (isImage) chatViewModel.analyzePhotoWithGemini(imageUri, context,address)
        else Log.d("YkisLog", "Gemini: [SKIP] AI only works with images")
      },
      showAttachIcon = false,
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
      address = "",
      onSent = {},
      isLoadingAfterSending = false,
      chatViewModel = viewModel()
    )
  }
}
