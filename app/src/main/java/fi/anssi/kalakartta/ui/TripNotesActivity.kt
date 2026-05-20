package fi.anssi.kalakartta.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R

class TripNotesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_notes)

        val notes = intent.getStringExtra("EXTRA_NOTES") ?: ""
        findViewById<TextView>(R.id.tripNotesTextView).text = notes

        findViewById<TextView>(R.id.okButton).setOnClickListener {
            finish()
        }
    }
}
