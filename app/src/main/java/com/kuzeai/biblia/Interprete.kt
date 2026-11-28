package com.kuzeai.biblia

import java.text.Normalizer

/**
 * Convierte lo que dijo la persona ("Génesis capítulo 4 versículo 10") en un pedido:
 * libro, capítulo y versículo. Tolera errores del reconocedor ("generos capilo cuatro").
 */
object Interprete {

    /** [capitulo] y [versiculo] son índices desde 0. Si [error] no es null, no hay que leer. */
    class Pedido(val libro: Int, val capitulo: Int, val versiculo: Int, val error: String? = null)

    private val PARASITAS = setOf("de", "del", "la", "las", "los", "el", "libro", "san", "s")

    private val ORDINALES = mapOf(
        "primero" to "1", "primera" to "1", "primer" to "1", "uno" to "1", "un" to "1", "una" to "1",
        "segundo" to "2", "segunda" to "2", "dos" to "2",
        "tercero" to "3", "tercera" to "3", "tercer" to "3", "tres" to "3"
    )

    private val UNIDADES = mapOf(
        "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11,
        "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16,
        "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20,
        "veintiuno" to 21, "veintiun" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
        "veintiocho" to 28, "veintinueve" to 29
    )

    private val DECENAS = mapOf(
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90
    )

    fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun palabras(s: String): List<String> =
        normalizar(s).split(" ").filter { it.isNotEmpty() && it !in PARASITAS }

    fun distancia(a: String, b: String): Int {
        var anterior = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val actual = IntArray(b.length + 1)
            actual[0] = i
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                actual[j] = minOf(actual[j - 1] + 1, anterior[j] + 1, anterior[j - 1] + costo)
            }
            anterior = actual
        }
        return anterior[b.length]
    }

    /** Lee un número (en cifras o en palabras) desde la posición [i]. Devuelve (valor, palabras usadas). */
    private fun numero(t: List<String>, i: Int): Pair<Int, Int>? {
        t.getOrNull(i)?.toIntOrNull()?.let { return it to 1 }
        var total = 0
        var j = i
        var hay = false
        if (t.getOrNull(j) == "ciento" || t.getOrNull(j) == "cien") {
            total = 100; j++; hay = true
        }
        val decena = DECENAS[t.getOrNull(j) ?: ""]
        if (decena != null) {
            total += decena; j++; hay = true
            if (t.getOrNull(j) == "y") {
                val u = UNIDADES[t.getOrNull(j + 1) ?: ""]
                if (u != null && u in 1..9) { total += u; j += 2 }
            }
        } else {
            val u = UNIDADES[t.getOrNull(j) ?: ""]
            if (u != null) { total += u; j++; hay = true }
        }
        return if (hay) total to (j - i) else null
    }

    private fun esCapitulo(w: String) =
        w.length >= 3 && (w.startsWith("cap") || distancia(w, "capitulo") <= 3)

    private fun esVersiculo(w: String) =
        w.length >= 4 && (w.startsWith("vers") || distancia(w, "versiculo") <= 3)

    fun interpretar(libros: List<Libro>, frase: String): Pedido? {
        val t = palabras(frase)
        if (t.isEmpty()) return null
        val nombres = libros.map { palabras(it.nombre).joinToString(" ") }

        var mejorLibro = -1
        var mejorDist = Int.MAX_VALUE
        var mejorLargo = 0
        var mejorFin = 0
        for (s in t.indices) {
            for (k in 1..3) {
                if (s + k > t.size) break
                val cand = t.subList(s, s + k).toMutableList()
                if (k > 1) ORDINALES[cand[0]]?.let { cand[0] = it }
                val c = cand.joinToString(" ")
                for ((i, n) in nombres.withIndex()) {
                    if (c[0].isDigit() != n[0].isDigit()) continue
                    val maximo = if (n.length <= 4) 0 else n.length / 3
                    val d = distancia(c, n)
                    if (d > maximo) continue
                    if (d < mejorDist || (d == mejorDist && k > mejorLargo)) {
                        mejorDist = d; mejorLibro = i; mejorLargo = k; mejorFin = s + k
                    }
                }
            }
        }
        if (mejorLibro < 0) return null

        val resto = t.drop(mejorFin)
        var cap: Int? = null
        var ver: Int? = null
        var capExplicito = false
        var i = 0
        while (i < resto.size) {
            val w = resto[i]
            if (esCapitulo(w)) {
                val n = numero(resto, i + 1)
                if (n != null) { cap = n.first; capExplicito = true; i += 1 + n.second } else i++
            } else if (esVersiculo(w)) {
                val n = numero(resto, i + 1)
                if (n != null) { ver = n.first; i += 1 + n.second } else i++
            } else {
                val n = numero(resto, i)
                if (n != null) {
                    if (cap == null) cap = n.first else if (ver == null) ver = n.first
                    i += n.second
                } else i++
            }
        }

        val libro = libros[mejorLibro]
        var capitulo = cap ?: 1
        // Libros de un solo capítulo (Judas, 2 Juan…): "Judas 5" se entiende como versículo 5
        if (libro.capitulos.size == 1 && cap != null && ver == null && !capExplicito) {
            ver = cap
            capitulo = 1
        }
        if (capitulo < 1 || capitulo > libro.capitulos.size) {
            return Pedido(mejorLibro, 0, 0,
                "${libro.nombre} tiene ${libro.capitulos.size} capítulos. Ese capítulo no existe.")
        }
        val versos = libro.capitulos[capitulo - 1]
        var indiceVerso = 0
        if (ver != null) {
            indiceVerso = versos.indexOfFirst { it.first == ver }
            if (indiceVerso < 0) {
                return Pedido(mejorLibro, capitulo - 1, 0, "Ese versículo no existe en ese capítulo.")
            }
        }
        return Pedido(mejorLibro, capitulo - 1, indiceVerso)
    }
}
