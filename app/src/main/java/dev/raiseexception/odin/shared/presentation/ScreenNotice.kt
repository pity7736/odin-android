package dev.raiseexception.odin.shared.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

const val SCREEN_NOTICE_KEY = "screen_notice"

@Composable
fun rememberScreenNoticeHostState(notice: String?, onNoticeShown: () -> Unit): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeScope = rememberCoroutineScope()
    LaunchedEffect(notice) {
        val shownNotice = notice ?: return@LaunchedEffect
        onNoticeShown()
        noticeScope.launch { snackbarHostState.showSnackbar(shownNotice) }
    }
    return snackbarHostState
}
