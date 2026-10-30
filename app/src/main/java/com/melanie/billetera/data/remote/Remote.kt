package com.melanie.billetera.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// ---------- REST ----------
data class AuthReq(
    val name: String? = null,
    val email: String? = null,
    val account: String? = null,
    val password: String? = null,
    val pin: String? = null,
    val force: Boolean? = null,
)
data class UserDto(
    val name: String,
    val email: String,
    val account: String,
    val balance: Double,
    val hasPin: Boolean = false,
)
data class AuthResponse(
    val ok: Boolean = true,
    val message: String? = null,
    val token: String? = null,
    val user: UserDto? = null,
    val retryAfter: Int? = null,
    val method: String? = null,
    val activeSession: Boolean = false,
)

data class MovementDto(
    val type: String,
    val amount: Double,
    val counterpart: String?,
    val reference: String?,
    val timestamp: Long,
)
data class MovementsResponse(
    val ok: Boolean = true,
    val movements: List<MovementDto> = emptyList(),
)

data class AmountReq(val amount: Double)
data class TransferReq(val to: String, val amount: Double)
data class ServiceReq(val contract: String, val amount: Double)
data class AccountValidateReq(val to: String)

data class AccountValidateResponse(
    val ok: Boolean = true,
    val message: String? = null,
    val account: String? = null,
    val name: String? = null,
)

data class OpResponse(
    val ok: Boolean = true,
    val message: String? = null,
    val balance: Double = 0.0,
    val reference: String? = null,
    val service: String? = null,
    val timestamp: Long = 0,
)

interface WalletApi {
    @POST("api/auth/register") suspend fun register(@Body b: AuthReq): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body b: AuthReq): AuthResponse
    @POST("api/auth/login-pin") suspend fun loginPin(@Body b: AuthReq): AuthResponse
    @POST("api/auth/set-pin") suspend fun setPin(@Body b: AuthReq): OpResponse
    @POST("api/auth/remove-pin") suspend fun removePin(@Body b: AuthReq): OpResponse
    @GET("api/me") suspend fun me(): AuthResponse
    @GET("api/movements") suspend fun getMovements(): MovementsResponse
    @POST("api/auth/logout") suspend fun logout(@Header("Authorization") authorization: String): retrofit2.Response<Void>
    @POST("api/deposit") suspend fun deposit(@Body b: AmountReq): OpResponse
    @POST("api/withdraw") suspend fun withdraw(@Body b: AmountReq): OpResponse
    @POST("api/account/validate") suspend fun validateAccount(@Body b: AccountValidateReq): AccountValidateResponse
    @POST("api/transfer") suspend fun transfer(@Body b: TransferReq): OpResponse
    @POST("api/services/pay") suspend fun payService(@Body b: ServiceReq): OpResponse
}

fun normalizeBase(host: String): String {
    var h = host.trim()
    if (h.isBlank()) return "https://desktop-cae8044.tail338a50.ts.net/"
    if (!h.startsWith("http://") && !h.startsWith("https://")) {
        h = if (h.startsWith("10.0.2.2") || h.startsWith("127.0.0.1") || h.startsWith("localhost") || h.startsWith("192.168.")) {
            "http://$h"
        } else {
            "https://$h"
        }
    }
    if (!h.endsWith("/")) h += "/"
    return h
}

fun buildWsUrl(host: String): String {
    val base = normalizeBase(host)
    return if (base.startsWith("https://")) {
        base.replaceFirst("https://", "wss://") + "ws"
    } else {
        base.replaceFirst("http://", "ws://") + "ws"
    }
}

fun buildApi(host: String, token: () -> String?): WalletApi {
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
            token()?.let { req.header("Authorization", "Bearer $it") }
            chain.proceed(req.build())
        }
        .build()
    return Retrofit.Builder()
        .baseUrl(normalizeBase(host))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WalletApi::class.java)
}

/** Convierte cualquier error HTTP / de red en un mensaje legible. */
fun Throwable.friendlyMessage(): String = when (this) {
    is retrofit2.HttpException -> {
        val bodyStr = try { response()?.errorBody()?.string() } catch (_: Exception) { null }
        val parsedMsg = if (!bodyStr.isNullOrBlank()) {
            try {
                Gson().fromJson(bodyStr, OpResponse::class.java)?.message
            } catch (_: Exception) { null }
        } else null
        parsedMsg ?: when (code()) {
            404 -> "La cuenta de destino no existe"
            400 -> "No puedes transferirte a ti mismo"
            409 -> "Saldo insuficiente"
            else -> "Error del servidor (${code()})"
        }
    }
    is java.io.IOException -> "No se pudo conectar con el servidor"
    else -> message ?: "Error inesperado"
}

// ---------- WebSocket ----------
data class SocketEvent(
    val type: String? = null,
    val from: String? = null,
    val amount: Double = 0.0,
    val balance: Double = 0.0,
    val timestamp: Long = 0,
    val reference: String? = null,
    val message: String? = null,
)

/**
 * Mantiene el túnel WebSocket abierto en Dispatchers.IO y se reconecta solo.
 */
class WalletSocket(private val scope: CoroutineScope) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SocketEvent> = _events

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private var job: Job? = null

    fun start(host: String, token: String) {
        stop()
        val wsUrl = buildWsUrl(host)
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val closed = CompletableDeferred<Unit>()
                val request = Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                val ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { _connected.value = true }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching { gson.fromJson(text, SocketEvent::class.java) }
                            .getOrNull()?.let { ev ->
                                scope.launch(Dispatchers.IO) {
                                    _events.emit(ev)
                                }
                            }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        _connected.value = false; closed.complete(Unit)
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _connected.value = false; closed.complete(Unit)
                    }
                })
                try {
                    closed.await()
                } finally {
                    ws.cancel()
                }
                delay(3000) // reconexión
            }
        }
    }

    fun stop() {
        job?.cancel()
        _connected.value = false
    }
}
