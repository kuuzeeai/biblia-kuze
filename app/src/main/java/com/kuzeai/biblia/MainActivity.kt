package com.kuzeai.biblia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var libros: List<Libro>
    private lateinit var adapter: LibroAdapter
    private lateinit var lista: RecyclerView
    private var busquedaId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val desdeLectura = intent.getBooleanExtra("lista", false)

        // Si ya había una lectura guardada, abrir directamente en ese libro/capítulo/versículo
        if (savedInstanceState == null && !desdeLectura) {
            val ultima = Preferencias.ultimaLectura(this)
            if (ultima != null) {
                startActivity(Intent(this, LecturaActivity::class.java).apply {
                    putExtra("libro", ultima.first)
                    putExtra("capitulo", ultima.second)
                    putExtra("versiculo", ultima.third)
                })
                finish()
                return
            }
        }

        setContentView(R.layout.activity_main)

        libros = Biblia.cargar(this)

        // Prepara el buscador de palabras en segundo plano para que responda rápido
        val app = applicationContext
        Thread { Buscador.preparar(app) }.start()

        adapter = LibroAdapter(
            todos = libros,
            alTocarLibro = { abrir(it, 0, 0) },
            alTocarResultado = { r -> abrir(r.indiceLibro, r.capitulo, r.numero) }
        )

        lista = findViewById(R.id.lista_libros)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adapter

        val buscar = findViewById<EditText>(R.id.buscar)
        buscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val texto = s?.toString()?.trim().orEmpty()
                val id = ++busquedaId
                adapter.filtrar(texto)
                lista.scrollToPosition(0)
                if (texto.length >= Buscador.MIN_LETRAS) {
                    Thread {
                        val resultado = Buscador.buscar(app, texto)
                        runOnUiThread {
                            if (id == busquedaId && !isFinishing && !isDestroyed) {
                                adapter.mostrarResultados(resultado)
                            }
                        }
                    }.start()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        buscar.setOnEditorActionListener { v, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }

        if (intent.getBooleanExtra("buscar", false)) {
            buscar.requestFocus()
            buscar.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(buscar, InputMethodManager.SHOW_IMPLICIT)
            }, 250)
        }

        // Botón para regresar a la lectura (solo cuando se llegó desde ella)
        findViewById<View>(R.id.boton_volver).apply {
            visibility = if (desdeLectura) View.VISIBLE else View.GONE
            setOnClickListener { finish() }
        }

        findViewById<View>(R.id.boton_ayuda).setOnClickListener { Ayuda.mostrar(this) }

        findViewById<View>(R.id.boton_marcadores).setOnClickListener {
            Marcadores.mostrar(this) { libro, capitulo, versiculo -> abrir(libro, capitulo, versiculo) }
        }

        // Primera vez: ayuda corta. Después, una vez al día: letrero con un versículo al azar
        if (!Ayuda.mostrarSiCorresponde(this)) {
            VersiculoDelDia.mostrarSiCorresponde(this)
        }
    }

    // Si la lectura ya está abierta debajo, se vuelve a ella en vez de apilar otra pantalla
    private fun abrir(libro: Int, capitulo: Int, versiculo: Int) {
        startActivity(Intent(this, LecturaActivity::class.java).apply {
            putExtra("libro", libro)
            putExtra("capitulo", capitulo)
            putExtra("versiculo", versiculo)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    class LibroAdapter(
        private val todos: List<Libro>,
        private val alTocarLibro: (Int) -> Unit,
        private val alTocarResultado: (Buscador.Resultado) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TIPO_SECCION = 0
            private const val TIPO_LIBRO = 1
            private const val TIPO_RESULTADO = 2
        }

        private sealed class Elemento {
            data class Seccion(val titulo: String) : Elemento()
            data class ItemLibro(val indiceReal: Int, val libro: Libro) : Elemento()
            data class ItemResultado(val resultado: Buscador.Resultado) : Elemento()
        }

        private var consulta = ""
        private var librosQueCoinciden: List<Int> = emptyList()
        private var encontrados: Buscador.Busqueda? = null
        private var visible: List<Elemento> = construirLista()

        private fun construirLista(): List<Elemento> {
            val lista = mutableListOf<Elemento>()
            lista.add(Elemento.Seccion("Antiguo Testamento"))
            for (i in 0 until min(39, todos.size)) {
                lista.add(Elemento.ItemLibro(i, todos[i]))
            }
            lista.add(Elemento.Seccion("Nuevo Testamento"))
            for (i in 39 until todos.size) {
                lista.add(Elemento.ItemLibro(i, todos[i]))
            }
            return lista
        }

        fun filtrar(texto: String) {
            consulta = texto
            encontrados = null
            librosQueCoinciden = if (texto.isEmpty()) {
                emptyList()
            } else {
                val n = Buscador.normalizar(texto)
                todos.indices.filter { Buscador.normalizar(todos[it].nombre).contains(n) }
            }
            reconstruir()
        }

        fun mostrarResultados(busqueda: Buscador.Busqueda) {
            encontrados = busqueda
            reconstruir()
        }

        private fun reconstruir() {
            visible = if (consulta.isEmpty()) {
                construirLista()
            } else {
                val l = mutableListOf<Elemento>()
                if (librosQueCoinciden.isNotEmpty()) {
                    l.add(Elemento.Seccion("Libros"))
                    librosQueCoinciden.forEach { l.add(Elemento.ItemLibro(it, todos[it])) }
                }
                val b = encontrados
                when {
                    consulta.length < Buscador.MIN_LETRAS ->
                        l.add(Elemento.Seccion("Escribe 3 letras o más para buscar dentro de la Biblia"))
                    b == null -> l.add(Elemento.Seccion("Buscando…"))
                    b.total == 0 -> l.add(Elemento.Seccion("No se encontró “$consulta” en el texto"))
                    else -> {
                        l.add(
                            Elemento.Seccion(
                                if (b.total > b.resultados.size) "Versículos: ${b.resultados.size} de ${b.total}"
                                else "Versículos (${b.total})"
                            )
                        )
                        b.resultados.forEach { l.add(Elemento.ItemResultado(it)) }
                    }
                }
                l
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (visible[position]) {
                is Elemento.Seccion -> TIPO_SECCION
                is Elemento.ItemLibro -> TIPO_LIBRO
                is Elemento.ItemResultado -> TIPO_RESULTADO
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout = when (viewType) {
                TIPO_SECCION -> R.layout.item_seccion
                TIPO_LIBRO -> R.layout.item_lista
                else -> R.layout.item_resultado
            }
            val v = inflater.inflate(layout, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val elem = visible[position]) {
                is Elemento.Seccion -> {
                    holder.itemView.findViewById<TextView>(R.id.texto_seccion).text = elem.titulo
                }
                is Elemento.ItemLibro -> {
                    val tv = holder.itemView.findViewById<TextView>(R.id.texto_item)
                    tv.text = "${elem.libro.nombre}  (${elem.libro.capitulos.size})"
                    holder.itemView.setOnClickListener { alTocarLibro(elem.indiceReal) }
                }
                is Elemento.ItemResultado -> {
                    val r = elem.resultado
                    val vista = holder.itemView
                    vista.findViewById<TextView>(R.id.texto_referencia).text =
                        "${todos[r.indiceLibro].nombre} ${r.capitulo + 1}:${r.numero}"
                    vista.findViewById<TextView>(R.id.texto_fragmento).text = fragmento(vista.context, r)
                    vista.setOnClickListener { alTocarResultado(r) }
                }
            }
        }

        // Recorta el versículo alrededor de la palabra encontrada y la resalta
        private fun fragmento(context: Context, r: Buscador.Resultado): CharSequence {
            val texto = r.texto
            val primera = r.posiciones.minOfOrNull { it.first } ?: 0
            val inicio = if (texto.length <= 170) 0 else max(0, primera - 60)
            val fin = min(texto.length, inicio + 190)
            val prefijo = if (inicio > 0) "…" else ""
            val sufijo = if (fin < texto.length) "…" else ""
            val sb = SpannableStringBuilder(prefijo + texto.substring(inicio, fin) + sufijo)
            val color = context.getColor(R.color.fondo_marcado)
            for (p in r.posiciones) {
                val desde = max(p.first, inicio)
                val hasta = min(p.last + 1, fin)
                if (desde < hasta) {
                    val a = prefijo.length + (desde - inicio)
                    val b = prefijo.length + (hasta - inicio)
                    sb.setSpan(BackgroundColorSpan(color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return sb
        }

        override fun getItemCount() = visible.size
    }
}
