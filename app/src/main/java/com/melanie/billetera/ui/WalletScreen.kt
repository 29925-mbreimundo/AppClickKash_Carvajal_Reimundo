package com.melanie.billetera.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melanie.billetera.WalletViewModel
import com.melanie.billetera.data.local.TransactionEntity
import com.melanie.billetera.data.local.TxType
import com.melanie.billetera.money
import com.melanie.billetera.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class Op(val title: String, val icon: ImageVector, val color: Color) {
    DEPOSIT("Depositar", Icons.Rounded.ArrowDownward, Color(0xFF16804A)),
    WITHDRAW("Retirar", Icons.Rounded.ArrowUpward, Color(0xFFCC4545)),
    TRANSFER("Transferir", Icons.Rounded.SwapHoriz, Color(0xFF315D24)),
    SERVICE("Pagar servicios", Icons.Rounded.ReceiptLong, Color(0xFF8BC34A)),
}

private enum class DateFilter(val label: String) {
    ALL("Todos"),
    TODAY("Hoy"),
    LAST_7_DAYS("Últimos 7 días"),
    LAST_30_DAYS("Últimos 30 días"),
    CUSTOM("Personalizado")
}

private enum class PinAction { CREATE, CHANGE, REMOVE }

@Composable
fun WalletScreen(vm: WalletViewModel) {
    val balance by vm.balance.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var op by remember { mutableStateOf<Op?>(null) }
    var showConfig by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf<PinAction?>(null) }
    var lastOk by remember { mutableStateOf(true) }
    val snack = remember { SnackbarHostState() }

    // Filtros de fecha
    var activeFilter by remember { mutableStateOf(DateFilter.ALL) }
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            lastOk = it.success
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it.text, duration = SnackbarDuration.Short)
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(history.firstOrNull()?.reference) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Filtrado de movimientos en tiempo real
    val filteredHistory = remember(history, activeFilter, customStartMs, customEndMs) {
        val cal = java.util.Calendar.getInstance()
        when (activeFilter) {
            DateFilter.ALL -> history
            DateFilter.TODAY -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val startOfToday = cal.timeInMillis
                history.filter { it.timestamp >= startOfToday }
            }
            DateFilter.LAST_7_DAYS -> {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val sevenDaysAgo = cal.timeInMillis
                history.filter { it.timestamp >= sevenDaysAgo }
            }
            DateFilter.LAST_30_DAYS -> {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -30)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val thirtyDaysAgo = cal.timeInMillis
                history.filter { it.timestamp >= thirtyDaysAgo }
            }
            DateFilter.CUSTOM -> {
                val start = customStartMs
                val end = customEndMs
                if (start != null && end != null) {
                    history.filter { it.timestamp in start..end }
                } else {
                    history
                }
            }
        }
    }

    Scaffold(
        containerColor = SurfaceLight,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snack, modifier = Modifier.navigationBarsPadding()) {
                Snackbar(
                    snackbarData = it, shape = RoundedCornerShape(16.dp),
                    containerColor = if (lastOk) BrandDark else Expense, contentColor = Color.White,
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            // Cabecera e información del usuario
            Header(balance, connected, vm.session?.name.orEmpty(), vm.session?.account.orEmpty(), busy) { showConfig = true }

            Spacer(Modifier.height(18.dp))
            ActionPanel(enabled = !busy) { op = it }

            Spacer(Modifier.height(20.dp))

            // Encabezado del Historial
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Movimientos", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("${filteredHistory.size} registros", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(8.dp))

            // Barra de Filtros por Fecha
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateFilter.entries.forEach { filter ->
                    val isSelected = activeFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (filter == DateFilter.CUSTOM) {
                                showCustomDateDialog = true
                            } else {
                                activeFilter = filter
                                customStartMs = null
                                customEndMs = null
                            }
                        },
                        label = {
                            Text(
                                filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandDark,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Ink
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderLight,
                            selectedBorderColor = BrandDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (activeFilter != DateFilter.ALL) {
                    AssistChip(
                        onClick = {
                            activeFilter = DateFilter.ALL
                            customStartMs = null
                            customEndMs = null
                        },
                        label = { Text("Restablecer", fontSize = 12.sp, color = Expense, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Rounded.Close, null, tint = Expense, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Expense.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filteredHistory.isEmpty()) {
                EmptyState(
                    message = if (activeFilter != DateFilter.ALL)
                        "No se encontraron movimientos en el período seleccionado"
                    else
                        "Aún no hay movimientos registrados",
                    showReset = activeFilter != DateFilter.ALL,
                    onReset = {
                        activeFilter = DateFilter.ALL
                        customStartMs = null
                        customEndMs = null
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(filteredHistory, key = { _, item -> item.id }) { index, tx ->
                        TxRow(tx, isLatest = (index == 0))
                    }
                }
            }
        }
    }

    op?.let { current ->
        when (current) {
            Op.SERVICE -> {
                ServicePaymentDialog(
                    balance = balance,
                    userName = vm.session?.name ?: "Usuario",
                    busy = busy,
                    onDismiss = { op = null },
                    onConfirm = { target, amount ->
                        vm.payService(target, amount)
                        op = null
                    }
                )
            }
            Op.TRANSFER -> {
                TransferDialog(
                    vm = vm,
                    balance = balance,
                    busy = busy,
                    onDismiss = { op = null }
                )
            }
            else -> {
                OperationDialog(
                    op = current,
                    onDismiss = { op = null },
                    onConfirm = { target, amount ->
                        when (current) {
                            Op.DEPOSIT -> vm.deposit(amount)
                            Op.WITHDRAW -> vm.withdraw(amount)
                            else -> {}
                        }
                        op = null
                    },
                )
            }
        }
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDateDialog = false },
            onApply = { startMs, endMs ->
                customStartMs = startMs
                customEndMs = endMs
                activeFilter = DateFilter.CUSTOM
                showCustomDateDialog = false
            }
        )
    }

    if (showConfig) AccountDialog(
        session = vm.session, host = vm.host, pinEnabled = vm.pinEnabled,
        onDismiss = { showConfig = false },
        onSaveHost = { vm.saveHost(it) },
        onPinCreate = { pinAction = PinAction.CREATE },
        onPinChange = { pinAction = PinAction.CHANGE },
        onPinRemove = { pinAction = PinAction.REMOVE },
        onLogout = { showConfig = false; vm.logout() },
    )

    pinAction?.let { action ->
        PinManageDialog(
            action = action,
            busy = busy,
            onDismiss = { pinAction = null },
            onConfirm = { pass, newPin ->
                if (action == PinAction.REMOVE) {
                    vm.removePin(password = pass) {
                        pinAction = null
                        showConfig = false
                    }
                } else {
                    vm.setupPin(password = pass, pin = newPin) {
                        pinAction = null
                        showConfig = false
                    }
                }
            },
        )
    }
}

// ---------------- Cabecera Institucional ----------------
@Composable
private fun Header(balance: Double, connected: Boolean, name: String, account: String, busy: Boolean, onSettings: () -> Unit) {
    val animated by animateFloatAsState(balance.toFloat(), tween(700), label = "balance")
    var showBalance by remember { mutableStateOf(true) }
    val currentDateText = remember {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "EC")).format(Date())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "EC")) else it.toString() }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF8BC34A), Color(0xFF9ED75B), Color(0xFF7CB342))
                )
            )
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 26.dp)) {
            // Fila superior: Saludo, Fecha, Estado de conexión y Configuración
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Ink.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Person, null, tint = Ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Hola, ${name.substringBefore(' ')}",
                        color = Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(currentDateText, color = Ink.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                ConnectionPill(connected)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, "Configuración", tint = Ink)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Tarjeta Institucional de Saldo sobre fondo #9ED75B
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Saldo disponible", color = Ink.copy(alpha = .85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { showBalance = !showBalance },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = "Ocultar/Mostrar saldo",
                                    tint = Ink.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text("Cuenta $account", color = Ink.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showBalance) money(animated.toDouble()) else "$**,**",
                            color = Ink,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (busy) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPill(connected: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, .35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )
    val color = if (connected) Color(0xFF3DDC97) else Color(0xFFFF6B6B)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(if (connected) pulse else 1f).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(if (connected) "En línea" else "Desconectado", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------- Acciones Principales ----------------
@Composable
private fun ActionPanel(enabled: Boolean, onClick: (Op) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Op.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ActionTile(it, enabled, Modifier.weight(1f)) { onClick(it) } }
            }
        }
    }
}

@Composable
private fun ActionTile(op: Op, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(78.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(op.color.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(op.icon, null, tint = op.color, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(op.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink, maxLines = 2, lineHeight = 18.sp)
        }
    }
}

// ---------------- Historial ----------------
private val dateFmt = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "EC")).apply {
    timeZone = java.util.TimeZone.getDefault()
}

@Composable
private fun TxRow(tx: TransactionEntity, isLatest: Boolean = false) {
    val (icon, tint, title) = when (tx.txType) {
        TxType.DEPOSIT -> Triple(Icons.Rounded.ArrowDownward, Income, "Depósito")
        TxType.WITHDRAW -> Triple(Icons.Rounded.ArrowUpward, Expense, "Retiro")
        TxType.TRANSFER_OUT -> Triple(
            Icons.Rounded.CallMade,
            Expense,
            if (!tx.counterpart.isNullOrBlank()) "Transferencia a ${tx.counterpart}" else "Transferencia enviada"
        )
        TxType.TRANSFER_IN -> Triple(
            Icons.Rounded.CallReceived,
            Income,
            if (!tx.counterpart.isNullOrBlank()) "Transferencia de ${tx.counterpart}" else "Transferencia recibida"
        )
        TxType.SERVICE -> Triple(
            when (tx.detail) {
                "Luz" -> Icons.Rounded.Bolt
                "Agua" -> Icons.Rounded.WaterDrop
                "Internet / Cable" -> Icons.Rounded.Wifi
                else -> Icons.Rounded.ReceiptLong
            }, Expense, tx.detail ?: "Servicio Básico"
        )
    }
    val sub = when (tx.txType) {
        TxType.TRANSFER_OUT -> if (!tx.counterpart.isNullOrBlank()) "Enviado a ${tx.counterpart}" else null
        TxType.TRANSFER_IN -> if (!tx.counterpart.isNullOrBlank()) "Recibido de ${tx.counterpart}" else null
        TxType.SERVICE -> "${tx.category ?: "Servicio Básico"} · ${tx.counterpart}"
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateFmt.format(Date(tx.timestamp)), fontSize = 12.sp, color = Muted)
            }
            Text(
                (if (tx.isIncome) "+" else "−") + money(tx.amount),
                color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String, showReset: Boolean = false, onReset: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.FilterListOff, null, tint = Muted.copy(alpha = .5f), modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(10.dp))
        Text(message, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        if (showReset && onReset != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BrandDark)
            ) {
                Text("Restablecer historial completo", color = BrandDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------- Diálogos y Modales ----------------
@Composable
private fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onApply: (startMs: Long, endMs: Long) -> Unit
) {
    var startDateText by remember { mutableStateOf("") }
    var endDateText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es", "EC")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = { Text("Filtrar por fecha personalizada", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingresa el rango de fechas en formato DD/MM/AAAA:", fontSize = 13.sp, color = Muted)

                OutlinedTextField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = { Text("Desde (DD/MM/AAAA)") },
                    placeholder = { Text("01/10/2026") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endDateText,
                    onValueChange = { endDateText = it },
                    label = { Text("Hasta (DD/MM/AAAA)") },
                    placeholder = { Text("24/10/2026") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = Expense, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val startDate = fmt.parse(startDateText.trim())
                        val endDate = fmt.parse(endDateText.trim())
                        if (startDate != null && endDate != null) {
                            val calStart = java.util.Calendar.getInstance()
                            calStart.time = startDate
                            calStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            calStart.set(java.util.Calendar.MINUTE, 0)
                            calStart.set(java.util.Calendar.SECOND, 0)

                            val calEnd = java.util.Calendar.getInstance()
                            calEnd.time = endDate
                            calEnd.set(java.util.Calendar.HOUR_OF_DAY, 23)
                            calEnd.set(java.util.Calendar.MINUTE, 59)
                            calEnd.set(java.util.Calendar.SECOND, 59)

                            if (calStart.timeInMillis <= calEnd.timeInMillis) {
                                onApply(calStart.timeInMillis, calEnd.timeInMillis)
                            } else {
                                errorMsg = "La fecha 'Desde' debe ser anterior o igual a 'Hasta'"
                            }
                        } else {
                            errorMsg = "Formato de fecha inválido (DD/MM/AAAA)"
                        }
                    } catch (e: Exception) {
                        errorMsg = "Ingresa fechas válidas en formato DD/MM/AAAA"
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink)
            ) {
                Text("Aplicar filtro", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Muted)
            }
        }
    )
}

@Composable
private fun TransferDialog(
    vm: WalletViewModel,
    balance: Double,
    busy: Boolean,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var accountInput by remember { mutableStateOf("") }
    var validatedAccount by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }

    var amountText by remember { mutableStateOf("") }
    var triedStep1 by remember { mutableStateOf(false) }
    var triedStep2 by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var transferResult by remember { mutableStateOf<com.melanie.billetera.data.remote.OpResponse?>(null) }
    var transferTimeText by remember { mutableStateOf("") }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val commission = 0.0
    val total = (amount ?: 0.0) + commission

    AlertDialog(
        onDismissRequest = { if (step != 4 && !busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Column {
                Text(
                    text = when (step) {
                        1 -> "Transferir dinero"
                        2 -> "Monto a transferir"
                        3 -> "Confirmar transferencia"
                        else -> "¡Transferencia exitosa!"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Ink
                )
                Text(
                    text = when (step) {
                        1 -> "Ingresa el número de cuenta del destinatario"
                        2 -> "Indica el valor que deseas enviar"
                        3 -> "Revisa los datos antes de confirmar"
                        else -> "La operación se procesó de forma segura"
                    },
                    fontSize = 13.sp,
                    color = Muted,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    1 -> {
                        OutlinedTextField(
                            value = accountInput,
                            onValueChange = {
                                accountInput = it
                                errorMsg = null
                            },
                            singleLine = true,
                            label = { Text("Número de cuenta del destinatario") },
                            placeholder = { Text("Ejemplo: ACC-2043") },
                            isError = triedStep1 && accountInput.isBlank(),
                            supportingText = if (errorMsg != null) ({ Text(errorMsg!!) }) else null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandDark,
                                unfocusedBorderColor = BorderLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Income,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Cuenta encontrada",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Income
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    recipientName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink
                                )
                                Text(
                                    "Cuenta $validatedAccount",
                                    fontSize = 12.sp,
                                    color = Muted
                                )
                            }
                        }

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it; errorMsg = null },
                            singleLine = true,
                            label = { Text("Monto a transferir") },
                            prefix = { Text("$ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = triedStep2 && (amount == null || amount <= 0),
                            supportingText = if (errorMsg != null) ({ Text(errorMsg!!) }) else null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandDark,
                                unfocusedBorderColor = BorderLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Saldo disponible:", fontSize = 13.sp, color = Muted)
                            Text(
                                money(balance),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balance >= total) Income else Expense
                            )
                        }
                    }
                    3 -> {
                        SummaryRow("Destinatario", recipientName, bold = true)
                        SummaryRow("Cuenta", validatedAccount)
                        SummaryRow("Monto a transferir", money(amount ?: 0.0))
                        SummaryRow("Comisión", money(commission))

                        HorizontalDivider(color = BorderLight)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total a debitar", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                                Text(money(total), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = BrandDark)
                            }
                        }

                        SummaryRow("Saldo disponible", money(balance), color = if (balance >= total) Income else Expense)

                        if (balance < total) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Expense.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Saldo insuficiente para realizar la transferencia",
                                    color = Expense,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Income,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Transferencia realizada con éxito",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Income,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                SummaryRow("Destinatario", recipientName, bold = true)
                                SummaryRow("Cuenta", validatedAccount)
                                SummaryRow("Monto transferido", money(amount ?: 0.0), color = Income)
                                SummaryRow("Referencia", transferResult?.reference ?: "TRF-OK")
                                SummaryRow("Fecha y hora", transferTimeText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> {
                    Button(
                        onClick = {
                            triedStep1 = true
                            if (accountInput.isNotBlank()) {
                                vm.validateAccount(
                                    target = accountInput,
                                    onSuccess = { acct, name ->
                                        validatedAccount = acct
                                        recipientName = name
                                        errorMsg = null
                                        step = 2
                                    },
                                    onError = { err ->
                                        errorMsg = err
                                    }
                                )
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp)
                        else Text("Validar cuenta", fontWeight = FontWeight.Bold)
                    }
                }
                2 -> {
                    Button(
                        onClick = {
                            triedStep2 = true
                            if (amount != null && amount > 0) {
                                if (balance >= total) {
                                    errorMsg = null
                                    step = 3
                                } else {
                                    errorMsg = "Saldo insuficiente"
                                }
                            } else {
                                errorMsg = "Ingresa un monto válido mayor a 0"
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Continuar", fontWeight = FontWeight.Bold)
                    }
                }
                3 -> {
                    Button(
                        onClick = {
                            if (amount != null && amount > 0 && balance >= total && !busy) {
                                vm.executeTransfer(
                                    to = validatedAccount,
                                    amount = amount,
                                    recipientName = recipientName,
                                    onSuccess = { res ->
                                        transferResult = res
                                        val ts = if (res.timestamp > 0) res.timestamp else System.currentTimeMillis()
                                        transferTimeText = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "EC")).apply {
                                            timeZone = java.util.TimeZone.getDefault()
                                        }.format(Date(ts))
                                        step = 4
                                    },
                                    onError = { err ->
                                        errorMsg = err
                                    }
                                )
                            }
                        },
                        enabled = !busy && amount != null && amount > 0 && balance >= total,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp)
                        else Text("Confirmar transferencia", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandDark, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Entendido", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            if (step != 4) {
                TextButton(
                    onClick = {
                        if (step == 2 || step == 3) {
                            step--
                            errorMsg = null
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = !busy
                ) {
                    Text(if (step == 1) "Cancelar" else "Atrás", color = Muted)
                }
            }
        }
    )
}

enum class ServiceType(val code: String, val title: String, val icon: ImageVector) {
    LUZ("LUZ", "Luz", Icons.Rounded.Bolt),
    AGUA("AGUA", "Agua", Icons.Rounded.WaterDrop),
    INTERNET("NET", "Internet / Cable", Icons.Rounded.Wifi),
    OTROS("OTR", "Otros servicios", Icons.Rounded.ReceiptLong)
}

@Composable
private fun ServicePaymentDialog(
    balance: Double,
    userName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var selectedService by remember { mutableStateOf(ServiceType.LUZ) }
    var contractCode by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }

    var invoiceAmount by remember { mutableDoubleStateOf(0.0) }
    var invoiceHolder by remember { mutableStateOf("") }
    var invoiceDueDate by remember { mutableStateOf("") }
    val commission = 0.50

    val contractErr = tried && contractCode.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Column {
                Text(
                    text = if (step == 1) "Pagar servicio" else "Confirmar pago",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Ink
                )
                Text(
                    text = if (step == 1) "Selecciona el servicio que deseas pagar" else "Revisa el resumen antes de continuar",
                    fontSize = 13.sp,
                    color = Muted,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            if (step == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ServiceType.entries.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowItems.forEach { srv ->
                                    val selected = selectedService == srv
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) SurfaceLight else Color.White,
                                        border = BorderStroke(1.5.dp, if (selected) BrandDark else BorderLight),
                                        modifier = Modifier.weight(1f).clickable { selectedService = srv }.padding(2.dp).height(74.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(srv.icon, null, tint = if (selected) BrandDark else Muted)
                                            Spacer(Modifier.height(4.dp))
                                            Text(srv.title, fontSize = 12.sp, color = if (selected) BrandDark else Muted, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = contractCode,
                        onValueChange = { contractCode = it.filter { c -> c.isDigit() } },
                        label = { Text("Código de contrato") },
                        placeholder = { Text("Ejemplo: 123456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = contractErr,
                        supportingText = if (contractErr) ({ Text("Ingresa el código de contrato") }) else null,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val total = invoiceAmount + commission
                    SummaryRow("Servicio", selectedService.title)
                    SummaryRow("Código de contrato", contractCode)
                    SummaryRow("Titular", invoiceHolder)
                    SummaryRow("Fecha de vencimiento", invoiceDueDate)

                    HorizontalDivider(color = BorderLight)

                    SummaryRow("Importe", money(invoiceAmount))
                    if (commission > 0) SummaryRow("Comisión", money(commission))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total a pagar", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                            Text(money(total), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = BrandDark)
                        }
                    }

                    SummaryRow("Saldo disponible", money(balance), color = if (balance >= total) Income else Expense)

                    if (balance < total) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Expense.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Saldo insuficiente para realizar el pago",
                                color = Expense,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        tried = true
                        if (contractCode.isNotBlank()) {
                            val hash = contractCode.hashCode() and Int.MAX_VALUE
                            invoiceAmount = when (selectedService) {
                                ServiceType.AGUA -> 5.0 + (hash % 2001) / 100.0
                                ServiceType.LUZ -> 15.0 + (hash % 2501) / 100.0
                                ServiceType.INTERNET -> 15.0 + (hash % 3001) / 100.0
                                ServiceType.OTROS -> 10.0 + (hash % 4001) / 100.0
                            }
                            invoiceHolder = userName
                            val c = java.util.Calendar.getInstance()
                            c.add(java.util.Calendar.DAY_OF_MONTH, hash % 15)
                            invoiceDueDate = SimpleDateFormat("dd MMM yyyy", Locale("es", "EC")).format(c.time)
                            step = 2
                        }
                    } else {
                        val total = invoiceAmount + commission
                        if (balance >= total && !busy) {
                            val fullContract = "${selectedService.code}-$contractCode"
                            onConfirm(fullContract, total)
                        }
                    }
                },
                enabled = !busy && (step == 1 || balance >= (invoiceAmount + commission)),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp)
                else Text(if (step == 1) "Consultar factura" else "Confirmar pago", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 2 && !busy) step = 1 else onDismiss() }, enabled = !busy) {
                Text(if (step == 2) "Atrás" else "Cancelar", color = Muted)
            }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false, color: Color = Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 14.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun OperationDialog(op: Op, onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var target by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val amountErr = tried && (amount == null || amount <= 0)
    val needsTarget = op == Op.TRANSFER
    val targetErr = tried && needsTarget && target.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        icon = {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(op.color.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(op.icon, null, tint = op.color, modifier = Modifier.size(28.dp)) }
        },
        title = { Text(op.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsTarget) OutlinedTextField(
                    value = target, onValueChange = { target = it }, singleLine = true,
                    label = { Text("Cuenta destino") },
                    placeholder = { Text("ACC-2002") },
                    isError = targetErr, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, singleLine = true,
                    label = { Text("Monto") }, prefix = { Text("$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountErr,
                    supportingText = if (amountErr) ({ Text("Ingresa un monto mayor a 0") }) else null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    tried = true
                    if (amount != null && amount > 0 && (!needsTarget || target.isNotBlank()))
                        onConfirm(target.trim(), amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } },
    )
}

@Composable
private fun AccountDialog(
    session: com.melanie.billetera.Session?, host: String, pinEnabled: Boolean,
    onDismiss: () -> Unit, onSaveHost: (String) -> Unit,
    onPinCreate: () -> Unit, onPinChange: () -> Unit, onPinRemove: () -> Unit,
    onLogout: () -> Unit,
) {
    var h by remember { mutableStateOf(host) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text(session?.name ?: "Mi cuenta", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(session?.email.orEmpty(), color = Muted, fontSize = 14.sp)
                Text("Cuenta ${session?.account.orEmpty()}", color = BrandDark, fontWeight = FontWeight.Bold)
                OutlinedTextField(h, { h = it }, singleLine = true, label = { Text("Servidor (host:puerto)") },
                    supportingText = { Text("Funnel: desktop-cae8044.tail338a50.ts.net") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                    modifier = Modifier.fillMaxWidth())

                if (pinEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onPinChange) {
                            Icon(Icons.Rounded.Lock, null, tint = BrandDark, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cambiar PIN", color = BrandDark, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onPinRemove) {
                            Icon(Icons.Rounded.Delete, null, tint = Expense, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quitar PIN", color = Expense, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(onPinCreate) {
                        Icon(Icons.Rounded.Lock, null, tint = BrandDark, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear PIN de acceso", color = BrandDark, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onLogout) {
                    Icon(Icons.Rounded.Logout, null, tint = Expense, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cerrar sesión", color = Expense, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { Button({ onSaveHost(h); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink), shape = RoundedCornerShape(14.dp)) { Text("Guardar", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cerrar", color = Muted) } },
    )
}

@Composable
private fun PinManageDialog(
    action: PinAction,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, pin: String) -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    var password by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val passErr = tried && password.isEmpty()

    val titleText = when {
        action == PinAction.REMOVE -> "Quitar PIN de acceso"
        step == 1 -> if (action == PinAction.CREATE) "Crear PIN de acceso" else "Cambiar PIN de acceso"
        step == 2 -> if (action == PinAction.CREATE) "Nuevo PIN" else "Ingresa tu nuevo PIN"
        else -> "Confirma tu PIN"
    }

    val subtitleText = when {
        action == PinAction.REMOVE -> "Ingresa tu contraseña actual para confirmar la eliminación de tu PIN."
        step == 1 -> if (action == PinAction.CREATE) "Ingresa tu contraseña actual para continuar." else "Ingresa tu contraseña actual para autorizar el cambio."
        step == 2 -> "4 dígitos para tu nuevo PIN"
        else -> "Escríbelo otra vez para confirmar"
    }

    LaunchedEffect(pin) {
        if (step > 1 && pin.length == 4) {
            delay(120)
            val f = firstPin
            when {
                f == null -> {
                    firstPin = pin
                    pin = ""
                    error = null
                    step = 3
                }
                f == pin -> {
                    onConfirm(password, pin)
                }
                else -> {
                    firstPin = null
                    pin = ""
                    error = "No coinciden, empieza de nuevo"
                    step = 2
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        icon = {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(BrandDark.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = BrandDark, modifier = Modifier.size(28.dp))
            }
        },
        title = { Text(titleText, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = subtitleText,
                    fontSize = 13.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )

                if (step == 1) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text("Contraseña actual") },
                        isError = passErr,
                        supportingText = if (passErr) ({ Text("Ingresa tu contraseña") }) else null,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = Muted)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandDark, unfocusedBorderColor = BorderLight),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(4) { index ->
                            val filled = index < pin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) BrandDark else Color(0xFFE2ECC8))
                            )
                        }
                    }

                    if (error != null) {
                        Text(error!!, color = Expense, fontSize = 12.sp)
                    }

                    Keypad(
                        enabled = !busy,
                        onDigit = { digit -> if (pin.length < 4) pin += digit },
                        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    )
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = {
                        tried = true
                        if (password.isNotEmpty()) {
                            if (action == PinAction.REMOVE) onConfirm(password, "") else step = 2
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Ink),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Continuar", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) }
        },
    )
}

@Composable
private fun Keypad(
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
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                        "" -> Spacer(Modifier.size(52.dp))
                        "<" -> Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onBackspace),
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
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFF3F7EE)).clickable(enabled = enabled) { onDigit(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
