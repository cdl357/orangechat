/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.diary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.ui.components.nav.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryPage() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记") },
                navigationIcon = { BackButton() },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("日记功能开发中")
        }
    }
}
