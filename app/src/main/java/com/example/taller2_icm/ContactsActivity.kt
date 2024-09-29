package com.example.taller2_icm

import com.example.taller2_icm.adapters.ContactsAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2_icm.databinding.ActivityContactsBinding

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding

    val projection = arrayOf(ContactsContract.Profile._ID, ContactsContract.Profile.DISPLAY_NAME)
    lateinit var adapter : ContactsAdapter

    val contactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            updateUI(it)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = ContactsAdapter(this,null,8)
        binding.listContacts.adapter = adapter
        contactsPermissionRequest(android.Manifest.permission.READ_CONTACTS)
    }

    private fun contactsPermissionRequest(permission : String){
        if(ContextCompat.checkSelfPermission(this,permission)== PackageManager.PERMISSION_DENIED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                Toast.makeText(this, "The permission is needed for fun", Toast.LENGTH_LONG).show()
            }
            contactsPermission.launch(permission)
        }
        else{
            updateUI(true)
        }
    }

    fun updateUI(flag : Boolean){
        if(flag){
            val cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI,projection,null,null,null)
            adapter.changeCursor(cursor)
        }else{
            val arreglo = Array<String>(2){i->"Sin acceso"}
            val errorAdapter = ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,arreglo)
            binding.listContacts.adapter = errorAdapter
        }
    }
}