package com.kuzeai.biblia

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import kotlin.math.abs
import kotlin.math.roundToInt

class LecturaActivity : AppCompatActivity() {

    private lateinit var libro: Libro
    private var indiceLibro = 0
    private var capitulo = 0
    private lateinit var textoView: TextView
    private lateinit var tituloView: TextView
    private lateinit var scroll: NestedScrollView
    private var baseZoom = 18f
    private var zoomPendiente = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lectura)

        val libros = Biblia.cargar(this)
        indiceLibro = intent.getIntExtra("libro", 0)
        capitulo = intent.getIntExtra("capitulo", 0)
        libro = libros[indiceLibro]

        textoView = findViewById(R.id.texto_biblia)
        tituloView = findViewById(R.id.titulo_lectura)
        scroll = findViewById(R.id.scroll_lectura)

        // Deslizar a la izquierda o derecha para cambiar de capítulo
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 80 && abs(dx) > abs(vy) * 1.4f) {
                    if (dx < 0) siguiente() else anterior()
                    return true
                }
                return false
            }
        })

        // Pellizcar con dos dedos para agrandar o achicar la letra
        val zoom = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                baseZoom = Preferencias.tamanoLetra(this@LecturaActivity).toFloat()
                zoomPendiente = false
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val nueva = (baseZoom * detector.scaleFactor).coerceIn(14f, 34f)
                textoView.textSize = nueva
                zoomPendiente = true
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (zoomPendiente) {
                    val final = textoView.textSize.roundToInt().coerceIn(14, 34)
                    Preferencias.guardarTamano(this@LecturaActivity, final)
                    zoomPendiente = false
                }
            }
        })

        textoView.setOnTouchListener { _, event ->
            zoom.onTouchEvent(event)
            detector.onTouchEvent(event) || zoom.isInProgress
        }

        findViewById<View>(R.id.fab_izquierda).setOnClickListener { mostrarNavegacion() }
        findViewById<View>(R.id.fab_derecha).setOnClickListener { siguiente() }
        // fab_audio: aún sin función (lector en voz alta pendiente)

        findViewById<View>(R.id.boton_menu).setOnClickListener { mostrarMenu() }

        aplicarTamano()
        mostrar()
    }

    private fun claveCapitulo() = "${libro.nombre}|${capitulo + 1}"

    private fun claveVersiculo(versiculo: Int) = "${libro.nombre}|${capitulo + 1}|$versiculo"

    private fun mostrarNavegacion() {
        AlertDialog.Builder(this)
            .setTitle("Navegar")
            .setItems(arrayOf("Libros", "Capítulo", "Versículo")) { _, which ->
                when (which) {
                    0 -> finish()
                    1 -> mostrarCapitulos()
                    2 -> mostrarVersiculos()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarMenu() {
        val capMarcado = claveCapitulo() in Preferencias.marcadores(this)
        val opciones = mutableListOf(
            if (capMarcado) getString(R.string.quitar_marcador_capitulo) else getString(R.string.marcar_capitulo),
            getString(R.string.marcar_versiculo),
            getString(R.string.marcadores)
        )
        AlertDialog.Builder(this)
            .setTitle(tituloView.text)
            .setItems(opciones.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val agregado = Preferencias.alternarMarcador(this, claveCapitulo())
                        Toast.makeText(
                            this,
                            if (agregado) getString(R.string.guardado_marcador) else getString(R.string.quitado_marcador),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> mostrarVersiculos()
                    2 -> mostrarMarcadores(Biblia.cargar(this))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarVersiculos() {
        val versiculos = libro.capitulos[capitulo]
        val nombres = versiculos.map { (num, texto) ->
            val recorte = if (texto.length > 40) texto.take(40) + "…" else texto
            "$num. $recorte"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.seleccionar_versiculo))
            .setItems(nombres) { _, which ->
                val num = versiculos[which].first
                val agregado = Preferencias.alternarMarcadorVersiculo(this, claveVersiculo(num))
                Toast.makeText(
                    this,
                    if (agregado) getString(R.string.guardado_marcador) else getString(R.string.quitado_marcador),
                    Toast.LENGTH_SHORT
                ).show()
                mostrar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarCapitulos() {
        val total = libro.capitulos.size
        val numeros = Array(total) { (it + 1).toString() }
        AlertDialog.Builder(this)
            .setTitle("${libro.nombre} — ${getString(R.string.capitulos)}")
            .setItems(numeros) { _, which -> irACapitulo(which) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun irACapitulo(destino: Int) {
        if (destino < 0 || destino > libro.capitulos.lastIndex) return
        capitulo = destino
        mostrar()
    }

    private fun mostrarMarcadores(libros: List<Libro>) {
        val claves = (Preferencias.marcadores(this) + Preferencias.marcadoresVersiculo(this)).sorted()
        if (claves.isEmpty()) {
            Toast.makeText(this, getString(R.string.sin_marcadores), Toast.LENGTH_SHORT).show()
            return
        }
        val nombres = claves.map {
            val partes = it.split('|')
            if (partes.size >= 3) "${partes[0]} ${partes[1]}:${partes[2]}"
            else it.replace('|', ' ')
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.marcadores))
            .setItems(nombres) { _, which ->
                val partes = claves[which].split('|')
                val idx = libros.indexOfFirst { it.nombre == partes[0] }
                if (idx >= 0) {
                    val cap = (partes.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                    val intent = Intent(this, LecturaActivity::class.java)
                    intent.putExtra("libro", idx)
                    intent.putExtra("capitulo", cap)
                    startActivity(intent)
                    finish()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun aplicarTamano() {
        textoView.textSize = Preferencias.tamanoLetra(this).toFloat()
    }

    private fun mostrar() {
        tituloView.text = "${libro.nombre} ${capitulo + 1}"
        val marcados = Preferencias.marcadoresVersiculo(this)
        val sb = SpannableStringBuilder()
        for ((num, texto) in libro.capitulos[capitulo]) {
            val inicio = sb.length
            sb.append("$num. $texto\n\n")
            if (claveVersiculo(num) in marcados) {
                sb.setSpan(
                    BackgroundColorSpan(getColor(R.color.fondo_marcado)),
                    inicio,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        textoView.text = sb
        scroll.smoothScrollTo(0, 0)
    }

    private fun anterior() {
        if (capitulo > 0) {
            capitulo--
        } else if (indiceLibro > 0) {
            indiceLibro--
            libro = Biblia.cargar(this)[indiceLibro]
            capitulo = libro.capitulos.lastIndex
        } else {
            Toast.makeText(this, "Estás al inicio", Toast.LENGTH_SHORT).show()
            return
        }
        mostrar()
    }

    private fun siguiente() {
        val libros = Biblia.cargar(this)
        if (capitulo < libro.capitulos.lastIndex) {
            capitulo++
        } else if (indiceLibro < libros.lastIndex) {
            indiceLibro++
            libro = libros[indiceLibro]
            capitulo = 0
        } else {
            Toast.makeText(this, "Estás al final", Toast.LENGTH_SHORT).show()
            return
        }
        mostrar()
    }
}
