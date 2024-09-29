package com.example.taller2_icm

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.taller2_icm.databinding.ActivityLocationBinding
import com.example.taller2_icm.model.MyLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.sql.Date

class LocationActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityLocationBinding

    private lateinit var mMap: GoogleMap
    //direcciones y Geocoder
    private lateinit var geocoder: Geocoder


    private var darkSensor: Sensor? = null
    //sensores de luz
    private lateinit var sensorManager : SensorManager
    private var ligthSensor: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    private var currentLocation: Location? = null

    val RADIUD_OF_EARTH_KM = 6371

    //permission

    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it) {
                locationSettings()
            }else
                Toast.makeText(this, "NO PERMISSION", Toast.LENGTH_SHORT).show()

        }

    )

    val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if(it.resultCode== RESULT_OK){
                startLocationUpdates()
            }
            else{
                Toast.makeText(this, "GPS OFF!", Toast.LENGTH_SHORT).show()
            }

        }
    )

    private lateinit var locationClient: FusedLocationProviderClient
    var locations = mutableListOf<JSONObject>()
    //request y callback
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback //tienen que ser del gms location
    //la dependencia en grade

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //para la ubicación actual
        locationClient= LocationServices.getFusedLocationProviderClient(this)//es hija de activity entinces es base también
        locationRequest= createLocationRequest()
        locationCallback= createLocationCallback()

        //sensores y geocoder
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager //castear porque necesito el tipo de sensor manager
        ligthSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorEventListener = createSensorEventListener()
        geocoder = Geocoder(baseContext)

        //pedir el permiso
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.address.setOnEditorActionListener { v, actionId, event ->
            if(actionId == EditorInfo.IME_ACTION_SEARCH) {
                val address = binding.address.text.toString()
                val location = findLocation(address)
                if(location!=null) {
                    mMap.clear()
                    drawMarker(location, address, R.drawable.baseline_place_24)
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(10f))
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(location))

                }
            }
            return@setOnEditorActionListener true
        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

override fun onMapReady(googleMap: GoogleMap) {
    mMap = googleMap

    if (currentLocation != null) {
        val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
    } else {
        Toast.makeText(this, "Current location is not available", Toast.LENGTH_SHORT).show()
    }
    mMap.setOnMapLongClickListener {
        val address = this.findAddress(it)
        drawMarker(it,address,R.drawable.baseline_place_24)

    }
}

    private fun createLocationRequest(): LocationRequest {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)//encadenar llamados al mismo método
            .setMinUpdateIntervalMillis(3000).build()
        return request

    }

    private fun createLocationCallback(): LocationCallback {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation
                if (location != null) {
                    if (currentLocation == null) {
                        currentLocation = location
                        // Update the map with the current location
                        val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location"))
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        if (distance(currentLocation!!.latitude, currentLocation!!.longitude, location.latitude, location.longitude) > 30) {
                            currentLocation = location
                            persistLocation()
                        }
                    }
                }
            }
        }
        return callback
    }

    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsRespose ->
            startLocationUpdates()

        }
        task.addOnFailureListener { excepcion ->
            if (excepcion is ResolvableApiException) {
                try {
                    var isr: IntentSenderRequest =
                        IntentSenderRequest.Builder(excepcion.resolution).build()
                    locationSettings.launch(isr)

                } catch (sendEx: IntentSender.SendIntentException) {
                    //binding.altitude.text = "Device with no GPS!"
                }
            }
        }
    }

    private fun startLocationUpdates(){

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())//suscribirme a cambios, me falta la reacción a esos cambios (callback)
        }else{
            Toast.makeText(this, "NO PERMISSION", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause(){
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(sensorEventListener)


    }


    private fun stopLocationUpdates(){
        locationClient.removeLocationUpdates(locationCallback)//si entra en pausa, cancelo suscripción de GPS
    }

    private fun persistLocation(){
        val myLocation = MyLocation(Date(System.currentTimeMillis()), currentLocation!!.latitude, currentLocation!!.longitude)
        locations.add(myLocation.toJSON())
        val filename= "locations.json"
        val file = File(baseContext.getExternalFilesDir(null), filename)
        val output = BufferedWriter(FileWriter(file))
        output.write(locations.toString())
        output.close()
        Log.i("LOCATION", "File modified at path" + file)
    }
//----------------------------------------------------------------------------------------------

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat1 - lat2)
        val dLon = Math.toRadians(lon1 - lon2)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val result = RADIUD_OF_EARTH_KM * c * 1000 // Convert to meters
        return Math.round(result * 1000.0) / 1000.0
    }
    fun drawMarker(location : LatLng, description : String?, icon: Int){
        val addressMarker = mMap.addMarker(MarkerOptions().position(location).icon(bitmapDescriptorFromVector(this,
            icon)))!!
        if(description!=null){
            addressMarker.title=description
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
    }

    fun bitmapDescriptorFromVector(context : Context, vectorResId : Int) : BitmapDescriptor {
        val vectorDrawable : Drawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        val bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(),
            Bitmap.Config.ARGB_8888);
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    override fun onResume() {
        super.onResume()
        ligthSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }


    fun findAddress (location : LatLng):String?{
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 2, /*Geocoder.GeocodeListener {  }*/)
        if(addresses != null && !addresses.isEmpty()){
            val addr = addresses.get(0)
            val locname = addr.getAddressLine(0)
            return locname
        }
        return null
    }
    fun findLocation(address : String):LatLng?{
        val addresses = geocoder.getFromLocationName(address, 2)
        if(addresses != null && !addresses.isEmpty()){
            val addr = addresses.get(0)
            val location = LatLng(addr.latitude, addr.
            longitude)
            return location
        }
        return null
    }


    private fun createSensorEventListener(): SensorEventListener {
        val listener : SensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                //se autopregunta su estructura
                //BASECONTEXT YA NO SIRVE PARA INTROSPECCIÓN DE OBJETOS
                if(this@LocationActivity::mMap.isInitialized) {

                    if (ligthSensor != null) {
                        if (event != null) {
                            if (event.values[0] < 5000) {
                                //dark
                                mMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(
                                        baseContext,
                                        R.raw.map_dark
                                    )
                                )

                            } else {
                                mMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(
                                        baseContext,
                                        R.raw.map_light
                                    )
                                )
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }

        }
        return listener

    }


}