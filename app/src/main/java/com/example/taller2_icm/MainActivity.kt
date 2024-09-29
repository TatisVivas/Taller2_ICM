package com.example.taller2_icm

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2_icm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.contacts.setOnClickListener {
            startActivity(Intent(baseContext, ContactsActivity::class.java))
        }
        binding.images.setOnClickListener {
            startActivity(Intent(baseContext, ImagesActivity::class.java))
        }
        binding.map.setOnClickListener {
            startActivity(Intent(baseContext, LocationActivity::class.java))
        }


    }
}