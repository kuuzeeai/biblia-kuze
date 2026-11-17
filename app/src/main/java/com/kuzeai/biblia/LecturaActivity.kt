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
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ListView
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

    // Navegador de pantalla completa
    private lateinit var navegador: View
    private lateinit var navTitulo: TextView
    private lateinit var navLibros: ListView
    private lateinit var navCapitulos: GridView
    private lateinit var navVersiculos: GridView
    private var navPaso = 0 // 0 = libros, 1 = capítulos, 2 = versículos
    private var navLibro = 0
    private var navCap = 0

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

        navegador = findViewById(R.id.navegador)
        navTitulo = findViewById(R.id.nav_titulo)
        navLibros = findViewById(R.id.nav_libros)
        navCapitulos = findViewById(R.id.nav_capitulos)
        navVersiculos = findViewById(R.id.nav_versiculos)

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

        findViewById<View>(R.id.fab_izquierda).setOnClickListener { anterior() }
        findViewById<View>(R.id.fab_derecha).setOnClickListener { siguiente() }
        findViewById<View>(R.id.fab_navegar).setOnClickListener { abrirNavegador() }
        // fab_audio: aún sin función (lector en voz alta pendiente)

        findViewById<View>(R.id.boton_menu).setOnClickListener { mostrarMenu() }

        findViewById<View>(R.id.nav_atras).setOnClickListener { pasoAtras() }

        // Listas del navegador
        navLibros.setOnItemClickListener { _, _, pos, _ -> elegirLibro(pos) }
        navCapitulos.setOnItemClickListener { _, _, pos, _ -> elegirCapitulo(pos) }
        navVersiculos.setOnItemClickListener { _, _, pos, _ -> elegirVersiculo(pos) }

        aplicarTamano()
        mostrar()
    }

    // ---------- Navegador a pantalla completa ----------

    private fun abrirNavegador() {
        navLibro = indiceLibro
        navCap = capitulo
        navPaso = 0
        cargarLibros()
        val ancho = resources.displayMetrics.widthPixels
        navegador.translationX = ancho.toFloat()
        navegador.visibility = View.VISIBLE
        navegador.animate().translationX(0f).setDuration(280).start()
    }

    private fun cerrarNavegador() {
        val ancho = resources.displayMetrics.widthPixels
        navegador.animate().translationX(ancho.toFloat()).setDuration(240)
            .withEndAction {
                navegador.visibility = View.GONE
                navegador.translationX = 0f
            }
            .start()
    }

    private fun cargarLibros() {
        navPaso = 0
        navTitulo.text = "Libros"
        val libros = Biblia.cargar(this)
        val nombres = libros.mapIndexed { i, l -> "${l.nombre}  (${l.capitulos.size})" }
        navLibros.adapter = ArrayAdapter(this, R.layout.item_lista, R.id.texto_item, nombres)
        cambiarVista(navLibros, null, desdeIzquierda = false)
        cerrarCapitulos()
    }

    private fun cargarCapitulos() {
        navPaso = 1
        navTitulo.text = "${Biblia.cargar(this)[navLibro].nombre} — Capítulos"
        val total = Biblia.cargar(this)[navLibro].capitulos.size
        val numeros = Array(total) { "Capítulo ${it + 1}" }.toList()
        navCapitulos.adapter = ArrayAdapter(this, R.layout.item_capsula, R.id.texto_capsula, numeros)
        cambiarVista(navCapitulos, navLibros, desdeIzquierda = true)
        cerrarVersiculos()
    }

    private fun cargarVersiculos() {
        navPaso = 2
        val libro = Biblia.cargar(this)[navLibro]
        navTitulo.text = "${libro.nombre} ${navCap + 1} — Versículos"
        val versiculos = libro.capitulos[navCap]
        val nombres = versiculos.map { (num, texto) -> "$num. $texto" }
        navVersiculos.adapter = ArrayAdapter(this, R.layout.item_capsula, R.id.texto_capsula, nombres)
        cambiarVista(navVersiculos, navCapitulos, desdeIzquierda = true)
    }

    private fun cambiarVista(entrante: View, saliente: View?, desdeIzquierda: Boolean) {
        saliente?.visibility = View.GONE
        val ancho = resources.displayMetrics.widthPixels
        entrante.translationX = if (desdeIzquierda) -ancho.toFloat() else ancho.toFloat()
        entrante.visibility = View.VISIBLE
        entrante.animate().translationX(0f).setDuration(280).start()
    }

    private fun cerrarCapitulos() {
        navCapitulos.visibility = View.GONE
        navCapitulos.translationX = 0f
    }

    private fun cerrarVersiculos() {
        navVersiculos.visibility = View.GONE
        navVersiculos.translationX = 0f
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
        val numero = Biblia.cargar(this)[navLibro].capitulos[navCap][pos].first
        cerrarNavegador()
        irALibroCapituloVersiculo(navLibro, navCap, numero)
    }

    private fun pasoAtras() {
        when (navPaso) {
            0 -> cerrarNavegador()
            1 -> cargarLibros()
            2 -> cargarCapitulos()
        }
    }

    private fun irALibroCapituloVersiculo(nLibro: Int, nCap: Int, nVersiculo: Int) {
        indiceLibro = nLibro
        capitulo = nCap
        libro = Biblia.cargar(this)[nLibro]
        mostrar(nVersiculo)
    }

    // ---------- Menú del lápiz (marcar) ----------

    private fun claveCapitulo() = "${libro.nombre}|${capitulo + 1}"

    private fun claveVersiculo(versiculo: Int) = "${libro.nombre}|${capitulo + 1}|$versiculo"

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
                    1 -> mostrarVersiculosParaMarcar()
                    2 -> mostrarMarcadores(Biblia.cargar(this))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarVersiculosParaMarcar() {
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

    // ---------- Texto ----------

    private fun aplicarTamano() {
        textoView.textSize = Preferencias.tamanoLetra(this).toFloat()
    }

    private fun mostrar(versiculoDestino: Int? = null) {
        tituloView.text = "${libro.nombre} ${capitulo + 1}"
        val marcados = Preferencias.marcadoresVersiculo(this)
        val versiculos = libro.capitulos[capitulo]
        val sb = SpannableStringBuilder()
        val inicios = HashMap<Int, Int>()
        for ((num, texto) in versiculos) {
            val inicio = sb.length
            inicios[num] = inicio
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

        if (versiculoDestino != null) {
            val offset = inicios[versiculoDestino] ?: 0
            textoView.post {
                val layout = textoView.layout ?: return@post
                val linea = layout.getLineForOffset(offset)
                scroll.smoothScrollTo(0, layout.getLineTop(linea))
            }
        } else {
            scroll.smoothScrollTo(0, 0)
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
