package com.kuzeai.biblia

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Letrero con un versículo al azar la primera vez que se abre la app cada día. */
object VersiculoDelDia {

    private class Elegido(
        val indiceLibro: Int,
        val libro: String,
        val capitulo: Int, // desde 0
        val numero: Int,
        val texto: String
    )

    fun mostrarSiCorresponde(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (Preferencias.diaVersiculo(activity) == hoy) return
        val elegido = elegir(activity) ?: return
        Preferencias.guardarDiaVersiculo(activity, hoy)
        mostrar(activity, elegido)
    }

    private fun elegir(context: Context): Elegido? {
        val libros = Biblia.cargar(context)
        val total = libros.fold(0) { a, l -> a + l.capitulos.fold(0) { b, c -> b + c.size } }
        if (total == 0) return null
        var ultimo: Elegido? = null
        // Preferir versículos de longitud agradable para leer en un letrero
        for (intento in 1..40) {
            val e = porIndice(libros, Random.nextInt(total)) ?: continue
            ultimo = e
            if (e.texto.length in 50..280) return e
        }
        return ultimo
    }

    private fun porIndice(libros: List<Libro>, indice: Int): Elegido? {
        var r = indice
        for ((i, libro) in libros.withIndex()) {
            for ((c, versiculos) in libro.capitulos.withIndex()) {
                if (r < versiculos.size) {
                    val (numero, texto) = versiculos[r]
                    return Elegido(i, libro.nombre, c, numero, texto)
                }
                r -= versiculos.size
            }
        }
        return null
    }

    private fun mostrar(activity: AppCompatActivity, v: Elegido) {
        val vista = LayoutInflater.from(activity).inflate(R.layout.dialog_versiculo_dia, null)
        val referencia = "${v.libro} ${v.capitulo + 1}:${v.numero}"
        vista.findViewById<TextView>(R.id.dia_referencia).text = referencia
        vista.findViewById<TextView>(R.id.dia_texto).text = "“${v.texto}”"
        val botonAudio = vista.findViewById<TextView>(R.id.boton_dia_audio)

        // Lee con la última voz elegida (el lector carga el perfil y la voz guardados)
        val lector = LectorVoz()
        var activo = true
        var sonando = false

        fun actualizarBoton() {
            botonAudio.text = if (sonando) "⏸  Pausar" else "▶  Escuchar"
        }

        fun hablar() {
            sonando = true
            actualizarBoton()
            lector.leer(
                titulo = "Versículo del día. ${v.libro}, capítulo ${v.capitulo + 1}, versículo ${v.numero}.",
                versiculos = listOf(v.texto),
                alVersiculo = {},
                alTerminar = { sonando = false; actualizarBoton() },
                alError = { sonando = false; actualizarBoton() }
            )
        }
        actualizarBoton()

        val dialogo: AlertDialog = AlertDialog.Builder(activity).setView(vista).create()
        dialogo.setOnDismissListener {
            activo = false
            lector.limpiar()
        }

        botonAudio.setOnClickListener {
            if (sonando) {
                lector.detener()
                sonando = false
                actualizarBoton()
            } else {
                hablar()
            }
        }
        vista.findViewById<TextView>(R.id.boton_dia_cerrar).setOnClickListener { dialogo.dismiss() }
        vista.findViewById<TextView>(R.id.boton_dia_leer).setOnClickListener {
            val intent = Intent(activity, LecturaActivity::class.java).apply {
                putExtra("libro", v.indiceLibro)
                putExtra("capitulo", v.capitulo)
                putExtra("versiculo", v.numero)
            }
            dialogo.dismiss()
            activity.startActivity(intent)
            if (activity is LecturaActivity) activity.finish()
        }

        dialogo.show()
        dialogo.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        lector.inicializar(activity) { ok -> if (ok && activo) hablar() }
    }
}
