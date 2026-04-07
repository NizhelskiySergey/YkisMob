/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.ykis.mob.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument


interface Destination{
    val route:String
}

object SignInScreen : Destination {
    override val route: String
        get() = "SignInScreen"
}
object VerifyEmailScreen : Destination {
    override val route: String
        get() = "VerifyEmailScreen"

}
object SignUpScreen : Destination {
    override val route: String
        get() = "SignUpScreen"
}
object AddApartmentScreen : Destination {
    override val route: String
        get() = "AddApartmentScreen"

}
object MeterScreen : Destination {
    override val route: String
        get() = "MeterScreen"

}
object ServiceListScreen : Destination {
    override val route: String
        get() = "ServiceListScreen"
}
object UserListScreen : Destination {
    override val route: String
        get() = "UserListScreen"
}
object SendImageScreenDest : Destination {
    override val route: String
        get() = "SendImageScreen"
}
object CameraScreenDest : Destination {
    override val route: String
        get() = "CameraScreen"
}
object ImageDetailScreenDest : Destination {
    override val route: String
        get() = "ImageDetailScreen"
}

object ChatScreenDest : Destination {
    override val route: String
        get() = "ChatScreen"
}
object ProfileScreenDest : Destination {
    override val route: String
        get() = "ProfileScreen"

}
object SettingsScreenDest : Destination {
    override val route: String
        get() = "SettingsScreen"

}
//object ApartmentScreenDest : Destination {
//    override val route: String
//        get() = "ApartmentScreen"
//    const val addressIdArg = "address_id"
//
//    val routeWithArgs = "$route/{$addressIdArg}"
//
//    val arguments = listOf(
//        navArgument(addressIdArg) {
//            type = NavType.IntType
//            defaultValue = 0
//        }
//    )
//
//}
object BtiScreenDest : Destination{
    override val route: String
        get() = "BtiScreen"

}
object FamilyScreenDest : Destination{
    override val route: String
        get() = "FamilyScreen"

}
object InfoApartmentScreenDest : Destination{
    override val route: String
        get() = "InfoApartmentScreen"
    const val addressIdArg = "address_id"
}

object WebViewScreenDest : Destination {
    override val route: String
        get() = "WebViewScreen"
    const val link = "link"

    val routeWithArgs = "$route/{$link}"

    val arguments = listOf(
        navArgument(link) {
            type = NavType.StringType
        }
    )

}
//const val LAUNCH_SCREEN = "LaunchScreen"
//const val SIGN_IN_SCREEN = "SignInScreen"
//const val VERIFY_EMAIL_SCREEN = "VerifyEmailScreen"
//const val SIGN_UP_SCREEN = "SignUpScreen"
const val APARTMENT_SCREEN = "ApartmentScreen"
//const val ADD_APARTMENT_SCREEN = "AddApartmentScreen"

const val WATER_SCREEN = "WaterScreen"
const val SERVICE_DETAIL_SCREEN ="ServiceDetailScreen"
//const val METER_SCREEN = "MeterScreen"
//const val SERVICE_LIST_SCREEN = "ServiceListScreen"



const val ADDRESS_ID = "addressId"
const val ADDRESS_DEFAULT_ID = "0"

const val HOUSE_ID="houseId"
const val HOUSE_DEFAULT_ID="0"

const val SERVICE="service"
const val SERVICE_DEFAULT="1"

const val SERVICE_NAME="serviceName"
const val SERVICE_DEFAULT_NAME="ОСББ"

const val ADDRESS = "address"
const val ADDRESS_DEFAULT = "адреса"

const val ADDRESS_ID_ARG = "?$ADDRESS_ID={$ADDRESS_ID}"
const val FLAT_ARG = "?$ADDRESS_ID={$ADDRESS_ID},$ADDRESS={$ADDRESS}"
const val SERVICE_ARG = "?$ADDRESS_ID={$ADDRESS_ID},$ADDRESS={$ADDRESS},$HOUSE_ID={$HOUSE_ID},$SERVICE={$SERVICE},$SERVICE_NAME={$SERVICE_NAME}"


