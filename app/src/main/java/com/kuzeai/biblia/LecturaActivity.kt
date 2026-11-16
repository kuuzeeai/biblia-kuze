package com.kuzeai.biblia

import android.os.Bundle
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class LecturaActivity : AppCompatActivity() {

    private lateinit var libro: Libro
    private var indiceLibro = 0
    private var capitulo = 0
    private lateinit var textoView: TextView
    private lateinit var tituloView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lectura)

        val libros = Biblia.cargar(this)
        indiceLibro = intent.getIntExtra("libro", 0)
        capitulo = intent.getIntExtra("capitulo", 0)
        libro = libros[indiceLibro]

        textoView = findViewById(R.id.texto_biblia)
        tituloView = findViewById(R.id.titulo_lectura)

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 100 && abs(dx) > abs(vy)) {
                    if (dx < 0) siguiente() else anterior()
                    return true
                }
                return false
            }
        })
        textoView.setOnTouchListener { _, event -> detector.onTouchEvent(event) }

        findViewById<View>(R.id.btn_anterior).setOnClickListener { anterior() }
        findViewById<View>(R.id.btn_siguiente).setOnClickListener { siguiente() }
        findViewById<View>(R.id.btn_marcador).setOnClickListener {
            val agregado = Preferencias.alternarMarcador(this, claveMarcador())
            Toast.makeText(
                this,
                if (agregado) getString(R.string.guardado_marcador)
                else getString(R.string.quitado_marcador),
                Toast.LENGTH_SHORT
            ).show()
            actualizarBotonMarcador()
        }
        findViewById<View>(R.id.btn_mas).setOnClickListener { cambiarTamano(2) }
        findViewById<View>(R.id.btn_menos).setOnClickListener { cambiarTamano(-2) }

        findViewById<View>(R.id.boton_menu).setOnClickListener {
            mostrarMenuOpciones(libros)
        }

        aplicarTamano()
        mostrar()
    }

    private fun cambiarTamano(delta: Int) {
        val nuevo = (Preferencias.tamanoLetra(this) + delta).coerceIn(14, 34)
        Preferencias.guardarTamano(this, nuevo)
        aplicarTamano()
    }

    private fun mostrarMenuOpciones(libros: List<Libro>) {
        val opciones = arrayOf(
            if (claveMarcador() in Preferencias.marcadores(this)) "Quitar marcador de este capítulo" else "Guardar marcador de este capítulo",
            "Ver todos los marcadores"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(tituloView.text)
            .setItems(opciones) { _, which ->
                if (which == 0) {
                    val agregado = Preferencias.alternarMarcador(this, claveMarcador())
                    Toast.makeText(
                        this,
                        if (agregado) getString(R.string.guardado_marcador) else getString(R.string.quitado_marcador),
                        Toast.LENGTH_SHORT
                    ).show()
                    actualizarBotonMarcador()
                } else {
                    mostrarMarcadores(libros)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarMarcadores(libros: List<Libro>) {
        val claves = Preferencias.marcadores(this).sorted()
        if (claves.isEmpty()) {
            Toast.makeText(this, getString(R.string.sin_marcadores), Toast.LENGTH_SHORT).show()
            return
        }
        val nombres = claves.map { it.replace('|', ' ') }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
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

    private fun claveMarcador() = "${libro.nombre}|${capitulo + 1}"

    private fun aplicarTamano() {
        textoView.textSize = Preferencias.tamanoLetra(this).toFloat()
    }

    private fun mostrar() {
        tituloView.text = "${libro.nombre} ${capitulo + 1}"
        val versiculos = libro.capitulos[capitulo]
        val sb = StringBuilder()
        for ((num, texto) in versiculos) {
            sb.append("$num. $texto\n\n")
        }
        textoView.text = sb.toString()
        actualizarBotonMarcador()
    }

    private fun actualizarBotonMarcador() {
        val btn = findViewById<TextView>(R.id.btn_marcador)
        if (claveMarcador() in Preferencias.marcadores(this)) {
            btn.text = "★ Marcado"
        } else {
            btn.text = "★ Marcar"
        }
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
