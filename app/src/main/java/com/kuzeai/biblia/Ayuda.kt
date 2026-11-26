package com.kuzeai.biblia

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Explicación corta de cómo usar la app: sale sola la primera vez y desde el menú. */
object Ayuda {

    /** Devuelve true si la mostró (para no encimar otro letrero). */
    fun mostrarSiCorresponde(activity: AppCompatActivity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (Preferencias.ayudaVista(activity)) return false
        mostrar(activity)
        return true
    }

    fun mostrar(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        Preferencias.marcarAyudaVista(activity)
        val vista = LayoutInflater.from(activity).inflate(R.layout.dialog_ayuda, null)
        val dialogo = AlertDialog.Builder(activity).setView(vista).create()
        vista.findViewById<TextView>(R.id.boton_ayuda_cerrar).setOnClickListener { dialogo.dismiss() }
        dialogo.show()
        dialogo.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
