package com.example.llmtest

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.llmtest.databinding.ActivityModeSelectBinding

class ModeSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModeSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModeSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnWalk.setOnClickListener {
            startActivity(Intent(this, WalkActivity::class.java))
        }

        binding.btnVisually.setOnClickListener {
            startActivity(Intent(this, VisuallyActivity::class.java))
        }
    }
}
