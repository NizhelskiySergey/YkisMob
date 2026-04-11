package com.ykis.mob.ui.screens.service

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ykis.mob.R
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.service.detail.ServiceDetailContent
import com.ykis.mob.ui.screens.service.list.TotalDebtState
import com.ykis.mob.ui.screens.service.payment.choice.PaymentChoiceStateful
import com.ykis.mob.ui.screens.service.payment.list.PaymentListStateful

@Composable
fun ServiceDetailScreen(
    modifier:Modifier = Modifier,
    navigationType: NavigationType,
    viewModel: ServiceViewModel,
    contentDetail: ContentDetail,
    baseUIState: BaseUIState,
    totalDebtState: TotalDebtState,
    navigateToWebView: (String) -> Unit
) {
    Column(
        modifier = modifier
    ){
      DefaultAppBar(
        navigationType = navigationType,
        canNavigateBack = true,
        onBackClick = {
          viewModel.closeContentDetail()
        },
        // Основной заголовок (жирный)
        title = when (contentDetail) {
          ContentDetail.OSBB -> baseUIState.osbb ?: ""
          ContentDetail.WATER_SERVICE -> stringResource(id = R.string.vodokanal)
          ContentDetail.WARM_SERVICE -> stringResource(id = R.string.ytke)
          ContentDetail.GARBAGE_SERVICE -> stringResource(id = R.string.yzhtrans)
          else -> stringResource(id = R.string.payment_list)
        },
        // Добавляем адрес (меньшим шрифтом)
        subtitle = baseUIState.address
      )

      when(contentDetail){
            ContentDetail.PAYMENT_LIST->{
                PaymentListStateful(
                    serviceViewModel = viewModel,
                    baseUIState = baseUIState
                )
            }
            ContentDetail.PAYMENT_CHOICE->{
                PaymentChoiceStateful(
                    baseUIState =baseUIState , totalDebtState =totalDebtState ,viewModel = viewModel,
                    navigateToWebView = navigateToWebView
                )
            }
            else ->{
                ServiceDetailContent(
                    contentDetail = contentDetail,
                    baseUIState = baseUIState
                )
            }
        }
    }

}
