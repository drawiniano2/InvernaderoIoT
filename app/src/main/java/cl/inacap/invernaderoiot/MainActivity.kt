package cl.inacap.invernaderoiot

import android.os.Bundle
import android.util.Patterns

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import cl.inacap.invernaderoiot.ui.theme.InvernaderoIoTTheme

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.*

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================
// INVERNADERO IoT - VERSION 7.23 CORREGIDA
// PARTE 1 DE 4
// =====================================================

// =====================================================
// CONFIGURACION DE FIREBASE
// =====================================================

private const val FIREBASE_DATABASE_URL =
    "https://invernaderoiot-1f128-default-rtdb.firebaseio.com"

// =====================================================
// PALETA BAUHAUS
// =====================================================

private val AZUL = Color(0xFF19375E)
private val VERDE = Color(0xFF168A80)
private val AMARILLO = Color(0xFFE2AE4B)
private val ROJO = Color(0xFFC73532)

// =====================================================
// ROLES DEL SISTEMA
// =====================================================

private val ROLES_PERMITIDOS = setOf(
    "administrador",
    "operador",
    "observador"
)

private fun rolLegible(rol: String): String {
    return when (rol) {
        "administrador" -> "Administrador"
        "operador" -> "Operador"
        "observador" -> "Observador"
        else -> rol
    }
}

private fun puedeControlarVentilador(rol: String): Boolean {
    return rol == "administrador" || rol == "operador"
}

// =====================================================
// FUNCIONES DE FECHA
// =====================================================

private fun formatoFecha(millis: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy HH:mm:ss",
        Locale.getDefault()
    ).format(Date(millis))
}

private fun fechaLegible(millis: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    ).format(Date(millis))
}

// =====================================================
// MAIN ACTIVITY
// =====================================================

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        setContent {
            InvernaderoIoTTheme {
                InvernaderoApp(auth)
            }
        }
    }
}

// =====================================================
// BOTON BAUHAUS ANIMADO
// =====================================================
// Conserva la firma utilizada en la version 7.22.
// Estado normal: fondo translucido.
// Estado presionado: color solido.
// =====================================================

@Composable
fun BotonColorAnimado(
    texto: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    enabled: Boolean = true
) {

    val interaccion = remember {
        MutableInteractionSource()
    }

    val presionado by
    interaccion.collectIsPressedAsState()

    val fondo by animateColorAsState(
        targetValue =
            if (presionado) {
                color
            } else {
                color.copy(alpha = 0.12f)
            },
        animationSpec = tween(180),
        label = "fondoBoton"
    )

    val textoColor by animateColorAsState(
        targetValue =
            if (presionado) {
                Color.White
            } else {
                color
            },
        animationSpec = tween(180),
        label = "textoBoton"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        interactionSource = interaccion,
        shape = RoundedCornerShape(12.dp),

        contentPadding = PaddingValues(
            horizontal =
                if (compacto) 12.dp else 16.dp,
            vertical =
                if (compacto) 8.dp else 12.dp
        ),

        colors = ButtonDefaults.buttonColors(
            containerColor = fondo,
            contentColor = textoColor,
            disabledContainerColor =
                color.copy(alpha = 0.08f),
            disabledContentColor =
                color.copy(alpha = 0.5f)
        ),

        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {

        Text(
            texto,
            fontWeight = FontWeight.Bold,
            style =
                if (compacto) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                }
        )
    }
}

// =====================================================
// CONTROL DE AUTENTICACION Y ROLES
// =====================================================

@Composable
fun InvernaderoApp(auth: FirebaseAuth) {

    val database = remember {
        FirebaseDatabase.getInstance(
            FIREBASE_DATABASE_URL
        )
    }

    var usuario by remember {
        mutableStateOf(auth.currentUser)
    }

    var rol by remember {
        mutableStateOf<String?>(null)
    }

    var verificando by remember {
        mutableStateOf(usuario != null)
    }

    var registrando by remember {
        mutableStateOf(false)
    }

    var mensajeAcceso by remember {
        mutableStateOf("")
    }

    // =================================================
    // OBSERVADOR DE AUTENTICACION FIREBASE
    // =================================================

    DisposableEffect(auth) {

        val listener = FirebaseAuth.AuthStateListener {
            usuario = it.currentUser
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // =================================================
    // VERIFICACION DE PERMISOS EN FIREBASE
    // =================================================

    DisposableEffect(
        usuario?.uid,
        registrando,
        database
    ) {

        val uid = usuario?.uid

        rol = null

        if (uid == null || registrando) {

            verificando = false

            onDispose { }

        } else {

            verificando = true

            var vigente = true
            var consultandoSolicitud = false

            val referencia = database.reference
                .child("usuarios")
                .child(uid)

            // =========================================
            // DENEGAR ACCESO NO AUTORIZADO
            // =========================================

            fun denegar(mensaje: String) {

                if (
                    !vigente ||
                    auth.currentUser?.uid != uid
                ) {
                    return
                }

                mensajeAcceso = mensaje
                rol = null
                verificando = false

                auth.signOut()
                usuario = null
            }

            // =========================================
            // OBSERVADOR DEL PERFIL
            // =========================================

            val listener = object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    if (
                        !vigente ||
                        auth.currentUser?.uid != uid
                    ) {
                        return
                    }

                    val activo =
                        snapshot.child("activo").value == true

                    val perfil =
                        snapshot.child("rol").value as? String

                    if (
                        activo &&
                        perfil != null &&
                        perfil in ROLES_PERMITIDOS
                    ) {

                        consultandoSolicitud = false

                        rol = perfil
                        verificando = false
                        mensajeAcceso = ""

                    } else if (!consultandoSolicitud) {

                        rol = null
                        verificando = true
                        consultandoSolicitud = true

                        // Consultar solicitud de registro.

                        database.reference
                            .child("solicitudes_registro")
                            .child(uid)
                            .get()
                            .addOnCompleteListener { tarea ->

                                if (
                                    vigente &&
                                    auth.currentUser?.uid == uid &&
                                    rol == null
                                ) {

                                    val estado =
                                        tarea.resultOrNull()
                                            ?.child("estado")
                                            ?.value as? String

                                    denegar(
                                        when (estado) {

                                            "pendiente" ->
                                                "Tu solicitud está pendiente de aprobación."

                                            "rechazada" ->
                                                "Tu solicitud fue rechazada. Contacta al administrador."

                                            else ->
                                                "Cuenta sin acceso autorizado. Si el registro quedó incompleto, usa CREAR CUENTA con el mismo correo y contraseña."
                                        }
                                    )
                                }
                            }
                    }
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    denegar(
                        "No fue posible verificar los permisos: ${error.message}"
                    )
                }
            }

            referencia.addValueEventListener(listener)

            onDispose {
                vigente = false
                referencia.removeEventListener(listener)
            }
        }
    }

    // =================================================
    // NAVEGACION SEGUN ESTADO DE AUTENTICACION
    // =================================================

    when {

        // LOGIN O REGISTRO

        usuario == null || registrando -> {

            PantallaLogin(
                auth = auth,
                database = database,
                mensajeExterno = mensajeAcceso,

                onRegistroIniciado = {
                    registrando = true
                    mensajeAcceso = ""
                },

                onRegistroFinalizado = {
                    auth.signOut()
                    usuario = null
                    rol = null
                    verificando = false
                    registrando = false
                },

                onLoginCorrecto = {
                    mensajeAcceso = ""
                    verificando = true
                    usuario = auth.currentUser
                }
            )
        }

        // VERIFICACION DE PERMISOS

        verificando || rol == null -> {

            Surface(
                modifier = Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        LogoInvernadero(110)

                        Spacer(
                            Modifier.height(16.dp)
                        )

                        CircularProgressIndicator(
                            color = VERDE
                        )

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Text(
                            "Verificando permisos..."
                        )
                    }
                }
            }
        }

        // PANEL PRINCIPAL

        else -> {

            PantallaInicio(
                correo =
                    usuario?.email ?: "Usuario",

                rol = rol.orEmpty(),

                uidActual =
                    usuario?.uid.orEmpty(),

                onCerrarSesion = {

                    auth.signOut()

                    usuario = null
                    rol = null
                    mensajeAcceso = ""
                }
            )
        }
    }
}

// =====================================================
// FUNCION AUXILIAR DE FIREBASE
// =====================================================

private fun <T> Task<T>.resultOrNull(): T? {
    return if (isSuccessful) result else null
}

// =====================================================
// LOGOTIPO ORIGINAL DEL PROYECTO
// =====================================================

@Composable
fun LogoInvernadero(
    tamano: Int = 100
) {

    Image(
        painter = painterResource(
            R.drawable.logo_invernadero_iot
        ),

        contentDescription = "Invernadero IoT",

        modifier = Modifier
            .size(tamano.dp)
            .clip(CircleShape),

        contentScale = ContentScale.Crop
    )
}

// =====================================================
// FIN DE LA PARTE 1 DE 4
// CONTINUA: LOGIN Y REGISTRO DE CUENTAS
// =====================================================

// =====================================================
// INVERNADERO IoT - VERSION 7.23 CORREGIDA
// PARTE 2 DE 4
// LOGIN, REGISTRO Y COMPONENTES COMPARTIDOS
// =====================================================

// =====================================================
// PANTALLA DE INICIO DE SESION
// =====================================================

@Composable
fun PantallaLogin(
    auth: FirebaseAuth,
    database: FirebaseDatabase,
    mensajeExterno: String,
    onRegistroIniciado: () -> Unit,
    onRegistroFinalizado: () -> Unit,
    onLoginCorrecto: () -> Unit
) {
    var rolSeleccionado by remember {
        mutableStateOf("administrador")
    }

    var mostrarRegistro by remember {
        mutableStateOf(false)
    }

    var correo by remember {
        mutableStateOf("")
    }

    var contrasena by remember {
        mutableStateOf("")
    }

    var mostrarContrasena by remember {
        mutableStateOf(false)
    }

    var cargando by remember {
        mutableStateOf(false)
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoInvernadero(120)

            Spacer(Modifier.height(8.dp))

            Text(
                "INVERNADERO IoT",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AZUL
            )

            Text(
                "Sistema de Monitoreo y Control",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(20.dp))

            if (mostrarRegistro) {

                FormularioRegistro(
                    auth = auth,
                    database = database,
                    onRegistroIniciado = onRegistroIniciado,
                    onRegistroFinalizado = onRegistroFinalizado,
                    onVolver = {
                        mostrarRegistro = false
                    }
                )

            } else {

                Text(
                    "Selecciona tu perfil",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(
                            "administrador",
                            "Admin",
                            "🛡️"
                        ),
                        Triple(
                            "operador",
                            "Operador",
                            "⚙️"
                        ),
                        Triple(
                            "observador",
                            "Observador",
                            "👁️"
                        )
                    ).forEach { perfil ->

                        val seleccionado =
                            rolSeleccionado == perfil.first

                        OutlinedCard(
                            onClick = {
                                if (!cargando) {
                                    rolSeleccionado = perfil.first
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor =
                                    if (seleccionado) {
                                        AZUL.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (seleccionado) {
                                    AZUL
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                Text(
                                    perfil.third,
                                    style =
                                        MaterialTheme.typography.headlineSmall
                                )

                                Spacer(Modifier.height(5.dp))

                                Text(
                                    perfil.second,
                                    style =
                                        MaterialTheme.typography.labelSmall,
                                    fontWeight =
                                        if (seleccionado) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Perfil seleccionado: ${rolSeleccionado.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AZUL
                )

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = {
                        correo = it
                        mensaje = ""
                    },
                    label = {
                        Text("Correo electrónico")
                    },
                    singleLine = true,
                    enabled = !cargando,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = {
                        contrasena = it
                        mensaje = ""
                    },
                    label = {
                        Text("Contraseña")
                    },
                    singleLine = true,
                    enabled = !cargando,
                    visualTransformation =
                        if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                mostrarContrasena =
                                    !mostrarContrasena
                            }
                        ) {
                            Icon(
                                imageVector =
                                    if (mostrarContrasena) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                contentDescription =
                                    "Mostrar u ocultar contraseña"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                BotonColorAnimado(
                    texto = "INICIAR SESIÓN",
                    color = AZUL,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !cargando,
                    onClick = {
                        if (
                            correo.isBlank() ||
                            contrasena.isBlank()
                        ) {
                            mensaje = "Completa todos los campos."
                        } else {
                            cargando = true
                            mensaje = ""

                            auth.signInWithEmailAndPassword(
                                correo.trim(),
                                contrasena
                            ).addOnCompleteListener { tarea ->

                                cargando = false

                                if (tarea.isSuccessful) {
                                    contrasena = ""
                                    onLoginCorrecto()
                                } else {
                                    mensaje =
                                        "Credenciales incorrectas o acceso no disponible."
                                }
                            }
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                BotonColorAnimado(
                    texto = "CREAR CUENTA",
                    color = VERDE,
                    onClick = {
                        mostrarRegistro = true
                        mensaje = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !cargando
                )

                if (cargando) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(color = VERDE)
                }

                if (
                    mensaje.isNotBlank() ||
                    mensajeExterno.isNotBlank()
                ) {
                    Spacer(Modifier.height(12.dp))

                    Aviso(
                        mensaje.ifBlank {
                            mensajeExterno
                        },
                        ROJO
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Los permisos reales se verifican con Firebase.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Proyecto Académico - INACAP",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// =====================================================
// FORMULARIO DE REGISTRO
// =====================================================

@Composable
fun FormularioRegistro(
    auth: FirebaseAuth,
    database: FirebaseDatabase,
    onRegistroIniciado: () -> Unit,
    onRegistroFinalizado: () -> Unit,
    onVolver: () -> Unit
) {
    var nombre by remember {
        mutableStateOf("")
    }

    var correo by remember {
        mutableStateOf("")
    }

    var contrasena by remember {
        mutableStateOf("")
    }

    var confirmar by remember {
        mutableStateOf("")
    }

    var rolSolicitado by remember {
        mutableStateOf("observador")
    }

    var cargando by remember {
        mutableStateOf(false)
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    var completado by remember {
        mutableStateOf(false)
    }

    BackHandler(enabled = cargando) { }

    // =================================================
    // FINALIZAR OPERACION
    // =================================================

    fun terminar(
        texto: String,
        exito: Boolean
    ) {
        onRegistroFinalizado()

        cargando = false
        completado = exito
        contrasena = ""
        confirmar = ""
        mensaje = texto
    }

    // =================================================
    // GUARDAR SOLICITUD EN FIREBASE
    // =================================================

    fun guardarSolicitud() {

        val actual = auth.currentUser

        if (actual == null) {
            terminar(
                "No se pudo autenticar la cuenta. Intenta nuevamente.",
                false
            )
            return
        }

        val uid = actual.uid

        val solicitud = database.reference
            .child("solicitudes_registro")
            .child(uid)

        database.reference
            .child("usuarios")
            .child(uid)
            .get()
            .addOnCompleteListener { perfil ->

                if (!perfil.isSuccessful) {

                    terminar(
                        "No se pudo verificar la cuenta. Reintenta con el mismo correo y contraseña.",
                        false
                    )

                } else if (perfil.result.exists()) {

                    terminar(
                        "La cuenta ya tiene un perfil. Vuelve al inicio de sesión.",
                        false
                    )

                } else {

                    solicitud.get()
                        .addOnCompleteListener { consulta ->

                            if (!consulta.isSuccessful) {

                                terminar(
                                    "No se pudo consultar la solicitud. Reintenta con el mismo correo y contraseña.",
                                    false
                                )

                            } else if (consulta.result.exists()) {

                                val estado = consulta.result
                                    .child("estado")
                                    .value as? String

                                terminar(
                                    when (estado) {
                                        "pendiente" ->
                                            "Ya tienes una solicitud pendiente de aprobación."

                                        "rechazada" ->
                                            "La solicitud fue rechazada. Contacta al administrador."

                                        else ->
                                            "La solicitud ya fue revisada. Vuelve al inicio de sesión."
                                    },
                                    estado == "pendiente"
                                )

                            } else {

                                val datos = mapOf<String, Any>(
                                    "nombre" to nombre.trim(),
                                    "correo" to (
                                            actual.email ?: correo.trim()
                                            ),
                                    "estado" to "pendiente",
                                    "rol_solicitado" to rolSolicitado,
                                    "fecha_solicitud" to
                                            ServerValue.TIMESTAMP
                                )

                                solicitud.setValue(datos)
                                    .addOnCompleteListener { guardado ->

                                        if (guardado.isSuccessful) {

                                            terminar(
                                                "Solicitud enviada. Podrás ingresar cuando el administrador la apruebe.",
                                                true
                                            )

                                        } else {

                                            terminar(
                                                "La cuenta se creó, pero no se pudo enviar la solicitud. Reintenta con el mismo correo y contraseña. Detalle: ${guardado.exception?.localizedMessage.orEmpty()}",
                                                false
                                            )
                                        }
                                    }
                            }
                        }
                }
            }
    }

    // =================================================
    // INTERFAZ DE REGISTRO
    // =================================================

    Text(
        "Crear cuenta",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = VERDE
    )

    Spacer(Modifier.height(8.dp))

    Text(
        "Las cuentas nuevas requieren aprobación del administrador.",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall
    )

    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = nombre,
        onValueChange = {
            nombre = it
        },
        label = {
            Text("Nombre completo")
        },
        singleLine = true,
        enabled = !cargando && !completado,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = correo,
        onValueChange = {
            correo = it
        },
        label = {
            Text("Correo electrónico")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email
        ),
        enabled = !cargando && !completado,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(10.dp))

    CampoClave(
        etiqueta = "Contraseña",
        valor = contrasena,
        onCambio = {
            contrasena = it
        },
        enabled = !cargando && !completado
    )

    Spacer(Modifier.height(8.dp))

    CampoClave(
        etiqueta = "Confirmar contraseña",
        valor = confirmar,
        onCambio = {
            confirmar = it
        },
        enabled = !cargando && !completado
    )

    Spacer(Modifier.height(8.dp))

    SelectorRol(
        etiqueta = "Perfil solicitado",
        rol = rolSolicitado,
        onCambio = {
            rolSolicitado = it
        },
        enabled = !cargando && !completado
    )

    Text(
        "El perfil solicitado no concede permisos. El administrador decide el rol definitivo.",
        style = MaterialTheme.typography.bodySmall,
        color = AZUL
    )

    Spacer(Modifier.height(12.dp))

    BotonColorAnimado(
        texto =
            if (cargando) {
                "ENVIANDO..."
            } else {
                "SOLICITAR CUENTA"
            },
        color = AMARILLO,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = !cargando && !completado,
        onClick = {

            mensaje = when {

                nombre.isBlank() ->
                    "Escribe tu nombre completo."

                !Patterns.EMAIL_ADDRESS.matcher(
                    correo.trim()
                ).matches() ->
                    "Escribe un correo válido."

                contrasena.length < 6 ->
                    "La contraseña debe tener al menos 6 caracteres."

                contrasena != confirmar ->
                    "Las contraseñas no coinciden."

                else -> ""
            }

            if (mensaje.isBlank()) {

                cargando = true

                onRegistroIniciado()

                auth.createUserWithEmailAndPassword(
                    correo.trim(),
                    contrasena
                ).addOnCompleteListener { tarea ->

                    when {

                        tarea.isSuccessful -> {
                            guardarSolicitud()
                        }

                        tarea.exception is
                                FirebaseAuthUserCollisionException -> {

                            // Recuperacion de una solicitud
                            // que quedo incompleta.

                            auth.signInWithEmailAndPassword(
                                correo.trim(),
                                contrasena
                            ).addOnCompleteListener { ingreso ->

                                if (ingreso.isSuccessful) {
                                    guardarSolicitud()
                                } else {
                                    terminar(
                                        "La cuenta ya existe. Verifica el correo y la contraseña.",
                                        false
                                    )
                                }
                            }
                        }

                        else -> {
                            terminar(
                                "No se pudo crear la cuenta: ${tarea.exception?.localizedMessage.orEmpty()}",
                                false
                            )
                        }
                    }
                }
            }
        }
    )

    if (cargando) {

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (mensaje.isNotBlank()) {

        Spacer(Modifier.height(12.dp))

        Aviso(
            mensaje,
            if (completado) VERDE else ROJO
        )
    }

    Spacer(Modifier.height(10.dp))

    BotonColorAnimado(
        texto = "VOLVER AL LOGIN",
        color = AZUL,
        onClick = onVolver,
        modifier = Modifier.fillMaxWidth(),
        enabled = !cargando
    )
}

// =====================================================
// COMPONENTE COMPARTIDO: CONTRASEÑA
// =====================================================

@Composable
private fun CampoClave(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    enabled: Boolean = true
) {
    var visible by remember {
        mutableStateOf(false)
    }

    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = {
            Text(etiqueta)
        },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        visualTransformation =
            if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        trailingIcon = {
            IconButton(
                onClick = {
                    visible = !visible
                },
                enabled = enabled
            ) {
                Icon(
                    imageVector =
                        if (visible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                    contentDescription =
                        if (visible) {
                            "Ocultar $etiqueta"
                        } else {
                            "Mostrar $etiqueta"
                        }
                )
            }
        }
    )
}

// =====================================================
// COMPONENTE COMPARTIDO: SELECTOR DE ROL
// =====================================================

@Composable
private fun SelectorRol(
    etiqueta: String,
    rol: String,
    onCambio: (String) -> Unit,
    enabled: Boolean = true
) {
    var abierto by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {

        OutlinedButton(
            onClick = {
                abierto = true
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "$etiqueta: ${rolLegible(rol)} ▾",
                textAlign = TextAlign.Center
            )
        }

        DropdownMenu(
            expanded = abierto,
            onDismissRequest = {
                abierto = false
            }
        ) {
            listOf(
                "observador",
                "operador",
                "administrador"
            ).forEach { perfil ->

                DropdownMenuItem(
                    text = {
                        Text(rolLegible(perfil))
                    },
                    onClick = {
                        onCambio(perfil)
                        abierto = false
                    }
                )
            }
        }
    }
}

// =====================================================
// COMPONENTE COMPARTIDO: MENSAJES
// =====================================================

@Composable
private fun Aviso(
    texto: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.09f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            texto,
            color = color,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// =====================================================
// FIN DE LA PARTE 2 DE 4
// CONTINUA: PANEL PRINCIPAL, SENSORES Y VENTILADOR
// =====================================================

// =====================================================
// INVERNADERO IoT - VERSION 7.23 CORREGIDA
// PARTE 3 DE 4
// PANEL PRINCIPAL, SENSORES, VENTILADOR Y ALERTAS
// =====================================================

// =====================================================
// MODELOS DE MONITOREO
// =====================================================

private data class LimitesTermicos(
    val advertencia: Double = 28.0,
    val critica: Double = 30.0,
    val recuperacion: Double = 27.0
)

private data class EstadoAlerta(
    val activa: Boolean = false,
    val recuperada: Boolean = false
)

private data class RegistroSensor(
    val temperatura: Double,
    val humedad: Double,
    val fecha: Long
)

private data class RegistroAlerta(
    val tipo: String,
    val temperatura: Double,
    val fecha: Long
)

private data class RegistroVentilador(
    val encendido: Boolean,
    val fecha: Long,
    val uid: String,
    val temperatura: Double?
)

// =====================================================
// EVALUACION DE ALERTAS CON HISTERESIS
// =====================================================

private fun actualizarAlerta(
    anterior: EstadoAlerta,
    temperatura: Double?,
    limites: LimitesTermicos
): EstadoAlerta {

    if (temperatura == null || !temperatura.isFinite()) {
        return anterior
    }

    return when {

        temperatura >= limites.advertencia -> {
            EstadoAlerta(
                activa = true,
                recuperada = false
            )
        }

        temperatura <= limites.recuperacion -> {
            EstadoAlerta(
                activa = false,
                recuperada = anterior.activa
            )
        }

        else -> {
            EstadoAlerta(
                activa = anterior.activa,
                recuperada = false
            )
        }
    }
}

// =====================================================
// PANEL PRINCIPAL
// =====================================================

@Composable
fun PantallaInicio(
    correo: String,
    rol: String,
    uidActual: String,
    onCerrarSesion: () -> Unit
) {

    val database = remember {
        FirebaseDatabase.getInstance(
            FIREBASE_DATABASE_URL
        )
    }

    val puedeAdministrar = rol == "administrador"

    val controlPermitido =
        puedeControlarVentilador(rol)

    // =================================================
    // ESTADO DE NAVEGACION
    // =================================================

    var pantalla by remember {
        mutableStateOf("panel")
    }

    // =================================================
    // ESTADO DE SENSORES
    // =================================================

    var temperatura by remember {
        mutableStateOf<Double?>(null)
    }

    var humedad by remember {
        mutableStateOf<Double?>(null)
    }

    var cargandoSensores by remember {
        mutableStateOf(true)
    }

    var errorSensores by remember {
        mutableStateOf<String?>(null)
    }

    var ultimaLectura by remember {
        mutableStateOf<Long?>(null)
    }

    // =================================================
    // ESTADO DEL VENTILADOR
    // =================================================

    var ventiladorEncendido by remember {
        mutableStateOf(false)
    }

    var cargandoVentilador by remember {
        mutableStateOf(true)
    }

    var cambiandoVentilador by remember {
        mutableStateOf(false)
    }

    var errorVentilador by remember {
        mutableStateOf<String?>(null)
    }

    var errorCambio by remember {
        mutableStateOf<String?>(null)
    }

    // =================================================
    // CONFIGURACION TERMICA
    // =================================================

    var limites by remember {
        mutableStateOf(LimitesTermicos())
    }

    var estadoAlerta by remember {
        mutableStateOf(EstadoAlerta())
    }

    var guardandoConfiguracion by remember {
        mutableStateOf(false)
    }

    var mensajeConfiguracion by remember {
        mutableStateOf("")
    }

    // =================================================
    // CONEXION FIREBASE
    // =================================================

    var conectado by remember {
        mutableStateOf(false)
    }

    // =================================================
    // SOLICITUDES DE CUENTAS
    // =================================================

    var solicitudes by remember {
        mutableStateOf<List<SolicitudRegistro>>(
            emptyList()
        )
    }

    var cargandoSolicitudes by remember {
        mutableStateOf(false)
    }

    var errorSolicitudes by remember {
        mutableStateOf("")
    }

    // =================================================
    // HISTORIALES
    // =================================================

    var historialSensores by remember {
        mutableStateOf<List<RegistroSensor>>(
            emptyList()
        )
    }

    var historialAlertas by remember {
        mutableStateOf<List<RegistroAlerta>>(
            emptyList()
        )
    }

    var historialVentilador by remember {
        mutableStateOf<List<RegistroVentilador>>(
            emptyList()
        )
    }

    var errorHistorial by remember {
        mutableStateOf("")
    }

    var ultimaCategoriaAlerta by remember {
        mutableStateOf("normal")
    }

    var ultimaMedicionRegistrada by remember {
        mutableStateOf(0L)
    }

    // =================================================
    // NAVEGACION
    // =================================================

    BackHandler(
        enabled = pantalla != "panel"
    ) {
        pantalla = "panel"
    }

    LaunchedEffect(rol) {
        if (!puedeAdministrar &&
            pantalla in listOf(
                "solicitudes",
                "usuarios",
                "configuracion"
            )
        ) {
            pantalla = "panel"
        }
    }

    // =================================================
    // CONEXION CON FIREBASE
    // =================================================

    DisposableEffect(database) {

        val referencia =
            database.getReference(".info/connected")

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {
                conectado = snapshot.value == true
            }

            override fun onCancelled(
                error: DatabaseError
            ) {
                conectado = false
            }
        }

        referencia.addValueEventListener(listener)

        onDispose {
            referencia.removeEventListener(listener)
        }
    }

    // =================================================
    // LEER CONFIGURACION TERMICA
    // =================================================

    DisposableEffect(database) {

        val referencia = database.reference
            .child("configuracion")
            .child("temperatura")

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                val advertencia =
                    (snapshot.child("advertencia").value
                            as? Number)?.toDouble()

                val critica =
                    (snapshot.child("critica").value
                            as? Number)?.toDouble()

                val recuperacion =
                    (snapshot.child("recuperacion").value
                            as? Number)?.toDouble()

                if (
                    advertencia != null &&
                    critica != null &&
                    recuperacion != null &&
                    advertencia.isFinite() &&
                    critica.isFinite() &&
                    recuperacion.isFinite() &&
                    recuperacion >= 0.0 &&
                    recuperacion < advertencia &&
                    advertencia < critica &&
                    critica <= 60.0
                ) {
                    limites = LimitesTermicos(
                        advertencia = advertencia,
                        critica = critica,
                        recuperacion = recuperacion
                    )
                }
            }

            override fun onCancelled(
                error: DatabaseError
            ) {
                mensajeConfiguracion =
                    "No se pudieron leer los límites: ${error.message}"
            }
        }

        referencia.addValueEventListener(listener)

        onDispose {
            referencia.removeEventListener(listener)
        }
    }

    // =================================================
    // LEER SOLICITUDES DE CUENTAS
    // =================================================

    DisposableEffect(
        database,
        puedeAdministrar
    ) {

        if (!puedeAdministrar) {

            solicitudes = emptyList()
            errorSolicitudes = ""

            onDispose { }

        } else {

            cargandoSolicitudes = true

            val referencia = database.reference
                .child("solicitudes_registro")

            val listener = object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    solicitudes = snapshot.children
                        .mapNotNull { hijo ->

                            if (
                                !hijo.hasChildren() ||
                                hijo.child("estado").value !=
                                "pendiente"
                            ) {
                                null
                            } else {

                                val uid = hijo.key

                                val nombre =
                                    hijo.child("nombre").value
                                            as? String

                                val correoSolicitud =
                                    hijo.child("correo").value
                                            as? String

                                if (
                                    uid == null ||
                                    nombre == null ||
                                    correoSolicitud == null
                                ) {
                                    null
                                } else {

                                    SolicitudRegistro(
                                        uid = uid,
                                        nombre = nombre,
                                        correo = correoSolicitud,
                                        fecha =
                                            (hijo.child(
                                                "fecha_solicitud"
                                            ).value as? Number)
                                                ?.toLong() ?: 0L,
                                        rolSolicitado =
                                            (hijo.child(
                                                "rol_solicitado"
                                            ).value as? String)
                                                ?.takeIf {
                                                    it in ROLES_PERMITIDOS
                                                }
                                    )
                                }
                            }
                        }
                        .sortedBy { it.fecha }

                    cargandoSolicitudes = false
                    errorSolicitudes = ""
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {
                    solicitudes = emptyList()
                    cargandoSolicitudes = false

                    errorSolicitudes =
                        "No se pudieron consultar solicitudes: ${error.message}"
                }
            }

            referencia.addValueEventListener(listener)

            onDispose {
                referencia.removeEventListener(listener)
            }
        }
    }

    // =================================================
    // LEER SENSORES EN TIEMPO REAL
    // =================================================

    DisposableEffect(database) {

        val referencia = database.reference
            .child("sensores")

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                temperatura =
                    (snapshot.child("temperatura").value
                            as? Number)
                        ?.toDouble()
                        ?.takeIf { it.isFinite() }

                humedad =
                    (snapshot.child("humedad").value
                            as? Number)
                        ?.toDouble()
                        ?.takeIf { it.isFinite() }

                ultimaLectura =
                    System.currentTimeMillis()

                cargandoSensores = false

                errorSensores =
                    if (
                        temperatura == null ||
                        humedad == null
                    ) {
                        "Faltan datos válidos de los sensores."
                    } else {
                        null
                    }
            }

            override fun onCancelled(
                error: DatabaseError
            ) {
                temperatura = null
                humedad = null
                cargandoSensores = false

                errorSensores =
                    "Error al leer sensores: ${error.message}"
            }
        }

        referencia.addValueEventListener(listener)

        onDispose {
            referencia.removeEventListener(listener)
        }
    }

    // =================================================
    // ACTUALIZAR ESTADO DE ALERTA
    // =================================================

    LaunchedEffect(
        temperatura,
        limites
    ) {
        estadoAlerta = actualizarAlerta(
            anterior = estadoAlerta,
            temperatura = temperatura,
            limites = limites
        )
    }

    // =================================================
    // LEER VENTILADOR EN TIEMPO REAL
    // =================================================

    DisposableEffect(database) {

        val referencia = database.reference
            .child("ventilador")
            .child("encendido")

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                val estado =
                    snapshot.value as? Boolean

                if (estado != null) {

                    ventiladorEncendido = estado
                    errorVentilador = null

                } else {

                    errorVentilador =
                        "Estado del ventilador no disponible."
                }

                cargandoVentilador = false
            }

            override fun onCancelled(
                error: DatabaseError
            ) {

                cargandoVentilador = false

                errorVentilador =
                    "Error al consultar ventilador: ${error.message}"
            }
        }

        referencia.addValueEventListener(listener)

        onDispose {
            referencia.removeEventListener(listener)
        }
    }

    // =================================================
    // LEER HISTORIALES DE FIREBASE
    // =================================================

    DisposableEffect(database) {

        val rutas = listOf(
            "historial_sensores",
            "historial_alertas",
            "historial_ventilador"
        )

        val listeners =
            mutableListOf<Pair<Query, ValueEventListener>>()

        rutas.forEach { ruta ->

            val referencia = database.reference
                .child(ruta)
                .orderByChild("fecha")
                .limitToLast(60)

            val listener = object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    when (ruta) {

                        "historial_sensores" -> {

                            historialSensores =
                                snapshot.children.mapNotNull { item ->

                                    val t =
                                        (item.child("temperatura").value
                                                as? Number)?.toDouble()

                                    val h =
                                        (item.child("humedad").value
                                                as? Number)?.toDouble()

                                    val f =
                                        (item.child("fecha").value
                                                as? Number)?.toLong()

                                    if (
                                        t != null &&
                                        h != null &&
                                        f != null &&
                                        t.isFinite() &&
                                        h.isFinite()
                                    ) {
                                        RegistroSensor(t, h, f)
                                    } else {
                                        null
                                    }
                                }.sortedBy { it.fecha }
                        }

                        "historial_alertas" -> {

                            historialAlertas =
                                snapshot.children.mapNotNull { item ->

                                    val tipo =
                                        item.child("tipo").value
                                                as? String

                                    val t =
                                        (item.child("temperatura").value
                                                as? Number)?.toDouble()

                                    val f =
                                        (item.child("fecha").value
                                                as? Number)?.toLong()

                                    if (
                                        tipo != null &&
                                        t != null &&
                                        f != null &&
                                        t.isFinite()
                                    ) {
                                        RegistroAlerta(tipo, t, f)
                                    } else {
                                        null
                                    }
                                }.sortedByDescending { it.fecha }
                        }

                        "historial_ventilador" -> {

                            historialVentilador =
                                snapshot.children.mapNotNull { item ->

                                    val encendido =
                                        item.child("encendido").value
                                                as? Boolean

                                    val uid =
                                        item.child("uid").value
                                                as? String

                                    val fecha =
                                        (item.child("fecha").value
                                                as? Number)?.toLong()

                                    val temp =
                                        (item.child("temperatura").value
                                                as? Number)?.toDouble()

                                    if (
                                        encendido != null &&
                                        uid != null &&
                                        fecha != null
                                    ) {
                                        RegistroVentilador(
                                            encendido,
                                            fecha,
                                            uid,
                                            temp
                                        )
                                    } else {
                                        null
                                    }
                                }.sortedByDescending { it.fecha }
                        }
                    }

                    errorHistorial = ""
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    errorHistorial =
                        "Error al consultar $ruta: ${error.message}"
                }
            }

            referencia.addValueEventListener(listener)

            listeners.add(
                referencia to listener
            )
        }

        onDispose {
            listeners.forEach { (referencia, listener) ->
                referencia.removeEventListener(listener)
            }
        }
    }

    // =================================================
    // REGISTRAR MEDICIONES PERIODICAS
    // =================================================
    // Intervalo minimo: 60 segundos por sesion.
    // No reemplaza el registro desde el ESP32.
    // =================================================

    LaunchedEffect(
        temperatura,
        humedad,
        conectado,
        controlPermitido
    ) {

        val t = temperatura
        val h = humedad

        if (
            t != null &&
            h != null &&
            conectado &&
            controlPermitido
        ) {

            val ahora =
                System.currentTimeMillis()

            if (
                ahora - ultimaMedicionRegistrada >=
                60000L
            ) {

                ultimaMedicionRegistrada = ahora

                database.reference
                    .child("historial_sensores")
                    .push()
                    .setValue(
                        mapOf(
                            "temperatura" to t,
                            "humedad" to h,
                            "fecha" to ServerValue.TIMESTAMP,
                            "uid" to uidActual
                        )
                    )
            }
        }
    }

    // =================================================
    // REGISTRAR CAMBIOS DE ALERTA
    // =================================================

    LaunchedEffect(
        temperatura,
        estadoAlerta,
        limites,
        conectado,
        controlPermitido
    ) {

        val t = temperatura

        if (
            t != null &&
            conectado &&
            controlPermitido
        ) {

            val categoria = when {

                t >= limites.critica ->
                    "critica"

                estadoAlerta.activa ->
                    "advertencia"

                estadoAlerta.recuperada ->
                    "recuperacion"

                else ->
                    "normal"
            }

            if (
                categoria != ultimaCategoriaAlerta
            ) {

                val anterior =
                    ultimaCategoriaAlerta

                ultimaCategoriaAlerta =
                    categoria

                if (
                    categoria != "normal" &&
                    !(
                            anterior == "normal" &&
                                    categoria == "recuperacion"
                            )
                ) {

                    database.reference
                        .child("historial_alertas")
                        .push()
                        .setValue(
                            mapOf(
                                "tipo" to categoria,
                                "temperatura" to t,
                                "fecha" to ServerValue.TIMESTAMP,
                                "uid" to uidActual
                            )
                        )
                }
            }
        }
    }

    // =================================================
    // INTERFAZ DEL PANEL
    // =================================================

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
        ) {

            // =========================================
            // ENCABEZADO
            // =========================================

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            "Invernadero IoT",
                            fontWeight = FontWeight.Bold,
                            color = AZUL,
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        Text(
                            rol.uppercase(),
                            color = VERDE,
                            style =
                                MaterialTheme.typography.labelSmall
                        )
                    }

                    BotonColorAnimado(
                        texto = "Salir",
                        color = ROJO,
                        onClick = onCerrarSesion,
                        compacto = true
                    )
                }
            }

            // =========================================
            // NAVEGACION ENTRE PANTALLAS
            // =========================================

            when {

                pantalla == "solicitudes" &&
                        puedeAdministrar -> {

                    PanelSolicitudes(
                        database = database,
                        solicitudes = solicitudes,
                        cargando = cargandoSolicitudes,
                        errorLectura = errorSolicitudes,
                        modifier = Modifier.weight(1f),
                        onVolver = {
                            pantalla = "panel"
                        }
                    )
                }

                pantalla == "usuarios" &&
                        puedeAdministrar -> {

                    PanelUsuarios(
                        database = database,
                        uidActual = uidActual,
                        modifier = Modifier.weight(1f),
                        onVolver = {
                            pantalla = "panel"
                        }
                    )
                }

                pantalla == "historial" -> {

                    PantallaHistorial(
                        sensores = historialSensores,
                        alertas = historialAlertas,
                        ventilador = historialVentilador,
                        error = errorHistorial,
                        modifier = Modifier.weight(1f),
                        onVolver = {
                            pantalla = "panel"
                        }
                    )
                }

                pantalla == "configuracion" &&
                        puedeAdministrar -> {

                    PantallaConfiguracion(
                        actuales = limites,
                        guardando = guardandoConfiguracion,
                        mensaje = mensajeConfiguracion,
                        modifier = Modifier.weight(1f),
                        onVolver = {
                            pantalla = "panel"
                        },
                        onGuardar = { nuevos ->

                            guardandoConfiguracion = true
                            mensajeConfiguracion = ""

                            database.reference
                                .child("configuracion")
                                .child("temperatura")
                                .setValue(
                                    mapOf(
                                        "advertencia" to
                                                nuevos.advertencia,
                                        "critica" to
                                                nuevos.critica,
                                        "recuperacion" to
                                                nuevos.recuperacion
                                    )
                                )
                                .addOnCompleteListener { tarea ->

                                    guardandoConfiguracion = false

                                    mensajeConfiguracion =
                                        if (tarea.isSuccessful) {
                                            "Configuración guardada."
                                        } else {
                                            "No se pudo guardar: ${tarea.exception?.localizedMessage.orEmpty()}"
                                        }
                                }
                        }
                    )
                }

                else -> {

                    // =================================
                    // PANEL DE MONITOREO
                    // =================================

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(
                                rememberScrollState()
                            )
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = 100.dp
                            ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        LogoInvernadero(115)

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "Panel de monitoreo",
                            style =
                                MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            correo,
                            style =
                                MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // =============================
                        // ADMINISTRACION
                        // =============================

                        if (puedeAdministrar) {

                            Spacer(Modifier.height(12.dp))

                            val contador = when {

                                cargandoSolicitudes ->
                                    "…"

                                errorSolicitudes.isNotBlank() ->
                                    "!"

                                else ->
                                    solicitudes.size.toString()
                            }

                            BotonColorAnimado(
                                texto =
                                    "SOLICITUDES DE CUENTAS ($contador)",
                                color = AMARILLO,
                                onClick = {
                                    pantalla = "solicitudes"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(6.dp))

                            BotonColorAnimado(
                                texto = "GESTIONAR USUARIOS",
                                color = AZUL,
                                onClick = {
                                    pantalla = "usuarios"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // =============================
                        // HISTORIALES
                        // =============================

                        Spacer(Modifier.height(10.dp))

                        BotonColorAnimado(
                            texto = "VER GRÁFICOS E HISTORIALES",
                            color = AZUL,
                            onClick = {
                                pantalla = "historial"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // =============================
                        // CONFIGURACION
                        // =============================

                        if (puedeAdministrar) {

                            Spacer(Modifier.height(6.dp))

                            BotonColorAnimado(
                                texto =
                                    "CONFIGURAR LÍMITES TÉRMICOS",
                                color = VERDE,
                                onClick = {
                                    pantalla = "configuracion"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // =============================
                        // ESTADO DE FIREBASE
                        // =============================

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text =
                                "Estado Firebase: " +
                                        if (conectado) {
                                            "Conectado"
                                        } else {
                                            "Sin conexión"
                                        },
                            color =
                                if (conectado) VERDE else ROJO,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text =
                                "Última lectura recibida: " +
                                        (
                                                ultimaLectura
                                                    ?.let(::fechaLegible)
                                                    ?: "Pendiente"
                                                ),
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        // =============================
                        // MONITOREO
                        // =============================

                        Spacer(Modifier.height(18.dp))

                        Text(
                            "MONITOREO EN TIEMPO REAL",
                            color = AZUL,
                            fontWeight = FontWeight.Bold,
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(12.dp))

                        if (cargandoSensores) {

                            CircularProgressIndicator(
                                color = VERDE
                            )

                            Text(
                                "Conectando con Firebase..."
                            )

                        } else {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(10.dp)
                            ) {

                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {

                                    SensorCard(
                                        titulo = "TEMPERATURA",
                                        valor =
                                            temperatura?.let {
                                                String.format(
                                                    Locale.US,
                                                    "%.1f °C",
                                                    it
                                                )
                                            } ?: "Sin datos",
                                        descripcion = "Ambiente",
                                        color = ROJO
                                    )
                                }

                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {

                                    SensorCard(
                                        titulo = "HUMEDAD",
                                        valor =
                                            humedad?.let {
                                                String.format(
                                                    Locale.US,
                                                    "%.1f %%",
                                                    it
                                                )
                                            } ?: "Sin datos",
                                        descripcion = "Relativa",
                                        color = AZUL
                                    )
                                }
                            }
                        }

                        if (errorSensores != null) {

                            Spacer(Modifier.height(10.dp))

                            Text(
                                errorSensores.orEmpty(),
                                color = ROJO,
                                textAlign = TextAlign.Center
                            )
                        }

                        // =============================
                        // ALERTAS TERMICAS
                        // =============================

                        Spacer(Modifier.height(14.dp))

                        AlertaTemperatura(
                            temperatura = temperatura,
                            estado = estadoAlerta,
                            ventilador = ventiladorEncendido,
                            ventiladorDisponible =
                                !cargandoVentilador &&
                                        errorVentilador == null,
                            puedeControlar = controlPermitido,
                            limites = limites
                        )

                        // =============================
                        // CONTROL DEL VENTILADOR
                        // =============================

                        Spacer(Modifier.height(18.dp))

                        Text(
                            "CONTROL DEL VENTILADOR",
                            color = AZUL,
                            fontWeight = FontWeight.Bold,
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(12.dp))

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {

                                if (cargandoVentilador) {

                                    CircularProgressIndicator(
                                        color = VERDE,
                                        modifier = Modifier.size(25.dp)
                                    )

                                    Text(
                                        "Consultando ventilador..."
                                    )

                                } else {

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Column(
                                            modifier =
                                                Modifier.weight(1f)
                                        ) {

                                            Text(
                                                "Ventilador",
                                                fontWeight =
                                                    FontWeight.Bold,
                                                style =
                                                    MaterialTheme.typography.titleMedium
                                            )

                                            Spacer(
                                                Modifier.height(5.dp)
                                            )

                                            Text(
                                                text = when {

                                                    errorVentilador != null ->
                                                        "Estado no disponible"

                                                    ventiladorEncendido ->
                                                        "ENCENDIDO"

                                                    else ->
                                                        "APAGADO"
                                                },
                                                color =
                                                    if (
                                                        ventiladorEncendido &&
                                                        errorVentilador == null
                                                    ) {
                                                        VERDE
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                            )
                                        }

                                        Switch(
                                            checked =
                                                ventiladorEncendido,
                                            enabled =
                                                controlPermitido &&
                                                        !cambiandoVentilador &&
                                                        errorVentilador == null,
                                            colors =
                                                SwitchDefaults.colors(
                                                    checkedTrackColor =
                                                        VERDE
                                                ),
                                            onCheckedChange = { nuevo ->

                                                if (
                                                    controlPermitido &&
                                                    !cambiandoVentilador &&
                                                    errorVentilador == null
                                                ) {

                                                    cambiandoVentilador = true
                                                    errorCambio = null

                                                    database.reference
                                                        .child("ventilador")
                                                        .child("encendido")
                                                        .setValue(nuevo)
                                                        .addOnCompleteListener { tarea ->

                                                            cambiandoVentilador = false

                                                            if (!tarea.isSuccessful) {

                                                                errorCambio =
                                                                    "No se pudo actualizar: ${tarea.exception?.localizedMessage.orEmpty()}"

                                                            } else {

                                                                // Registrar auditoria.

                                                                val datos =
                                                                    mutableMapOf<String, Any>(
                                                                        "encendido" to nuevo,
                                                                        "fecha" to ServerValue.TIMESTAMP,
                                                                        "uid" to uidActual
                                                                    )

                                                                temperatura?.let {
                                                                    datos["temperatura"] = it
                                                                }

                                                                database.reference
                                                                    .child("historial_ventilador")
                                                                    .push()
                                                                    .setValue(datos)
                                                                    .addOnFailureListener { error ->

                                                                        errorCambio =
                                                                            "Ventilador actualizado; auditoría pendiente: ${error.localizedMessage}"
                                                                    }
                                                            }
                                                        }
                                                }
                                            }
                                        )
                                    }

                                    if (cambiandoVentilador) {

                                        LinearProgressIndicator(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    if (controlPermitido) {
                                        "Control habilitado para tu rol."
                                    } else {
                                        "Solo lectura. Tu rol no permite controlar el ventilador."
                                    },
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )

                                if (
                                    errorVentilador != null ||
                                    errorCambio != null
                                ) {

                                    Spacer(Modifier.height(10.dp))

                                    Text(
                                        errorVentilador
                                            ?: errorCambio.orEmpty(),
                                        color = ROJO,
                                        style =
                                            MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // =============================
                        // PERMISOS DEL PERFIL
                        // =============================

                        Spacer(Modifier.height(18.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {

                            Column(
                                modifier = Modifier.padding(18.dp)
                            ) {

                                Text(
                                    "Permisos del perfil",
                                    fontWeight = FontWeight.Bold,
                                    style =
                                        MaterialTheme.typography.titleMedium
                                )

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    "Monitoreo: Permitido"
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    if (controlPermitido) {
                                        "Ventilador: Permitido"
                                    } else {
                                        "Ventilador: Restringido"
                                    }
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    if (puedeAdministrar) {
                                        "Administración: Rol autorizado"
                                    } else {
                                        "Administración: Restringida"
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            "Control virtual conectado a Firebase. El hardware físico se integrará posteriormente.",
                            style =
                                MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// =====================================================
// TARJETAS DE SENSORES
// =====================================================

@Composable
fun SensorCard(
    titulo: String,
    valor: String,
    descripcion: String,
    color: Color
) {

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor =
                color.copy(alpha = 0.07f)
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 18.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                titulo,
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                valor,
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                descripcion,
                style =
                    MaterialTheme.typography.bodySmall
            )
        }
    }
}

// =====================================================
// ALERTAS VISUALES DE TEMPERATURA
// =====================================================

@Composable
private fun AlertaTemperatura(
    temperatura: Double?,
    estado: EstadoAlerta,
    ventilador: Boolean,
    ventiladorDisponible: Boolean,
    puedeControlar: Boolean,
    limites: LimitesTermicos
) {

    val lectura =
        temperatura?.takeIf { it.isFinite() }

    val titulo: String
    val color: Color
    val recomendacion: String

    when {

        lectura == null -> {

            titulo =
                "Sin lectura de temperatura"

            color = AZUL

            recomendacion =
                "No se puede evaluar la alerta hasta recibir un dato válido."
        }

        lectura >= limites.critica -> {

            titulo =
                "ALERTA CRÍTICA DE TEMPERATURA"

            color = ROJO

            recomendacion =
                "Temperatura crítica. Revisa la ventilación."
        }

        lectura >= limites.advertencia -> {

            titulo =
                "ADVERTENCIA DE TEMPERATURA"

            color = AMARILLO

            recomendacion =
                "Temperatura elevada. Se recomienda ventilación."
        }

        estado.activa -> {

            titulo =
                "Temperatura descendiendo"

            color = AMARILLO

            recomendacion =
                "Espera alcanzar el límite de recuperación para confirmar la normalización."
        }

        estado.recuperada -> {

            titulo =
                "Temperatura recuperada"

            color = VERDE

            recomendacion =
                "Se alcanzó el límite de recuperación. Evalúa si corresponde detener la ventilación."
        }

        else -> {

            titulo =
                "Temperatura normal"

            color = VERDE

            recomendacion =
                "Sin advertencia térmica según los límites configurados."
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Text(
                titulo,
                color = color,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                recomendacion,
                style =
                    MaterialTheme.typography.bodySmall
            )

            if (lectura != null) {

                Spacer(Modifier.height(6.dp))

                Text(
                    when {

                        !ventiladorDisponible ->
                            "Estado del ventilador no disponible."

                        ventilador ->
                            "Ventilador activado. Continúa monitoreando la temperatura."

                        estado.activa && puedeControlar ->
                            "Ventilador apagado. Puedes encenderlo con el control inferior."

                        estado.activa ->
                            "Ventilador apagado. Solicita la intervención de un operador o administrador."

                        else ->
                            "Ventilador apagado."
                    },
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Control manual · Límites: " +
                        "${limites.advertencia} / " +
                        "${limites.critica} / " +
                        "${limites.recuperacion} °C.",
                style =
                    MaterialTheme.typography.labelSmall
            )
        }
    }
}

// =====================================================
// FIN DE LA PARTE 3 DE 4
// CONTINUA: ADMINISTRACION, GRAFICOS E HISTORIALES
// =====================================================

// =====================================================
// INVERNADERO IoT - VERSION 7.23 CORREGIDA
// PARTE 4 DE 4
// ADMINISTRACION, HISTORIALES Y CONFIGURACION
// =====================================================

// =====================================================
// MODELO DE SOLICITUD DE REGISTRO
// =====================================================

private data class SolicitudRegistro(
    val uid: String,
    val nombre: String,
    val correo: String,
    val fecha: Long,
    val rolSolicitado: String?
)

// =====================================================
// MODELO DE USUARIO
// =====================================================

private data class PerfilUsuario(
    val uid: String,
    val nombre: String,
    val correo: String,
    val rol: String,
    val activo: Boolean
)

// =====================================================
// PANTALLA DE SOLICITUDES DE CUENTAS
// =====================================================

@Composable
private fun PanelSolicitudes(
    database: FirebaseDatabase,
    solicitudes: List<SolicitudRegistro>,
    cargando: Boolean,
    errorLectura: String,
    modifier: Modifier = Modifier,
    onVolver: () -> Unit
) {

    var procesandoUid by remember {
        mutableStateOf<String?>(null)
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    var solicitudSeleccionada by remember {
        mutableStateOf<SolicitudRegistro?>(null)
    }

    var rolAprobacion by remember {
        mutableStateOf("observador")
    }

    var mostrarConfirmacion by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        EncabezadoSeccion(
            titulo = "Solicitudes de acceso",
            onVolver = onVolver
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Revisión y aprobación de nuevas cuentas",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        if (cargando) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorLectura.isNotBlank()) {
            Aviso(errorLectura, ROJO)
        }

        if (mensaje.isNotBlank()) {
            Aviso(mensaje, AZUL)
            Spacer(Modifier.height(10.dp))
        }

        if (!cargando && solicitudes.isEmpty()) {

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    "No hay solicitudes pendientes.",
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        solicitudes.forEach { solicitud ->

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(14.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        solicitud.nombre,
                        fontWeight = FontWeight.Bold,
                        color = AZUL
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(solicitud.correo)

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "Perfil solicitado: " +
                                rolLegible(
                                    solicitud.rolSolicitado
                                        ?: "observador"
                                )
                    )

                    Text(
                        "Fecha: " +
                                if (solicitud.fecha > 0L) {
                                    fechaLegible(solicitud.fecha)
                                } else {
                                    "No disponible"
                                },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(12.dp))

                    BotonColorAnimado(
                        texto = "REVISAR SOLICITUD",
                        color = AZUL,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = procesandoUid == null,
                        onClick = {
                            solicitudSeleccionada = solicitud

                            rolAprobacion =
                                solicitud.rolSolicitado
                                    ?.takeIf {
                                        it in ROLES_PERMITIDOS
                                    }
                                    ?: "observador"

                            mostrarConfirmacion = true
                            mensaje = ""
                        }
                    )
                }
            }
        }
    }

    // =================================================
    // CONFIRMACION DE SOLICITUD
    // =================================================

    val seleccionada = solicitudSeleccionada

    if (mostrarConfirmacion && seleccionada != null) {

        AlertDialog(
            onDismissRequest = {
                if (procesandoUid == null) {
                    mostrarConfirmacion = false
                }
            },

            title = {
                Text("Revisar solicitud")
            },

            text = {

                Column {

                    Text(
                        "Solicitante: ${seleccionada.nombre}"
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(seleccionada.correo)

                    Spacer(Modifier.height(12.dp))

                    SelectorRol(
                        etiqueta = "Rol a asignar",
                        rol = rolAprobacion,
                        onCambio = {
                            rolAprobacion = it
                        },
                        enabled = procesandoUid == null
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "La aprobación activa la cuenta con el rol seleccionado.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },

            confirmButton = {

                TextButton(
                    enabled = procesandoUid == null,
                    onClick = {

                        procesandoUid = seleccionada.uid

                        val actualizaciones =
                            mapOf<String, Any>(
                                "usuarios/${seleccionada.uid}/nombre" to
                                        seleccionada.nombre,

                                "usuarios/${seleccionada.uid}/correo" to
                                        seleccionada.correo,

                                "usuarios/${seleccionada.uid}/rol" to
                                        rolAprobacion,

                                "usuarios/${seleccionada.uid}/activo" to
                                        true,

                                "solicitudes_registro/${seleccionada.uid}/estado" to
                                        "aprobada",

                                "solicitudes_registro/${seleccionada.uid}/rol_aprobado" to
                                        rolAprobacion,

                                "solicitudes_registro/${seleccionada.uid}/fecha_revision" to
                                        ServerValue.TIMESTAMP
                            )

                        database.reference
                            .updateChildren(actualizaciones)
                            .addOnCompleteListener { tarea ->

                                procesandoUid = null
                                mostrarConfirmacion = false

                                mensaje =
                                    if (tarea.isSuccessful) {
                                        "Solicitud aprobada correctamente."
                                    } else {
                                        "Error al aprobar: ${tarea.exception?.localizedMessage.orEmpty()}"
                                    }
                            }
                    }
                ) {
                    Text(
                        "APROBAR",
                        color = VERDE,
                        fontWeight = FontWeight.Bold
                    )
                }
            },

            dismissButton = {

                Row {

                    TextButton(
                        enabled = procesandoUid == null,
                        onClick = {

                            procesandoUid = seleccionada.uid

                            database.reference
                                .child("solicitudes_registro")
                                .child(seleccionada.uid)
                                .updateChildren(
                                    mapOf(
                                        "estado" to "rechazada",
                                        "fecha_revision" to
                                                ServerValue.TIMESTAMP
                                    )
                                )
                                .addOnCompleteListener { tarea ->

                                    procesandoUid = null
                                    mostrarConfirmacion = false

                                    mensaje =
                                        if (tarea.isSuccessful) {
                                            "Solicitud rechazada."
                                        } else {
                                            "No se pudo rechazar: ${tarea.exception?.localizedMessage.orEmpty()}"
                                        }
                                }
                        }
                    ) {
                        Text(
                            "RECHAZAR",
                            color = ROJO
                        )
                    }

                    TextButton(
                        enabled = procesandoUid == null,
                        onClick = {
                            mostrarConfirmacion = false
                        }
                    ) {
                        Text("CANCELAR")
                    }
                }
            }
        )
    }
}

// =====================================================
// GESTION DE USUARIOS
// =====================================================

@Composable
private fun PanelUsuarios(
    database: FirebaseDatabase,
    uidActual: String,
    modifier: Modifier = Modifier,
    onVolver: () -> Unit
) {

    var usuarios by remember {
        mutableStateOf<List<PerfilUsuario>>(
            emptyList()
        )
    }

    var cargando by remember {
        mutableStateOf(true)
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    var procesandoUid by remember {
        mutableStateOf<String?>(null)
    }

    var usuarioSeleccionado by remember {
        mutableStateOf<PerfilUsuario?>(null)
    }

    var rolEdicion by remember {
        mutableStateOf("observador")
    }

    var activoEdicion by remember {
        mutableStateOf(true)
    }

    // =================================================
    // CONSULTAR USUARIOS
    // =================================================

    DisposableEffect(database) {

        val referencia = database.reference
            .child("usuarios")

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                usuarios = snapshot.children
                    .mapNotNull { item ->

                        val uid = item.key

                        if (uid == null) {
                            null
                        } else {

                            PerfilUsuario(
                                uid = uid,
                                nombre =
                                    item.child("nombre").value
                                            as? String ?: "Sin nombre",
                                correo =
                                    item.child("correo").value
                                            as? String ?: "",
                                rol =
                                    item.child("rol").value
                                            as? String ?: "observador",
                                activo =
                                    item.child("activo").value == true
                            )
                        }
                    }
                    .sortedBy {
                        it.nombre.lowercase(Locale.getDefault())
                    }

                cargando = false
            }

            override fun onCancelled(
                error: DatabaseError
            ) {

                cargando = false

                mensaje =
                    "Error al consultar usuarios: ${error.message}"
            }
        }

        referencia.addValueEventListener(listener)

        onDispose {
            referencia.removeEventListener(listener)
        }
    }

    // =================================================
    // INTERFAZ
    // =================================================

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        EncabezadoSeccion(
            titulo = "Gestión de usuarios",
            onVolver = onVolver
        )

        Spacer(Modifier.height(14.dp))

        Text(
            "Cuentas registradas: ${usuarios.size}",
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        if (cargando) {

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (mensaje.isNotBlank()) {

            Aviso(mensaje, AZUL)

            Spacer(Modifier.height(12.dp))
        }

        usuarios.forEach { perfil ->

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(14.dp)
            ) {

                Column(
                    modifier = Modifier.padding(14.dp)
                ) {

                    Text(
                        perfil.nombre,
                        fontWeight = FontWeight.Bold,
                        color = AZUL
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(perfil.correo)

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "Rol: ${rolLegible(perfil.rol)}"
                    )

                    Text(
                        if (perfil.activo) {
                            "Estado: ACTIVO"
                        } else {
                            "Estado: INACTIVO"
                        },
                        color =
                            if (perfil.activo) VERDE else ROJO
                    )

                    if (perfil.uid == uidActual) {

                        Spacer(Modifier.height(5.dp))

                        Text(
                            "Tu cuenta",
                            color = AZUL,
                            style =
                                MaterialTheme.typography.labelSmall
                        )

                    } else {

                        Spacer(Modifier.height(10.dp))

                        BotonColorAnimado(
                            texto = "EDITAR USUARIO",
                            color = VERDE,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = procesandoUid == null,
                            onClick = {

                                usuarioSeleccionado = perfil

                                rolEdicion =
                                    perfil.rol.takeIf {
                                        it in ROLES_PERMITIDOS
                                    } ?: "observador"

                                activoEdicion = perfil.activo

                                mensaje = ""
                            }
                        )
                    }
                }
            }
        }
    }

    // =================================================
    // EDITAR PERFIL
    // =================================================

    val seleccionado = usuarioSeleccionado

    if (seleccionado != null) {

        AlertDialog(
            onDismissRequest = {
                if (procesandoUid == null) {
                    usuarioSeleccionado = null
                }
            },

            title = {
                Text("Editar usuario")
            },

            text = {

                Column {

                    Text(
                        seleccionado.nombre,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(seleccionado.correo)

                    Spacer(Modifier.height(14.dp))

                    SelectorRol(
                        etiqueta = "Rol",
                        rol = rolEdicion,
                        onCambio = {
                            rolEdicion = it
                        },
                        enabled = procesandoUid == null
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            "Cuenta activa",
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = activoEdicion,
                            onCheckedChange = {
                                activoEdicion = it
                            },
                            enabled = procesandoUid == null,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = VERDE
                            )
                        )
                    }
                }
            },

            confirmButton = {

                TextButton(
                    enabled = procesandoUid == null,
                    onClick = {

                        procesandoUid = seleccionado.uid

                        database.reference
                            .child("usuarios")
                            .child(seleccionado.uid)
                            .updateChildren(
                                mapOf(
                                    "rol" to rolEdicion,
                                    "activo" to activoEdicion
                                )
                            )
                            .addOnCompleteListener { tarea ->

                                procesandoUid = null
                                usuarioSeleccionado = null

                                mensaje =
                                    if (tarea.isSuccessful) {
                                        "Usuario actualizado correctamente."
                                    } else {
                                        "No se pudo actualizar: ${tarea.exception?.localizedMessage.orEmpty()}"
                                    }
                            }
                    }
                ) {
                    Text(
                        "GUARDAR",
                        color = VERDE
                    )
                }
            },

            dismissButton = {

                TextButton(
                    enabled = procesandoUid == null,
                    onClick = {
                        usuarioSeleccionado = null
                    }
                ) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

// =====================================================
// ENCABEZADO COMPARTIDO
// =====================================================

@Composable
private fun EncabezadoSeccion(
    titulo: String,
    onVolver: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        BotonColorAnimado(
            texto = "VOLVER",
            color = AZUL,
            compacto = true,
            onClick = onVolver
        )

        Spacer(Modifier.width(12.dp))

        Text(
            titulo,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AZUL,
            modifier = Modifier.weight(1f)
        )
    }
}

// =====================================================
// GRAFICO HISTORICO DE TEMPERATURA Y HUMEDAD
// =====================================================

@Composable
private fun GraficoHistorico(
    titulo: String,
    valores: List<Double>,
    color: Color,
    unidad: String,
    modifier: Modifier = Modifier
) {

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                titulo,
                fontWeight = FontWeight.Bold,
                color = AZUL,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(10.dp))

            if (valores.isEmpty()) {

                Text(
                    "No hay datos para generar el gráfico.",
                    style = MaterialTheme.typography.bodySmall
                )

            } else {

                val minimo = valores.minOrNull() ?: 0.0
                val maximo = valores.maxOrNull() ?: 0.0

                val rango =
                    (maximo - minimo).coerceAtLeast(1.0)

                Text(
                    "Mín: ${
                        String.format(
                            Locale.US,
                            "%.1f",
                            minimo
                        )
                    } $unidad  ·  Máx: ${
                        String.format(
                            Locale.US,
                            "%.1f",
                            maximo
                        )
                    } $unidad",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {

                    val ancho = size.width
                    val alto = size.height

                    // Lineas horizontales de referencia.

                    repeat(5) { indice ->

                        val y =
                            alto * indice / 4f

                        drawLine(
                            color = Color.Gray.copy(
                                alpha = 0.20f
                            ),
                            start = Offset(0f, y),
                            end = Offset(ancho, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val puntos =
                        valores.mapIndexed { indice, valor ->

                            val x =
                                if (valores.size == 1) {
                                    ancho / 2f
                                } else {
                                    ancho *
                                            indice /
                                            (valores.size - 1).toFloat()
                                }

                            val normalizado =
                                ((valor - minimo) / rango)
                                    .toFloat()

                            val y =
                                alto * (1f - normalizado)

                            Offset(x, y)
                        }

                    if (puntos.size > 1) {

                        val camino = Path().apply {

                            moveTo(
                                puntos.first().x,
                                puntos.first().y
                            )

                            puntos.drop(1).forEach { punto ->

                                lineTo(
                                    punto.x,
                                    punto.y
                                )
                            }
                        }

                        drawPath(
                            path = camino,
                            color = color,
                            style = Stroke(
                                width = 3.dp.toPx()
                            )
                        )
                    }

                    puntos.forEach { punto ->

                        drawCircle(
                            color = color,
                            radius = 3.dp.toPx(),
                            center = punto
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Lecturas: ${valores.size} · Orden cronológico",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// =====================================================
// PANTALLA DE HISTORIALES
// =====================================================

@Composable
private fun PantallaHistorial(
    sensores: List<RegistroSensor>,
    alertas: List<RegistroAlerta>,
    ventilador: List<RegistroVentilador>,
    error: String,
    modifier: Modifier = Modifier,
    onVolver: () -> Unit
) {

    var pestana by remember {
        mutableStateOf("sensores")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {

        EncabezadoSeccion(
            titulo = "Gráficos e historiales",
            onVolver = onVolver
        )

        Spacer(Modifier.height(14.dp))

        if (error.isNotBlank()) {

            Aviso(error, ROJO)

            Spacer(Modifier.height(12.dp))
        }

        // =================================================
        // PESTANAS
        // =================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {

            listOf(
                "sensores" to "Sensores",
                "alertas" to "Alertas",
                "ventilador" to "Ventilador"
            ).forEach { opcion ->

                val seleccionado =
                    pestana == opcion.first

                OutlinedButton(
                    onClick = {
                        pestana = opcion.first
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = 3.dp,
                        vertical = 8.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor =
                            if (seleccionado) {
                                AZUL.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (seleccionado) AZUL
                        else Color.LightGray
                    )
                ) {

                    Text(
                        opcion.second,
                        style =
                            MaterialTheme.typography.labelSmall,
                        color = AZUL
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // =================================================
        // HISTORIAL DE SENSORES
        // =================================================

        when (pestana) {

            "sensores" -> {

                Text(
                    "Historial de mediciones",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                GraficoHistorico(
                    titulo = "Temperatura",
                    valores = sensores.map {
                        it.temperatura
                    },
                    color = ROJO,
                    unidad = "°C"
                )

                Spacer(Modifier.height(12.dp))

                GraficoHistorico(
                    titulo = "Humedad",
                    valores = sensores.map {
                        it.humedad
                    },
                    color = AZUL,
                    unidad = "%"
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Últimas mediciones",
                    fontWeight = FontWeight.Bold,
                    color = AZUL
                )

                Spacer(Modifier.height(8.dp))

                if (sensores.isEmpty()) {

                    Text(
                        "Todavía no hay registros de sensores."
                    )

                } else {

                    sensores.asReversed()
                        .take(30)
                        .forEach { registro ->

                            HistorialItem(
                                titulo =
                                    "Temperatura: ${
                                        String.format(
                                            Locale.US,
                                            "%.1f",
                                            registro.temperatura
                                        )
                                    } °C",
                                detalle =
                                    "Humedad: ${
                                        String.format(
                                            Locale.US,
                                            "%.1f",
                                            registro.humedad
                                        )
                                    } %",
                                fecha = registro.fecha,
                                color = AZUL
                            )
                        }
                }
            }

            // =================================================
            // HISTORIAL DE ALERTAS
            // =================================================

            "alertas" -> {

                Text(
                    "Eventos de temperatura",
                    fontWeight = FontWeight.Bold,
                    color = AZUL,
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(12.dp))

                if (alertas.isEmpty()) {

                    Text(
                        "No se han registrado alertas."
                    )

                } else {

                    alertas.forEach { registro ->

                        val color = when (
                            registro.tipo
                        ) {

                            "critica" -> ROJO

                            "advertencia" -> AMARILLO

                            "recuperacion" -> VERDE

                            else -> AZUL
                        }

                        HistorialItem(
                            titulo =
                                registro.tipo.uppercase(
                                    Locale.getDefault()
                                ),
                            detalle =
                                "Temperatura: ${
                                    String.format(
                                        Locale.US,
                                        "%.1f",
                                        registro.temperatura
                                    )
                                } °C",
                            fecha = registro.fecha,
                            color = color
                        )
                    }
                }
            }

            // =================================================
            // HISTORIAL DEL VENTILADOR
            // =================================================

            "ventilador" -> {

                Text(
                    "Cambios del ventilador",
                    fontWeight = FontWeight.Bold,
                    color = AZUL,
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(12.dp))

                if (ventilador.isEmpty()) {

                    Text(
                        "No existen cambios registrados."
                    )

                } else {

                    ventilador.forEach { registro ->

                        HistorialItem(
                            titulo =
                                if (registro.encendido) {
                                    "VENTILADOR ENCENDIDO"
                                } else {
                                    "VENTILADOR APAGADO"
                                },
                            detalle = buildString {

                                append(
                                    "Usuario: ${registro.uid}"
                                )

                                registro.temperatura?.let {

                                    append(
                                        " · Temperatura: ${
                                            String.format(
                                                Locale.US,
                                                "%.1f",
                                                it
                                            )
                                        } °C"
                                    )
                                }
                            },
                            fecha = registro.fecha,
                            color =
                                if (registro.encendido) {
                                    VERDE
                                } else {
                                    ROJO
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// =====================================================
// TARJETA DE REGISTRO HISTORICO
// =====================================================

@Composable
private fun HistorialItem(
    titulo: String,
    detalle: String,
    fecha: Long,
    color: Color
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = color.copy(alpha = 0.07f),
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Text(
                titulo,
                color = color,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                detalle,
                style =
                    MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(5.dp))

            Text(
                if (fecha > 0L) {
                    fechaLegible(fecha)
                } else {
                    "Fecha no disponible"
                },
                style =
                    MaterialTheme.typography.labelSmall
            )
        }
    }
}

// =====================================================
// CONFIGURACION DE LIMITES TERMICOS
// =====================================================

@Composable
private fun PantallaConfiguracion(
    actuales: LimitesTermicos,
    guardando: Boolean,
    mensaje: String,
    modifier: Modifier = Modifier,
    onVolver: () -> Unit,
    onGuardar: (LimitesTermicos) -> Unit
) {

    var advertencia by remember(actuales) {
        mutableStateOf(
            actuales.advertencia.toString()
        )
    }

    var critica by remember(actuales) {
        mutableStateOf(
            actuales.critica.toString()
        )
    }

    var recuperacion by remember(actuales) {
        mutableStateOf(
            actuales.recuperacion.toString()
        )
    }

    var errorValidacion by remember {
        mutableStateOf("")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        EncabezadoSeccion(
            titulo = "Configuración térmica",
            onVolver = onVolver
        )

        Spacer(Modifier.height(14.dp))

        Text(
            "Límites de temperatura",
            color = AZUL,
            fontWeight = FontWeight.Bold,
            style =
                MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Configura los valores utilizados para mostrar advertencias, alertas críticas y recuperación térmica.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(16.dp))

        // =================================================
        // ADVERTENCIA
        // =================================================

        OutlinedTextField(
            value = advertencia,
            onValueChange = {
                advertencia = it
                errorValidacion = ""
            },
            label = {
                Text("Advertencia (°C)")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !guardando,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            )
        )

        Spacer(Modifier.height(12.dp))

        // =================================================
        // CRITICA
        // =================================================

        OutlinedTextField(
            value = critica,
            onValueChange = {
                critica = it
                errorValidacion = ""
            },
            label = {
                Text("Temperatura crítica (°C)")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !guardando,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            )
        )

        Spacer(Modifier.height(12.dp))

        // =================================================
        // RECUPERACION
        // =================================================

        OutlinedTextField(
            value = recuperacion,
            onValueChange = {
                recuperacion = it
                errorValidacion = ""
            },
            label = {
                Text("Temperatura de recuperación (°C)")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !guardando,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            )
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            color = AZUL.copy(alpha = 0.07f),
            shape = RoundedCornerShape(12.dp)
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                Text(
                    "Condiciones de configuración",
                    fontWeight = FontWeight.Bold,
                    color = AZUL
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "• Recuperación menor que advertencia."
                )

                Text(
                    "• Advertencia menor que crítica."
                )

                Text(
                    "• Valores entre 0 y 60 °C."
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        BotonColorAnimado(
            texto =
                if (guardando) {
                    "GUARDANDO..."
                } else {
                    "GUARDAR CONFIGURACIÓN"
                },
            color = VERDE,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !guardando,
            onClick = {

                val a = advertencia
                    .replace(',', '.')
                    .toDoubleOrNull()

                val c = critica
                    .replace(',', '.')
                    .toDoubleOrNull()

                val r = recuperacion
                    .replace(',', '.')
                    .toDoubleOrNull()

                errorValidacion = when {

                    a == null ||
                            c == null ||
                            r == null ->
                        "Debes ingresar números válidos."

                    !a.isFinite() ||
                            !c.isFinite() ||
                            !r.isFinite() ->
                        "Los valores deben ser finitos."

                    r < 0.0 ||
                            a < 0.0 ||
                            c > 60.0 ->
                        "Los valores deben estar entre 0 y 60 °C."

                    r >= a ->
                        "La recuperación debe ser menor que la advertencia."

                    a >= c ->
                        "La advertencia debe ser menor que la temperatura crítica."

                    else -> ""
                }

                if (
                    errorValidacion.isBlank() &&
                    a != null &&
                    c != null &&
                    r != null
                ) {

                    onGuardar(
                        LimitesTermicos(
                            advertencia = a,
                            critica = c,
                            recuperacion = r
                        )
                    )
                }
            }
        )

        if (guardando) {

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorValidacion.isNotBlank()) {

            Spacer(Modifier.height(12.dp))

            Aviso(errorValidacion, ROJO)
        }

        if (mensaje.isNotBlank()) {

            Spacer(Modifier.height(12.dp))

            Aviso(mensaje, AZUL)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Los límites se almacenan en Firebase Realtime Database.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
    }
}

// =====================================================
// FIN DE MAINACTIVITY.KT
// INVERNADERO IoT - VERSION 7.23
// FIN DE LA PARTE 4 DE 4
// =====================================================
