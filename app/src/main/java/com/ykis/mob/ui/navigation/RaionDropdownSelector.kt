package com.ykis.mob.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ykis.mob.domain.apartment.RaionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaionDropdownSelector(
  raions: List<RaionEntity>,
  onRaionSelected: (RaionEntity) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  var selectedName by remember { mutableStateOf("Выберите район") }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    OutlinedTextField(
      value = selectedName,
      onValueChange = {},
      readOnly = true,
      label = { Text("Район города") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.menuAnchor().fillMaxWidth(),
      shape = RoundedCornerShape(12.dp)
    )

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      raions.forEach { raion ->
        DropdownMenuItem(
          text = { Text(raion.raion) },
          onClick = {
            selectedName = raion.raion
            expanded = false
            onRaionSelected(raion)
          }
        )
      }
    }
  }
}
