package com.ykis.mob.ui.components
import com.ykis.mob.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ykis.mob.ui.theme.YkisPAMTheme

@Composable
fun BaseCard(
  cardModifier: Modifier = Modifier
    .fillMaxWidth()
    .padding(vertical = 6.dp, horizontal = 12.dp),
  columnModifier: Modifier = Modifier
    .fillMaxWidth()
    .padding(16.dp),
  labelModifier: Modifier = Modifier,
  label: String? = null,
  actionButton: @Composable (() -> Unit)? = null,
  content: @Composable () -> Unit
) {
  // Используем OutlinedCard для четких границ в светлой теме
  // В файле BaseCard.kt
  OutlinedCard(
    modifier = cardModifier,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.outlinedCardColors(
      // Карточка будет белой/светлой (surface), а фон под ней — серым (surfaceContainer)
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface
    ),
    border = BorderStroke(
      width = 0.5.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
  )
  {
    Column(
      modifier = columnModifier,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (label != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            modifier = labelModifier
              .weight(1f)
              .padding(bottom = 2.dp),
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary // Акцент на заголовке
            )
          )
          actionButton?.invoke()
        }
      }
      content()
    }
  }
}




@Preview(showBackground = true)
@Composable
private fun PreviewBaseCard() {
    YkisPAMTheme {
        BaseCard(
            label = stringResource(id = R.string.last_reading)
        ) {
            LabelTextWithText(
                labelText = stringResource(id = R.string.model_colon),
                valueText = "0"
            )
            LabelTextWithText(
                labelText = stringResource(id = R.string.number_colon),
                valueText = "0"
            )
            LabelTextWithText(
                labelText = stringResource(id = R.string.place_colon),
                valueText = "0"
            )
            LabelTextWithText(
                labelText = stringResource(id = R.string.position_colon),
                valueText = "0"
            )
            LabelTextWithCheckBox(
                labelText = stringResource(id = R.string.stoki_colon),
                checked = true
            )
            LabelTextWithCheckBox(
                labelText = stringResource(id = R.string.general_colon),
                checked = true
            )
            LabelTextWithText(
                labelText = stringResource(id = R.string.zdate_colon),
                valueText = "0"
            )
            LabelTextWithText(
                labelText = stringResource(id = R.string.sdate_colon),
                valueText = "0"
            )
        }
    }
}
