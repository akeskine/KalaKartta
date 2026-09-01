package fi.anssi.kalakartta.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R

class DiaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        val titleView = findViewById<TextView>(R.id.diaryTitle)
        titleView.text = "Kalapäiväkirja"

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            setResult(RESULT_OK, android.content.Intent().putExtra("BACK_TO_SETTINGS", true))
            finish()
        }
    }
}
