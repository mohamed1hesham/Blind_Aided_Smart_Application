package com.example.basaapp

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import androidx.annotation.RequiresApi

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        setContentView(R.layout.activity_main)
        val TextToSpeechh: Button = findViewById(R.id.textBtn)
        val EmotionsBtn: Button = findViewById(R.id.emotionsBtn)
        val CommonBtn: Button = findViewById(R.id.CommonBtn)
        val FruitBtn: Button = findViewById(R.id.FruitBtn)
        val MoneyBtn: Button = findViewById(R.id.MoneyBtn)
        TextToSpeechh.setOnClickListener {
            val intent = Intent(this, TextToSpeechActivity::class.java)
            startActivity(intent)
        }
        EmotionsBtn.setOnClickListener {
            val intent = Intent(this, EmotionsActivity::class.java)
            startActivity(intent)
        }
        CommonBtn.setOnClickListener {
            val intent = Intent(this, CommonThingsActivity::class.java)
            startActivity(intent)
        }
        FruitBtn.setOnClickListener {
            val intent = Intent(this, FruitsActivity::class.java)
            startActivity(intent)
        }
        MoneyBtn.setOnClickListener {
            val intent = Intent(this, MoneyActivity::class.java)
            startActivity(intent)
        }
    }
}