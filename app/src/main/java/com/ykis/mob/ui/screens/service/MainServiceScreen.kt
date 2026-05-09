package com.ykis.mob.ui.screens.service

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.DisplayFeature
import com.ykis.mob.domain.service.request.ServiceParams
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.BaseDualPanelContent
import com.ykis.mob.ui.components.DetailPanel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.ContentType
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.service.list.ServiceListScreen
import com.ykis.mob.ui.screens.service.list.TotalDebtState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainServiceScreen(
    modifier: Modifier = Modifier,
    viewModel : ServiceViewModel,
    baseUIState: BaseUIState,
    navigationType: NavigationType,
    displayFeature: List<DisplayFeature>,
    contentType: ContentType,
    onDrawerClick :()->Unit,
    navigateToWebView: (String) -> Unit
) {
    val totalDebtState by viewModel.totalDebtState.collectAsStateWithLifecycle()
    val contentDetail : ContentDetail = totalDebtState.serviceDetail
// ТРИГГЕР ЗАГРУЗКИ: Срабатывает при входе и при смене квартиры

  if (contentType == ContentType.DUAL_PANE) {
    // ЛОГ ПРИ КАЖДОЙ ПЕРЕРИСОВКЕ (RECOMPOSITION) ПАНЕЛЕЙ
    Log.d("YkisLog", "MainService.Tablet: [RECOMPOSE] CurrentDetail: $contentDetail | ShowDetail: ${totalDebtState.showDetail}")

    BaseDualPanelContent(
      modifier = modifier,
      displayFeatures = displayFeature,
      firstScreen = {
        ServiceListScreen(
          baseUIState = baseUIState,
          navigationType = navigationType,
          onDrawerClick = onDrawerClick,
          totalDebtState = totalDebtState,
          getTotalServiceDebt = { params ->
            Log.d("YkisLog", "MainService.Tablet: [GET_DEBT] Triggered for ${params.addressId}")
            viewModel.getTotalServiceDebt(params = params)
          },
          setContentDetail = { content ->
            // ЛОГ КЛИКА: Проверяем, что лямбда вообще вызывается
            Log.i("YkisLog", "MainService.Tablet: [CLICK_EVENT] Пользователь выбрал: $content")

            // При клике на планшете просто меняем контент во вьюмодели
            viewModel.setContentDetail(content)
          }
        )
      },
      secondScreen = {
        // Проверяем, что видит вторая панель
        Log.v("YkisLog", "MainService.Tablet: [DRAW_SECOND_PANE] State check -> $contentDetail")

        // Если контент выбран (не UNKNOWN), показываем ServiceDetailScreen сразу
        if (contentDetail != ContentDetail.UNKNOWN) {
          ServiceDetailScreen(
            modifier = Modifier.background(Color.Transparent),
            navigationType = navigationType,
            viewModel = viewModel,
            contentDetail = contentDetail,
            baseUIState = baseUIState,
            totalDebtState = totalDebtState,
            navigateToWebView = navigateToWebView
          )
        } else {
          // Заглушка, если ничего не выбрано
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Оберіть послугу для перегляду деталей",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
              )
            }
          }
        }
      }
    )
  }  else {
  // РЕЖИМ СМАРТФОНА - ВОЗВРАЩАЕМ ВЫЗОВ СЮДА
  SinglePanelService(
    contentDetail = contentDetail,
    baseUIState = baseUIState,
    navigationType = navigationType,
    onDrawerClick = onDrawerClick,
    totalDebtState = totalDebtState,
    viewModel = viewModel,
    navigateToWebView = navigateToWebView
  )
}




}
@Composable
fun SinglePanelService(
    modifier: Modifier = Modifier,
    contentDetail: ContentDetail,
    baseUIState: BaseUIState,
    navigationType: NavigationType,
    onDrawerClick: () -> Unit,
    totalDebtState: TotalDebtState,
    viewModel: ServiceViewModel,
    navigateToWebView: (String) -> Unit
) {

    Crossfade(targetState = totalDebtState.showDetail) {
        if(it){
            BackHandler {
                viewModel.closeContentDetail()
            }
            ServiceDetailScreen(
                modifier = modifier.background(MaterialTheme.colorScheme.background),
                navigationType = navigationType,
                viewModel = viewModel,
                contentDetail = contentDetail,
                baseUIState =baseUIState,
                totalDebtState = totalDebtState,
                navigateToWebView =navigateToWebView

            )
        }else ServiceListScreen(
            baseUIState =baseUIState ,
            navigationType = navigationType,
            onDrawerClick = onDrawerClick,
            totalDebtState = totalDebtState,
            getTotalServiceDebt = { params -> viewModel.getTotalServiceDebt(params = params)},
            setContentDetail = { content -> viewModel.setContentDetail(contentDetail = content)}
        )
    }
}
