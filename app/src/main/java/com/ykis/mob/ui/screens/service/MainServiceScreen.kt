package com.ykis.mob.ui.screens.service

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.DisplayFeature
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
    viewModel : ServiceViewModel = koinViewModel(),
    baseUIState: BaseUIState,
    navigationType: NavigationType,
    displayFeature: List<DisplayFeature>,
    contentType: ContentType,
    onDrawerClick :()->Unit,
    navigateToWebView: (String) -> Unit
) {
    val totalDebtState by viewModel.totalDebtState.collectAsStateWithLifecycle()
    val contentDetail : ContentDetail = totalDebtState.serviceDetail

    if(contentType==ContentType.DUAL_PANE){
        BaseDualPanelContent(
            modifier = modifier,
            displayFeatures = displayFeature ,
            firstScreen = {
                ServiceListScreen(
                    baseUIState =baseUIState ,
                    navigationType = navigationType,
                    onDrawerClick = onDrawerClick,
                    totalDebtState = totalDebtState,
                    getTotalServiceDebt = { params -> viewModel.getTotalServiceDebt(params = params)},
                    setContentDetail = { contentDetail -> viewModel.setContentDetail(contentDetail)}
                )
            },
            secondScreen = {
                DetailPanel(
                    showDetail = totalDebtState.showDetail,
                    detailContent = {
                        ServiceDetailScreen(
                            modifier = Modifier.background(Color.Transparent),
                            navigationType = navigationType,
                            viewModel = viewModel,
                            contentDetail = contentDetail,
                            baseUIState =baseUIState,
                            totalDebtState = totalDebtState,
                            navigateToWebView = navigateToWebView
                        )
                    }
                )

            }
        )
    }else{
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
