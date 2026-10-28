package com.aesprt.marisuki.feature.more

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aesprt.marisuki.BuildConfig
import com.aesprt.marisuki.R
import com.aesprt.marisuki.ui.components.ToolCard
import com.aesprt.marisuki.ui.components.ToolSectionHeader

@Composable
fun DebugDeveloperTools() {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToolCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToolSectionHeader(stringResource(R.string.debug_build_information), Icons.Default.Code, stringResource(R.string.debug_staging_description))
                BuildDetail(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                BuildDetail(stringResource(R.string.debug_environment), "${BuildConfig.FLAVOR} · ${BuildConfig.BUILD_TYPE}")
                BuildDetail(stringResource(R.string.debug_application_id), BuildConfig.APPLICATION_ID)
            }
        }
        ToolCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToolSectionHeader(stringResource(R.string.debug_device), Icons.Default.PhoneAndroid)
                BuildDetail(stringResource(R.string.debug_device_model), "${Build.MANUFACTURER} ${Build.MODEL}")
                BuildDetail(stringResource(R.string.debug_android_version), "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
            }
        }
    }
}

@Composable
private fun BuildDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
