package com.aesprt.marisuki.feature.owner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aesprt.marisuki.R
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.ui.components.ToolDialog
import com.aesprt.marisuki.ui.components.ToolIcon
import com.aesprt.marisuki.ui.theme.PosRadius

/** Whole owner sections require an owner login; PIN approvals are scoped to individual operations. */
@Composable
fun OwnerPinDialog(viewModel: PosViewModel, onDismiss: () -> Unit) {
    ToolDialog(
        onDismissRequest = onDismiss,
        icon = { ToolIcon(Icons.Default.Lock) },
        title = { Text("Owner sign-in required", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This tool is available only after signing in with the owner account. A PIN approval cannot unlock an entire section.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = { viewModel.lockOwnerTools(); onDismiss() },
                shape = PosRadius.button,
            ) {
                Text(stringResource(R.string.close), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
