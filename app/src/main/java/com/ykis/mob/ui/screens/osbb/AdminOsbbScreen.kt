package com.ykis.mob.ui.screens.osbb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.firebase.entity.UserFirebase
import com.ykis.mob.ui.navigation.ContentType
import com.ykis.mob.ui.screens.chat.ChatViewModel
import com.ykis.mob.ui.screens.service.ServiceViewModel
import org.koin.compose.viewmodel.koinViewModel

object AdminRoutes {
  const val USER_LIST = "admin_user_list"
  const val SERVICE_REQUESTS = "admin_service_requests"
  const val STATISTICS = "admin_statistics"
}
//
//@Composable
//fun AdminOsbbScreen(
//  contentType: ContentType,
//  osbbId: Int,
//  chatViewModel: ChatViewModel = koinViewModel(),
//  serviceViewModel: ServiceViewModel = koinViewModel()
//) {
//  val userList by chatViewModel.userList.collectAsStateWithLifecycle()
//  val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
//
//  if (contentType == ContentType.DUAL_PANE) {
//    // РЕЖИМ ПЛАНШЕТА: Список слева, контент справа
//    Row(Modifier.fillMaxSize()) {
//      Box(Modifier.weight(0.4f)) {
//        UserListContent(
//          users = userList,
//          onUserClick = { chatViewModel.setSelectedUser(it) }
//        )
//      }
//      VerticalDivider()
//      Box(Modifier.weight(0.6f)) {
//        if (selectedUser.uid.isNotEmpty()) {
//          ChatScreenContent(user = selectedUser, viewModel = chatViewModel)
//        } else {
//          EmptyAdminState("Выберите пользователя для чата")
//        }
//      }
//    }
//  } else {
//    // РЕЖИМ ТЕЛЕФОНА: Только список (переход в чат через навигацию)
//    UserListContent(
//      users = userList,
//      onUserClick = { user ->
//        chatViewModel.setSelectedUser(user)
//        // Здесь вызываем переход на ChatScreen через navController
//      }
//    )
//  }
//}
