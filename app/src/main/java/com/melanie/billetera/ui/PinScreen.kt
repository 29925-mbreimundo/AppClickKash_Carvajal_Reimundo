package com.melanie.billetera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melanie.billetera.R
import com.melanie.billetera.WalletViewModel
import com.melanie.billetera.ui.theme.*
import kotlinx.coroutines.delay

private const val PIN_LENGTH = 4

private fun isWeakPin(pin: String): Boolean {
    if (pin.length != PIN_LENGTH) return true
    if (pin.toSet().size == 1) return true
    val weak = listOf(
        "1234", "0123", "2345", "3456", "4567", "5678", "6789",
        "4321", "3210", "5432", "6543", "7654", "8765", "9876",
        "1212", "1122"
    )
    return pin in weak
}

/** Crear PIN (si vm.needsPinSetup) o desbloquear con PIN. */
@Composable
fun PinScreen(vm: WalletViewModel) {
    if (vm.needsPinSetup) PinSetup(vm) else PinUnlock(vm)
}

@Composable
private fun PinUnlock(vm: WalletViewModel) {
    val name = vm.session?.name?.substringBefore(' ').orEmpty()
    PinFrame(
        title = "Hola, $name",
        subtitle = "Ingresa tu PIN de acceso para continuar",
        error = vm.pinError,
        onComplete = vm::unlockWithPin,
        footer = {
            TextButton({ vm.forgotPin() }) {
                Text("¿Olvidaste tu PIN? Entra con contraseña", color = Color.White.copy(alpha = .85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun PinSetup(vm: WalletViewModel) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()

    PinFrame(
        title = if (first == null) "Crea tu PIN de acceso" else "Confirma tu PIN",
        subtitle = if (first == null) "4 dígitos para ingresar más rápido a tu billetera" else "Escríbelo nuevamente para confirmar",
        error = error,
        busy = busy,
        onComplete = { pin ->
            if (busy) return@PinFrame
            val f = first
            when {
                f == null -> {
                    if (isWeakPin(pin)) {
                        error = "Elige un PIN menos fácil de adivinar (ej. evita 1234 o números repetidos)"
                    } else {
                        first = pin
                        error = null
                    }
                }
                f == pin -> {
                    vm.setupPin(pin, onSuccess = {
                        error = null
                        first = null
                    })
                }
                else -> {
                    first = null
                    error = "Los PIN no coinciden, empieza de nuevo"
                }
            }
        },
        footer = {
            TextButton(
                onClick = { vm.skipPinSetup() },
                enabled = !busy
            ) {
                Text("Ahora no", color = Color.White.copy(alpha = .85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun PinFrame(
    title: String, subtitle: String, error: String?,
    busy: Boolean = false,
    onComplete: (String) -> Unit, footer: @Composable () -> Unit,
) {
    var digits by remember { mutableStateOf("") }

    LaunchedEffect(digits) {
        if (digits.length == PIN_LENGTH && !busy) {
            delay(140)
            onComplete(digits)
            digits = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BrandDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        // Logo Oficial ClickKash Institucional
        Image(
            painter = painterResource(id = R.drawable.logo_clickkash_login),
            contentDescription = "ClickKash Logo",
            modifier = Modifier
                .height(80.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(24.dp))
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Color.White.copy(alpha = .8f), fontSize = 14.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))

        // Indicadores de PIN (Dots Cifrados)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(PIN_LENGTH) { i ->
                val filled = i < digits.length
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) Brand else Color.White.copy(alpha = .25f))
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        if (busy) {
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Brand, strokeWidth = 2.dp)
            }
        } else {
            Text(
                error.orEmpty(),
                color = Color(0xFFFF8A80),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(32.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Teclado Numérico
        Keypad(
            enabled = !busy,
            onDigit = { if (digits.length < PIN_LENGTH && !busy) digits += it },
            onBackspace = { if (!busy) digits = digits.dropLast(1) },
        )

        Spacer(Modifier.height(12.dp))
        footer()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(68.dp))
                        "<" -> Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable(enabled = enabled, onClick = onBackspace),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, "Borrar", tint = Color.White.copy(alpha = .9f))
                        }
                        else -> Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = .15f))
                                .clickable(enabled = enabled) { onDigit(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
