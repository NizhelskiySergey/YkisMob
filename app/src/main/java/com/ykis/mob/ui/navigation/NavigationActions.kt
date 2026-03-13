
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

package com.ykis.mob.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HeatPump
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HeatPump
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.ykis.mob.R

data class TopLevelDestination(
    val route: String = "",
    val selectedIcon: ImageVector = Icons.Default.Adjust,
    val unselectedIcon: ImageVector = Icons.Default.Adjust,
    val labelId: Int,
    val alwaysVisible : Boolean,
)

data class TabItem(
    val titleId : Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val INFO_APARTMENT_TAB_ITEM = listOf(
    TabItem(
        titleId = R.string.bti,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    TabItem(
        titleId = R.string.list_family,
        selectedIcon = Icons.Filled.Group,
        unselectedIcon = Icons.Outlined.Group,
    )
)

val METER_TAB_ITEM = listOf(
    TabItem(
        titleId = R.string.water_meter,
        selectedIcon = Icons.Filled.WaterDrop,
        unselectedIcon = Icons.Outlined.WaterDrop,
    ),
    TabItem(
        titleId = R.string.heat_meter,
        selectedIcon = Icons.Filled.HeatPump,
        unselectedIcon = Icons.Outlined.HeatPump,
    )
)



val NAV_BAR_DESTINATIONS :List<TopLevelDestination>
@Composable
get(){
    return listOf(
        TopLevelDestination(
            route = InfoApartmentScreen.route,
            selectedIcon = Icons.Filled.Info,
            unselectedIcon = Icons.Outlined.Info,
            labelId = R.string.info,
            alwaysVisible = false,

            ),
        TopLevelDestination(
            route = MeterScreen.route,
            selectedIcon = ImageVector.vectorResource(id = R.drawable.ic_valve_filled),
            unselectedIcon = ImageVector.vectorResource(id = R.drawable.ic_valve_outline),
            labelId = R.string.meters,
            alwaysVisible = false
        ),
        TopLevelDestination(
            route = ServiceListScreen.route,
            selectedIcon = Icons.Filled.Payments,
            unselectedIcon = Icons.Outlined.Payments,
            labelId = R.string.accrued,
            alwaysVisible = false

        ),
        TopLevelDestination(
            route = UserListScreen.route,
            selectedIcon = Icons.AutoMirrored.Filled.Chat,
            unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
            labelId = R.string.chat,
            alwaysVisible = false

        ),
        TopLevelDestination(
            route = SettingsScreen.route,
            selectedIcon = Icons.Default.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            labelId = R.string.settings,
            alwaysVisible = true
        )
//        TopLevelDestination(
//            route = ProfileScreen.route,
//            selectedIcon = Icons.Default.AccountCircle,
//            unselectedIcon = Icons.Outlined.AccountCircle,
//            labelId = R.string.profile,
//            alwaysVisible = true
//        ),

        )
    }
 
val NAV_RAIL_DESTINATIONS :List<TopLevelDestination>
@Composable
get(){
  return   NAV_BAR_DESTINATIONS
//    + listOf(
//      TopLevelDestination(
//          route = SettingsScreen.route,
//          selectedIcon = Icons.Default.Settings,
//          unselectedIcon = Icons.Outlined.Settings,
//          labelId = R.string.settings,
//          alwaysVisible = true
//      )
//  )
}
  
