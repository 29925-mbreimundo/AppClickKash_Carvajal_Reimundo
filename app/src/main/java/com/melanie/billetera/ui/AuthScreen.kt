package com.melanie.billetera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melanie.billetera.WalletViewModel
import com.melanie.billetera.ui.theme.*
import kotlinx.coroutines.delay

/** Requisitos de contraseña (los mismos que valida el servidor). */
private val passwordRules: List<Pair<String, (String) -> Boolean>> = listOf(
    "Mínimo 8 caracteres" to { p -> p.length >= 8 },
    "Una letra mayúscula" to { p -> p.any { it.isUpperCase() } },
    "Una letra minúscula" to { p -> p.any { it.isLowerCase() } },
    "Un número" to { p -> p.any { it.isDigit() } },
)

private fun isWeakPin(pin: String): Boolean {
    if (pin.length != 4) return false
    if (pin.toSet().size == 1) return true
    val weak = listOf(
        "1234", "0123", "2345", "3456", "4567", "5678", "6789",
        "4321", "3210", "5432", "6543", "7654", "8765", "9876",
        "1212", "1122"
    )
    return pin in weak
}

@Composable
fun AuthScreen(vm: WalletViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var showServer by remember { mutableStateOf(false) }

    var register by remember { mutableStateOf(false) }
    var usePinLogin by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it.text)
        }
    }

    LaunchedEffect(vm.notice) {
        vm.notice?.let { snack.showSnackbar(it); vm.clearNotice() }
    }

    val isLocked = if (usePinLogin) {
        vm.isPinLoginLocked(vm.lastUserEmail ?: "")
    } else {
        vm.isPasswordLocked(email.trim())
    }

    LaunchedEffect(pin) {
        if (!register && usePinLogin && pin.length == 4 && !isLocked) {
            delay(120)
            vm.authenticateWithPin(pin)
            pin = ""
        }
    }

    val emailOk = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    val nameErr = tried && register && name.trim().length < 2
    val emailErr = tried && (register || !usePinLogin) && !emailOk
    val passOk = passwordRules.all { it.second(pass) }
    val passErr = tried && !usePinLogin && (if (register) !passOk else pass.isEmpty())
    val pass2Err = tried && register && pass2 != pass

    val pinOk = pin.isEmpty() || (pin.length == 4 && pin.all { it.isDigit() } && !isWeakPin(pin))
    val pinErrText = when {
        pin.isNotEmpty() && (pin.length != 4 || !pin.all { it.isDigit() }) -> "El PIN debe ser de exactamente 4 dígitos numéricos"
        pin.isNotEmpty() && isWeakPin(pin) -> "Elige un PIN menos fácil de adivinar (ej. evita 1234 o números repetidos)"
        else -> "PIN no válido"
    }
    val pinErr = tried && register && pin.isNotEmpty() && !pinOk

    fun submit() {
        if (isLocked) return
        tried = true
        if (register) {
            val valid = name.trim().length >= 2 && emailOk && passOk && pass2 == pass && pinOk
            if (valid) vm.authenticate(register = true, name = name, email = email, password = pass, pin = pin.takeIf { it.isNotBlank() })
        } else if (!usePinLogin) {
            val valid = emailOk && pass.isNotEmpty()
            if (valid) vm.authenticate(register = false, name = "", email = email, password = pass)
        }
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snack, Modifier.imePadding().navigationBarsPadding()) {
                Snackbar(it, shape = RoundedCornerShape(14.dp), containerColor = Brand, contentColor = Ink)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            // ---- 1. PARTE SUPERIOR INSTITUCIONAL ----
            AuthInstitutionalHeader(register = register)

            // ---- 2. HOJA DEL FORMULARIO ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Cream)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp)
                    .padding(top = 18.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
            ) {
                // ---- TÍTULO Y SUBTÍTULO DE LA PANTALLA (UBICADO ENTRE EL DIFUMINADO Y EL BLANCO) ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (register) "Crea tu cuenta" else "ClickKash",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (register)
                            "Empieza a gestionar tu dinero de forma fácil y segura."
                        else
                            "Administra tu dinero de forma fácil, segura y desde cualquier lugar.",
                        fontSize = 14.sp,
                        color = Muted,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Selector de Método para Iniciar Sesión (Contraseña / PIN) UBICADO ARRIBA DEL CORREO
                AnimatedVisibility(!register) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFE2ECC8))
                                .padding(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (!usePinLogin) Color.White else Color.Transparent)
                                    .clickable { usePinLogin = false; tried = false; pin = "" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Lock, null,
                                        tint = if (!usePinLogin) BrandDark else Muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Contraseña",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!usePinLogin) Ink else Muted,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (usePinLogin) Color.White else Color.Transparent)
                                    .clickable { usePinLogin = true; tried = false; pin = "" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Key, null,
                                        tint = if (usePinLogin) BrandDark else Muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "PIN de 4 dígitos",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (usePinLogin) Ink else Muted,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Modo PIN: Saludo + Indicadores cifrados (Dots) + Keypad
                AnimatedVisibility(!register && usePinLogin) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val lastUser = vm.lastUserName ?: vm.lastUserEmail
                        if (lastUser != null) {
                            Text(
                                "Hola, $lastUser",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Ingresa tu PIN de 4 dígitos",
                                fontSize = 13.sp,
                                color = Muted,
                            )
                        } else {
                            Text(
                                "Inicia sesión con contraseña por primera vez",
                                fontSize = 13.sp,
                                color = Expense,
                            )
                        }

                        // Indicadores Visuales Cifrados (Dots)
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(4) { index ->
                                val filled = index < pin.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) BrandDark else Color(0xFFC5DA9C))
                                )
                            }
                        }

                        if (busy) {
                            Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = BrandDark, strokeWidth = 2.dp)
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }

                        // Teclado Numérico Integrado (Custom Keypad)
                        AuthKeypad(
                            enabled = !busy && !isLocked,
                            onDigit = { digit -> if (pin.length < 4 && !isLocked) pin += digit },
                            onBackspace = { if (pin.isNotEmpty() && !isLocked) pin = pin.dropLast(1) },
                        )
                    }
                }

                // ---- CAMPOS DEL FORMULARIO ----
                AnimatedVisibility(register) {
                    Column {
                        Field(
                            value = name,
                            onChange = { name = it },
                            label = "Nombre completo",
                            icon = Icons.Rounded.Person,
                            error = nameErr,
                            errorText = "Escribe tu nombre completo",
                            options = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                AnimatedVisibility(register || !usePinLogin) {
                    Column {
                        Field(
                            value = email,
                            onChange = { email = it },
                            label = "Correo electrónico",
                            icon = Icons.Rounded.Mail,
                            error = emailErr,
                            errorText = "Ingresa un correo electrónico válido",
                            options = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                        )
                    }
                }

                // Campo Contraseña
                AnimatedVisibility(register || !usePinLogin) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Field(
                            value = pass,
                            onChange = { pass = it },
                            label = "Contraseña",
                            icon = Icons.Rounded.Lock,
                            error = passErr,
                            errorText = if (register) "Revisa los requisitos de abajo" else "Ingresa tu contraseña",
                            options = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
                            password = true,
                            visible = showPass,
                            onToggle = { showPass = !showPass },
                            onDone = ::submit
                        )
                    }
                }

                // Enlace "¿Olvidaste tu contraseña?" (Solo en Login por contraseña)
                AnimatedVisibility(!register && !usePinLogin) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandDark,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { /* Enlace visual sin funcionalidad */ }
                                .padding(4.dp)
                        )
                    }
                }

                // Requisitos de contraseña en Registro
                AnimatedVisibility(register) {
                    Column(Modifier.padding(start = 6.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        passwordRules.forEach { (label, check) ->
                            val ok = check(pass)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (ok) Income else Muted.copy(alpha = .6f), modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontSize = 12.sp, color = if (ok) Income else Muted)
                            }
                        }
                    }
                }

                // Confirmación de contraseña en Registro
                AnimatedVisibility(register) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Field(
                            value = pass2,
                            onChange = { pass2 = it },
                            label = "Confirmación de contraseña",
                            icon = Icons.Rounded.Lock,
                            error = pass2Err,
                            errorText = "Las contraseñas no coinciden",
                            options = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            password = true,
                            visible = showPass
                        )
                    }
                }

                // Campo PIN opcional para registro
                AnimatedVisibility(register) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Field(
                            value = pin,
                            onChange = { newValue ->
                                val filtered = newValue.filter { c -> c.isDigit() }
                                if (filtered.length <= 4) pin = filtered
                            },
                            label = "PIN de 4 dígitos (opcional)",
                            icon = Icons.Rounded.Key,
                            error = pinErr,
                            errorText = pinErrText,
                            options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            password = true,
                            visible = showPin,
                            onToggle = { showPin = !showPin },
                            onDone = ::submit,
                        )
                    }
                }

                // ---- BOTÓN PRINCIPAL VERDE INSTITUCIONAL (#8BC34A) ----
                AnimatedVisibility(register || !usePinLogin) {
                    Column {
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = ::submit,
                            enabled = !busy && !isLocked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand,
                                contentColor = Ink,
                                disabledContainerColor = Brand.copy(alpha = 0.5f),
                                disabledContentColor = Ink.copy(alpha = 0.5f)
                            ),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.5.dp)
                            } else {
                                Text(
                                    text = if (register) "Crear mi cuenta" else "Iniciar sesión",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }



                // ---- ENLACE DE CONMUTACIÓN (LOGIN <-> REGISTRO) ----
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (register) "¿Ya tienes cuenta? " else "¿No tienes cuenta? ",
                        fontSize = 14.sp,
                        color = Muted
                    )
                    Text(
                        text = if (register) "Iniciar sesión" else "Crear cuenta",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                register = !register
                                tried = false
                                pin = ""
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Indicador Discreto de Conexión de Servidor
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showServer = true }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Dns, "Servidor", tint = Muted.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Conexión de red", color = Muted.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }

    if (vm.showActiveSessionDialog) {
        AlertDialog(
            onDismissRequest = vm::cancelForceLogin,
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White,
            icon = {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Brand.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Devices, null, tint = BrandDark, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Sesión activa en otro dispositivo", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink) },
            text = {
                Text(
                    text = vm.activeSessionMessage.ifBlank { "Tu cuenta ya está abierta en otro dispositivo. Si continúas, se cerrará la sesión anterior." },
                    fontSize = 14.sp,
                    color = Muted,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmForceLogin,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink)
                ) {
                    Text("Continuar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelForceLogin) {
                    Text("Cancelar", color = Muted)
                }
            }
        )
    }

    if (showServer) HostDialog(vm.host, { showServer = false }) { vm.saveHost(it); showServer = false }
}

/**
 * Encabezado institucional con imagen de fondo (fondo.jpg) que abarca el 25% de la interfaz
 * y se difumina suavemente con la hoja inferior.
 */
@Composable
private fun AuthInstitutionalHeader(register: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (register) 0.18f else 0.25f),
        contentAlignment = Alignment.Center
    ) {
        // Imagen de fondo (fondo.jpg)
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.melanie.billetera.R.drawable.fondo),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        // Degradado difuminado inferior que conecta de forma fluida la imagen con la hoja clara inferior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Cream.copy(alpha = 0.5f),
                            Cream
                        )
                    )
                )
        )
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    error: Boolean, errorText: String, options: KeyboardOptions,
    password: Boolean = false, visible: Boolean = false, onToggle: (() -> Unit)? = null, onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = if (error) Expense else BrandDark) },
        trailingIcon = if (password && onToggle != null) ({
            IconButton(onToggle) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar contraseña", tint = Muted)
            }
        }) else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = options,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        isError = error,
        supportingText = if (error) ({ Text(errorText) }) else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedBorderColor = BrandDark, unfocusedBorderColor = Color(0xFFE7ECE5),
            focusedLabelColor = BrandDark, cursorColor = BrandDark,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun HostDialog(host: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var h by remember { mutableStateOf(host) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text("Configurar Servidor", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                h, { h = it }, singleLine = true, label = { Text("URL / Host del servidor") },
                supportingText = { Text("Funnel: desktop-cae8044.tail338a50.ts.net · Emulador: 10.0.2.2:3000") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button({ onSave(h) }, shape = RoundedCornerShape(14.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = Muted) } },
    )
}

@Composable
private fun AuthKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<"),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(56.dp))
                        "<" -> Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .clickable(enabled = enabled, onClick = onBackspace),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Borrar",
                                tint = Ink.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        else -> Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2ECC8))
                                .clickable(enabled = enabled) { onDigit(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key,
                                color = Ink,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
