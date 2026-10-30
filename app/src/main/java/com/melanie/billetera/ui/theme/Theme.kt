package com.melanie.billetera.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val BrandDark = Color(0xFF315D24)   // Verde institucional oscuro
val Brand = Color(0xFF8BC34A)       // Verde principal
val BrandLight = Color(0xFF8BC34A)  // Verde principal
val Lime = Color(0xFF8BC34A)
val SurfaceLight = Color(0xFFF3F7EE) // Verde claro para superficies
val Cream = Color(0xFFF3F7EE)        // Verde claro para superficies
val Income = Color(0xFF16804A)       // Verde para ingresos / operaciones exitosas
val Expense = Color(0xFFCC4545)      // Rojo para egresos / errores
val Ink = Color(0xFF19301B)          // Texto principal oscuro
val Muted = Color(0xFF627A65)        // Texto secundario
val BorderLight = Color(0xFFE7ECE5)  // Bordes suaves

private val scheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Income,
    background = Cream,
    surface = Color.White,
    onSurface = Ink,
    error = Expense,
)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
