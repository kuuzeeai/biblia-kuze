package com.kuzeai.biblia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var libros: List<Libro>
    private lateinit var adapter: LibroAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        libros = Biblia.cargar(this)
        adapter = LibroAdapter(libros) { indice ->
            val intent = Intent(this, LecturaActivity::class.java)
            intent.putExtra("libro", indice)
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
    }

    class LibroAdapter(
        private val todos: List<Libro>,
        private val alTocar: (Int) -> Unit
    ) : RecyclerView.Adapter<LibroAdapter.VH>() {

        private var visible = todos.toList()

        fun filtrar(texto: String) {
            visible = if (texto.isEmpty()) todos
            else todos.filter { it.nombre.contains(texto, ignoreCase = true) }
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val texto: TextView = v.findViewById(R.id.texto_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lista, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val libro = visible[position]
            val indiceReal = todos.indexOf(libro)
            holder.texto.text = "${libro.nombre}  (${libro.capitulos.size})"
            holder.itemView.setOnClickListener { alTocar(indiceReal) }
        }

        override fun getItemCount() = visible.size
    }
}
