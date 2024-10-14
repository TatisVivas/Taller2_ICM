package com.example.taller2_icm

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.os.StrictMode
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
import com.google.android.gms.maps.model.PolylineOptions
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.sql.Date

class LocationActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityLocationBinding
    private lateinit var mMap: GoogleMap
    private lateinit var geocoder: Geocoder

    private var darkSensor: Sensor? = null
    private lateinit var sensorManager: SensorManager
    private var ligthSensor: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    private var currentLocation: Location? = null
    private val RADIUD_OF_EARTH_KM = 6371

    //Para OSM Bonuspack
    private lateinit var roadManager: RoadManager
    private var roadOverlay: Polyline? = null



    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if (it) {
                locationSettings()
            } else {
                Toast.makeText(this, "NO PERMISSION", Toast.LENGTH_SHORT).show()
            }
        }
    )

    private val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "GPS OFF!", Toast.LENGTH_SHORT).show()
            }
        }
    )
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val locations = mutableListOf<JSONObject>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roadManager = OSRMRoadManager(this, "ANDROID")

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Initialize location services
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        // Initialize sensors and geocoder
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        ligthSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorEventListener = createSensorEventListener()
        geocoder = Geocoder(baseContext)

        // Request location permissions
        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)


        binding.address.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val address = binding.address.text.toString()
                val location = findLocation(address)
                val addressMarker= findAddress(location!!)
                if (location != null) {
                    //mMap.clear()
                    drawMarker(location, addressMarker, R.drawable.baseline_place_24)
                    //hacer zoom al punto nuevo
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(location))


                    currentLocation?.let { currentLoc ->
                        val distance = distance(currentLoc.latitude, currentLoc.longitude, location.latitude, location.longitude)
                        val formattedDistance = String.format("%.3f", distance)
                        Toast.makeText(this, "Distance to marker: $formattedDistance meters", Toast.LENGTH_SHORT).show()
                        drawRoute(GeoPoint(currentLoc.latitude, currentLoc.longitude), GeoPoint(location.latitude, location.longitude))
                        drawMarker(location, addressMarker, R.drawable.baseline_place_24)
                        drawMarker(LatLng(currentLoc.latitude, currentLoc.longitude), "Current Location ${findAddress(LatLng(currentLoc.latitude, currentLoc.longitude))}", R.drawable.baseline_place_24)
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
                    }
                }

            }
            return@setOnEditorActionListener true
        }

        binding.rutaCompleta.setOnClickListener {

            drawRouteJson()
        }
        // Get the SupportMapFragment and notify when the map is ready to be used
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.uiSettings.isZoomControlsEnabled = true

        mMap.setOnMapLongClickListener {
            val address = this.findAddress(it)
            drawMarker(it, address, R.drawable.baseline_place_24)
            currentLocation?.let { location ->
                val distance = distance(location.latitude, location.longitude, it.latitude, it.longitude)
                val formattedDistance = String.format("%.3f", distance)
                Toast.makeText(this, "Distance to marker: $formattedDistance meters", Toast.LENGTH_SHORT).show()
                drawRoute(GeoPoint(location.latitude, location.longitude), GeoPoint(it.latitude, it.longitude))
                drawMarker(it, address, R.drawable.baseline_place_24)
                drawMarker(LatLng(location.latitude, location.longitude), "Current Location ${findAddress(LatLng(location.latitude, location.longitude))}", R.drawable.baseline_place_24)
                //mover la camara a la nueva ubicacion seleccionada por el usuario
                mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
                mMap.moveCamera(CameraUpdateFactory.newLatLng(it))


            }
        }

    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(3000)
            .build()
    }

    private fun createLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation
                if (location != null) {
                    if (currentLocation == null) {
                        currentLocation = location
                        val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location ${findAddress(currentLatLng)}"))
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLatLng))
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
                    } else {
                        if (distance(currentLocation!!.latitude, currentLocation!!.longitude, location.latitude, location.longitude) > 30) {
                            currentLocation = location
                            persistLocation()
                        }
                    }
                }
            }
        }
    }

    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val isr = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Handle the exception
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            Toast.makeText(this, "NO PERMISSION", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }


    private fun persistLocation(){
        val myLocation = MyLocation(Date(System.currentTimeMillis()), currentLocation!!.latitude, currentLocation!!.longitude)
        locations.add(myLocation.toJSON())
        val filename= "locations.json"
        val file = File(baseContext.getExternalFilesDir(null), filename)
        val output = BufferedWriter(FileWriter(file))
        output.write(locations.toString())
        output.close()
        Log.i("LOCATIONHELP", "File modified at path" + file)
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat1 - lat2)
        val dLon = Math.toRadians(lon1 - lon2)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return RADIUD_OF_EARTH_KM * c * 1000 // Convert to meters
    }

    //--------------------------------------------------------------------------------

    private fun drawMarker(location: LatLng, description: String?, icon: Int) {
        val addressMarker = mMap.addMarker(MarkerOptions().position(location).icon(bitmapDescriptorFromVector(this, icon)))!!
        if (description != null) {
            addressMarker.title = description
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
        val vectorDrawable: Drawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
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

    fun findAddress(location: LatLng): String? {
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 2)
        if (addresses != null && addresses.isNotEmpty()) {
            return addresses[0].getAddressLine(0)
        }
        return null
    }

    fun findLocation(address: String): LatLng? {
        val addresses = geocoder.getFromLocationName(address, 2)
        if (addresses != null && addresses.isNotEmpty()) {
            val addr = addresses[0]
            return LatLng(addr.latitude, addr.longitude)
        }
        return null
    }

    private fun createSensorEventListener(): SensorEventListener {
        return object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (this@LocationActivity::mMap.isInitialized) {
                    if (ligthSensor != null && event != null) {
                        if (event.values[0] < 5000) {
                            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(baseContext, R.raw.map_dark))
                        } else {
                            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(baseContext, R.raw.map_light))
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    //--------------------------------------------------------------------------------
    fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        val routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)
        val road = roadManager.getRoad(routePoints)
        Log.i("MapsApp", "Route length: ${road.mLength} km")
        Log.i("MapsApp", "Duration: ${road.mDuration / 60} min")
        if (mMap != null) {
            if (roadOverlay != null) {
                // Remove the previous overlay from the map
                (roadOverlay as? Overlay)?.let { overlay ->
                    mMap.clear() // Clear all overlays
                }
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.outlinePaint.color = Color.RED
            roadOverlay!!.outlinePaint.strokeWidth = 10F
            // Add the new overlay to the map
            // Note: Google Maps does not support osmdroid overlays directly
            // You need to convert the osmdroid overlay to a Google Maps Polyline
            val polylineOptions = PolylineOptions()
            for (point in roadOverlay!!.points) {
                polylineOptions.add(LatLng(point.latitude, point.longitude))
            }
            polylineOptions.color(Color.RED)
            polylineOptions.width(10F)
            mMap.addPolyline(polylineOptions)
        }
    }

    fun drawRouteJson() {
        val filename = "locations.json"
        val file = File(baseContext.getExternalFilesDir(null), filename)
        if (!file.exists()) {
            Toast.makeText(this, "No locations found", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonString = BufferedReader(FileReader(file)).use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val routePoints = ArrayList<GeoPoint>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val latitude = jsonObject.getDouble("latitude")
            val longitude = jsonObject.getDouble("longitude")
            routePoints.add(GeoPoint(latitude, longitude))

            val latLng = LatLng(latitude, longitude)
            val address = findAddress(latLng)
            drawMarker(latLng, address, R.drawable.baseline_place_24)
        }

        if (routePoints.size < 1) {
            Toast.makeText(this, "Not enough points to draw a route", Toast.LENGTH_SHORT).show()
            return
        }

        val road = roadManager.getRoad(routePoints)
        if (mMap != null) {
            if (roadOverlay != null) {
                // Remove the previous overlay from the map
                (roadOverlay as? Overlay)?.let { overlay ->
                    mMap.clear() // Clear all overlays
                }
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.outlinePaint.color = Color.RED
            roadOverlay!!.outlinePaint.strokeWidth = 10F
            // Add the new overlay to the map
            val polylineOptions = PolylineOptions()
            for (point in roadOverlay!!.points) {
                polylineOptions.add(LatLng(point.latitude, point.longitude))
            }
            polylineOptions.color(Color.RED)
            polylineOptions.width(10F)
            mMap.addPolyline(polylineOptions)
        }
    }

}