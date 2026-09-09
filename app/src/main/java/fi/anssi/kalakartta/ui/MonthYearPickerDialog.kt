package fi.anssi.kalakartta.ui

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.Calendar

object MonthYearPickerDialog {

    private val monthNames = arrayOf(
        "Tammikuu", "Helmikuu", "Maaliskuu", "Huhtikuu",
        "Toukokuu", "Kesäkuu", "Heinäkuu", "Elokuu",
        "Syyskuu", "Lokakuu", "Marraskuu", "Joulukuu"
    )

    fun show(
        context: Context,
        initialCalendar: Calendar,
        onMonthSelected: (year: Int, month: Int) -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), 0, dp(context, 20), 0)
        }

        val yearInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Vuosi"
            setText(initialCalendar.get(Calendar.YEAR).toString())
            setSelectAllOnFocus(true)
            contentDescription = "Vuosi"
        }
        container.addView(yearInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val selectedMonthText = TextView(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 12), 0, dp(context, 8))
            text = "Valittu kuukausi: ${monthNames[initialCalendar.get(Calendar.MONTH)]}"
        }
        container.addView(selectedMonthText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        var selectedMonth = initialCalendar.get(Calendar.MONTH)
        val monthGrid = GridLayout(context).apply {
            columnCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = true
        }

        monthNames.forEachIndexed { month, name ->
            val monthButton = Button(context).apply {
                text = name
                contentDescription = "Valitse $name"
                setOnClickListener {
                    selectedMonth = month
                    selectedMonthText.text = "Valittu kuukausi: $name"
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            monthGrid.addView(monthButton, params)
        }
        container.addView(monthGrid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(context)
            .setTitle("Siirry kuukauteen")
            .setView(container)
            .setNegativeButton("Peruuta", null)
            .setPositiveButton("Siirry", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val year = yearInput.text.toString().toIntOrNull()
                if (year == null || year !in 1..9999) {
                    yearInput.error = "Anna vuosi väliltä 1–9999"
                    return@setOnClickListener
                }
                onMonthSelected(year, selectedMonth)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
