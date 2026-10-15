package com.lito.planify.dashboard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/** Todas las listas del dashboard, una columna por lista (con desplazamiento lateral si no caben). */
@Composable
fun TasksScreen(state: UiState) {
    val lists = state.content?.tasks.orEmpty().groupBy { it.task_list_id }.values.toList()
    ScreenPadding {
        if (lists.isEmpty()) {
            EmptyState(Icons.Outlined.TaskAlt, Palette.Online, "Todo hecho", "No quedan tareas pendientes en las listas del dashboard", Modifier.fillMaxSize(), large = true)
            return@ScreenPadding
        }
        val width = if (lists.size <= 4) null else 480
        Row(
            Modifier.fillMaxSize().then(if (width != null) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            lists.forEach { tasks ->
                val first = tasks.first()
                Panel((if (width != null) Modifier.width(width.dp) else Modifier.weight(1f)).fillMaxHeight()) {
                    CardHeader(first.task_list_name.orEmpty(), Icons.Outlined.Checklist, parseColor(first.task_list_color), count = tasks.size)
                    Column(Modifier.padding(top = 8.dp).fadingScroll(rememberScrollState())) {
                        tasks.forEach { TaskRow(it) }
                    }
                }
            }
        }
    }
}
