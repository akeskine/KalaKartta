package fi.anssi.kalakartta.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishSpecies
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class EditSpeciesDetailActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var speciesId: String? = null
    private var currentSpecies: FishSpecies? = null

    private lateinit var nameInput: EditText
    private lateinit var favouriteCheckBox: CheckBox
    private lateinit var smallWeightInput: EditText
    private lateinit var smallLengthInput: EditText
    private lateinit var largeWeightInput: EditText
    private lateinit var largeLengthInput: EditText
    private lateinit var giantWeightInput: EditText
    private lateinit var giantLengthInput: EditText

    private var hasChanges: Boolean = false
    private var isDataLoaded: Boolean = false

    private var iconDefaultPath: String = ""
    private var iconSmallPath: String = ""
    private var iconLargePath: String = ""
    private var iconGiantPath: String = ""

    private val selectIconLauncherWithType = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val type = pendingIconType ?: return@registerForActivityResult
            processSelectedIcon(uri, type)
        }
    }

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
        setContentView(R.layout.activity_edit_species_detail)

        db = AppDatabase.getInstance(this)
        speciesId = intent.getStringExtra("EXTRA_SPECIES_ID")

        initViews()
        loadData()
    }

    private fun initViews() {
        nameInput = findViewById(R.id.speciesNameInput)
        favouriteCheckBox = findViewById(R.id.favouriteFishCheckBox)
        smallWeightInput = findViewById(R.id.smallWeightInput)
        smallLengthInput = findViewById(R.id.smallLengthInput)
        largeWeightInput = findViewById(R.id.largeWeightInput)
        largeLengthInput = findViewById(R.id.largeLengthInput)
        giantWeightInput = findViewById(R.id.giantWeightInput)
        giantLengthInput = findViewById(R.id.giantLengthInput)

        setupChangeListeners()

        setupIconEdit(R.id.iconDefaultEdit, getString(R.string.icon_default), getString(R.string.icon_help), "DEFAULT")
        setupIconEdit(R.id.iconSmallEdit, getString(R.string.icon_small), getString(R.string.icon_small_help), "SMALL")
        setupIconEdit(R.id.iconLargeEdit, getString(R.string.icon_large), getString(R.string.icon_large_help), "LARGE")
        setupIconEdit(R.id.iconGiantEdit, getString(R.string.icon_giant), getString(R.string.icon_giant_help), "GIANT")

        findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        findViewById<MaterialButton>(R.id.okButton).setOnClickListener {
            saveData()
        }

        val deleteBtn = findViewById<MaterialButton>(R.id.deleteSpeciesButton)
        val defaultIds = fi.anssi.kalakartta.data.FishSpecies.getDefaultList().map { it.id }
        if (speciesId != null && speciesId !in defaultIds) {
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage(R.string.delete_species_confirm)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        currentSpecies?.let { db.fishSpeciesDao().delete(it) }
                        finish()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun setupChangeListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isDataLoaded) hasChanges = true
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        nameInput.addTextChangedListener(watcher)
        smallWeightInput.addTextChangedListener(watcher)
        smallLengthInput.addTextChangedListener(watcher)
        largeWeightInput.addTextChangedListener(watcher)
        largeLengthInput.addTextChangedListener(watcher)
        giantWeightInput.addTextChangedListener(watcher)
        giantLengthInput.addTextChangedListener(watcher)
        favouriteCheckBox.setOnCheckedChangeListener { _, _ ->
            if (isDataLoaded) hasChanges = true
        }
    }

    private fun setupIconEdit(layoutId: Int, label: String, help: String, type: String) {
        val layout = findViewById<View>(layoutId)
        layout.findViewById<TextView>(R.id.iconLabel).text = label
        layout.findViewById<ImageView>(R.id.helpIcon).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(help)
                .setPositiveButton("OK", null)
                .show()
        }
        layout.findViewById<MaterialButton>(R.id.selectIconButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/png"
            pendingIconType = type
            selectIconLauncherWithType.launch(intent)
        }
        layout.findViewById<ImageButton>(R.id.deleteIconButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_icon_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    val defaultSpecies = fi.anssi.kalakartta.data.FishSpecies.getDefaultList().find { it.id == speciesId }
                    val defaultPath = when (type) {
                        "DEFAULT" -> defaultSpecies?.icon_default ?: ""
                        "SMALL" -> defaultSpecies?.icon_small ?: ""
                        "LARGE" -> defaultSpecies?.icon_large ?: ""
                        "GIANT" -> defaultSpecies?.icon_giant ?: ""
                        else -> ""
                    }
                    
                    when (type) {
                        "DEFAULT" -> iconDefaultPath = defaultPath
                        "SMALL" -> iconSmallPath = defaultPath
                        "LARGE" -> iconLargePath = defaultPath
                        "GIANT" -> iconGiantPath = defaultPath
                    }
                    if (isDataLoaded) hasChanges = true
                    updateIconUI(layoutId, defaultPath)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private var pendingIconType: String? = null

    private fun loadData() {
        isDataLoaded = false
        val id = speciesId
        if (id != null) {
            currentSpecies = db.fishSpeciesDao().getById(id)
            currentSpecies?.let { s ->
                findViewById<TextView>(R.id.dialogTitle).text = s.name.lowercase().replaceFirstChar { it.uppercase() }
                nameInput.setText(s.name.lowercase().replaceFirstChar { it.uppercase() })
                favouriteCheckBox.isChecked = s.favourite_fish
                smallWeightInput.setText(s.small_weight.toString())
                smallLengthInput.setText(s.small_length.toString())
                largeWeightInput.setText(s.large_weight.toString())
                largeLengthInput.setText(s.large_length.toString())
                giantWeightInput.setText(s.giant_weight.toString())
                giantLengthInput.setText(s.giant_length.toString())

                updateIconUI(R.id.iconDefaultEdit, s.icon_default)
                updateIconUI(R.id.iconSmallEdit, s.icon_small)
                updateIconUI(R.id.iconLargeEdit, s.icon_large)
                updateIconUI(R.id.iconGiantEdit, s.icon_giant)
                
                iconDefaultPath = getIconFileName(s.icon_default)
                iconSmallPath = getIconFileName(s.icon_small)
                iconLargePath = getIconFileName(s.icon_large)
                iconGiantPath = getIconFileName(s.icon_giant)

                // Rajoitus: vain itse lisätyn kalalajin nimeä saa muuttaa.
                // Järjestelmässä valmiina olevien lajien nimi ei ole muokattavissa.
                // Myöskään itse lisätyillä lajeilla nimeä ei saa enää ensimmäisen tallennuksen jälkeen muuttaa.
                // Koska speciesId != null, tämä on joko oletuslaji tai jo kerran tallennettu itse lisätty laji.
                nameInput.isEnabled = false
            }
        } else {
            findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.add_new_species)
            favouriteCheckBox.isChecked = true
            nameInput.isEnabled = true
        }
        isDataLoaded = true
    }

    private fun getIconFileName(path: String): String {
        if (path.isEmpty() || (!path.contains("/") && !path.startsWith("custom_icon_"))) return path
        return try {
            File(path).name
        } catch (e: Exception) {
            path
        }
    }

    private fun updateIconUI(layoutId: Int, iconPath: String) {
        val layout = findViewById<View>(layoutId)
        val preview = layout.findViewById<ImageView>(R.id.iconPreview)
        val pathText = layout.findViewById<TextView>(R.id.iconPath)
        val deleteButton = layout.findViewById<ImageButton>(R.id.deleteIconButton)

        pathText.text = iconPath
        
        // Näytä poistonappi vain, jos ikoni on asetettu EIKÄ se ole oletuslajin oletusikoni
        val defaultSpecies = fi.anssi.kalakartta.data.FishSpecies.getDefaultList().find { it.id == speciesId }
        val isDefaultIcon = defaultSpecies != null && (
            iconPath == defaultSpecies.icon_default ||
            iconPath == defaultSpecies.icon_small ||
            iconPath == defaultSpecies.icon_large ||
            iconPath == defaultSpecies.icon_giant
        )
        deleteButton.visibility = if (iconPath.isNotEmpty() && !isDefaultIcon) View.VISIBLE else View.GONE
        
        if (iconPath.isNotEmpty()) {
            val drawableId = getDrawableId(iconPath)
            if (drawableId != 0) {
                preview.setImageResource(drawableId)
            } else {
                val file = if (iconPath.startsWith("/")) File(iconPath) else File(filesDir, iconPath)
                if (file.exists()) {
                    preview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                } else {
                    preview.setImageDrawable(null)
                }
            }
        } else {
            preview.setImageDrawable(null)
        }
    }

    private fun processSelectedIcon(uri: Uri, type: String) {
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                if (options.outMimeType != "image/png") {
                    Toast.makeText(this, "Vain PNG-kuvat ovat sallittuja", Toast.LENGTH_SHORT).show()
                    return
                }

                if (options.outWidth > 1536 || options.outHeight > 1025) {
                    Toast.makeText(this, "Kuva on liian suuri (max 1536x1025)", Toast.LENGTH_SHORT).show()
                    return
                }

                // Re-open stream for actual decoding
                contentResolver.openInputStream(uri).use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    val fileName = "custom_icon_${UUID.randomUUID()}.png"
                    val file = File(filesDir, fileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    when (type) {
                        "DEFAULT" -> { iconDefaultPath = fileName; updateIconUI(R.id.iconDefaultEdit, file.absolutePath) }
                        "SMALL" -> { iconSmallPath = fileName; updateIconUI(R.id.iconSmallEdit, file.absolutePath) }
                        "LARGE" -> { iconLargePath = fileName; updateIconUI(R.id.iconLargeEdit, file.absolutePath) }
                        "GIANT" -> { iconGiantPath = fileName; updateIconUI(R.id.iconGiantEdit, file.absolutePath) }
                    }
                    if (isDataLoaded) hasChanges = true
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Kuvan käsittely epäonnistui", Toast.LENGTH_SHORT).show()
        }
    }

    // Standard activity result launcher doesn't easily allow passing extra data back in the result
    // except via intent, so we override this to handle the pendingIconType
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // This is handled by selectIconLauncher, but we need to ensure pendingIconType is used
    }

    // Need a different approach for the launcher to know the type
    private fun dummy() {}

    override fun onBackPressed() {
        if (hasChanges) {
            AlertDialog.Builder(this)
                .setMessage(R.string.unsaved_species_changes)
                .setPositiveButton(R.string.back, null)
                .setNegativeButton(R.string.discard) { _, _ ->
                    super.onBackPressed()
                }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun saveData(silent: Boolean = false) {
        var name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            if (!silent) {
                Toast.makeText(this, "Lajin nimi on annettava", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val id = speciesId ?: name.uppercase().replace(" ", "_")
        
        // Tallennetaan nimi suuraakkosina
        name = name.uppercase()
        
        // Jos luodaan uusi, varmistetaan että id ei muutu nimen mukana jatkossa jos tallennetaan heti
        if (speciesId == null) {
            speciesId = id
        }

        val order = currentSpecies?.sortOrder ?: (db.fishSpeciesDao().getAll().size + 1)

        val updated = FishSpecies(
            id = id,
            name = name,
            small_weight = smallWeightInput.text.toString().toLongOrNull() ?: 0,
            small_length = smallLengthInput.text.toString().toLongOrNull() ?: 0,
            large_weight = largeWeightInput.text.toString().toLongOrNull() ?: 0,
            large_length = largeLengthInput.text.toString().toLongOrNull() ?: 0,
            giant_weight = giantWeightInput.text.toString().toLongOrNull() ?: 0,
            giant_length = giantLengthInput.text.toString().toLongOrNull() ?: 0,
            icon_default = iconDefaultPath,
            icon_small = iconSmallPath,
            icon_large = iconLargePath,
            icon_giant = iconGiantPath,
            favourite_fish = favouriteCheckBox.isChecked,
            sortOrder = order
        )

        db.fishSpeciesDao().insert(updated)
        currentSpecies = updated
        
        if (!silent) {
            Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        return resources.getIdentifier(iconName, "drawable", packageName)
    }
}
