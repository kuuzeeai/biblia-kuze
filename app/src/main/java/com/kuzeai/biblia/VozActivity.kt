package com.kuzeai.biblia

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pantalla para quien no lee: una bola. Se toca, dice "Te escucho", la persona pide
 * "Génesis capítulo 4 versículo 10" y la app lo lee. Tocar la bola de nuevo detiene.
 * Los botones laterales retroceden o adelantan unos 10 segundos.
 */
class VozActivity : AppCompatActivity() {

    private enum class Estado { REPOSO, AVISO, ESCUCHANDO, LEYENDO, PAUSADO }

    private val lector = LectorVoz()
    private lateinit var libros: List<Libro>
    private var reconocedor: SpeechRecognizer? = null

    private var estado = Estado.REPOSO
    private var ticketAviso = 0
    private var hayContenido = false

    private var libro = 0
    private var capitulo = 0
    private var verso = 0
    private var inicioVerso = 0L
    private var transcurrido = 0L

    private lateinit var bola: View
    private lateinit var icono: ImageView
    private lateinit var texto: TextView
    private var pulso: ObjectAnimator? = null

    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) pedirVoz()
            else avisar("Necesito permiso para usar el micrófono. Actívalo en los ajustes del teléfono.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voz)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        libros = Biblia.cargar(this)
        bola = findViewById(R.id.bola)
        icono = findViewById(R.id.icono)
        texto = findViewById(R.id.texto)

        lector.inicializar(this)

        bola.setOnClickListener { alTocarBola() }
        findViewById<View>(R.id.btn_atras).setOnClickListener { saltar(-10) }
        findViewById<View>(R.id.btn_adelante).setOnClickListener { saltar(10) }

        actualizarUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        reconocedor?.destroy()
        reconocedor = null
        lector.limpiar()
    }

    // ---------- Botones ----------

    private fun alTocarBola() {
        when (estado) {
            Estado.REPOSO, Estado.PAUSADO -> pedirVoz()
            Estado.AVISO -> { ticketAviso++; lector.detener(); reposo() }
            Estado.ESCUCHANDO -> { reconocedor?.cancel(); reposo() }
            Estado.LEYENDO -> parar()
        }
        actualizarUI()
    }

    private fun reposo() {
        estado = if (hayContenido) Estado.PAUSADO else Estado.REPOSO
    }

    private fun parar() {
        transcurrido = min(SystemClock.elapsedRealtime() - inicioVerso, duracion(verso))
        lector.detener()
        estado = Estado.PAUSADO
    }

    // ---------- Escuchar a la persona ----------

    private fun pedirVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            avisar("Este teléfono no tiene reconocimiento de voz.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermiso.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (estado == Estado.LEYENDO) parar()
        estado = Estado.AVISO
        val ticket = ++ticketAviso
        lector.decir("Te escucho. ¿Qué quieres escuchar?") {
            if (ticket == ticketAviso && estado == Estado.AVISO) empezarEscucha()
        }
        actualizarUI()
    }

    private fun empezarEscucha() {
        estado = Estado.ESCUCHANDO
        val r = reconocedor ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(oyente)
            reconocedor = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Espera ~1.5 s de silencio: así la persona termina su frase completa
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        r.startListening(intent)
        actualizarUI()
    }

    private val oyente = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (estado != Estado.ESCUCHANDO) return
            avisar(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No te escuché. Toca la bola e inténtalo otra vez."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER ->
                        "No pude entenderte. Revisa la conexión a internet."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Necesito permiso para usar el micrófono."
                    else -> "Algo salió mal. Toca la bola e inténtalo otra vez."
                }
            )
        }

        override fun onResults(results: Bundle?) {
            if (estado != Estado.ESCUCHANDO) return
            procesar(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
        }
    }

    private fun procesar(frases: List<String>?) {
        val lista = frases ?: emptyList()
        texto.text = lista.firstOrNull().orEmpty()
        var error: String? = null
        for (frase in lista) {
            val p = Interprete.interpretar(libros, frase) ?: continue
            if (p.error != null) {
                if (error == null) error = p.error
                continue
            }
            leerDesde(p.libro, p.capitulo, p.versiculo, conTitulo = true)
            return
        }
        avisar(error ?: "No entendí. Toca la bola y di, por ejemplo: Génesis capítulo tres.")
    }

    // ---------- Leer ----------

    private fun leerDesde(l: Int, c: Int, v: Int, conTitulo: Boolean) {
        libro = l; capitulo = c; verso = v
        inicioVerso = SystemClock.elapsedRealtime()
        transcurrido = 0L
        hayContenido = true
        estado = Estado.LEYENDO

        val lib = libros[l]
        val versos = lib.capitulos[c].map { it.second }
        lector.leer(
            titulo = "${lib.nombre}, capítulo ${c + 1}",
            versiculos = versos,
            desde = v,
            conTitulo = conTitulo,
            alVersiculo = { i ->
                verso = i
                inicioVerso = SystemClock.elapsedRealtime()
            },
            alTerminar = { siguienteCapitulo() },
            alError = { avisar("Hubo un problema con la voz del teléfono.") }
        )
        actualizarUI()
    }

    /** Al acabar un capítulo sigue con el siguiente (y luego con el siguiente libro). */
    private fun siguienteCapitulo() {
        val lib = libros[libro]
        verso = max(lib.capitulos[capitulo].size - 1, 0)
        transcurrido = duracion(verso)
        when {
            capitulo + 1 < lib.capitulos.size -> leerDesde(libro, capitulo + 1, 0, true)
            libro + 1 < libros.size -> leerDesde(libro + 1, 0, 0, true)
            else -> { estado = Estado.PAUSADO; actualizarUI() }
        }
    }

    // ---------- Retroceder / adelantar ----------

    /** Duración estimada de un versículo en milisegundos (el motor de voz no permite saltar por tiempo). */
    private fun duracion(v: Int): Long {
        val versos = libros[libro].capitulos[capitulo]
        if (v !in versos.indices) return 0L
        val velocidad = (lector.perfilActual().velocidad * Preferencias.velocidadLectura(this))
            .coerceIn(0.4f, 2.0f)
        return (versos[v].second.length / (15f * velocidad) * 1000f).toLong() + 600L
    }

    private fun saltar(segundos: Int) {
        if (!hayContenido) {
            avisar("Primero toca la bola y dime qué quieres escuchar.")
            return
        }
        val ahora = if (estado == Estado.LEYENDO)
            min(SystemClock.elapsedRealtime() - inicioVerso, duracion(verso))
        else transcurrido
        ticketAviso++
        reconocedor?.cancel()
        lector.detener()

        val total = libros[libro].capitulos[capitulo].size
        var restante = abs(segundos) * 1000L
        if (segundos < 0) {
            restante -= ahora
            var v = verso
            while (restante > 0 && v > 0) { v--; restante -= duracion(v) }
            leerDesde(libro, capitulo, v, conTitulo = false)
        } else {
            restante -= max(duracion(verso) - ahora, 0L)
            var v = verso + 1
            while (restante > 0 && v < total) { restante -= duracion(v); v++ }
            when {
                v < total -> leerDesde(libro, capitulo, v, conTitulo = false)
                capitulo + 1 < libros[libro].capitulos.size -> leerDesde(libro, capitulo + 1, 0, true)
                libro + 1 < libros.size -> leerDesde(libro + 1, 0, 0, true)
                else -> leerDesde(libro, capitulo, total - 1, conTitulo = false)
            }
        }
    }

    // ---------- Avisos y pantalla ----------

    /** Dice un mensaje en voz alta y vuelve a reposo. */
    private fun avisar(mensaje: String) {
        estado = Estado.AVISO
        val ticket = ++ticketAviso
        lector.decir(mensaje) {
            if (ticket == ticketAviso) { reposo(); actualizarUI() }
        }
        actualizarUI()
    }

    private fun actualizarUI() {
        val escuchando = estado == Estado.AVISO || estado == Estado.ESCUCHANDO
        val (c1, c2) = when {
            estado == Estado.LEYENDO -> "#FFB347" to "#B5651D"
            escuchando -> "#5FD38D" to "#1F7A4A"
            else -> "#4F8CFF" to "#1F3F8A"
        }
        val densidad = resources.displayMetrics.density
        bola.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 130f * densidad
            colors = intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
            setStroke((3 * densidad).toInt(), Color.parseColor("#88FFFFFF"))
        }
        icono.setImageResource(if (estado == Estado.LEYENDO) R.drawable.ic_pausa else R.drawable.ic_mic)
        bola.contentDescription =
            if (estado == Estado.LEYENDO) "Detener la lectura" else "Tocar para hablar"

        if (escuchando) {
            if (pulso == null) {
                pulso = ObjectAnimator.ofPropertyValuesHolder(
                    bola,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.09f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.09f)
                ).apply {
                    duration = 700
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
            }
        } else {
            pulso?.cancel()
            pulso = null
            bola.scaleX = 1f
            bola.scaleY = 1f
        }
    }
}
