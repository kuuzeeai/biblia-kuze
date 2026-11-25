package com.kuzeai.biblia

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Lector de voz (offline) basado en el motor TextToSpeech de Android.
 *
 * - Lee el capítulo versículo por versículo (el motor TTS tiene un límite de ~4000
 *   caracteres por llamada y capítulos como el Salmo 119 lo superan).
 * - Intenta usar una voz masculina en español. Android no expone el género de una voz,
 *   así que se usa una heurística y, si no acierta, el usuario puede elegir otra voz
 *   (ver [vocesEspanol] / [usarVoz]).
 */
class LectorVoz {

    private var tts: TextToSpeech? = null
    private var listo = false
    private val main = Handler(Looper.getMainLooper())

    // Cada lectura nueva incrementa la "generación"; los eventos de lecturas viejas se ignoran.
    private var generacion = 0
    private var ultimoVersiculo = -1
    private var alVersiculo: ((Int) -> Unit)? = null
    private var alTerminar: (() -> Unit)? = null
    private var alError: (() -> Unit)? = null
    private var reproduciendo = false

    private var perfil: Perfil = PERFILES.first()

    fun inicializar(context: Context, alListo: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
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

    /** Voces en español instaladas (primero las que funcionan sin internet). */
    fun vocesEspanol(): List<Voice> {
        val todas = try { tts?.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val es = todas.filter { it.locale.language == "es" }
        val sinRed = es.filter { !it.isNetworkConnectionRequired }
        return if (sinRed.isNotEmpty()) sinRed else es
    }

    /** Voces del dispositivo ordenadas para el género pedido (las que mejor encajan, primero). */
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
        var tono = perfil.tono
        if (elegida != null) {
            t.voice = elegida
        } else if (voces.isNotEmpty()) {
            val mejor = voces.first()
            t.voice = mejor
            // Si la voz no parece del género pedido, ajustar el tono para acercarla
            if (pareceMasculina(mejor) != hombre) tono *= if (hombre) 0.85f else 1.15f
        } else {
            val r = t.setLanguage(Locale("es", "MX"))
            if (r < TextToSpeech.LANG_AVAILABLE) t.setLanguage(Locale("es", "ES"))
            tono *= if (hombre) 0.85f else 1.0f
        }
        t.setPitch(tono.coerceIn(0.5f, 2.0f))
        t.setSpeechRate(perfil.velocidad)
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
     * Lee un título y luego los versículos desde el índice [desde].
     * [alVersiculo] se llama (en el hilo principal) al empezar cada versículo.
     * [alTerminar] se llama al acabar el último. [alError] si el motor falla.
     */
    fun leer(
        titulo: String,
        versiculos: List<String>,
        desde: Int = 0,
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
        ultimoVersiculo = versiculos.lastIndex
        reproduciendo = true

        var primero = true
        fun encolar(texto: String, id: String) {
            val modo = if (primero) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            primero = false
            t.speak(texto, modo, Bundle(), id)
        }
        if (desde == 0) encolar(titulo, "g${gen}_titulo")
        for (i in desde..versiculos.lastIndex) encolar(versiculos[i], "g${gen}_v$i")
        if (primero) { reproduciendo = false; alTerminar() } // no había nada que leer
    }

    fun detener() {
        generacion++ // invalida eventos pendientes
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
            val (gen, idx) = analizar(utteranceId) ?: return
            if (gen != generacion || idx < 0) return
            main.post { if (gen == generacion) alVersiculo?.invoke(idx) }
        }

        override fun onDone(utteranceId: String?) {
            val (gen, idx) = analizar(utteranceId) ?: return
            if (gen != generacion) return
            if (idx == ultimoVersiculo) {
                main.post {
                    if (gen == generacion) {
                        reproduciendo = false
                        alTerminar?.invoke()
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (gen, _) = analizar(utteranceId) ?: return
            if (gen != generacion) return
            main.post {
                if (gen == generacion) {
                    reproduciendo = false
                    alError?.invoke()
                }
            }
        }
    }

    /** "g3_v12" -> (3, 12); "g3_titulo" -> (3, -1); otro -> null */
    private fun analizar(id: String?): Pair<Int, Int>? {
        if (id == null || !id.startsWith("g")) return null
        val partes = id.substring(1).split('_')
        val gen = partes.getOrNull(0)?.toIntOrNull() ?: return null
        val idx = partes.getOrNull(1)?.takeIf { it.startsWith("v") }?.drop(1)?.toIntOrNull() ?: -1
        return gen to idx
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
        val PERFILES = listOf(
            Perfil("narrador", "Narrador", true, 1.0f, 0.90f),
            Perfil("profeta", "Profeta (grave y solemne)", true, 0.75f, 0.80f),
            Perfil("moises", "Estilo Moisés (anciano, pausado)", true, 0.70f, 0.75f),
            Perfil("david", "Estilo David (cálido)", true, 0.90f, 0.90f),
            Perfil("timoteo", "Estilo Timoteo (joven)", true, 1.10f, 1.00f),
            Perfil("narradora", "Narradora", false, 1.0f, 0.90f),
            Perfil("debora", "Estilo Débora (firme)", false, 0.90f, 0.95f),
            Perfil("maria", "Estilo María (suave)", false, 1.10f, 0.85f),
            Perfil("rut", "Estilo Rut (tierna)", false, 1.05f, 0.85f)
        )

        // Códigos de voces de Google TTS que, según reportes de usuarios, son masculinas.
        // Es una pista, no una garantía: varía según el dispositivo y la versión de Google TTS.
        private val PISTAS_MASCULINAS = listOf("-esd-", "-eed-")
    }
}
