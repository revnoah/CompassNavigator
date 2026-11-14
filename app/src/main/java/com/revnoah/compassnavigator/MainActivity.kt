package com.revnoah.compassnavigator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {
    private lateinit var compassView: CompassView
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    private var rotationVectorSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false
    private var lastMagneticHeading = Float.NaN
    private var currentLocation: Location? = null
    private var currentDeclination = 0f

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                startLocationUpdates()
            } else {
                compassView.setLocationState(LocationState(message = "Location permission denied"))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)

        compassView = CompassView(this)
        setContentView(compassView)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVectorSensor == null && (accelerometer == null || magnetometer == null)) {
            compassView.setSensorState("Compass sensors unavailable")
        }

        requestLocationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        super.onPause()
    }

    private fun registerSensors() {
        val rotationSensor = rotationVectorSensor
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            compassView.setLocationState(LocationState(message = "Waiting for location permission"))
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val bestLastLocation = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
        bestLastLocation?.let(::updateLocation)

        var registered = false
        providers.forEach { provider ->
            if (locationManager.isProviderEnabled(provider)) {
                runCatching {
                    locationManager.requestLocationUpdates(provider, 1_000L, 1f, this, Looper.getMainLooper())
                    registered = true
                }
            }
        }

        if (!registered && bestLastLocation == null) {
            compassView.setLocationState(LocationState(message = "Enable location services for geographic info"))
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> updateHeadingFromRotationVector(event.values)
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity)
                hasGravity = true
                updateHeadingFromGravityAndMagnetic()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPass(event.values, geomagnetic)
                hasGeomagnetic = true
                updateHeadingFromGravityAndMagnetic()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            compassView.setSensorState("Calibrate compass with a figure-eight motion")
        }
    }

    private fun updateHeadingFromRotationVector(values: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        updateHeadingFromRotationMatrix(rotationMatrix)
    }

    private fun updateHeadingFromGravityAndMagnetic() {
        if (!hasGravity || !hasGeomagnetic) return

        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, geomagnetic)) {
            updateHeadingFromRotationMatrix(rotationMatrix)
        }
    }

    private fun updateHeadingFromRotationMatrix(rotationMatrix: FloatArray) {
        val adjustedRotationMatrix = FloatArray(9)
        when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                adjustedRotationMatrix
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                adjustedRotationMatrix
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                adjustedRotationMatrix
            )
            else -> rotationMatrix.copyInto(adjustedRotationMatrix)
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjustedRotationMatrix, orientation)
        val magneticHeading = normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
        if (lastMagneticHeading.isNaN() || abs(shortestAngle(magneticHeading, lastMagneticHeading)) > 0.3f) {
            lastMagneticHeading = magneticHeading
            compassView.setHeading(magneticHeading, normalizeDegrees(magneticHeading + currentDeclination))
        }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            windowManager.defaultDisplay.rotation
        }
    }

    private fun updateLocation(location: Location) {
        currentLocation = location
        currentDeclination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            location.time
        ).declination

        if (!lastMagneticHeading.isNaN()) {
            compassView.setHeading(lastMagneticHeading, normalizeDegrees(lastMagneticHeading + currentDeclination))
        }

        compassView.setLocationState(
            LocationState(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                declinationDegrees = currentDeclination,
                provider = location.provider ?: "location"
            )
        )
    }

    override fun onLocationChanged(location: Location) = updateLocation(location)

    override fun onProviderDisabled(provider: String) {
        if (currentLocation == null) {
            compassView.setLocationState(LocationState(message = "Enable location services for geographic info"))
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray) {
        val alpha = 0.12f
        for (i in output.indices) {
            output[i] += alpha * (input[i] - output[i])
        }
    }

    private fun normalizeDegrees(degrees: Float): Float = (degrees % 360f + 360f) % 360f

    private fun shortestAngle(a: Float, b: Float): Float {
        var delta = (a - b + 540f) % 360f - 180f
        if (delta < -180f) delta += 360f
        return delta
    }
}
