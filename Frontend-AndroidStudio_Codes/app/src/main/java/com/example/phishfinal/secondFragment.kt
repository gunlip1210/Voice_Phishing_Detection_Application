package com.example.phishfinal

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import kotlin.math.*
import android.widget.TextView

class secondFragment : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var sharedPreferences : SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_second)
        sharedPreferences = this.getSharedPreferences("analysis", Context.MODE_PRIVATE)

        val temp = sharedPreferences.getString("last", "default_value")
        Log.d("WebSocket_sharedPreferences", "$temp")

        val jsonObject = JSONObject(temp)
        val phone_number = jsonObject.optString("number")
        val percent = jsonObject.optDouble("percent", 0.0)
        val reasons = jsonObject.optJSONArray("reasons")?.let {
            List(it.length()) { i -> it.getString(i) }
        } ?: emptyList()
        val reason = reasons.joinToString("\n")

        progressBar = findViewById(R.id.progressBar)
        progressBar.progress = percent.roundToInt()

        val textView1: TextView = findViewById(R.id.textViewFragment)
        val textView2: TextView = findViewById(R.id.textViewFragment1)

        // Set text for the TextViews
        textView1.text = phone_number + " - " + percent + "%"
        textView2.text = reason

        // ImageButton으로 캐스팅
        val buttonBack2 = findViewById<ImageButton>(R.id.buttonGoBack2)
        buttonBack2.setOnClickListener {
            val intentBack2 = Intent(this, MainActivity::class.java)
            startActivity(intentBack2)
        }
    }
}
