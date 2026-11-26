package com.kuzeai.biblia

import android.view.View
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Navegador a pantalla completa dentro de la lectura: Libros → Capítulos → Versículos. */
class Navegador(
    private val activity: AppCompatActivity,
    private val alElegir: (libro: Int, capitulo: Int, versiculo: Int) -> Unit,
    private val alCambiar: (abierto: Boolean) -> Unit
) {
    companion object {
        private const val ANIM = 280L
    }

    private val raiz: View = activity.findViewById(R.id.navegador)
    private val titulo: TextView = activity.findViewById(R.id.nav_titulo)
    private val listaLibros: ListView = activity.findViewById(R.id.nav_libros)
    private val gridCapitulos: GridView = activity.findViewById(R.id.nav_capitulos)
    private val gridVersiculos: GridView = activity.findViewById(R.id.nav_versiculos)

    private var paso = 0 // 0 = libros, 1 = capítulos, 2 = versículos
    private var navLibro = 0
    private var navCap = 0

    val visible: Boolean get() = raiz.visibility == View.VISIBLE

    init {
        activity.findViewById<View>(R.id.nav_atras).setOnClickListener { atras() }
        listaLibros.setOnItemClickListener { _, _, pos, _ -> elegirLibro(pos) }
        gridCapitulos.setOnItemClickListener { _, _, pos, _ -> elegirCapitulo(pos) }
        gridVersiculos.setOnItemClickListener { _, _, pos, _ -> elegirVersiculo(pos) }
    }

    /** pasoInicial: 0 = libros, 1 = capítulos, 2 = versículos (del libro y capítulo que se están leyendo). */
    fun abrir(pasoInicial: Int, libroActual: Int, capituloActual: Int) {
        navLibro = libroActual
        navCap = capituloActual
        when (pasoInicial) {
            0 -> cargarLibros()
            1 -> cargarCapitulos()
            else -> cargarVersiculos()
        }
        val ancho = activity.resources.displayMetrics.widthPixels
        raiz.translationX = ancho.toFloat()
        raiz.visibility = View.VISIBLE
        raiz.animate().translationX(0f).setDuration(ANIM).start()
        alCambiar(true)
    }

    fun cerrar() {
        alCambiar(false)
        val ancho = activity.resources.displayMetrics.widthPixels
        raiz.animate().translationX(ancho.toFloat()).setDuration(ANIM)
            .withEndAction {
                raiz.visibility = View.GONE
                raiz.translationX = 0f
            }
            .start()
    }

    fun atras() {
        when (paso) {
            0 -> cerrar()
            1 -> cargarLibros()
            2 -> cargarCapitulos()
        }
    }

    private fun cargarLibros() {
        paso = 0
        titulo.text = "Libros"
        val libros = Biblia.cargar(activity)
        val nombres = libros.map { "${it.nombre}  (${it.capitulos.size})" }
        listaLibros.adapter = ArrayAdapter(activity, R.layout.item_lista, R.id.texto_item, nombres)
        mostrarSolo(listaLibros, desdeIzquierda = false)
    }

    private fun cargarCapitulos() {
        paso = 1
        val libro = Biblia.cargar(activity)[navLibro]
        titulo.text = "${libro.nombre} — Capítulos"
        val numeros = List(libro.capitulos.size) { "Capítulo ${it + 1}" }
        gridCapitulos.adapter = ArrayAdapter(activity, R.layout.item_capsula, R.id.texto_capsula, numeros)
        mostrarSolo(gridCapitulos, desdeIzquierda = true)
    }

    private fun cargarVersiculos() {
        paso = 2
        val libro = Biblia.cargar(activity)[navLibro]
        titulo.text = "${libro.nombre} ${navCap + 1} — Versículos"
        val nombres = List(libro.capitulos[navCap].size) { "Versículo ${it + 1}" }
        gridVersiculos.adapter = ArrayAdapter(activity, R.layout.item_capsula, R.id.texto_capsula, nombres)
        mostrarSolo(gridVersiculos, desdeIzquierda = true)
    }

    private fun mostrarSolo(entrante: View, desdeIzquierda: Boolean) {
        val ancho = activity.resources.displayMetrics.widthPixels
        for (v in listOf<View>(listaLibros, gridCapitulos, gridVersiculos)) {
            if (v != entrante) {
                v.visibility = View.GONE
                v.translationX = 0f
            }
        }
        entrante.translationX = if (desdeIzquierda) -ancho.toFloat() else ancho.toFloat()
        entrante.visibility = View.VISIBLE
        entrante.animate().translationX(0f).setDuration(ANIM).start()
    }

    private fun elegirLibro(pos: Int) {
        navLibro = pos
        navCap = 0
        cargarCapitulos()
    }

    private fun elegirCapitulo(pos: Int) {
        navCap = pos
        cargarVersiculos()
    }

    private fun elegirVersiculo(pos: Int) {
        val numero = Biblia.cargar(activity)[navLibro].capitulos[navCap][pos].first
        cerrar()
        alElegir(navLibro, navCap, numero)
    }
}
