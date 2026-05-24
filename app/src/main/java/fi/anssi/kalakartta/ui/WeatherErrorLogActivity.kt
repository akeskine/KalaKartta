package fi.anssi.kalakartta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.WeatherError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WeatherErrorLogActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var showMoreButton: Button
    private val errorList = mutableListOf<WeatherError>()
    private lateinit var adapter: ErrorAdapter
    private var currentOffset = 0
    private val limit = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_errors)

        db = AppDatabase.getInstance(this)
        recyclerView = findViewById(R.id.errorRecyclerView)
        showMoreButton = findViewById(R.id.showMoreButton)

        adapter = ErrorAdapter(errorList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        showMoreButton.setOnClickListener { loadMoreErrors() }

        loadMoreErrors()
    }

    private fun loadMoreErrors() {
        lifecycleScope.launch(Dispatchers.IO) {
            val errors = db.weatherErrorDao().getErrors(limit, currentOffset)
            withContext(Dispatchers.Main) {
                if (errors.isNotEmpty()) {
                    errorList.addAll(errors)
                    adapter.notifyItemRangeInserted(currentOffset, errors.size)
                    currentOffset += errors.size
                }
                
                if (errors.size < limit) {
                    showMoreButton.visibility = View.GONE
                }
            }
        }
    }

    class ErrorAdapter(private val errors: List<WeatherError>) : RecyclerView.Adapter<ErrorAdapter.ViewHolder>() {
        
        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val timeText: TextView = view.findViewById(android.R.id.text1)
            val messageText: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val error = errors[position]
            holder.timeText.text = dateFormat.format(Date(error.timestamp))
            holder.messageText.text = error.message
        }

        override fun getItemCount() = errors.size
    }
}
