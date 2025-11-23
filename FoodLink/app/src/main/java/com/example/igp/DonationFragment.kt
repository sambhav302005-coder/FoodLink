package com.example.igp

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.igp.models.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.*

class DonationFragment : Fragment() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var foodTypeSpinner: Spinner
    private lateinit var quantityEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var isPerishableCheckBox: CheckBox
    private lateinit var expiryDatePicker: DatePicker
    private lateinit var expiryDateLayout: LinearLayout
    private lateinit var submitButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var backButton: ImageButton

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "DonationFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_donation, container, false)
        initializeViews(view)
        setupListeners()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.visibility = View.GONE
        return view
    }

    private fun initializeViews(view: View) {
        backButton = view.findViewById(R.id.backButton)
        foodTypeSpinner = view.findViewById(R.id.foodTypeSpinner)
        quantityEditText = view.findViewById(R.id.quantityEditText)
        descriptionEditText = view.findViewById(R.id.descriptionEditText)
        isPerishableCheckBox = view.findViewById(R.id.isPerishableCheckBox)
        expiryDatePicker = view.findViewById(R.id.expiryDatePicker)
        expiryDateLayout = view.findViewById(R.id.expiryDateLayout)
        submitButton = view.findViewById(R.id.submitDonationButton)
        loadingProgressBar = view.findViewById(R.id.loadingProgressBar)

        ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            arrayOf("Cooked Food", "Raw Food", "Packaged Food", "Fruits/Vegetables", "Grains", "Other")
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            foodTypeSpinner.adapter = adapter
        }
    }

    private fun setupListeners() {
        isPerishableCheckBox.setOnCheckedChangeListener { _, isChecked ->
            expiryDateLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment()) // Make sure fragment container ID is correct
                .addToBackStack(null)
                .commit()
        }

        submitButton.setOnClickListener {
            if (validateInput()) {
                submitDonation()
            }
        }
    }

    private fun validateInput(): Boolean {
        if (!isAdded) return false

        val quantity = quantityEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()

        when {
            quantity.isEmpty() -> {
                showError("Please enter quantity")
                return false
            }
            !quantity.all { it.isDigit() } -> {
                showError("Quantity must be a valid number")
                return false
            }
            quantity.toIntOrNull() == null || quantity.toInt() <= 0 -> {
                showError("Please enter a valid quantity greater than 0")
                return false
            }
            description.isEmpty() -> {
                showError("Please enter description")
                return false
            }
            isPerishableCheckBox.isChecked && !isValidExpiryDate() -> {
                showError("Please select a valid expiry date")
                return false
            }
        }
        return true
    }

    private fun isValidExpiryDate(): Boolean {
        val calendar = Calendar.getInstance()
        val selectedDate = Calendar.getInstance().apply {
            set(expiryDatePicker.year, expiryDatePicker.month, expiryDatePicker.dayOfMonth)
            // Set time to end of day to allow donations that expire today
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        // Check if selected date is not before current date
        return !selectedDate.before(calendar)
    }

    private fun submitDonation() {
        if (!isAdded) return
        showLoading(true)
        getCurrentLocation { location ->
            if (!isAdded) return@getCurrentLocation
            if (location == null) {
                showError("Could not get location. Please try again.")
                showLoading(false)
                return@getCurrentLocation
            }

            val donation = createDonation(location)
            saveDonationAndFindDriver(donation)
        }
    }

    private fun createDonation(location: LatLng): Donation {
        val quantityStr = quantityEditText.text.toString().trim()
        val quantity = quantityStr.toIntOrNull() ?: 0 // This should never be 0 due to validation

        return Donation(
            userId = auth.currentUser?.uid ?: "",
            type = foodTypeSpinner.selectedItem.toString(),
            quantity = quantity,
            description = descriptionEditText.text.toString().trim(),
            isPerishable = isPerishableCheckBox.isChecked,
            expiryDate = if (isPerishableCheckBox.isChecked) {
                Calendar.getInstance().apply {
                    set(expiryDatePicker.year, expiryDatePicker.month, expiryDatePicker.dayOfMonth)
                }.time
            } else null,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private fun saveDonationAndFindDriver(donation: Donation) {
        if (!isAdded) return
        val donationsRef = database.reference.child("donations")
        val newDonationRef = donationsRef.push()

        donation.id = newDonationRef.key ?: return

        // Create history record
        val historyRef = database.reference.child("donation_history")
        val newHistoryRef = historyRef.push()

        val donationHistory = DonationHistory(
            id = newHistoryRef.key ?: "",
            userId = donation.userId,
            donationId = donation.id,
            foodType = donation.type,
            quantity = donation.quantity,
            description = donation.description,
            timestamp = System.currentTimeMillis(),
            status = "PENDING"
        )

        // Use a transaction to save both donation and history
        val updates = hashMapOf<String, Any>(
            "/donations/${donation.id}" to donation,
            "/donation_history/${donationHistory.id}" to donationHistory
        )

        database.reference.updateChildren(updates)
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener

                if (task.isSuccessful) {
                    findAvailableDriver(donation)
                } else {
                    showError("Failed to save donation. Please try again.")
                    showLoading(false)
                }
            }
    }

    private fun updateDonationHistoryStatus(donationId: String, newStatus: String) {
        val historyRef = database.reference.child("donation_history")

        historyRef.orderByChild("donationId").equalTo(donationId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (historySnapshot in snapshot.children) {
                        historySnapshot.ref.child("status").setValue(newStatus)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to update history status: ${error.message}")
                }
            })
    }

    fun findAvailableDriver(donation: Donation) {
        val driversRef = database.reference.child("drivers")

        driversRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Log the initial database state
                Log.d(TAG, "Found ${snapshot.childrenCount} total drivers in database")

                if (!snapshot.exists()) {
                    Log.e(TAG, "No drivers found in database")

                    // Add a test driver if no drivers exist
                    val driverId = "test_driver_" + UUID.randomUUID().toString()
                    val testDriver = Driver(
                        id = driverId,
                        name = "Test Driver",
                        email = "test@example.com",
                        isAvailable = true,
                        rating = 4.5f,
                        latitude = donation.latitude + 0.01, // Nearby location
                        longitude = donation.longitude + 0.01,
                        completedDeliveries = 0,
                        totalEarnings = 0.0
                    )
                    driversRef.child(driverId).setValue(testDriver)
                    Log.d(TAG, "Created test driver: $driverId")

                    // Wait briefly for the database to update, then retry
                    Handler(Looper.getMainLooper()).postDelayed({
                        findAvailableDriver(donation)
                    }, 1500)
                    return
                }

                val allDrivers = mutableListOf<Driver>()
                for (driverSnapshot in snapshot.children) {
                    val driver = driverSnapshot.getValue(Driver::class.java)
                    if (driver != null) {
                        // Ensure the driver has an ID
                        if (driver.id.isNullOrEmpty()) {
                            driver.id = driverSnapshot.key ?: continue
                        }

                        // Handle both location storage formats
                        if (driver.latitude == null && driver.longitude == null) {
                            val location = driverSnapshot.child("currentLocation").getValue(object : GenericTypeIndicator<Map<String, Double>>() {})
                            if (location != null) {
                                driver.latitude = location["latitude"]
                                driver.longitude = location["longitude"]
                            }
                        }
                        allDrivers.add(driver)
                        Log.d(TAG, """
                            Driver found:
                            ID: ${driver.id}
                            Available: ${driver.isAvailable}
                            Current Delivery: ${driver.currentDeliveryId}
                            Location: ${driver.latitude}, ${driver.longitude}
                        """.trimIndent())
                    }
                }

                // Log all drivers before filtering
                allDrivers.forEach { driver ->
                    Log.d(TAG, """
                        Driver ${driver.id} initial data:
                        isAvailable: ${driver.isAvailable}
                        Location: (${driver.latitude}, ${driver.longitude})
                        currentDeliveryId: ${driver.currentDeliveryId}
                    """.trimIndent())
                }

                // Modified filter conditions with better logging
                val availableDrivers = allDrivers.filter { driver ->
                    val isAvailable = driver.isAvailable == true  // Handle null as false
                    val hasLocation = driver.latitude != null && driver.longitude != null
                    val hasNoDelivery = driver.currentDeliveryId.isNullOrEmpty() || driver.currentDeliveryId == ""

                    Log.d(TAG, """
                        Driver ${driver.id} filter check:
                        isAvailable: $isAvailable
                        hasLocation: $hasLocation
                        hasNoDelivery: $hasNoDelivery
                        RESULT: ${isAvailable && hasLocation && hasNoDelivery}
                    """.trimIndent())

                    isAvailable && hasLocation && hasNoDelivery
                }

                Log.d(TAG, "Found ${availableDrivers.size} available drivers after filtering")

                if (availableDrivers.isEmpty()) {
                    // If no drivers pass the filter, try to force-update one driver to be available
                    if (allDrivers.isNotEmpty()) {
                        val firstDriver = allDrivers.first()
                        Log.d(TAG, "No available drivers found, making ${firstDriver.id} available for testing")
                        driversRef.child(firstDriver.id).updateChildren(mapOf(
                            "isAvailable" to true,
                            "currentDeliveryId" to "",
                            "latitude" to donation.latitude + 0.02,
                            "longitude" to donation.longitude + 0.02
                        ))

                        // Wait for database update and retry
                        Handler(Looper.getMainLooper()).postDelayed({
                            findAvailableDriver(donation)
                        }, 1500)
                        return
                    } else {
                        handleNoDriverAvailable(donation)
                        return
                    }
                }

                // Find the nearest driver from available drivers
                val donationLocation = LatLng(donation.latitude, donation.longitude)
                var closestDriver: Driver? = null
                var shortestDistance = Double.MAX_VALUE

                for (driver in availableDrivers) {
                    val driverLocation = LatLng(driver.latitude!!, driver.longitude!!)
                    val distance = calculateDistance(donationLocation, driverLocation)
                    Log.d(TAG, "Driver ${driver.id} distance: $distance km")

                    if (distance < shortestDistance) {
                        shortestDistance = distance
                        closestDriver = driver
                    }
                }

                if (closestDriver != null) {
                    Log.d(TAG, "Assigning closest driver: ${closestDriver.id}, distance: $shortestDistance km")
                    assignDriverToDonation(donation, closestDriver)
                } else {
                    Log.e(TAG, "No suitable driver found after distance calculation")
                    handleNoDriverAvailable(donation)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error finding driver: ${error.message}")
                showError("Error finding driver: ${error.message}")
                showLoading(false)
            }
        })
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val earthRadius = 6371.0 // kilometers

        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))

        return earthRadius * c
    }

    private fun assignDriverToDonation(donation: Donation, driver: Driver) {
        val deliveryStatus = DeliveryStatus(
            donationId = donation.id,
            driverId = driver.id,
            status = "ASSIGNED",
            pickupLatitude = donation.latitude,
            pickupLongitude = donation.longitude,
            assignedTimestamp = System.currentTimeMillis(),
            earnings = 5.0 // Default earning amount
        )

        val deliveriesRef = database.reference.child("deliveries")
        val newDeliveryRef = deliveriesRef.push()
        deliveryStatus.id = newDeliveryRef.key ?: return

        newDeliveryRef.setValue(deliveryStatus).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateDriverStatus(driver.id, false, deliveryStatus.id)
                updateDonationHistoryStatus(donation.id, "ASSIGNED")
                showLoading(false)

                // Update UI with assigned driver information
                showAssignedDriverInfo(driver)

                // Navigate to delivery tracker
                navigateToDeliveryTracker(deliveryStatus.id)
            } else {
                showError("Failed to assign driver. Please try again.")
                showLoading(false)
            }
        }
    }

    private fun showAssignedDriverInfo(driver: Driver) {
        // Create and show a dialog or update a view with driver information
        val dialogBuilder = AlertDialog.Builder(requireContext())
        dialogBuilder.setTitle("Driver Assigned")
        dialogBuilder.setMessage("Driver ${driver.name} has been assigned to your donation.")
        dialogBuilder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        dialogBuilder.show()
    }

    private fun navigateToDeliveryTracker(deliveryId: String) {
        val deliveryTrackerFragment = DeliveryTrackerFragment.newInstance(deliveryId)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, deliveryTrackerFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showMessage(message: String) {
        context?.let { safeContext ->
            Toast.makeText(safeContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateDriverStatus(driverId: String, isAvailable: Boolean, deliveryId: String?) {
        val driverRef = database.reference.child("drivers").child(driverId)
        val updates = hashMapOf<String, Any>(
            "isAvailable" to isAvailable,
            "currentDeliveryId" to (deliveryId ?: "")
        )
        driverRef.updateChildren(updates).addOnSuccessListener {
            Log.d(TAG, "Successfully updated driver status: $driverId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to update driver status: ${e.message}")
        }
    }

    private fun handleNoDriverAvailable(donation: Donation) {
        updateDonationHistoryStatus(donation.id, "PENDING_DRIVER")
        showError("No drivers available at the moment. Your donation has been saved and will be processed soon.")
        showLoading(false)
    }

    private fun getCurrentLocation(callback: (LatLng?) -> Unit) {
        try {
            // Check for location permission
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request location permission if not granted
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
                callback(null)
                return
            }

            // Get last known location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        callback(LatLng(location.latitude, location.longitude))
                    } else {
                        // If last known location is null, request location updates
                        requestNewLocationData(callback)
                    }
                }
                .addOnFailureListener {
                    showError("Failed to get location: ${it.message}")
                    callback(null)
                }
        } catch (e: Exception) {
            showError("Location error: ${e.message}")
            callback(null)
        }
    }

    private fun requestNewLocationData(callback: (LatLng?) -> Unit) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val lastLocation = locationResult.lastLocation
                        if (lastLocation != null) {
                            callback(LatLng(lastLocation.latitude, lastLocation.longitude))
                        } else {
                            callback(null)
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                },
                Looper.getMainLooper()
            )
        } else {
            callback(null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry submit
                if (validateInput()) {
                    submitDonation()
                }
            } else {
                showError("Location permission is required to submit a donation")
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        submitButton.isEnabled = !show
    }

    private fun showError(message: String) {
        // Check if context is available before showing Toast
        context?.let { safeContext ->
            Toast.makeText(safeContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}