package com.ykis.mob.ui.screens.bti

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ykis.mob.R
import com.ykis.mob.core.ext.isTrue
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.BaseCard
import com.ykis.mob.ui.components.ColumnLabelTextWithTextAndIcon
import com.ykis.mob.ui.components.LabelTextWithCheckBox
import com.ykis.mob.ui.components.LabelTextWithText
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.theme.YkisPAMTheme


@Composable
fun BtiPanelContent(
    modifier: Modifier = Modifier,
    baseUIState: BaseUIState,
    viewModel: ApartmentViewModel
) {
    LaunchedEffect(key1 = baseUIState.addressId) {
        viewModel.initialContactState()
    }
    val contactUiState by viewModel.contactUIState.collectAsState()
    BtiContent(
        modifier = modifier,
        baseUIState = baseUIState,
        contactUiState = contactUiState,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onUpdateBti = {
            baseUIState.uid?.let { viewModel.onUpdateBti(it) }
        },

        )
}
@Composable
fun BtiContent(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  contactUiState: ContactUIState,
  onEmailChange: (String) -> Unit,
  onPhoneChange: (String) -> Unit,
  onUpdateBti: () -> Unit,
) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .fillMaxSize()
      .padding(16.dp), // Добавляем общий отступ
    verticalArrangement = Arrangement.spacedBy(12.dp), // Равномерные отступы между карточками
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Карточка Ответственного (Нанимателя)
    BaseCard {
      ColumnLabelTextWithTextAndIcon(
        imageVector = Icons.Default.Person,
        labelText = stringResource(id = R.string.employer_text_colon),
        valueText = baseUIState.apartment.nanim,
        // Добавьте в ваш компонент выделение цветом Primary для текста значения
      )
    }

    // Состав семьи и проживающие
    BaseCard(label = stringResource(id = R.string.compound_text)) {
      // Используем FlowRow или сетку, если элементов станет больше
      Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(
          modifier = Modifier.weight(1f),
          label = stringResource(R.string.tenant_text),
          value = baseUIState.apartment.tenant.toString(),
          icon = Icons.Default.Groups // Если есть такая возможность добавить иконку
        )
        InfoItem(
          modifier = Modifier.weight(1f),
          label = stringResource(R.string.podnan_text),
          value = baseUIState.apartment.podnan.toString()
        )
      }
      Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        InfoItem(
          modifier = Modifier.weight(1f),
          label = stringResource(id = R.string.absent_text),
          value = baseUIState.apartment.absent.toString()
        )
      }
    }

    // Площади (Сделаем акцент на цифрах)
    BaseCard(label = stringResource(id = R.string.area_flat)) {
      Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(Modifier.weight(1f), stringResource(R.string.area_full), baseUIState.apartment.areaFull.toString())
        InfoItem(Modifier.weight(1f), stringResource(R.string.area_life), baseUIState.apartment.areaLife.toString())
      }
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
      Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(Modifier.weight(1f), stringResource(R.string.area_extra), baseUIState.apartment.areaDop.toString())
        InfoItem(Modifier.weight(1f), stringResource(R.string.area_otopl), baseUIState.apartment.areaOtopl.toString())
      }
    }

    // Данные БТИ и Ордер
    BaseCard(label = stringResource(id = R.string.data_bti)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        InfoItem(Modifier.weight(1f), stringResource(R.string.rooms_colon), baseUIState.apartment.room.toString())

        // Чекбоксы лучше выровнять по правому краю или сделать в ряд
        LabelTextWithCheckBox(
          labelText = stringResource(id = R.string.private_text_colon),
          checked = baseUIState.apartment.privat.isTrue()
        )
        LabelTextWithCheckBox(
          labelText = stringResource(id = R.string.elevator_colon),
          checked = baseUIState.apartment.lift.isTrue()
        )
      }

      // Ордер выносим как отдельные строки с иконкой документа
      Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabelTextWithText(
          labelText = stringResource(id = R.string.order_text),
          valueText = baseUIState.apartment.order
        )
        LabelTextWithText(
          labelText = stringResource(id = R.string.date_orde_colon),
          valueText = baseUIState.apartment.dataOrder
        )
      }
    }

    ContactsCard(
      baseUIState = baseUIState,
      phone = contactUiState.phone,
      email = contactUiState.email,
      onEmailChange = onEmailChange,
      onPhoneChange = onPhoneChange,
      onUpdateBti = onUpdateBti
    )
  }
}

// Вспомогательный компонент для единообразия ячеек
@Composable
fun InfoItem(
  modifier: Modifier = Modifier,
  label: String,
  value: String,
  icon: ImageVector? = null
) {
  Column(modifier = modifier.padding(4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(16.dp).padding(end = 4.dp),
          tint = MaterialTheme.colorScheme.primary
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}


@Preview(showBackground = true)
@Composable
fun BtiContentPreview() {
    YkisPAMTheme {
        BtiContent(
            modifier = Modifier,
            baseUIState = BaseUIState(apartment = ApartmentEntity()),
            contactUiState = ContactUIState(
                addressId = 6314,
                address = "Гр.Десанту 21 кв.71",
                email = "nizelskiy.sergey@gmail.com"
            ),
            onEmailChange = { },
            onPhoneChange = { },
            onUpdateBti = {},
        )
    }
}
