package com.aesprt.marisuki.feature.credit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aesprt.marisuki.ui.components.ToolDialog
import com.aesprt.marisuki.ui.components.ToolIcon

@Composable
internal fun CreditDialogIcon(icon: ImageVector) {
    ToolIcon(icon)
}

@Composable
internal fun CreditDialogFrame(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ToolDialog(
        onDismissRequest = onDismiss,
        icon = { CreditDialogIcon(icon) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        scrollable = scrollable,
        text = {
            Column(
                Modifier.fillMaxWidth().then(if (!scrollable) Modifier.fillMaxHeight() else Modifier),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = actions)
        },
    )
}
