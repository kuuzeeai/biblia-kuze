package com.kuzeai.biblia

import android.content.Context
import org.json.JSONObject

data class Libro(val nombre: String, val capitulos: List<List<Pair<Int, String>>>)

object Biblia {
    @Volatile
    private var libros: List<Libro>? = null

    fun cargar(context: Context): List<Libro> {
        libros?.let { return it }
        val json = context.assets.open("biblia.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val arr = root.getJSONArray("books")
        val resultado = mutableListOf<Libro>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val nombre = obj.getString("n")
            val capsObj = obj.getJSONArray("c")
            val capitulos = mutableListOf<List<Pair<Int, String>>>()
            for (c in 0 until capsObj.length()) {
                val versArr = capsObj.getJSONArray(c)
                val versiculos = mutableListOf<Pair<Int, String>>()
                for (v in 0 until versArr.length()) {
                    val par = versArr.getJSONArray(v)
                    versiculos.add(par.getInt(0) to par.getString(1))
                }
                capitulos.add(versiculos)
            }
            resultado.add(Libro(nombre, capitulos))
        }
        libros = resultado
        return resultado
    }
}
