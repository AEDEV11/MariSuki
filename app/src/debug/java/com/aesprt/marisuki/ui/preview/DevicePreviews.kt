package com.aesprt.marisuki.ui.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.aesprt.marisuki.ui.theme.MariSukiTheme
import android.content.res.Configuration

/** Applies the same four device and theme combinations to each screen preview. */
@Preview(name = "Phone · Light", device = "spec:width=360dp,height=800dp,dpi=420", showBackground = true)
@Preview(name = "Phone · Dark", device = "spec:width=360dp,height=800dp,dpi=420", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Tablet · Light", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Preview(name = "Tablet · Dark", device = "spec:width=1280dp,height=800dp,dpi=240", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class MariSukiDevicePreviews

@Composable
internal fun MariSukiPreviewSurface(content: @Composable () -> Unit) {
    MariSukiTheme(dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
