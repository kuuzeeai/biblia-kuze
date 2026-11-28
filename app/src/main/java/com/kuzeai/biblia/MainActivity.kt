package com.kuzeai.biblia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var libros: List<Libro>
    private lateinit var adapter: LibroAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya había una lectura guardada, abrir directamente en ese libro/capítulo/versículo
        if (savedInstanceState == null) {
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
        adapter = LibroAdapter(libros) { indiceReal ->
            val intent = Intent(this, LecturaActivity::class.java)
            intent.putExtra("libro", indiceReal)
            startActivity(intent)
        }

        findViewById<RecyclerView>(R.id.lista_libros).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
        }

        findViewById<EditText>(R.id.buscar).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val texto = s?.toString()?.trim().orEmpty()
                adapter.filtrar(texto)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<ImageView>(R.id.boton_ajustes).setOnClickListener {
            val claves = Preferencias.marcadores(this).sorted()
            if (claves.isEmpty()) {
                android.widget.Toast.makeText(this, getString(R.string.sin_marcadores), android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
                    }
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }

        // Primera vez del día: letrero con un versículo al azar, leído con la última voz elegida
        VersiculoDelDia.mostrarSiCorresponde(this)
    }

    class LibroAdapter(
        private val todos: List<Libro>,
        private val alTocar: (Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TIPO_SECCION = 0
            private const val TIPO_LIBRO = 1
        }

        private sealed class Elemento {
            data class Seccion(val titulo: String) : Elemento()
            data class ItemLibro(val indiceReal: Int, val libro: Libro) : Elemento()
        }

        private var visible: List<Elemento> = construirLista(todos)

        private fun construirLista(todos: List<Libro>): List<Elemento> {
            val lista = mutableListOf<Elemento>()
            lista.add(Elemento.Seccion("Antiguo Testamento"))
            for (i in 0 until 39) {
                if (i < todos.size) {
                    lista.add(Elemento.ItemLibro(i, todos[i]))
                }
            }
            lista.add(Elemento.Seccion("Nuevo Testamento"))
            for (i in 39 until todos.size) {
                lista.add(Elemento.ItemLibro(i, todos[i]))
            }
            return lista
        }

        fun filtrar(texto: String) {
            visible = if (texto.isEmpty()) {
                construirLista(todos)
            } else {
                todos.indices
                    .filter { todos[it].nombre.contains(texto, ignoreCase = true) }
                    .map { Elemento.ItemLibro(it, todos[it]) }
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (visible[position]) {
                is Elemento.Seccion -> TIPO_SECCION
                is Elemento.ItemLibro -> TIPO_LIBRO
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TIPO_SECCION) {
                val v = inflater.inflate(R.layout.item_seccion, parent, false)
                object : RecyclerView.ViewHolder(v) {}
            } else {
                val v = inflater.inflate(R.layout.item_lista, parent, false)
                object : RecyclerView.ViewHolder(v) {
                    val texto: TextView = v.findViewById(R.id.texto_item)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val elem = visible[position]) {
                is Elemento.Seccion -> {
                    val tv = holder.itemView.findViewById<TextView>(R.id.texto_seccion)
                    tv.text = elem.titulo
                }
                is Elemento.ItemLibro -> {
                    val tv = holder.itemView.findViewById<TextView>(R.id.texto_item)
                    tv.text = "${elem.libro.nombre}  (${elem.libro.capitulos.size})"
                    holder.itemView.setOnClickListener { alTocar(elem.indiceReal) }
                }
            }
        }

        override fun getItemCount() = visible.size
    }
}
