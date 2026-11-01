@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.launcher.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.launcher.ui.theme.Tokens as T

/** Use the actual Android window bounds, including OEM three-button navigation. */
@Composable internal fun FullScreenPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
        // Reserve a bottom gutter explicitly: some OEM dialog windows report
        // consumed safe-drawing insets even while navigation buttons are visible.
        val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
            .calculateBottomPadding().coerceAtLeast(24.dp)
        Column(Modifier.fillMaxSize().background(T.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = bottomInset)) {
            content()
        }
    }
}
