package com.ykis.mob.ui.screens.service.list

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.ykis.mob.R
import com.ykis.mob.domain.service.request.ServiceParams
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.theme.extendedColor

@Composable
fun assembleServiceList(
    totalDebtState: TotalDebtState,
    baseUIState: BaseUIState,
): List<TotalServiceDebt> {
    val serviceList = mutableListOf<TotalServiceDebt>()
    if (baseUIState.osmdId != 0) {
        serviceList.add(
            TotalServiceDebt(
                name = baseUIState.osbb,
                color = MaterialTheme.colorScheme.extendedColor.sectorColor4.color,
                debt = totalDebtState.totalDebt.dolg4!!,
                icon = Icons.Default.CorporateFare,
                contentDetail = ContentDetail.OSBB
            )
        )
    }
    serviceList.addAll(
        listOf(
            TotalServiceDebt(
                name = stringResource(R.string.vodokanal),
                color = MaterialTheme.colorScheme.extendedColor.sectorColor1.color,
                debt = totalDebtState.totalDebt.dolg1!!,
                icon = Icons.Default.Water,
                contentDetail = ContentDetail.WATER_SERVICE,
            ),
            TotalServiceDebt(
                name = stringResource(id = R.string.ytke_short),
                color = MaterialTheme.colorScheme.extendedColor.sectorColor2.color,
                debt = totalDebtState.totalDebt.dolg2!!,
                icon = Icons.Default.HotTub,
                contentDetail = ContentDetail.WARM_SERVICE
            ),
            TotalServiceDebt(
                name = stringResource(id = R.string.yzhtrans),
                color = MaterialTheme.colorScheme.extendedColor.sectorColor3.color,
                debt = totalDebtState.totalDebt.dolg3!!,
                icon = Icons.Default.Commute,
                contentDetail = ContentDetail.GARBAGE_SERVICE
            )
        )
    )
    return serviceList
}


@Composable
fun ServiceListScreen(
    totalDebtState: TotalDebtState,
    baseUIState: BaseUIState,
    navigationType: NavigationType,
    onDrawerClick : () -> Unit,
    getTotalServiceDebt: (ServiceParams) -> Unit,
    setContentDetail :(ContentDetail)->Unit,
) {
    LaunchedEffect(key1 = baseUIState.addressId) {
        getTotalServiceDebt(
            ServiceParams(
                uid = baseUIState.uid!!,
                addressId = baseUIState.addressId,
                houseId = baseUIState.houseId,
                service = 0,
                total = 1,
                year = "2023"
            )
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DefaultAppBar(
            title = stringResource(id = R.string.accrued),
            onDrawerClick = onDrawerClick,
            canNavigateBack = false,
            navigationType = navigationType
        ) {

            IconButton(
                onClick = {
                    setContentDetail(ContentDetail.PAYMENT_LIST)
                },
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_history),
                    contentDescription = "Історія платіжок",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Crossfade(
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(delayMillis = 500),
            targetState = totalDebtState.isLoading, label = ""
        ) { isLoading ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else ServiceListStateless(
                modifier = Modifier.fillMaxSize(),
                items = assembleServiceList(
                    totalDebtState = totalDebtState,
                    baseUIState = baseUIState
                ),
                debts = { totalServiceDebtList -> totalServiceDebtList.debt },
                colors = { totalServiceDebtList -> totalServiceDebtList.color },
                total = totalDebtState.totalDebt.dolg!!,
                circleLabel = stringResource(R.string.summary),
                rows = {
                    ServiceRow(
                        color = it.color,
                        title = it.name,
                        debt = it.debt,
                        icon = it.icon,
                        onClick = {
                            setContentDetail(it.contentDetail)
                        }
                    )
                },
            )
        }
    }
}
