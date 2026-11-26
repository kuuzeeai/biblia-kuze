package com.kuzeai.biblia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

/** Selector de voz con el mismo diseño del resto de la app (tarjetas crema y marrón). */
object DialogosVoz {

    private const val FRASE = "En el principio creó Dios los cielos y la tierra."

    private class Fila(
        val icono: String,
        val titulo: String,
        val detalle: String?,
        val activa: () -> Boolean,
        val alTocar: () -> Unit
    )

    private class Seccion(val titulo: String?, val filas: List<Fila>)

    private val DETALLES = mapOf(
        "narrador" to "Voz clara y serena",
        "profeta" to "Estilo grave y solemne",
        "moises" to "Estilo anciano, muy pausado",
        "david" to "Estilo cálido y cercano",
        "timoteo" to "Estilo joven y ágil",
        "narradora" to "Voz clara y serena",
        "debora" to "Estilo firme y decidido",
        "maria" to "Estilo suave y tranquilo",
        "rut" to "Estilo tierno y amable"
    )

    /** Paso 1: elegir el estilo (hombre / mujer). Se puede tocar varios para compararlos. */
    fun selector(activity: AppCompatActivity, lector: LectorVoz) {
        mostrar(
            activity,
            titulo = "Voz de lectura",
            subtitulo = "Toca una voz para escucharla",
            lectorVelocidad = lector,
            secciones = {
                val actual = lector.perfilActual()
                fun filasDe(hombre: Boolean) = LectorVoz.PERFILES.filter { it.hombre == hombre }.map { p ->
                    Fila(
                        icono = if (p.hombre) "♂" else "♀",
                        titulo = p.nombre,
                        detalle = DETALLES[p.id],
                        activa = { lector.perfilActual().id == p.id },
                        alTocar = {
                            lector.usarPerfil(activity, p)
                            lector.probar(FRASE)
                        }
                    )
                }
                listOf(
                    Seccion("Voces de hombre", filasDe(true)),
                    Seccion("Voces de mujer", filasDe(false)),
                    Seccion(
                        null,
                        listOf(
                            Fila(
                                icono = "⚙",
                                titulo = "Otra voz del dispositivo",
                                detalle = "Afinar la voz de ${actual.nombre}",
                                activa = { false },
                                alTocar = { selectorDeDispositivo(activity, lector) }
                            )
                        )
                    )
                )
            }
        )
    }

    /** Paso 2 (opcional): elegir una voz concreta instalada en el celular. */
    private fun selectorDeDispositivo(activity: AppCompatActivity, lector: LectorVoz) {
        val perfil = lector.perfilActual()
        val voces = lector.vocesPara(perfil.hombre)
        if (voces.isEmpty()) {
            Toast.makeText(
                activity,
                "No hay voces en español instaladas. Instálalas en Ajustes > Salida de texto a voz.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        mostrar(
            activity,
            titulo = "Voces del dispositivo",
            subtitulo = "Para el estilo ${perfil.nombre}",
            secciones = {
                listOf(
                    Seccion(
                        null,
                        voces.mapIndexed { i, v ->
                            val pais = v.locale.displayCountry.ifEmpty { v.locale.country }
                            val red = if (v.isNetworkConnectionRequired) "requiere internet" else "sin internet"
                            Fila(
                                icono = if (lector.pareceMasculina(v)) "♂" else "♪",
                                titulo = "Voz ${i + 1}",
                                detalle = "$pais · $red",
                                activa = { Preferencias.vozDispositivo(activity, perfil.hombre) == v.name },
                                alTocar = {
                                    lector.usarVoz(activity, v)
                                    lector.probar(FRASE)
                                }
                            )
                        }
                    )
                )
            }
        )
    }

    /**
     * Recuadro "Ir a…" con tres caminos. [alElegir] recibe 0 (libros), 1 (capítulos) o 2 (versículos).
     */
    fun menuIrA(
        activity: AppCompatActivity,
        detalleLibros: String,
        detalleCapitulos: String,
        detalleVersiculos: String,
        alElegir: (Int) -> Unit
    ) {
        val vista = LayoutInflater.from(activity).inflate(R.layout.dialog_voces, null)
        vista.findViewById<TextView>(R.id.titulo_dialogo).text = "Ir a…"
        vista.findViewById<TextView>(R.id.subtitulo_dialogo).text = "Elige por dónde quieres empezar"
        vista.findViewById<TextView>(R.id.boton_cerrar_dialogo).text = "Cancelar"
        val lista = vista.findViewById<LinearLayout>(R.id.lista_dialogo)
        var dialogo: AlertDialog? = null

        val opciones = listOf(
            Triple("📖", "Libros", detalleLibros),
            Triple("📑", "Capítulos", detalleCapitulos),
            Triple("❝", "Versículos", detalleVersiculos)
        )
        opciones.forEachIndexed { indice, (icono, titulo, detalle) ->
            val fila = Fila(icono, titulo, detalle, activa = { false }, alTocar = { alElegir(indice) })
            lista.addView(vistaFila(activity, lista, fila) { dialogo?.dismiss() })
        }

        val d = AlertDialog.Builder(activity).setView(vista).create()
        dialogo = d
        vista.findViewById<View>(R.id.boton_cerrar_dialogo).setOnClickListener { d.dismiss() }
        d.show()
        d.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // ---------- Construcción del diálogo ----------

    private fun mostrar(
        activity: AppCompatActivity,
        titulo: String,
        subtitulo: String,
        lectorVelocidad: LectorVoz? = null,
        secciones: () -> List<Seccion>
    ) {
        val vista = LayoutInflater.from(activity).inflate(R.layout.dialog_voces, null)
        vista.findViewById<TextView>(R.id.titulo_dialogo).text = titulo
        vista.findViewById<TextView>(R.id.subtitulo_dialogo).text = subtitulo
        val lista = vista.findViewById<LinearLayout>(R.id.lista_dialogo)
        val scroll = vista.findViewById<ScrollView>(R.id.scroll_dialogo)
        val proporcion = if (lectorVelocidad != null) 0.42f else 0.5f
        scroll.layoutParams.height = (activity.resources.displayMetrics.heightPixels * proporcion).toInt()

        // Barra de velocidad: de 50 % a 120 % en pasos de 5 %
        if (lectorVelocidad != null) {
            vista.findViewById<View>(R.id.panel_velocidad).visibility = View.VISIBLE
            val barra = vista.findViewById<SeekBar>(R.id.barra_velocidad)
            val valor = vista.findViewById<TextView>(R.id.valor_velocidad)
            fun etiqueta(p: Int) = "${((0.5f + p * 0.05f) * 100).roundToInt()} %"
            barra.max = 14
            barra.progress = ((Preferencias.velocidadLectura(activity) - 0.5f) / 0.05f)
                .roundToInt().coerceIn(0, 14)
            valor.text = etiqueta(barra.progress)
            barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, deUsuario: Boolean) {
                    valor.text = etiqueta(p)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val p = sb?.progress ?: return
                    lectorVelocidad.cambiarVelocidad(activity, 0.5f + p * 0.05f)
                    lectorVelocidad.probar(FRASE) // para oír cómo quedó
                }
            })
        }

        fun pintar() {
            lista.removeAllViews()
            for (seccion in secciones()) {
                seccion.titulo?.let { lista.addView(etiqueta(activity, it)) }
                for (fila in seccion.filas) {
                    lista.addView(vistaFila(activity, lista, fila) { pintar() })
                }
            }
        }
        pintar()

        val dialogo = AlertDialog.Builder(activity).setView(vista).create()
        vista.findViewById<View>(R.id.boton_cerrar_dialogo).setOnClickListener { dialogo.dismiss() }
        dialogo.show()
        dialogo.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun etiqueta(activity: AppCompatActivity, texto: String): TextView {
        val dp = activity.resources.displayMetrics.density
        return TextView(activity).apply {
            text = texto
            setTextColor(activity.getColor(R.color.primario))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((6 * dp).toInt(), (10 * dp).toInt(), 0, (8 * dp).toInt())
        }
    }

    private fun vistaFila(
        activity: AppCompatActivity,
        padre: ViewGroup,
        fila: Fila,
        alCambiar: () -> Unit
    ): View {
        val v = LayoutInflater.from(activity).inflate(R.layout.item_voz, padre, false)
        val activa = fila.activa()
        val nombre = v.findViewById<TextView>(R.id.nombre_voz)
        val detalle = v.findViewById<TextView>(R.id.detalle_voz)
        v.findViewById<TextView>(R.id.icono_voz).text = fila.icono
        nombre.text = fila.titulo
        if (fila.detalle != null) {
            detalle.text = fila.detalle
            detalle.visibility = View.VISIBLE
        }
        if (activa) {
            v.setBackgroundResource(R.drawable.cajon_activo)
            nombre.setTextColor(activity.getColor(R.color.sobre_primario))
            detalle.setTextColor(0xCCFFFFFF.toInt())
            v.findViewById<View>(R.id.marca_voz).visibility = View.VISIBLE
        }
        v.setOnClickListener {
            fila.alTocar()
            alCambiar()
        }
        return v
    }
}
