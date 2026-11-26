package com.kuzeai.biblia

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
    private lateinit var estrella: ImageView
    private lateinit var scroll: NestedScrollView

    // Navegador de pantalla completa (Libros → Capítulos → Versículos)
    private lateinit var navegador: Navegador

    // El gesto/botón de atrás del sistema devuelve un paso en el navegador mientras está abierto
    private val atras = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            navegador.atras()
        }
    }

    // Lector de voz
    private val lector = LectorVoz()
    private var escuchando = false
    private var iniciosVersiculos: Map<Int, Int> = emptyMap()
    private var versiculoActual = 0
    private var saltarScrollEn = -1 // índice de versículo cuyo desplazamiento automático se omite

    // Zoom con dos dedos (como ampliar una foto)
    private lateinit var zoom: ScaleGestureDetector
    private var tamanoSp = 18f
    private var escalando = false
    private var ultimoZoomT = 0L
    private var focoOffset = 0        // letra del texto que está bajo los dedos
    private var focoViewportY = 0f    // altura de los dedos dentro del área visible
    private var anclajePendiente = false

    // Toques múltiples sobre el texto: 2 = leer desde ahí, 3 = leer solo ese versículo
    private val manejador = android.os.Handler(android.os.Looper.getMainLooper())
    private var toques = 0
    private var toqueX = 0f
    private var toqueY = 0f
    private var bajadaX = 0f
    private var bajadaY = 0f
    private var bajadaT = 0L
    private var toqueValido = false
    private val resolverToques = Runnable {
        val n = toques
        toques = 0
        if (n == 2) leerDesdeToque(soloUno = false)
        else if (n >= 3) leerDesdeToque(soloUno = true)
    }

    companion object {
        private const val TAMANO_MIN = 12f
        private const val TAMANO_MAX = 64f
        private const val CLAVE_LIBRO = "i_libro"
        private const val CLAVE_CAP = "i_cap"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_lectura)

        // Al girar el celular se regresa al mismo capítulo, no al primero que se abrió
        aplicarDestino(intent, savedInstanceState)
        val versiculoInicial = if (savedInstanceState != null) {
            Preferencias.ultimaLectura(this)?.third ?: 0
        } else {
            intent.getIntExtra("versiculo", 0)
        }

        textoView = findViewById(R.id.texto_biblia)
        tituloView = findViewById(R.id.titulo_lectura)
        estrella = findViewById(R.id.boton_estrella)
        scroll = findViewById(R.id.scroll_lectura)

        navegador = Navegador(
            activity = this,
            alElegir = { l, c, v -> irALibroCapituloVersiculo(l, c, v) },
            alCambiar = { abierto -> atras.isEnabled = abierto }
        )
        onBackPressedDispatcher.addCallback(this, atras)

        // Deslizar a la izquierda o derecha para cambiar de capítulo
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            // Necesario para que el TextView reciba también el resto del gesto (soltar el dedo, etc.)
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                // Al soltar los dedos de un pellizco no se debe cambiar de capítulo
                if (escalando || android.os.SystemClock.uptimeMillis() - ultimoZoomT < 500) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 80 && abs(dx) > abs(vy) * 1.4f) {
                    if (dx < 0) siguiente() else anterior()
                    return true
                }
                return false
            }
        })

        // Pellizcar con dos dedos para agrandar o achicar la letra, como una foto:
        // el texto bajo los dedos se queda bajo los dedos mientras se amplía.
        zoom = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                if (navegador.visible) return false
                escalando = true
                scroll.requestDisallowInterceptTouchEvent(true)
                val pos = IntArray(2)
                textoView.getLocationInWindow(pos)
                focoOffset = textoView.getOffsetForPosition(d.focusX - pos[0], d.focusY - pos[1])
                actualizarFoco(d)
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                // scaleFactor es el cambio desde el movimiento anterior: se va multiplicando
                tamanoSp = (tamanoSp * d.scaleFactor).coerceIn(TAMANO_MIN, TAMANO_MAX)
                textoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanoSp)
                actualizarFoco(d)
                pedirAnclaje()
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                escalando = false
                ultimoZoomT = android.os.SystemClock.uptimeMillis()
                Preferencias.guardarTamano(this@LecturaActivity, tamanoSp.roundToInt())
            }
        })

        textoView.setOnTouchListener { _, event ->
            detectarToques(event, escalando)
            detector.onTouchEvent(event) || escalando
        }

        // Barra inferior: acciones
        findViewById<View>(R.id.fab_izquierda).setOnClickListener { anterior() }
        findViewById<View>(R.id.fab_derecha).setOnClickListener { siguiente() }
        findViewById<View>(R.id.fab_audio).setOnClickListener { reproducirTexto() }
        findViewById<View>(R.id.fab_audio).setOnLongClickListener { elegirVoz(); true }

        // Barra inferior: pestañas
        findViewById<View>(R.id.tab_leer).setOnClickListener { if (navegador.visible) navegador.cerrar() }
        findViewById<View>(R.id.tab_ir).setOnClickListener { elegirDestino() }
        findViewById<View>(R.id.tab_buscar).setOnClickListener {
            detenerLectura()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("lista", true)
                    .putExtra("buscar", true)
            )
        }
        findViewById<View>(R.id.tab_marcadores).setOnClickListener { abrirMarcadores() }

        // Barra superior
        findViewById<View>(R.id.boton_menos).setOnClickListener { cambiarTamano(-2f) }
        findViewById<View>(R.id.boton_mas).setOnClickListener { cambiarTamano(2f) }
        estrella.setOnClickListener { alternarCapitulo() }
        findViewById<View>(R.id.boton_menu).setOnClickListener { mostrarMenu() }

        // Configurar lector de voz
        lector.inicializar(this)

        aplicarTamano()
        mostrar(versiculoInicial)

        // Primera vez: ayuda corta. Después, una vez al día: letrero con un versículo al azar
        if (!Ayuda.mostrarSiCorresponde(this)) {
            VersiculoDelDia.mostrarSiCorresponde(this)
        }
    }

    // La pantalla ya estaba abierta y se pidió otro lugar (desde Buscar, Marcadores o el versículo del día)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        detenerLectura()
        aplicarDestino(intent, null)
        mostrar(intent.getIntExtra("versiculo", 0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CLAVE_LIBRO, indiceLibro)
        outState.putInt(CLAVE_CAP, capitulo)
    }

    private fun aplicarDestino(i: Intent, estado: Bundle?) {
        val libros = Biblia.cargar(this)
        val guardado = estado != null && estado.containsKey(CLAVE_LIBRO)
        val l = (if (guardado) estado!!.getInt(CLAVE_LIBRO) else i.getIntExtra("libro", 0))
            .coerceIn(0, libros.lastIndex)
        indiceLibro = l
        libro = libros[l]
        capitulo = (if (guardado) estado!!.getInt(CLAVE_CAP) else i.getIntExtra("capitulo", 0))
            .coerceIn(0, libro.capitulos.lastIndex)
    }

    // ---------- Ir a… (navegador) ----------

    // Recuadro con tres caminos: Libros, Capítulos o Versículos (del libro/capítulo que se está leyendo)
    private fun elegirDestino() {
        DialogosVoz.menuIrA(
            this,
            detalleLibros = "Los 66 libros de la Biblia",
            detalleCapitulos = "De ${libro.nombre}",
            detalleVersiculos = "De ${libro.nombre} ${capitulo + 1}"
        ) { paso -> navegador.abrir(paso, indiceLibro, capitulo) }
    }

    private fun irALibroCapituloVersiculo(nLibro: Int, nCap: Int, nVersiculo: Int) {
        detenerLectura()
        indiceLibro = nLibro
        capitulo = nCap
        libro = Biblia.cargar(this)[nLibro]
        mostrar(nVersiculo)
    }

    // ---------- Marcadores ----------

    private fun claveCapitulo() = "${libro.nombre}|${capitulo + 1}"

    private fun claveVersiculo(versiculo: Int) = "${libro.nombre}|${capitulo + 1}|$versiculo"

    private fun actualizarEstrella() {
        val marcado = claveCapitulo() in Preferencias.marcadores(this)
        estrella.setImageResource(if (marcado) R.drawable.ic_estrella_llena else R.drawable.ic_estrella)
        estrella.contentDescription =
            if (marcado) "Quitar el marcador de este capítulo" else "Guardar este capítulo"
    }

    private fun alternarCapitulo() {
        val agregado = Preferencias.alternarMarcador(this, claveCapitulo())
        Toast.makeText(
            this,
            if (agregado) getString(R.string.guardado_marcador) else getString(R.string.quitado_marcador),
            Toast.LENGTH_SHORT
        ).show()
        actualizarEstrella()
    }

    private fun abrirMarcadores() {
        Marcadores.mostrar(this, alCambiar = { actualizarEstrella(); dibujarTexto() }) { l, c, v ->
            irALibroCapituloVersiculo(l, c, v)
        }
    }

    private fun mostrarMenu() {
        val capMarcado = claveCapitulo() in Preferencias.marcadores(this)
        val opciones = listOf(
            if (capMarcado) "Quitar marcador de este capítulo" else "Guardar este capítulo",
            getString(R.string.marcar_versiculo),
            getString(R.string.voz_lectura),
            "Ayuda: cómo usar la Biblia"
        )
        AlertDialog.Builder(this)
            .setTitle(tituloView.text)
            .setAdapter(ArrayAdapter(this, R.layout.item_lista, R.id.texto_item, opciones)) { _, which ->
                when (which) {
                    0 -> alternarCapitulo()
                    1 -> mostrarVersiculosParaMarcar()
                    2 -> elegirVoz()
                    3 -> Ayuda.mostrar(this)
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
                mostrar(num)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Texto ----------

    private fun aplicarTamano() {
        tamanoSp = Preferencias.tamanoLetra(this).toFloat().coerceIn(TAMANO_MIN, TAMANO_MAX)
        textoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanoSp)
    }

    // Botones A− y A+: cambian la letra sin perder el lugar donde se va leyendo
    private fun cambiarTamano(delta: Float) {
        tamanoSp = (tamanoSp + delta).coerceIn(TAMANO_MIN, TAMANO_MAX)
        val layout = textoView.layout
        if (layout != null && scroll.scrollY > textoView.paddingTop) {
            val linea = layout.getLineForVertical(scroll.scrollY - textoView.paddingTop)
            focoOffset = layout.getLineStart(linea)
            focoViewportY = 0f
            pedirAnclaje()
        }
        textoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanoSp)
        Preferencias.guardarTamano(this, tamanoSp.roundToInt())
    }

    // El zoom necesita ver todos los toques, incluso cuando el scroll los está usando
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::zoom.isInitialized && ::navegador.isInitialized && !navegador.visible) {
            zoom.onTouchEvent(ev)
            if (ev.pointerCount > 1) scroll.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun actualizarFoco(d: ScaleGestureDetector) {
        val pos = IntArray(2)
        scroll.getLocationInWindow(pos)
        focoViewportY = d.focusY - pos[1]
    }

    // Tras cambiar el tamaño, y antes de dibujar, se reajusta el scroll para que
    // la misma parte del texto siga bajo los dedos
    private fun pedirAnclaje() {
        if (anclajePendiente) return
        anclajePendiente = true
        textoView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                textoView.viewTreeObserver.removeOnPreDrawListener(this)
                anclajePendiente = false
                val layout = textoView.layout ?: return true
                val offset = focoOffset.coerceIn(0, textoView.text.length)
                val y = textoView.paddingTop + layout.getLineTop(layout.getLineForOffset(offset))
                scroll.scrollTo(0, (y - focoViewportY).toInt().coerceAtLeast(0))
                return true
            }
        })
    }

    private fun mostrar(versiculoDestino: Int? = null) {
        tituloView.text = "${libro.nombre} ${capitulo + 1}"
        Preferencias.guardarUltimaLectura(this, indiceLibro, capitulo, versiculoDestino ?: 0)
        actualizarEstrella()
        dibujarTexto()

        if (versiculoDestino != null && versiculoDestino > 0) {
            val offset = iniciosVersiculos[versiculoDestino] ?: 0
            textoView.post {
                val layout = textoView.layout ?: return@post
                val linea = layout.getLineForOffset(offset)
                scroll.smoothScrollTo(0, layout.getLineTop(linea))
            }
        } else {
            scroll.smoothScrollTo(0, 0)
        }
    }

    // Arma el texto del capítulo (con los versículos marcados resaltados) sin mover la pantalla
    private fun dibujarTexto() {
        val marcados = Preferencias.marcadoresVersiculo(this)
        val versiculos = libro.capitulos[capitulo]
        val sb = SpannableStringBuilder()
        val inicios = HashMap<Int, Int>()
        iniciosVersiculos = inicios
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
    }

    private fun anterior() {
        detenerLectura()
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

    private fun siguiente(auto: Boolean = false): Boolean {
        if (!auto) detenerLectura()
        val libros = Biblia.cargar(this)
        if (capitulo < libro.capitulos.lastIndex) {
            capitulo++
        } else if (indiceLibro < libros.lastIndex) {
            indiceLibro++
            libro = libros[indiceLibro]
            capitulo = 0
        } else {
            Toast.makeText(this, "Estás al final", Toast.LENGTH_SHORT).show()
            return false
        }
        mostrar()
        return true
    }

    // ---------- Toques sobre el texto ----------

    private fun detectarToques(event: MotionEvent, pellizcando: Boolean) {
        val holgura = android.view.ViewConfiguration.get(this).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bajadaX = event.x; bajadaY = event.y
                bajadaT = event.eventTime
                toqueValido = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> toqueValido = false
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - bajadaX) > holgura || abs(event.y - bajadaY) > holgura) toqueValido = false
            }
            MotionEvent.ACTION_CANCEL -> toqueValido = false
            MotionEvent.ACTION_UP -> {
                val corto = event.eventTime - bajadaT < 300
                if (toqueValido && corto && !pellizcando) {
                    // Un toque en otro lugar empieza la cuenta de nuevo
                    if (toques > 0 && (abs(event.x - toqueX) > holgura * 4 || abs(event.y - toqueY) > holgura * 4)) {
                        toques = 0
                    }
                    toques++
                    toqueX = event.x; toqueY = event.y
                    manejador.removeCallbacks(resolverToques)
                    if (toques >= 3) {
                        resolverToques.run()
                    } else {
                        manejador.postDelayed(resolverToques, 300)
                    }
                } else {
                    toques = 0
                    manejador.removeCallbacks(resolverToques)
                }
                toqueValido = false
            }
        }
    }

    // Versículo que contiene la posición tocada
    private fun indiceVersiculoTocado(): Int {
        val offset = textoView.getOffsetForPosition(toqueX, toqueY)
        var resultado = 0
        libro.capitulos[capitulo].forEachIndexed { i, par ->
            val inicio = iniciosVersiculos[par.first] ?: return@forEachIndexed
            if (inicio <= offset) resultado = i
        }
        return resultado
    }

    private fun leerDesdeToque(soloUno: Boolean) {
        if (navegador.visible) return
        val indice = indiceVersiculoTocado()
        val num = libro.capitulos[capitulo].getOrNull(indice)?.first ?: return
        saltarScrollEn = indice
        Toast.makeText(
            this,
            if (soloUno) "Leyendo solo el versículo $num" else "Leyendo desde el versículo $num",
            Toast.LENGTH_SHORT
        ).show()
        iniciarLectura(indice, soloUno)
    }

    // ---------- Reproducir texto en voz ----------

    private fun reproducirTexto() {
        if (escuchando) {
            detenerLectura()
        } else {
            iniciarLectura(0)
        }
    }

    private fun iniciarLectura(desde: Int, soloUno: Boolean = false) {
        val versiculos = libro.capitulos[capitulo].map { it.second }
        if (versiculos.isEmpty()) {
            Toast.makeText(this, "No hay texto para leer", Toast.LENGTH_SHORT).show()
            return
        }
        escuchando = true
        actualizarIconoAudio()
        lector.leer(
            titulo = "${libro.nombre}, capítulo ${capitulo + 1}",
            versiculos = versiculos,
            desde = desde,
            hasta = if (soloUno) desde else versiculos.lastIndex,
            conTitulo = desde == 0 && !soloUno,
            alVersiculo = { idx -> irAlVersiculoLeido(idx) },
            alTerminar = {
                // Al acabar el capítulo, continuar con el siguiente (modo "escuchar la Biblia")
                if (soloUno) {
                    detenerLectura()
                } else if (escuchando) {
                    if (siguiente(auto = true)) iniciarLectura(0) else detenerLectura()
                }
            },
            alError = {
                detenerLectura()
                Toast.makeText(
                    this,
                    "No se pudo reproducir la voz. Revisa que haya un motor de voz en español instalado.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun detenerLectura() {
        if (!escuchando && !lector.estaReproduciendo()) return
        escuchando = false
        lector.detener()
        actualizarIconoAudio()
    }

    private fun actualizarIconoAudio() {
        findViewById<ImageView>(R.id.icono_audio)
            .setImageResource(if (escuchando) R.drawable.ic_pausa else R.drawable.ic_audio)
        findViewById<TextView>(R.id.texto_audio).text = if (escuchando) "Pausar" else "Escuchar"
    }

    // Lleva la pantalla al versículo que se está leyendo
    private fun irAlVersiculoLeido(indice: Int) {
        val num = libro.capitulos[capitulo].getOrNull(indice)?.first ?: return
        versiculoActual = num
        Preferencias.guardarUltimaLectura(this, indiceLibro, capitulo, num)
        if (indice == saltarScrollEn) { saltarScrollEn = -1; return }
        val offset = iniciosVersiculos[num] ?: return
        textoView.post {
            val layout = textoView.layout ?: return@post
            scroll.smoothScrollTo(0, layout.getLineTop(layout.getLineForOffset(offset)))
        }
    }

    // ---------- Elegir voz ----------

    private fun elegirVoz() {
        detenerLectura()
        DialogosVoz.selector(this, lector)
    }

    override fun onDestroy() {
        super.onDestroy()
        escuchando = false
        manejador.removeCallbacks(resolverToques)
        lector.limpiar()
    }
}
