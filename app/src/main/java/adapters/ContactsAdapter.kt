package adapters

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import com.example.taller2_icm.databinding.ContactrowBinding

class ContactsAdapter(context: Context?, c: Cursor?, flags: Int) :
    CursorAdapter(context, c, flags) {
    private lateinit var  binding : ContactrowBinding

    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        binding = ContactrowBinding.inflate(LayoutInflater.from(context))
        return binding.contactrow
    }

    override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
        val id = cursor?.getInt(0)
        val name = cursor?.getString(1)
        binding.ContactID.text = id.toString()
        binding.ContactName.text = name
    }
}