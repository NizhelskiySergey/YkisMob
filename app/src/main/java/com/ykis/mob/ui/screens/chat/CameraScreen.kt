package com.ykis.mob.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.google.common.util.concurrent.ListenableFuture
import com.ykis.mob.ui.navigation.SendImageScreenDest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CameraScreen(
  navController: NavHostController,
  setImageUri: (Uri) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
  val outputDirectory = context.filesDir
  var previewView: PreviewView? by remember { mutableStateOf(null) }
  var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
  var isCapturing by remember { mutableStateOf(false) }
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { isGranted ->
      if (isGranted) {
        initializeCamera(cameraProviderFuture, lifecycleOwner, previewView) {
          imageCapture = it
        }
      } else {
        Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        navController.navigateUp() // Navigate back if permission is denied
      }
    }
  )

  LaunchedEffect(cameraProviderFuture) {
    if (ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      initializeCamera(cameraProviderFuture, lifecycleOwner, previewView) {
        imageCapture = it
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Box(
    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    contentAlignment = Alignment.BottomCenter
  ) {
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          previewView = this
          this.scaleType = PreviewView.ScaleType.FILL_CENTER
        }
      },
      modifier = Modifier.fillMaxSize()
    )

    // Кнопка назад (блокируем, если снимаем)
    IconButton(
      modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
      onClick = { if (!isCapturing) navController.navigateUp() },
      enabled = !isCapturing
    ) {
      Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
    }

    // Кнопка затвора
    Button(
      enabled = !isCapturing, // БЛОКИРУЕМ ПОВТОРНЫЙ КЛИК (как наш якорь 800 < 877)
      onClick = {
        isCapturing = true // Включаем режим ожидания

        val photoFile = File(
          outputDirectory,
          SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
          outputOptions,
          ContextCompat.getMainExecutor(context),
          object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
              isCapturing = false
              Log.e("YkisLog", "CameraScreen: Capture failed: ${exc.message}")
              Toast.makeText(context, "Помилка камери", Toast.LENGTH_SHORT).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
              // КРИТИЧЕСКИЙ ФИКС: Запускаем навигацию с минимальной задержкой
              // чтобы дать камере корректно закрыть файловый дескриптор
              android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val savedUri = Uri.fromFile(photoFile)
                setImageUri(savedUri)
                isCapturing = false
                navController.navigate(SendImageScreenDest.route)
              }, 300) // 300мс достаточно для финализации JPEG
            }
          }
        )
      },
      modifier = Modifier.padding(32.dp) // Чуть больше отступ для удобства
    ) {
      if (isCapturing) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
      } else {
        Text("Зробити фото")
      }
    }
  }
}

private fun initializeCamera(
  cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
  lifecycleOwner: LifecycleOwner,
  previewView: PreviewView?,
  onImageCaptureCreated: (ImageCapture) -> Unit
) {
  val cameraProvider = cameraProviderFuture.get()
  val preview = Preview.Builder().build().also {
    it.setSurfaceProvider(previewView?.surfaceProvider)
  }

  val imageCapture = ImageCapture.Builder().build()
  onImageCaptureCreated(imageCapture)

  val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

  try {
    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
      lifecycleOwner,
      cameraSelector,
      preview,
      imageCapture
    )
  } catch (exc: Exception) {
    Log.e("CameraScreen", "Use case binding failed", exc)
  }
}
