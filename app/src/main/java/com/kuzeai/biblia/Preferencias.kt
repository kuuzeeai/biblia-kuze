package com.kuzeai.biblia

import android.content.Context

object Preferencias {
    private const val NOMBRE = "biblia_prefs"
    private const val TAMANO = "tamano_letra"
    private const val MARCADORES = "marcadores"

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
}
