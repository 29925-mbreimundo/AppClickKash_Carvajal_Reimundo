package com.melanie.billetera

import android.app.Application
import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melanie.billetera.data.local.AppDatabase
import com.melanie.billetera.data.local.TransactionEntity
import com.melanie.billetera.data.local.TxType
import com.melanie.billetera.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Session(val token: String, val name: String, val email: String, val account: String)

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("wallet", Context.MODE_PRIVATE)
    private val dao = AppDatabase.get(app).transactionDao()
    private val socket = WalletSocket(viewModelScope)

    var host by mutableStateOf(prefs.getString("host", "https://desktop-cae8044.tail338a50.ts.net/")!!)
        private set

    @Volatile private var token: String? = null
    var session by mutableStateOf<Session?>(null)
        private set

    var lastUserEmail by mutableStateOf(prefs.getString("last_email", null))
        private set
    var lastUserName by mutableStateOf(prefs.getString("last_name", null))
        private set

    // ---------- Bloqueo Independiente: Contraseña vs PIN de Login ----------
    var passwordLockoutSeconds by mutableStateOf(0)
        private set
    var passwordLockoutIdentifier by mutableStateOf<String?>(null)
        private set
    private var passwordLockoutJob: Job? = null

    var pinLoginLockoutSeconds by mutableStateOf(0)
        private set
    var pinLoginLockoutIdentifier by mutableStateOf<String?>(null)
        private set
    private var pinLoginLockoutJob: Job? = null

    private fun formatLockoutTime(seconds: Int): String {
        return if (seconds >= 60) {
            val mins = (seconds + 59) / 60
            "$mins ${if (mins == 1) "minuto" else "minutos"}"
        } else {
            "$seconds ${if (seconds == 1) "segundo" else "segundos"}"
        }
    }

    fun isPasswordLocked(email: String): Boolean {
        if (passwordLockoutSeconds <= 0) return false
        val lockedId = passwordLockoutIdentifier ?: return true
        return lockedId.equals(email.trim(), ignoreCase = true)
    }

    fun isPinLoginLocked(identifier: String): Boolean {
        if (pinLoginLockoutSeconds <= 0) return false
        val lockedId = pinLoginLockoutIdentifier ?: return true
        return lockedId.equals(identifier.trim(), ignoreCase = true)
    }

    fun startPasswordLockout(seconds: Int, identifier: String?) {
        passwordLockoutJob?.cancel()
        passwordLockoutSeconds = seconds
        passwordLockoutIdentifier = identifier?.trim()?.lowercase()
        viewModelScope.launch {
            _messages.emit(UiMessage("Demasiados intentos de contraseña. Intenta de nuevo en ${formatLockoutTime(passwordLockoutSeconds)}", true))
        }
        passwordLockoutJob = viewModelScope.launch {
            while (passwordLockoutSeconds > 0) {
                delay(1000)
                passwordLockoutSeconds--
            }
            passwordLockoutIdentifier = null
            _messages.emit(UiMessage("Bloqueo de contraseña finalizado", true))
        }
    }

    fun startPinLoginLockout(seconds: Int, identifier: String?) {
        pinLoginLockoutJob?.cancel()
        pinLoginLockoutSeconds = seconds
        pinLoginLockoutIdentifier = identifier?.trim()?.lowercase()
        viewModelScope.launch {
            _messages.emit(UiMessage("Demasiados intentos de PIN de inicio de sesión. Intenta de nuevo en ${formatLockoutTime(pinLoginLockoutSeconds)}", true))
        }
        pinLoginLockoutJob = viewModelScope.launch {
            while (pinLoginLockoutSeconds > 0) {
                delay(1000)
                pinLoginLockoutSeconds--
            }
            pinLoginLockoutIdentifier = null
            _messages.emit(UiMessage("Bloqueo de PIN finalizado", true))
        }
    }

    fun notifyPasswordLockout() {
        if (passwordLockoutSeconds > 0) {
            viewModelScope.launch {
                _messages.emit(UiMessage("Demasiados intentos de contraseña. Intenta de nuevo en ${formatLockoutTime(passwordLockoutSeconds)}", true))
            }
        }
    }

    fun notifyPinLoginLockout() {
        if (pinLoginLockoutSeconds > 0) {
            viewModelScope.launch {
                _messages.emit(UiMessage("Demasiados intentos de PIN de inicio de sesión. Intenta de nuevo en ${formatLockoutTime(pinLoginLockoutSeconds)}", true))
            }
        }
    }

    // ---------- PIN (bloqueo local de la app) ----------
    var locked by mutableStateOf(session != null && hasPin(session?.account))
        private set
    var needsPinSetup by mutableStateOf(false)
        private set
    var pinEnabled by mutableStateOf(hasPin(session?.account))
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    /** Aviso que se muestra en la pantalla de login (p. ej. sesión expirada). */
    var notice by mutableStateOf<String?>(null)
        private set
    fun clearNotice() { notice = null }

    private var api: WalletApi = buildApi(host) { token }

    private val accountFlow = MutableStateFlow(session?.account ?: "")

    private val _balance = MutableStateFlow(prefs.getFloat("balance", 0f).toDouble())
    val balance: StateFlow<Double> = _balance
    val connected: StateFlow<Boolean> = socket.connected

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    // Historial reactivo: Room -> Flow (en IO) -> UI (Main). Solo del usuario con sesión.
    val history: StateFlow<List<TransactionEntity>> = accountFlow
        .flatMapLatest { dao.observeAll(it) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Eventos del WebSocket: se procesan en IO y el StateFlow actualiza la UI en Main.
        viewModelScope.launch(Dispatchers.IO) {
            socket.events.collect { ev ->
                when (ev.type) {
                    "TRANSFER_IN" -> {
                        val owner = accountFlow.value.ifBlank { session?.account.orEmpty() }
                        if (owner.isNotBlank()) {
                            dao.insert(
                                TransactionEntity(
                                    owner = owner,
                                    type = TxType.TRANSFER_IN.name,
                                    amount = ev.amount,
                                    counterpart = ev.from,
                                    category = null,
                                    detail = null,
                                    reference = ev.reference,
                                    timestamp = if (ev.timestamp > 0) ev.timestamp else System.currentTimeMillis(),
                                )
                            )
                            setBalance(ev.balance)
                            _messages.emit(UiMessage("Recibiste ${money(ev.amount)} de ${ev.from}", true))
                        }
                    }
                    "CONNECTED" -> setBalance(ev.balance)
                    "SESSION_REPLACED" -> {
                        withContext(Dispatchers.Main) {
                            logout(ev.message ?: "Tu sesión se cerró porque tu cuenta fue iniciada en otro dispositivo. Si no reconoces esta actividad, cambia tu contraseña.")
                        }
                    }
                }
            }
        }
        if (session != null) connectSession()
    }

    private fun setBalance(value: Double) {
        _balance.value = value
        prefs.edit().putFloat("balance", value.toFloat()).apply()
    }

    private fun connectSession() {
        val t = token ?: return
        socket.start(host, t)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val meRes = api.me()
                meRes.user?.let { u ->
                    setBalance(u.balance)
                    if (u.hasPin) {
                        pinEnabled = true
                    }
                }

                val movRes = api.getMovements()
                val owner = accountFlow.value.ifBlank { session?.account.orEmpty() }
                if (owner.isNotBlank()) {
                    movRes.movements.forEach { m ->
                        dao.insert(
                            TransactionEntity(
                                owner = owner,
                                type = m.type,
                                amount = m.amount,
                                counterpart = m.counterpart,
                                category = if (m.type == "SERVICE") "Servicio Básico" else null,
                                detail = if (m.type == "SERVICE") m.counterpart else null,
                                reference = m.reference,
                                timestamp = m.timestamp,
                            )
                        )
                    }
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) withContext(Dispatchers.Main) { logout("Tu sesión expiró") }
            } catch (_: Exception) { /* sin red: se usa el saldo y movimientos guardados en Room */ }
        }
    }

    fun saveHost(newHost: String) {
        host = newHost.trim().ifBlank { host }
        prefs.edit().putString("host", host).apply()
        api = buildApi(host) { token }
        if (session != null) connectSession()
    }

    private fun handleAuthError(e: Throwable, isPin: Boolean, identifier: String? = null) {
        if (e is retrofit2.HttpException && e.code() == 429) {
            val retryHeader = e.response()?.headers()?.get("Retry-After")?.toIntOrNull()
            val errorBodyStr = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val jsonRes = try {
                com.google.gson.Gson().fromJson(errorBodyStr, AuthResponse::class.java)
            } catch (_: Exception) { null }

            val seconds = retryHeader ?: jsonRes?.retryAfter ?: 60
            val method = jsonRes?.method ?: if (isPin) "PIN" else "PASSWORD"

            if (method.equals("PIN", ignoreCase = true)) {
                startPinLoginLockout(seconds, identifier)
            } else {
                startPasswordLockout(seconds, identifier)
            }
        } else {
            viewModelScope.launch { _messages.emit(UiMessage(e.friendlyMessage(), false)) }
        }
    }

    // ---------- Control de Sesión Única en Múltiples Dispositivos ----------
    var showActiveSessionDialog by mutableStateOf(false)
        private set
    var activeSessionMessage by mutableStateOf("")
        private set
    private var pendingAuthAction: (() -> Unit)? = null

    fun confirmForceLogin() {
        showActiveSessionDialog = false
        val action = pendingAuthAction
        pendingAuthAction = null
        action?.invoke()
    }

    fun cancelForceLogin() {
        showActiveSessionDialog = false
        pendingAuthAction = null
    }

    // ---------- Autenticación ----------
    fun authenticate(register: Boolean, name: String, email: String, password: String, pin: String? = null, force: Boolean = false) {
        val cleanEmail = email.trim()
        if (!register && isPasswordLocked(cleanEmail)) {
            notifyPasswordLockout()
            return
        }
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val cleanPin = pin?.trim()?.takeIf { it.length == 4 }
                val res = withContext(Dispatchers.IO) {
                    if (register) api.register(AuthReq(name = name.trim(), email = cleanEmail, password = password, pin = cleanPin))
                    else api.login(AuthReq(email = cleanEmail, password = password, force = if (force) true else null))
                }
                if (res.activeSession && !res.ok) {
                    activeSessionMessage = res.message ?: "Existe una sesión activa en otro dispositivo. Si continúas, se cerrará la sesión del dispositivo anterior."
                    pendingAuthAction = {
                        authenticate(register, name, email, password, pin, force = true)
                    }
                    showActiveSessionDialog = true
                    return@launch
                }
                val u = res.user
                val tok = res.token
                if (u == null || tok == null) {
                    _messages.emit(UiMessage(res.message ?: "Error al iniciar sesión", false))
                    return@launch
                }
                token = tok
                prefs.edit().putString("token", tok).putString("name", u.name)
                    .putString("email", u.email).putString("account", u.account)
                    .putString("last_email", u.email).putString("last_account", u.account)
                    .putString("last_name", u.name).apply()

                lastUserEmail = u.email
                lastUserName = u.name

                if (cleanPin != null) {
                    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    prefs.edit()
                        .putString("pin_salt_${u.account}", Base64.encodeToString(salt, Base64.NO_WRAP))
                        .putString("pin_hash_${u.account}", Base64.encodeToString(hashPin(cleanPin, salt), Base64.NO_WRAP))
                        .putInt("pin_fails_${u.account}", 0).apply()
                }

                setBalance(u.balance)
                accountFlow.value = u.account
                session = Session(tok, u.name, u.email, u.account)
                pinEnabled = u.hasPin || hasPin(u.account)
                locked = false
                needsPinSetup = false
                connectSession()
            } catch (e: Exception) {
                handleAuthError(e, isPin = false, identifier = cleanEmail)
            } finally {
                _busy.value = false
            }
        }
    }

    fun authenticateWithPin(pin: String, force: Boolean = false) {
        val target = lastUserEmail ?: prefs.getString("last_account", null)
        if (target.isNullOrEmpty()) {
            viewModelScope.launch {
                _messages.emit(UiMessage("Debes ingresar con contraseña por primera vez", false))
            }
            return
        }
        if (isPinLoginLocked(target)) {
            notifyPinLoginLockout()
            return
        }
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val cleanPin = pin.trim()
                val res = withContext(Dispatchers.IO) {
                    api.loginPin(AuthReq(email = target, pin = cleanPin, force = if (force) true else null))
                }
                if (res.activeSession && !res.ok) {
                    activeSessionMessage = res.message ?: "Existe una sesión activa en otro dispositivo. Si continúas, se cerrará la sesión del dispositivo anterior."
                    pendingAuthAction = {
                        authenticateWithPin(pin, force = true)
                    }
                    showActiveSessionDialog = true
                    return@launch
                }
                val u = res.user
                val tok = res.token
                if (u == null || tok == null) {
                    _messages.emit(UiMessage(res.message ?: "Error al iniciar sesión", false))
                    return@launch
                }
                token = tok
                prefs.edit().putString("token", tok).putString("name", u.name)
                    .putString("email", u.email).putString("account", u.account)
                    .putString("last_email", u.email).putString("last_account", u.account)
                    .putString("last_name", u.name).apply()

                lastUserEmail = u.email
                lastUserName = u.name

                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                prefs.edit()
                    .putString("pin_salt_${u.account}", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("pin_hash_${u.account}", Base64.encodeToString(hashPin(cleanPin, salt), Base64.NO_WRAP))
                    .putInt("pin_fails_${u.account}", 0).apply()

                setBalance(u.balance)
                accountFlow.value = u.account
                session = Session(tok, u.name, u.email, u.account)
                pinEnabled = true
                locked = false
                needsPinSetup = false
                connectSession()
                needsPinSetup = !pinEnabled
                connectSession()
            } catch (e: Exception) {
                handleAuthError(e, isPin = true, identifier = target)
            } finally {
                _busy.value = false
            }
        }
    }


    fun logout(reason: String? = null) {

        // Guardamos el token antes de eliminarlo.
        val oldToken = token
        val currentApi = api

        // Detener las conexiones WebSocket.
        socket.stop()

        // Eliminar la sesión local inmediatamente.
        token = null

        prefs.edit()
            .remove("token")
            .remove("name")
            .remove("email")
            .remove("account")
            .remove("balance")
            .apply()

        // Restablecer el estado de la aplicación.
        _balance.value = 0.0
        accountFlow.value = ""
        locked = false
        needsPinSetup = false
        pinEnabled = false
        pinError = null
        session = null

        if (reason != null) {
            notice = reason
        }

        // Solicitar al backend la revocación del token.
        if (!oldToken.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val response = currentApi.logout(
                        authorization = "Bearer $oldToken"
                    )

                    if (!response.isSuccessful &&
                        response.code() != 401
                    ) {
                        android.util.Log.w(
                            "WalletSecurity",
                            "No se confirmó el cierre remoto: HTTP ${response.code()}"
                        )
                    }

                } catch (e: Exception) {
                    // El cierre local se mantiene aunque
                    // el servidor no esté disponible.
                    android.util.Log.w(
                        "WalletSecurity",
                        "No se pudo confirmar la revocación remota",
                        e
                    )
                }
            }
        }
    }

    // ---------- PIN ----------
    private fun hasPin(account: String?) = account != null && prefs.contains("pin_hash_$account")

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    fun startPinSetup() { needsPinSetup = true }
    fun skipPinSetup() { needsPinSetup = false }

    fun setupPin(pin: String, password: String = "", onSuccess: () -> Unit = {}) {
        val acct = session?.account ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    api.setPin(AuthReq(password = password.ifBlank { null }, pin = pin))
                }
                if (res.ok) {
                    withContext(Dispatchers.IO) {
                        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                        prefs.edit()
                            .putString("pin_salt_$acct", Base64.encodeToString(salt, Base64.NO_WRAP))
                            .putString("pin_hash_$acct", Base64.encodeToString(hashPin(pin, salt), Base64.NO_WRAP))
                            .putInt("pin_fails_$acct", 0).apply()
                    }
                    pinEnabled = true
                    needsPinSetup = false
                    _messages.emit(UiMessage(res.message ?: "PIN guardado correctamente", true))
                    onSuccess()
                }
            } catch (e: Exception) {
                _messages.emit(UiMessage(e.friendlyMessage(), false))
            } finally {
                _busy.value = false
            }
        }
    }

    fun unlockWithPin(pin: String) {
        val acct = session?.account ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val salt = Base64.decode(prefs.getString("pin_salt_$acct", "") ?: "", Base64.NO_WRAP)
                val saved = Base64.decode(prefs.getString("pin_hash_$acct", "") ?: "", Base64.NO_WRAP)
                MessageDigest.isEqual(saved, hashPin(pin, salt))
            }
            if (ok) {
                prefs.edit().putInt("pin_fails_$acct", 0).apply()
                pinError = null
                locked = false
            } else {
                val fails = prefs.getInt("pin_fails_$acct", 0) + 1
                prefs.edit().putInt("pin_fails_$acct", fails).apply()
                if (fails >= MAX_PIN_FAILS) forgotPin("Demasiados intentos. Inicia sesión con tu contraseña")
                else pinError = "PIN incorrecto. Te quedan ${MAX_PIN_FAILS - fails} intentos"
            }
        }
    }

    fun removePin(password: String, onSuccess: () -> Unit = {}) {
        val acct = session?.account ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    api.removePin(AuthReq(password = password))
                }
                if (res.ok) {
                    clearPinData(acct)
                    pinEnabled = false
                    _messages.emit(UiMessage(res.message ?: "PIN eliminado correctamente", true))
                    onSuccess()
                }
            } catch (e: Exception) {
                _messages.emit(UiMessage(e.friendlyMessage(), false))
            } finally {
                _busy.value = false
            }
        }
    }

    /** Olvidó el PIN o se pasó de intentos: se borra el PIN y se pide la contraseña. */
    fun forgotPin(reason: String? = null) {
        session?.account?.let { clearPinData(it) }
        logout(reason)
    }

    private fun clearPinData(acct: String) {
        prefs.edit().remove("pin_hash_$acct").remove("pin_salt_$acct").remove("pin_fails_$acct").apply()
    }

    companion object { const val MAX_PIN_FAILS = 5 }

    // ---------- Operaciones (REST en IO, persistencia en IO, resultado a Main) ----------
    private fun run(
        onOk: String,
        call: suspend () -> OpResponse,
        toTx: (OpResponse, String) -> TransactionEntity,
    ) {
        if (_busy.value) return
        val owner = session?.account ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    val r = call()
                    dao.insert(toTx(r, owner))
                    r
                }
                setBalance(res.balance)
                _messages.emit(UiMessage(onOk, true))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) logout(e.friendlyMessage())
                else _messages.emit(UiMessage(e.friendlyMessage(), false))
            } catch (e: Exception) {
                _messages.emit(UiMessage(e.friendlyMessage(), false))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun tx(owner: String, type: TxType, amount: Double, counterpart: String? = null,
                   category: String? = null, detail: String? = null, ref: String? = null) =
        TransactionEntity(
            owner = owner, type = type.name, amount = amount, counterpart = counterpart,
            category = category, detail = detail, reference = ref,
            timestamp = System.currentTimeMillis(),
        )

    fun validateAccount(
        target: String,
        onSuccess: (account: String, name: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    api.validateAccount(AccountValidateReq(to = target.trim()))
                }
                if (res.ok && !res.account.isNullOrBlank() && !res.name.isNullOrBlank()) {
                    onSuccess(res.account, res.name)
                } else {
                    onError(res.message ?: "La cuenta o correo de destino no existe")
                }
            } catch (e: Exception) {
                onError(e.friendlyMessage())
            } finally {
                _busy.value = false
            }
        }
    }

    fun executeTransfer(
        to: String,
        amount: Double,
        recipientName: String,
        onSuccess: (OpResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_busy.value) return
        val owner = session?.account ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    val r = api.transfer(TransferReq(to.trim(), amount))
                    dao.insert(
                        TransactionEntity(
                            owner = owner,
                            type = TxType.TRANSFER_OUT.name,
                            amount = amount,
                            counterpart = recipientName,
                            category = null,
                            detail = null,
                            reference = r.reference,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                    r
                }
                setBalance(res.balance)
                _messages.emit(UiMessage("Transferencia realizada con éxito", true))
                onSuccess(res)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) logout(e.friendlyMessage())
                val msg = e.friendlyMessage()
                onError(msg)
                _messages.emit(UiMessage(msg, false))
            } catch (e: Exception) {
                val msg = e.friendlyMessage()
                onError(msg)
                _messages.emit(UiMessage(msg, false))
            } finally {
                _busy.value = false
            }
        }
    }

    fun deposit(amount: Double) = run("Depósito de ${money(amount)} exitoso",
        { api.deposit(AmountReq(amount)) }) { r, o -> tx(o, TxType.DEPOSIT, amount, ref = r.reference) }

    fun withdraw(amount: Double) = run("Retiro de ${money(amount)} exitoso",
        { api.withdraw(AmountReq(amount)) }) { r, o -> tx(o, TxType.WITHDRAW, amount, ref = r.reference) }

    fun transfer(to: String, amount: Double) = run("Enviaste ${money(amount)} a ${to.uppercase()}",
        { api.transfer(TransferReq(to, amount)) }) { r, o ->
        tx(o, TxType.TRANSFER_OUT, amount, counterpart = to.uppercase(), ref = r.reference)
    }

    fun payService(contract: String, amount: Double) = run("Servicio pagado: ${money(amount)}",
        { api.payService(ServiceReq(contract.trim().uppercase(), amount)) }) { r, o ->
        tx(o, TxType.SERVICE, amount, counterpart = contract.trim().uppercase(),
            category = "Servicio Básico", detail = r.service, ref = r.reference)
    }
}

data class UiMessage(val text: String, val success: Boolean)

fun money(v: Double): String = "$" + String.format(java.util.Locale.US, "%,.2f", v)
