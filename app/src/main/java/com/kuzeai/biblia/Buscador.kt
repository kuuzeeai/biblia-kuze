package com.kuzeai.biblia

import android.content.Context

/** Búsqueda de palabras dentro del texto de la Biblia (sin importar mayúsculas ni tildes). */
object Buscador {

    const val MIN_LETRAS = 3
    const val MAXIMO = 200

    class Resultado(
        val indiceLibro: Int,
        val capitulo: Int,   // desde 0
        val numero: Int,     // número de versículo
        val texto: String,
        val posiciones: List<IntRange>
    )

    class Busqueda(val resultados: List<Resultado>, val total: Int)

    private class Entrada(val libro: Int, val capitulo: Int, val numero: Int, val texto: String, val normal: String)

    @Volatile
    private var indice: List<Entrada>? = null

    // Cada letra se cambia por otra del mismo largo, así las posiciones coinciden con el texto original
    private fun base(c: Char): Char = when (val m = c.lowercaseChar()) {
        'á', 'à', 'â', 'ä' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ó', 'ò', 'ô', 'ö' -> 'o'
        'ú', 'ù', 'û', 'ü' -> 'u'
        'ñ' -> 'n'
        else -> m
    }

    fun normalizar(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(base(c))
        return sb.toString()
    }

    /** Arma el índice (tarda un poco la primera vez; conviene llamarlo en segundo plano). */
    fun preparar(context: Context) {
        datos(context)
    }

    private fun datos(context: Context): List<Entrada> {
        indice?.let { return it }
        synchronized(this) {
            indice?.let { return it }
            val libros = Biblia.cargar(context)
            val lista = ArrayList<Entrada>(32000)
            for ((l, libro) in libros.withIndex()) {
                for ((c, versiculos) in libro.capitulos.withIndex()) {
                    for ((numero, texto) in versiculos) {
                        lista.add(Entrada(l, c, numero, texto, normalizar(texto)))
                    }
                }
            }
            indice = lista
            return lista
        }
    }

    fun buscar(context: Context, consulta: String): Busqueda {
        val palabras = normalizar(consulta).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (palabras.isEmpty()) return Busqueda(emptyList(), 0)
        val lista = datos(context)
        val resultados = ArrayList<Resultado>()
        var total = 0
        for (e in lista) {
            if (palabras.all { e.normal.contains(it) }) {
                total++
                if (resultados.size < MAXIMO) {
                    val posiciones = palabras.map { p ->
                        val i = e.normal.indexOf(p)
                        i until (i + p.length)
                    }
                    resultados.add(Resultado(e.libro, e.capitulo, e.numero, e.texto, posiciones))
                }
            }
        }
        return Busqueda(resultados, total)
    }
}
