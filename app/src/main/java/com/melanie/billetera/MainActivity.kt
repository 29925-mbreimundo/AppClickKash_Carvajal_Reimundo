package com.melanie.billetera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.melanie.billetera.ui.AuthScreen
import com.melanie.billetera.ui.PinScreen
import com.melanie.billetera.ui.WalletScreen
import com.melanie.billetera.ui.WelcomeScreen
import com.melanie.billetera.ui.theme.WalletTheme

class MainActivity : ComponentActivity() {
    private val vm: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showWelcome by rememberSaveable { mutableStateOf(true) }

            WalletTheme {
                Crossfade(
                    targetState = when {
                        vm.session == null && showWelcome -> 0
                        vm.session == null -> 1
                        vm.needsPinSetup -> 2
                        else -> 3
                    },
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "ScreenTransition"
                ) { screenState ->
                    when (screenState) {
                        0 -> WelcomeScreen { showWelcome = false }
                        1 -> AuthScreen(vm)
                        2 -> PinScreen(vm)
                        else -> WalletScreen(vm)
                    }
                }
            }
        }
    }
}
