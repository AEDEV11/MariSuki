package com.aesprt.marisuki

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aesprt.marisuki.ui.theme.MariSukiTheme
import com.aesprt.marisuki.ui.navigation.MariSukiApp
import com.aesprt.marisuki.feature.auth.AuthViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent {
            val authViewModel: AuthViewModel = koinViewModel()
            val authState = authViewModel.state.collectAsStateWithLifecycle().value
            MariSukiTheme(themePreference = authState.theme) {
                MariSukiApp(authViewModel = authViewModel)
            }
        }
    }
}
