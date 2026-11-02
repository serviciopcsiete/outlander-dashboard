package com.davidg.outlander

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

const val MP = ViewGroup.LayoutParams.MATCH_PARENT
const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.tv(
    s: String, size: Float, color: Int,
    face: String = "sans-serif-condensed", style: Int = Typeface.NORMAL,
    gravity: Int = Gravity.START
): TextView = TextView(this).apply {
    text = s
    setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    setTextColor(color)
    typeface = Typeface.create(face, style)
    this.gravity = gravity
    includeFontPadding = false
}

fun Context.iconView(res: Int, color: Int): ImageView = ImageView(this).apply {
    setImageResource(res)
    setColorFilter(color, PorterDuff.Mode.SRC_IN)
}

fun FrameLayout.put(v: View, x: Int, y: Int, w: Int, h: Int) {
    val lp = FrameLayout.LayoutParams(w, h)
    lp.leftMargin = x
    lp.topMargin = y
    addView(v, lp)
}

fun LinearLayout.add(v: View, w: Int = MP, h: Int = WC, top: Int = 0, left: Int = 0, weight: Float = 0f) {
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = top
    lp.leftMargin = left
    addView(v, lp)
}

class Field(
    val id: String,
    val label: String,
    val type: String = "text",       // text, number, select, date
    val value: String = "",
    val options: List<String> = emptyList()
)

/** Ventana de captura sencilla. onSave devuelve true para cerrarla. */
fun Activity.form(
    title: String,
    fields: List<Field>,
    saveText: String = "Guardar",
    danger: String? = null,
    onDanger: (() -> Unit)? = null,
    onSave: (Map<String, String>) -> Boolean
) {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(40, 16, 40, 0)
    val getters = LinkedHashMap<String, () -> String>()
    for (f in fields) {
        val l = TextView(this)
        l.text = f.label
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        l.setPadding(0, 14, 0, 0)
        box.addView(l)
        when (f.type) {
            "select" -> {
                val sp = Spinner(this)
                sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, f.options)
                val i = f.options.indexOf(f.value)
                if (i >= 0) sp.setSelection(i)
                box.addView(sp)
                getters[f.id] = { sp.selectedItem?.toString() ?: "" }
            }
            "date" -> {
                val b = Button(this)
                var cur = f.value
                b.text = cur
                b.setOnClickListener {
                    val parts = cur.split("-")
                    val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
                    val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                    val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
                    DatePickerDialog(this, { _, yy, mm, dd ->
                        cur = String.format(Locale.US, "%04d-%02d-%02d", yy, mm + 1, dd)
                        b.text = cur
                    }, y, m, d).show()
                }
                box.addView(b)
                getters[f.id] = { cur }
            }
            else -> {
                val et = EditText(this)
                et.setText(f.value)
                if (f.type == "number") et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                box.addView(et)
                getters[f.id] = { et.text.toString() }
            }
        }
    }
    val scroll = ScrollView(this)
    scroll.addView(box)
    val b = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(scroll)
        .setPositiveButton(saveText, null)
        .setNegativeButton("Cancelar", null)
    if (danger != null) b.setNeutralButton(danger) { _, _ -> onDanger?.invoke() }
    val dlg = b.create()
    dlg.show()
    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val vals = HashMap<String, String>()
        for ((k, g) in getters) vals[k] = g()
        if (onSave(vals)) dlg.dismiss()
    }
}
