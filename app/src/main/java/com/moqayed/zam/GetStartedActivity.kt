package com.moqayed.zam

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class GetStartedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_started)
        val getstartedbtn = findViewById<Button>(R.id.GetStartedButton)
        getstartedbtn.setOnClickListener {

            MainActivity.sharedPreferences.edit().putBoolean("AppInitialized", true).commit()
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()

    }
}