package com.kuzeai.biblia

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Lector de voz basado en el motor TextToSpeech de Android.
 *
 * Para sonar lo más natural posible:
 * - Cada estilo (Narrador, Profeta, Moisés…) usa una voz distinta del dispositivo en lugar de
 *   deformar una sola voz con el tono (cambiar mucho el tono es lo que suena "robótico").
 * - Con internet se permiten las voces de red, que suelen ser de mayor calidad.
 * - El texto se lee por frases, con pausas cortas entre ellas y entre versículos.
 */
class LectorVoz {

    private var tts: TextToSpeech? = null
    private var listo = false
    private var contexto: Context? = null
    private val main = Handler(Looper.getMainLooper())

    // Cada lectura nueva incrementa la "generación"; los eventos de lecturas viejas se ignoran.
    private var generacion = 0
    private var ultimoVersiculo = -1
    private var ultimoFragmento = 0
    private var alVersiculo: ((Int) -> Unit)? = null
    private var alTerminar: (() -> Unit)? = null
    private var alError: (() -> Unit)? = null
    private var reproduciendo = false
    private var alAviso: (() -> Unit)? = null
    var ultimoError = 0
        private set

    private var perfil: Perfil = PERFILES.first()

    fun inicializar(context: Context, alListo: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
        contexto = app
        tts = TextToSpeech(app) { status ->
            listo = status == TextToSpeech.SUCCESS
            if (listo) {
                tts?.setOnUtteranceProgressListener(escuchador)
                perfil = perfilGuardado(app)
                aplicar(app)
            }
            main.post { alListo?.invoke(listo) }
        }
    }

    // ---------- Perfiles y selección de voz ----------

    fun perfilActual(): Perfil = perfil

    private fun perfilGuardado(context: Context): Perfil =
        PERFILES.firstOrNull { it.id == Preferencias.perfilVoz(context) } ?: PERFILES.first()

    /** Cambia el estilo de lectura (narrador, profeta, mujer…) y lo guarda. */
    fun usarPerfil(context: Context, nuevo: Perfil) {
        perfil = nuevo
        Preferencias.guardarPerfilVoz(context, nuevo.id)
        aplicar(context)
    }

    /** Cambia la velocidad general (1.0 = la del estilo) y la guarda. */
    fun cambiarVelocidad(context: Context, valor: Float) {
        Preferencias.guardarVelocidad(context, valor)
        aplicar(context)
    }

    private fun hayInternet(): Boolean = try {
        val cm = contexto?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) false
        else caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        false
    }

    /**
     * Voces en español disponibles. Sin internet solo las instaladas en el celular;
     * con internet también las de red, que suelen sonar mejor.
     */
    fun vocesEspanol(): List<Voice> {
        val todas = try { tts?.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val es = todas.filter { it.locale.language == "es" }
        val sinRed = es.filter { !it.isNetworkConnectionRequired }
        // Siempre se prefieren las voces instaladas en el teléfono (funcionan sin internet).
        // Las de red solo se usan si no hay ninguna instalada.
        return if (sinRed.isEmpty()) es else sinRed
    }

    /** Voces ordenadas para el género pedido: mejor encaje y mayor calidad primero. */
    fun vocesPara(hombre: Boolean): List<Voice> =
        vocesEspanol().sortedByDescending { puntaje(it, hombre) }

    /** Pista (no garantía) de que la voz es masculina. */
    fun pareceMasculina(v: Voice): Boolean {
        val n = v.name.lowercase()
        if ("female" in n || "mujer" in n || "femenin" in n) return false
        return "male" in n || "hombre" in n || "masculin" in n ||
            PISTAS_MASCULINAS.any { it in n }
    }

    private fun puntaje(v: Voice, hombre: Boolean): Int {
        var p = 0
        if (pareceMasculina(v) == hombre) p += 10
        // La RVR1960 se usa en Latinoamérica: preferir acentos latinos
        if (v.locale.country in setOf("US", "MX", "CO", "AR", "419")) p += 3
        // Calidad declarada por el motor (100 = muy baja ... 500 = muy alta)
        p += v.quality / 100
        if (!v.isNetworkConnectionRequired) p += 1
        return p
    }

    /** Aplica al motor la voz, el tono y la velocidad del perfil actual. */
    private fun aplicar(context: Context) {
        val t = tts ?: return
        val hombre = perfil.hombre
        val voces = vocesPara(hombre)
        val guardada = Preferencias.vozDispositivo(context, hombre)
        val elegida = voces.firstOrNull { it.name == guardada }
        var tono = 1.0f + (perfil.tono - 1.0f) * EFECTO_TONO // tono suavizado: menos artificial

        if (elegida != null) {
            t.voice = elegida
        } else if (voces.isNotEmpty()) {
            // Cada estilo usa una voz distinta (entre las que encajan con el género), si hay varias
            val candidatas = voces.filter { pareceMasculina(it) == hombre }.ifEmpty { voces }
            val posicion = PERFILES.filter { it.hombre == hombre }.indexOfFirst { it.id == perfil.id }
            val voz = candidatas[posicion.coerceAtLeast(0) % candidatas.size]
            t.voice = voz
            // Solo si la voz no parece del género pedido se acerca con el tono
            if (pareceMasculina(voz) != hombre) tono *= if (hombre) 0.85f else 1.1f
        } else {
            val r = t.setLanguage(Locale("es", "MX"))
            if (r < TextToSpeech.LANG_AVAILABLE) t.setLanguage(Locale("es", "ES"))
            if (hombre) tono *= 0.9f
        }
        t.setPitch(tono.coerceIn(0.6f, 1.4f))
        val velocidad = perfil.velocidad * Preferencias.velocidadLectura(context)
        t.setSpeechRate(velocidad.coerceIn(0.4f, 2.0f))
    }

    /** El usuario eligió una voz concreta del dispositivo para el género del perfil actual. */
    fun usarVoz(context: Context, voz: Voice) {
        Preferencias.guardarVozDispositivo(context, perfil.hombre, voz.name)
        aplicar(context)
    }

    /** Frase corta para probar una voz. */
    fun probar(frase: String) {
        val t = tts ?: return
        if (!listo) return
        generacion++
        reproduciendo = false
        t.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "prueba")
    }

    // ---------- Reproducción ----------

    /**
     * Lee un título y luego los versículos desde el índice [desde] hasta [hasta].
     * [alVersiculo] se llama (en el hilo principal) al empezar cada versículo.
     * [alTerminar] se llama al acabar el último. [alError] si el motor falla.
     */
    fun leer(
        titulo: String,
        versiculos: List<String>,
        desde: Int = 0,
        hasta: Int = versiculos.lastIndex,
        conTitulo: Boolean = true,
        alVersiculo: (Int) -> Unit,
        alTerminar: () -> Unit,
        alError: () -> Unit
    ) {
        val t = tts
        if (t == null || !listo) { alError(); return }

        generacion++
        val gen = generacion
        this.alVersiculo = alVersiculo
        this.alTerminar = alTerminar
        this.alError = alError
        ultimoVersiculo = hasta
        ultimoFragmento = 0
        reproduciendo = true

        var primero = true
        fun hablar(texto: String, id: String) {
            val modo = if (primero) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            primero = false
            t.speak(texto, modo, Bundle(), id)
        }
        fun pausa(ms: Long) {
            if (!primero) t.playSilentUtterance(ms, TextToSpeech.QUEUE_ADD, "g${gen}_p")
        }

        if (desde == 0 && conTitulo) {
            hablar(titulo, "g${gen}_titulo")
            pausa(600L)
        }
        for (i in desde..hasta) {
            val fragmentos = fragmentar(versiculos[i])
            fragmentos.forEachIndexed { j, f ->
                hablar(f.texto, "g${gen}_v${i}_f$j")
                if (i == hasta && j == fragmentos.lastIndex) {
                    ultimoFragmento = j // el final de todo: sin pausa después
                } else {
                    pausa(if (j == fragmentos.lastIndex) 500L else f.pausa)
                }
            }
        }
        if (primero) { reproduciendo = false; alTerminar() } // no había nada que leer
    }

    private class Fragmento(val texto: String, val pausa: Long)

    /** Divide un versículo en frases para darle ritmo natural (con pausas entre ellas). */
    private fun fragmentar(texto: String): List<Fragmento> {
        val limpio = texto.trim()
        val resultado = mutableListOf<Fragmento>()
        var acumulado = ""
        for (parte in Regex("(?<=[.;:?!])\\s+").split(limpio)) {
            acumulado = if (acumulado.isEmpty()) parte else "$acumulado $parte"
            if (acumulado.length < 10) continue // muy corto: unir con lo siguiente
            val pausa = if (acumulado.endsWith(";") || acumulado.endsWith(":")) 250L else 350L
            resultado.add(Fragmento(acumulado, pausa))
            acumulado = ""
        }
        if (acumulado.isNotEmpty()) resultado.add(Fragmento(acumulado, 350L))
        if (resultado.isEmpty()) resultado.add(Fragmento(limpio, 350L))
        return resultado
    }

    /** Dice una frase suelta (un aviso) y llama a [alTerminarAviso] cuando termina de hablar. */
    fun decir(texto: String, alTerminarAviso: () -> Unit) {
        val t = tts
        if (t == null || !listo) { main.post { alTerminarAviso() }; return }
        generacion++
        reproduciendo = false
        alAviso = alTerminarAviso
        t.speak(texto, TextToSpeech.QUEUE_FLUSH, Bundle(), "aviso")
    }

    /** Resumen del estado de la voz, para saber por qué no habla. */
    fun diagnostico(): String {
        val todas = try { tts?.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val es = todas.filter { it.locale.language == "es" }
        val sinRed = es.count { !it.isNetworkConnectionRequired }
        return "Voz ${if (listo) "lista" else "NO disponible"} · español: ${es.size}, sin internet: $sinRed · error $ultimoError"
    }

    fun detener() {
        generacion++ // invalida eventos pendientes
        alAviso = null
        reproduciendo = false
        tts?.stop()
    }

    fun estaReproduciendo(): Boolean = reproduciendo

    fun limpiar() {
        detener()
        tts?.shutdown()
        tts = null
        listo = false
    }

    private val escuchador = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val e = analizar(utteranceId) ?: return
            if (e.gen != generacion || e.versiculo < 0 || e.fragmento != 0) return
            main.post { if (e.gen == generacion) alVersiculo?.invoke(e.versiculo) }
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == "aviso") {
                main.post { val cb = alAviso; alAviso = null; cb?.invoke() }
                return
            }
            val e = analizar(utteranceId) ?: return
            if (e.gen != generacion) return
            if (e.versiculo == ultimoVersiculo && e.fragmento == ultimoFragmento) {
                main.post {
                    if (e.gen == generacion) {
                        reproduciendo = false
                        alTerminar?.invoke()
                    }
                }
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            ultimoError = errorCode
            onError(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == "aviso") {
                main.post { val cb = alAviso; alAviso = null; cb?.invoke() }
                return
            }
            val e = analizar(utteranceId) ?: return
            if (e.gen != generacion) return
            main.post {
                if (e.gen == generacion) {
                    reproduciendo = false
                    alError?.invoke()
                }
            }
        }
    }

    private class Evento(val gen: Int, val versiculo: Int, val fragmento: Int)

    /** "g3_v12_f2" -> (3, 12, 2); "g3_titulo" o "g3_p" -> (3, -1, -1); otro -> null */
    private fun analizar(id: String?): Evento? {
        if (id == null || !id.startsWith("g")) return null
        val p = id.substring(1).split('_')
        val gen = p.getOrNull(0)?.toIntOrNull() ?: return null
        val v = p.getOrNull(1)?.takeIf { it.startsWith("v") }?.drop(1)?.toIntOrNull() ?: -1
        val f = p.getOrNull(2)?.takeIf { it.startsWith("f") }?.drop(1)?.toIntOrNull() ?: -1
        return Evento(gen, v, f)
    }

    /** Estilo de lectura: género + tono + velocidad. Son ajustes sobre la voz del dispositivo. */
    data class Perfil(
        val id: String,
        val nombre: String,
        val hombre: Boolean,
        val tono: Float,
        val velocidad: Float
    )

    companion object {
        // Cuánto del cambio de tono de cada estilo se aplica (1.0 = todo, 0 = nada).
        // Cambiar mucho el tono hace que la voz suene artificial, por eso se reduce a la mitad.
        private const val EFECTO_TONO = 0.5f

        val PERFILES = listOf(
            Perfil("narrador", "Narrador", true, 1.0f, 0.95f),
            Perfil("profeta", "Profeta", true, 0.80f, 0.88f),
            Perfil("moises", "Moisés", true, 0.75f, 0.82f),
            Perfil("david", "David", true, 0.92f, 0.95f),
            Perfil("timoteo", "Timoteo", true, 1.08f, 1.00f),
            Perfil("narradora", "Narradora", false, 1.0f, 0.95f),
            Perfil("debora", "Débora", false, 0.92f, 0.98f),
            Perfil("maria", "María", false, 1.08f, 0.90f),
            Perfil("rut", "Rut", false, 1.04f, 0.90f)
        )

        // Códigos de voces de Google TTS que, según reportes de usuarios, son masculinas.
        // Es una pista, no una garantía: varía según el dispositivo y la versión de Google TTS.
        private val PISTAS_MASCULINAS = listOf("-esd-", "-eed-")
    }
}
