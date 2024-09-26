package com.example.taller2_icm

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2_icm.databinding.ActivityImagesBinding
import java.io.File

class ImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImagesBinding

    private lateinit var uri: Uri


    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(), ActivityResultCallback {
            loadImage(it!!)
        }
    )

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
        ActivityResultCallback {
            if(it){
                loadImage(uri)
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagesBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.gallery.setOnClickListener{
            //Debe estár protegido por el coso permission
            galleryLauncher.launch("image/*")
        }

        binding.camera.setOnClickListener{
            val file = File(getFilesDir(),"picFromCamera")
            uri = FileProvider.getUriForFile(baseContext,baseContext.packageName+".fileprovider",file)
            cameraLauncher.launch(uri)
        }
    }

    private fun loadImage(uri: Uri) {
        val imageStream = getContentResolver().openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(imageStream)
        binding.image.setImageBitmap(bitmap)

    }
}