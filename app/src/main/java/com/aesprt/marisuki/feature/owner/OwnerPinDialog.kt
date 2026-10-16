package com.aesprt.marisuki.feature.owner

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aesprt.marisuki.R
import com.aesprt.marisuki.feature.pos.PosViewModel

/** Whole owner sections require an owner login; PIN approvals are scoped to individual operations. */
@Composable
fun OwnerPinDialog(viewModel: PosViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Owner sign-in required") },
        text = { Text("This tool is available only after signing in with the owner account. A PIN approval cannot unlock an entire section.") },
        confirmButton = { TextButton(onClick = { viewModel.lockOwnerTools(); onDismiss() }) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
