package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishSpecies
import java.io.File

class EditSpeciesActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private lateinit var speciesList: MutableList<FishSpecies>
    private lateinit var adapter: ArrayAdapter<FishSpecies>

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
        setContentView(R.layout.activity_edit_species)

        db = AppDatabase.getInstance(this)
        
        setResult(RESULT_OK) // Asetetaan RESULT_OK oletuksena, jotta palatessa MainActivity päivittyy

        findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.edit_species)

        listView = findViewById(R.id.speciesListView)
        
        loadSpecies()

        findViewById<MaterialButton>(R.id.addNewSpeciesButton).setOnClickListener {
            val intent = Intent(this, EditSpeciesDetailActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadSpecies()
    }

    private fun loadSpecies() {
        speciesList = db.fishSpeciesDao().getAll().toMutableList()
        
        adapter = object : ArrayAdapter<FishSpecies>(this, R.layout.item_edit_species, speciesList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_edit_species, parent, false)
                val species = getItem(position)!!

                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)
                val favouriteCheckBox = view.findViewById<CheckBox>(R.id.favouriteCheckBox)
                val moveUpButton = view.findViewById<ImageButton>(R.id.moveUpButton)
                val moveDownButton = view.findViewById<ImageButton>(R.id.moveDownButton)

                nameView.text = species.name
                
                favouriteCheckBox.setOnCheckedChangeListener(null)
                favouriteCheckBox.isChecked = species.favourite_fish
                favouriteCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    val updated = species.copy(favourite_fish = isChecked)
                    db.fishSpeciesDao().insert(updated)
                    // Päivitetään paikallinen lista jotta tila säilyy scrollatessa
                    speciesList[position] = updated
                }
                
                val iconId = getDrawableId(species.icon_default)
                if (iconId != 0) {
                    iconView.setImageResource(iconId)
                } else {
                    val file = File(species.icon_default)
                    if (file.exists()) {
                        iconView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                    } else {
                        iconView.setImageDrawable(null)
                    }
                }

                moveUpButton.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
                moveDownButton.visibility = if (position < count - 1) View.VISIBLE else View.INVISIBLE

                moveUpButton.setOnClickListener {
                    moveSpecies(position, position - 1)
                }

                moveDownButton.setOnClickListener {
                    moveSpecies(position, position + 1)
                }

                view.setOnClickListener {
                    val intent = Intent(this@EditSpeciesActivity, EditSpeciesDetailActivity::class.java)
                    intent.putExtra("EXTRA_SPECIES_ID", species.id)
                    startActivity(intent)
                }

                return view
            }
        }
        listView.adapter = adapter
    }

    private fun moveSpecies(from: Int, to: Int) {
        val moved = speciesList.removeAt(from)
        speciesList.add(to, moved)
        
        // Update sortOrder for all
        speciesList.forEachIndexed { index, fishSpecies ->
            val updated = fishSpecies.copy(sortOrder = index + 1)
            db.fishSpeciesDao().insert(updated)
        }
        
        loadSpecies()
    }

    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        return resources.getIdentifier(iconName, "drawable", packageName)
    }
}
