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

package com.ykis.mob.core.snackbar

import android.content.res.Resources
import androidx.annotation.StringRes
import com.ykis.mob.core.Constants.INCORRECT_CODE
import com.ykis.mob.core.Constants.NO_FLAT_DELETE
import com.ykis.mob.core.Constants.NO_USER_IDENTIFIER
import com.ykis.mob.core.Constants.PASSWORD_FAILURE
import com.ykis.mob.core.Constants.SENSITIVE_OPERATION_MESSAGE
import com.ykis.mob.core.Constants.SUCCESS_SEND_MESSAGE
import com.ykis.mob.core.Constants.VERIFY_DELETE_FLAT
import com.ykis.mob.R.string as AppText

sealed class SnackbarMessage {
  class StringSnackbar(val message: String) : SnackbarMessage()
  class ResourceSnackbar(@StringRes val message: Int) : SnackbarMessage()

  companion object {
    fun SnackbarMessage.toMessage(resources: Resources): String {
      return when (this) {
        is StringSnackbar -> this.message
        is ResourceSnackbar -> resources.getString(this.message)
      }
    }


    fun Throwable.toSnackbarMessage(): SnackbarMessage {
      val message = this.message.orEmpty()
      return if (message.isNotBlank()) {
        when (message) {
          INCORRECT_CODE -> ResourceSnackbar(AppText.error_incorrect_code)
          NO_FLAT_DELETE -> ResourceSnackbar(AppText.error_delete_flat)
          VERIFY_DELETE_FLAT -> ResourceSnackbar(AppText.success_delete_flat)
          NO_USER_IDENTIFIER -> ResourceSnackbar(AppText.no_user_identifier)
          PASSWORD_FAILURE -> ResourceSnackbar(AppText.password_failure)
          SUCCESS_SEND_MESSAGE -> ResourceSnackbar(AppText.success_send_message)
          SENSITIVE_OPERATION_MESSAGE -> ResourceSnackbar(AppText.revoke_access_message)
          else -> StringSnackbar(message)
          }
      } else {
        ResourceSnackbar(AppText.generic_error)
      }
    }
  }
}
