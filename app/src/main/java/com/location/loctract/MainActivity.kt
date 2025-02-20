package com.location.loctract

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.location.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
    private var locationReceived = false

    override fun doWork(): Result {
        val latch = CountDownLatch(1)  // Wait for location before returning

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.MINUTES.toMillis(10)) // Higher accuracy
                .setWaitForAccurateLocation(true)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.locations.lastOrNull()?.let { location ->
                        Log.d("LocationWorker", "Logged Location: ${location.latitude}, ${location.longitude}")
                        // Save to database or send to server
                    }
                    locationReceived = true
                    fusedLocationClient.removeLocationUpdates(this)
                    latch.countDown()  // Allow doWork() to return
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

            // Wait for up to 20 seconds to get a location
            latch.await(20, TimeUnit.SECONDS)
        }

        return if (locationReceived) Result.success() else Result.retry()
    }
}


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestLocationPermissions()
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<LocationWorker>().build()
        WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
        val workRequest = PeriodicWorkRequestBuilder<LocationWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)  // Ensures work runs efficiently
                    .build()
            )
            .build()


        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LocationWork",
            ExistingPeriodicWorkPolicy.KEEP, // Prevents duplicate workers
            workRequest
        )
    }



    private fun requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Background location permission must be requested separately
            val foregroundPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            val backgroundPermissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, foregroundPermissions, 1)
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, backgroundPermissions, 2)
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 || requestCode == 2) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!granted) {
                Log.e("MainActivity", "Location permissions are required for tracking.")
            }
        }
    }
}
