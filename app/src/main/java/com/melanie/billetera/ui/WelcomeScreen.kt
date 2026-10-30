package com.melanie.billetera.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melanie.billetera.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onStartClick: () -> Unit) {
    val ballY = remember { Animatable(-1000f) }
    val ballScale = remember { Animatable(1f) }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val logoOffsetY = remember { Animatable(0f) }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(20f) }

    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(20f) }

    val buttonAlpha = remember { Animatable(0f) }
    val buttonOffsetY = remember { Animatable(20f) }

    val buttonPressScale = remember { Animatable(1f) }
    var isClicked by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(150)
        // 1. Cae la bolita
        ballY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 150f))
        delay(150)
        // 2. Se expande
        ballScale.animateTo(150f, tween(durationMillis = 600, easing = FastOutSlowInEasing))

        // 3. Ícono aparece suavemente en el centro
        launch { logoScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        logoAlpha.animateTo(1f, tween(500))
        delay(350)

        // 4. Logo sube suavemente a su posición
        logoOffsetY.animateTo(-130f, tween(700, easing = FastOutSlowInEasing))
        delay(100)

        // 5. Título aparece uno por uno
        launch { titleOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
        titleAlpha.animateTo(1f, tween(500))
        delay(200)

        // 6. Subtítulo aparece uno por uno
        launch { subtitleOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
        subtitleAlpha.animateTo(1f, tween(500))
        delay(200)

        // 7. Botón e imagen inferior aparecen uno por uno
        launch { buttonOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
        buttonAlpha.animateTo(1f, tween(500))
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // Bolita verde que se expande
        Box(
            modifier = Modifier
                .offset(y = ballY.value.dp)
                .scale(ballScale.value)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF9ED75B))
        )

        // Logo Inicial (Aparece suavemente)
        if (logoAlpha.value > 0f) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.melanie.billetera.R.drawable.logo_inicial),
                contentDescription = "Logo Inicial",
                modifier = Modifier
                    .offset(y = logoOffsetY.value.dp)
                    .scale(logoScale.value)
                    .size(160.dp)
                    .alpha(logoAlpha.value)
            )
        }

        // Elementos de texto y botón (Aparecen secuencialmente uno por uno)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = 70.dp)
                .padding(horizontal = 32.dp)
        ) {
            // Título
            if (titleAlpha.value > 0f) {
                Text(
                    text = "ClickKash",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF022814),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(y = titleOffsetY.value.dp)
                        .alpha(titleAlpha.value)
                )
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtítulo
            if (subtitleAlpha.value > 0f) {
                Text(
                    text = "Tu billetera digital, más cerca de ti",
                    fontSize = 18.sp,
                    color = Color(0xFF022814),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(y = subtitleOffsetY.value.dp)
                        .alpha(subtitleAlpha.value)
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Botón con efecto de pulsación
            if (buttonAlpha.value > 0f) {
                Button(
                    onClick = {
                        if (!isClicked) {
                            isClicked = true
                            coroutineScope.launch {
                                buttonPressScale.animateTo(0.92f, tween(90))
                                buttonPressScale.animateTo(1.0f, tween(90))
                                onStartClick()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF022814),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .offset(y = buttonOffsetY.value.dp)
                        .alpha(buttonAlpha.value)
                        .scale(buttonPressScale.value)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Comenzar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Continuar",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Imagen al pie de la pantalla
        if (buttonAlpha.value > 0f) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.melanie.billetera.R.drawable.pie_foto),
                contentDescription = "Pie de foto",
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .alpha(buttonAlpha.value)
            )
        }
    }
}
