package com.ykis.mob.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ykis.mob.R
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.service.list.TotalDebtState
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import com.ykis.mob.ui.screens.service.list.assembleServiceList

@Composable
fun ServiceSelectorScreen(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  chatViewModel: ChatViewModel,
  onServiceClick: (TotalServiceDebt) -> Unit,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType
) {
  val methodName = "ServiceSelectorScreen"
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  val isForwardingMode by chatViewModel.isForwardingMode.collectAsStateWithLifecycle()
  val selectedService by chatViewModel.selectedService.collectAsStateWithLifecycle()
  Column(modifier = modifier.fillMaxSize()) {
    DefaultAppBar(
      title = stringResource(id = R.string.services),
      subtitle = "Оберіть службу",
      onDrawerClick = onDrawerClicked,
      navigationType = navigationType
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        modifier = Modifier
          .width(IntrinsicSize.Max)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val residentServices = assembleServiceList(
          totalDebtState = TotalDebtState(),
          baseUIState = baseUIState
        )

        residentServices.forEach { service ->
          // СЧИТАЕМ СУММАРНЫЙ БЕЙДЖ (по всем квартирам жильца для этой службы)
          val totalCount = baseUIState.apartments.sumOf { apt ->
            val chatId = when (service.contentDetail) {
              ContentDetail.OSBB -> "OSBB_${apt.osmdId}_${apt.addressId}_${baseUIState.uid}"
              ContentDetail.WATER_SERVICE -> "WATER_SERVICE_9999_${apt.addressId}_${baseUIState.uid}"
              ContentDetail.WARM_SERVICE -> "WARM_SERVICE_9998_${apt.addressId}_${baseUIState.uid}"
              ContentDetail.GARBAGE_SERVICE -> "GARBAGE_SERVICE_9997_${apt.addressId}_${baseUIState.uid}"
              else -> "${service.contentDetail.name}_${apt.addressId}_${baseUIState.uid}"
            }
            unreadCounts[chatId] ?: 0
          }

          Box(modifier = Modifier.fillMaxWidth()) {
            Button(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
              onClick = {
                if (isForwardingMode) {
                  Log.d("YkisLog", "$methodName: [FORWARD_TO_SERVICE] ${service.contentDetail}")
                  chatViewModel.confirmForwardToService(service.contentDetail, baseUIState)
                } else {
                  Log.d("YkisLog", "$methodName: [SELECT_SERVICE] ${service.name}")
                  chatViewModel.setSelectedService(service)
                  onServiceClick(service)
                }
              }
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = service.icon,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  modifier = Modifier.weight(1f),
                  text = service.name,
                  textAlign = TextAlign.Start,
                  style = MaterialTheme.typography.titleMedium
                )
              }
            }

            // Отображение суммарного бейджа
            if (totalCount > 0 && !isForwardingMode) {
              Surface(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .offset(x = 6.dp, y = (-6).dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                tonalElevation = 4.dp
              ) {
                Text(
                  text = if (totalCount > 9) "9+" else totalCount.toString(),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = MaterialTheme.colorScheme.onError,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    }
  }
}

