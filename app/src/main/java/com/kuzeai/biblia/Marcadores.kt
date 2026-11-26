package com.kuzeai.biblia

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Lista de marcadores (capítulos y versículos): tocar uno ofrece Abrir o Borrar. */
object Marcadores {

    private class Marca(
        val clave: String,
        val indiceLibro: Int,
        val capitulo: Int,   // desde 0
        val versiculo: Int,  // 0 si es un capítulo completo
        val etiqueta: String
    )

    private fun todas(context: Context, libros: List<Libro>): List<Marca> {
        val claves = Preferencias.marcadores(context) + Preferencias.marcadoresVersiculo(context)
        val lista = mutableListOf<Marca>()
        for (clave in claves) {
            val partes = clave.split('|')
            val idx = libros.indexOfFirst { it.nombre == partes[0] }
            if (idx < 0) continue
            val cap = partes.getOrNull(1)?.toIntOrNull() ?: continue
            val vers = partes.getOrNull(2)?.toIntOrNull() ?: 0
            val etiqueta = if (vers > 0) "${partes[0]} $cap:$vers" else "${partes[0]} $cap"
            lista.add(Marca(clave, idx, cap - 1, vers, etiqueta))
        }
        return lista.sortedWith(compareBy({ it.indiceLibro }, { it.capitulo }, { it.versiculo }))
    }

    /** [alAbrir] recibe (libro, capítulo desde 0, versículo o 0). [alCambiar] se llama al borrar. */
    fun mostrar(
        activity: AppCompatActivity,
        alCambiar: () -> Unit = {},
        alAbrir: (Int, Int, Int) -> Unit
    ) {
        val marcas = todas(activity, Biblia.cargar(activity))
        if (marcas.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.sin_marcadores), Toast.LENGTH_LONG).show()
            return
        }
        val adaptador = ArrayAdapter(activity, R.layout.item_lista, R.id.texto_item, marcas.map { it.etiqueta })
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.marcadores))
            .setAdapter(adaptador) { _, cual -> acciones(activity, marcas[cual], alCambiar, alAbrir) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun acciones(
        activity: AppCompatActivity,
        marca: Marca,
        alCambiar: () -> Unit,
        alAbrir: (Int, Int, Int) -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(marca.etiqueta)
            .setPositiveButton("Abrir") { _, _ -> alAbrir(marca.indiceLibro, marca.capitulo, marca.versiculo) }
            .setNegativeButton("Borrar") { _, _ ->
                if (marca.versiculo > 0) Preferencias.quitarMarcadorVersiculo(activity, marca.clave)
                else Preferencias.quitarMarcador(activity, marca.clave)
                Toast.makeText(activity, activity.getString(R.string.quitado_marcador), Toast.LENGTH_SHORT).show()
                alCambiar()
                mostrar(activity, alCambiar, alAbrir)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }
}
