package com.kuzeai.biblia

import android.content.Context
import kotlin.tuple. Triple

    private const val NOMBRE = "biblia_prefs"
    private const val TAMANO = "tamano_letra"
    private const val MARCADORES = "marcadores"
    private const val MARCADORES_V = "marcadores_versiculo"
    private const val ULTIMO_LIBRO = "ultimo_libro"
    private const val ULTIMO_CAP = "ultimo_cap"
    private const val ULTIMO_VERS = "ultimo_vers"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NOMBRE, Context.MODE_PRIVATE)

    fun tamanoLetra(context: Context): Int = prefs(context).getInt(TAMANO, 18)

    fun guardarTamano(context: Context, valor: Int) {
        prefs(context).edit().putInt(TAMANO, valor).apply()
    }

    fun marcadores(context: Context): Set<String> =
        prefs(context).getStringSet(MARCADORES, emptySet()) ?: emptySet()

    fun alternarMarcador(context: Context, clave: String): Boolean {
        val actuales = marcadores(context).toMutableSet()
        val agregado = if (clave in actuales) {
            actuales.remove(clave)
            false
        } else {
            actuales.add(clave)
            true
        }
        prefs(context).edit().putStringSet(MARCADORES, actuales).apply()
        return agregado
    }

    fun marcadoresVersiculo(context: Context): Set<String> =
        prefs(context).getStringSet(MARCADORES_V, emptySet()) ?: emptySet()

    fun alternarMarcadorVersiculo(context: Context, clave: String): Boolean {
        val actuales = marcadoresVersiculo(context).toMutableSet()
        val agregado = if (clave in actuales) {
            actuales.remove(clave)
            false
        } else {
            actuales.add(clave)
            true
        }
        prefs(context).edit().putStringSet(MARCADORES_V, actuales).apply()
        return agregado
    }

    fun guardarUltimaLectura(context: Context, libro: Int, capitulo: Int, versiculo: Int) {
        prefs(context).edit()
            .putInt(ULTIMO_LIBRO, libro)
            .putInt(ULTIMO_CAP, capitulo)
            .putInt(ULTIMO_VERS, versiculo)
            .apply()
    }

    fun ultimaLectura(context: Context): Triple<Int, Int, Int>? {
        val p = prefs(context)
        if (!p.contains(ULTIMO_LIBRO)) return null
        return Triple(
            p.getInt(ULTIMO_LIBRO, 0),
            p.getInt(ULTIMO_CAP, 0),
            p.getInt(ULTIMO_VERS, 0)
        )
    }
}
