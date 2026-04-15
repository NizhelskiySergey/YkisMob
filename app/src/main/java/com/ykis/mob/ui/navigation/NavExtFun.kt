package com.ykis.mob.ui.navigation

import androidx.navigation.NavHostController

fun NavHostController.cleanNavigateTo(route: String){
  this.navigate(route) {
    popUpTo(0) { inclusive = true }
  }
}

fun NavHostController.navigateWithPopUp(route: String, popUp: String?) {
  this.navigate(route) {
    launchSingleTop = true
    restoreState = true

    // Используем ?.let чтобы передать String вместо String?
    popUp?.let { popRoute ->
      popUpTo(popRoute) {
        inclusive = false
        saveState = true
      }
    }
  }
}


fun NavHostController.navigateToInfoApartment(){
    this.navigate("InfoApartmentScreen"){
        popUpTo(0)
        restoreState = false
        launchSingleTop = true
    }
}
