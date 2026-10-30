package com.aesprt.marisuki.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.aesprt.marisuki.R
import com.aesprt.marisuki.ui.theme.PosRadius

@Composable
internal fun AuthFrame(scrollKey: Any? = Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val formScroll = rememberScrollState()
    LaunchedEffect(scrollKey) { formScroll.scrollTo(0) }
    val expanded = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    Scaffold(containerColor = colors.background) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).background(colors.background).imePadding()) {
            Row(
                Modifier.widthIn(max = 1140.dp).fillMaxSize().align(Alignment.Center).padding(if (expanded) 28.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expanded) {
                    Surface(
                        Modifier.weight(0.85f).fillMaxHeight(),
                        color = colors.primary,
                        contentColor = colors.onPrimary,
                        shape = PosRadius.dialog,
                    ) {
                        Column(
                            Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            AuthLogo()
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.auth_hero_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.auth_hero_body), style = MaterialTheme.typography.bodyLarge, color = colors.onPrimary.copy(alpha = .85f))
                            HorizontalDivider(color = colors.onPrimary.copy(alpha = .2f))
                            AuthHeroFeature(Icons.Default.PointOfSale, R.string.auth_feature_sales)
                            AuthHeroFeature(Icons.Default.Inventory2, R.string.auth_feature_stock)
                            AuthHeroFeature(Icons.Default.WifiOff, R.string.auth_feature_offline)
                        }
                    }
                }
                Surface(
                    Modifier.weight(1f).widthIn(max = 640.dp),
                    shape = PosRadius.dialog,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(formScroll).padding(if (expanded) 28.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthHeroFeature(icon: ImageVector, label: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(18.dp))
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun AuthLogo() {
    Image(
        painterResource(R.drawable.marisuki_logo),
        stringResource(R.string.app_name),
        Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(8.dp),
    )
}

@Composable
internal fun AuthIcon(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp))
    }
}

@Composable
internal fun AuthHeading(title: String, description: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AuthIcon(icon)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AuthNotice(text: String, error: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (error) colors.errorContainer else colors.primaryContainer.copy(alpha = .45f),
        shape = PosRadius.input,
        border = BorderStroke(1.dp, if (error) colors.error.copy(alpha = 0.3f) else colors.primary.copy(alpha = 0.25f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (error) Icons.Default.ErrorOutline else Icons.Default.Info,
                contentDescription = null,
                tint = if (error) colors.onErrorContainer else colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) colors.onErrorContainer else colors.onSurface,
            )
        }
    }
}

@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    val secret = visualTransformation is PasswordVisualTransformation
    OutlinedTextField(
        value,
        onValueChange,
        modifier,
        enabled = enabled,
        label = label,
        singleLine = singleLine,
        minLines = minLines,
        shape = PosRadius.input,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        visualTransformation = if (secret && visible) VisualTransformation.None else visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        leadingIcon = leadingIcon,
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
                    )
                }
            }
        } else null,
    )
}
