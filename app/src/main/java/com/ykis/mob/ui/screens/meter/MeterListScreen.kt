package com.ykis.mob.ui.screens.meter

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ykis.mob.R
import com.ykis.mob.domain.meter.heat.meter.HeatMeterEntity
import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.METER_TAB_ITEM
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.meter.heat.HeatMeterList
import com.ykis.mob.ui.screens.meter.heat.HeatMeterState
import com.ykis.mob.ui.screens.meter.water.WaterMeterList
import com.ykis.mob.ui.screens.meter.water.WaterMeterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterListScreen(
    modifier: Modifier = Modifier,
    viewModel : MeterViewModel,
    baseUIState: BaseUIState,
    navigationType: NavigationType,
    onWaterMeterClick : (WaterMeterEntity) ->Unit,
    onHeatMeterClick : (HeatMeterEntity) ->Unit,
    waterMeterState: WaterMeterState,
    heatMeterState: HeatMeterState,
    selectedTab:Int,
    onTabClick:(Int)->Unit,
    onDrawerClick: () ->Unit,
)
{

    Row(modifier.fillMaxSize()){
        Column(Modifier.weight(1f)) {
          DefaultAppBar(
            title = stringResource(id = R.string.meters),
            subtitle = baseUIState.address, // Добавили адрес текущей квартиры
            onBackClick = {},
            onDrawerClick = onDrawerClick,
            canNavigateBack = false,
            navigationType = navigationType
          )

          PrimaryTabRow(
                selectedTabIndex = selectedTab ,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                METER_TAB_ITEM.forEachIndexed { index, tabItem ->
                    LeadingIconTab(
                        selected = selectedTab==index,
                        onClick = {onTabClick(index)},
                        text = {
                            Text(text = stringResource( tabItem.titleId))
                        },
                        icon = {
                            Icon (
                                imageVector = if(index == selectedTab) tabItem.selectedIcon else tabItem.unselectedIcon,
                                contentDescription = stringResource( tabItem.titleId)
                            )
                        }
                    )

                }
            }
            Box(
                contentAlignment = Alignment.TopCenter
            ) {
                Crossfade(
                    targetState = selectedTab, label = ""
                ) { targetState ->
                    when (targetState) {
                        0 -> WaterMeterList(
                            viewModel = viewModel,
                            baseUIState = baseUIState ,
                            onWaterMeterClick = onWaterMeterClick,
                            waterMeterState = waterMeterState
                        )
                        else -> HeatMeterList(
                            viewModel = viewModel,
                            baseUIState = baseUIState,
                            onHeatMeterClick = onHeatMeterClick,
                            heatMeterState = heatMeterState
                        )
                    }
                }
            }
        }
        VerticalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

}
