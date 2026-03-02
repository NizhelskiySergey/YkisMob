/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ykis.mob.ui.screens.family

import com.ykis.mob.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // ОБЯЗАТЕЛЬНО
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykis.mob.domain.family.FamilyEntity
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.BaseCard
import com.ykis.mob.ui.components.LabelTextWithText
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun FamilyContent(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  // Возвращаем koinViewModel, так как у нас Koin проект
  viewModel: FamilyListViewModel = koinViewModel(),
) {
  LaunchedEffect(key1 = baseUIState.apartment) {
    viewModel.getFamilyList(baseUIState.uid ?: "", baseUIState.addressId)
  }
  val state by viewModel.state.collectAsState()

  Box(
    modifier = Modifier
      .padding(horizontal = 8.dp) // Немного увеличили для M3
      .fillMaxSize()
  ) {
    // Контент списка
    AnimatedVisibility(
      visible = !state.isLoading,
      enter = fadeIn(tween(500)),
      exit = fadeOut(tween(500))
    ) {
      FamilyList(
        familyList = state.familyList,
        modifier = modifier
      )
    }

    // Индикатор загрузки
    if (state.isLoading) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
  }
}

@Composable
fun FamilyList(
  familyList: List<FamilyEntity>,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(vertical = 12.dp)
  ) {
    items(items = familyList, key = { it.recId }) { person ->
      // Используем именованные параметры, чтобы избежать Ambiguity
      FamilyListItem(
        person = person,
        modifier = Modifier.fillMaxWidth()
      )
      HorizontalDivider(
        modifier = Modifier.padding(horizontal = 72.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
      )
    }
  }
}

@Composable
fun FamilyListItem(
  modifier: Modifier = Modifier,
  person: FamilyEntity,
) {
  BaseCard(
    cardModifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp, horizontal = 12.dp)
  ) {
    // 1. Шапка: Аватар-иконка + ФИО
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Круглый контейнер для иконки
      Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }

      Column(
        modifier = Modifier
          .padding(start = 16.dp)
          .weight(1f)
      ) {
        Text(
          text = "${person.surname} ${person.fistname} ${person.lastname}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = 20.sp
        )
        // Статус (родство) прямо под именем для компактности
        Text(
          text = person.rodstvo,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium
        )
      }
    }

    // Разделитель перед деталями
    HorizontalDivider(
      modifier = Modifier.padding(vertical = 8.dp),
      thickness = 0.5.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    // 2. Блок данных: Дата, Документ, ИНН
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (person.born.isNotEmpty()) {
        LabelTextWithText(
          labelText = stringResource(R.string.born_text),
          valueText = person.born
        )
      }
      if (person.document.isNotEmpty()) {
        LabelTextWithText(
          labelText = stringResource(R.string.doc_text),
          valueText = person.document
        )
      }
      if (person.inn.isNotEmpty()) {
        LabelTextWithText(
          labelText = stringResource(R.string.inn_text),
          valueText = person.inn
        )
      }
    }
  }
}



