package com.example.igp


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.igp.models.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.preference.PreferenceManager
import androidx.cardview.widget.CardView
import com.google.android.gms.location.*

class DriverFragment : Fragment() {
    private lateinit var profileCard: CardView
    private lateinit var profileImage: ImageView
    private lateinit var driverName: TextView
    private lateinit var driverEmail: TextView
    private lateinit var driverRating: RatingBar
    private lateinit var availabilitySwitch: Switch
    private lateinit var deliveriesCount: TextView
    private lateinit var totalEarnings: TextView
    // Add the missing button declarations
    private lateinit var logoutButton: Button

    private lateinit var currentOrderCard: CardView
    private lateinit var mapView: MapView
    private lateinit var currentDeliveryStatus: TextView
    private lateinit var pickupAddress: TextView
    private lateinit var dropAddress: TextView
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button
    private lateinit var updateStatusButton: Button

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: DeliveryHistoryAdapter

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private var currentDriver: Driver? = null
    private var currentDelivery: DeliveryStatus? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationRequest: LocationRequest? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_driver_new, container, false)
        initializeViews(view)
        setupMap()
        setupLocationServices()
        setupListeners()
        loadDriverData()
        loadDeliveryHistory()
        return view
    }
    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_UPDATE_INTERVAL / 2
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateDriverLocation(location.latitude, location.longitude)
                }
            }
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        locationRequest?.let { request ->
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun updateDriverLocation(latitude: Double, longitude: Double) {
        val driverId = auth.currentUser?.uid ?: return
        val driverRef = database.reference.child("drivers").child(driverId)

        val updates = HashMap<String, Any>()
        updates["latitude"] = latitude
        updates["longitude"] = longitude

        driverRef.updateChildren(updates).addOnFailureListener { e ->
            Log.e("DriverFragment", "Failed to update location: ${e.message}")
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
                startLocationUpdates()
            } else {
                showError("Location permission is required for driver status updates")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e("DriverFragment", "Error stopping location updates: ${e.message}")
        }
    }

    private fun initializeViews(view: View) {
        // Profile section

        profileCard = view.findViewById(R.id.profileCard)
        profileImage = view.findViewById(R.id.profileImage)
        driverName = view.findViewById(R.id.driverName)
        logoutButton = view.findViewById(R.id.logoutButton)
        driverEmail = view.findViewById(R.id.driverEmail)
        driverRating = view.findViewById(R.id.driverRating)
        availabilitySwitch = view.findViewById(R.id.availabilitySwitch)
        deliveriesCount = view.findViewById(R.id.deliveriesCount)
        totalEarnings = view.findViewById(R.id.totalEarnings)

        // Current order section
        currentOrderCard = view.findViewById(R.id.currentOrderCard)
        mapView = view.findViewById(R.id.mapView)
        currentDeliveryStatus = view.findViewById(R.id.currentDeliveryStatus)
        pickupAddress = view.findViewById(R.id.pickupAddress)
        dropAddress = view.findViewById(R.id.dropAddress)
        acceptButton = view.findViewById(R.id.acceptButton)
        rejectButton = view.findViewById(R.id.rejectButton)
        updateStatusButton = view.findViewById(R.id.updateStatusButton)

        // History section
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        historyRecyclerView.layoutManager = LinearLayoutManager(context)
        historyAdapter = DeliveryHistoryAdapter()
        historyRecyclerView.adapter = historyAdapter
    }

    private fun setupMap() {
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        // Set default location (can be updated when delivery is loaded)
        val defaultPoint = GeoPoint(0.0, 0.0)
        mapView.controller.setCenter(defaultPoint)
    }

    private fun setupListeners() {
        availabilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            updateDriverAvailability(isChecked)
        }

        acceptButton.setOnClickListener { acceptDelivery() }
        rejectButton.setOnClickListener { rejectDelivery() }
        updateStatusButton.setOnClickListener { updateDeliveryStatus() }


        logoutButton.setOnClickListener {
            // Handle logout
            (activity as? MainActivity)?.signOut()
        }
    }

    private fun loadDriverData() {
        val driverId = auth.currentUser?.uid ?: return
        val driverRef = database.reference.child("drivers").child(driverId)

        driverRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentDriver = snapshot.getValue(Driver::class.java)
                currentDriver?.let { driver ->
                    updateProfileUI(driver)
                    if (driver.currentDeliveryId != null) {
                        loadCurrentDelivery(driver.currentDeliveryId)
                    } else {
                        currentOrderCard.visibility = View.GONE
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Error loading driver data: ${error.message}")
            }
        })
    }

    private fun updateProfileUI(driver: Driver) {
        driverName.text = driver.name
        driverEmail.text = driver.email
        driverRating.rating = driver.rating ?: 0f
        availabilitySwitch.isChecked = driver.isAvailable
        deliveriesCount.text = "Total Deliveries: ${driver.completedDeliveries ?: 0}"
        totalEarnings.text = "Total Earnings: $${driver.totalEarnings ?: 0}"
    }
    private fun updateDeliveryUI() {
        currentDelivery?.let { delivery ->
            currentOrderCard.visibility = View.VISIBLE

            val status = when (delivery.status) {
                "PENDING" -> "Waiting for acceptance"
                "ASSIGNED" -> "New delivery assigned"
                "ACCEPTED" -> "En route to pickup"
                "PICKED_UP" -> "Food picked up, heading to delivery"
                "DELIVERED" -> "Delivery completed"
                else -> "Unknown status"
            }

            currentDeliveryStatus.text = status

            // Show/hide buttons based on status
            when (delivery.status) {
                "ASSIGNED" -> {
                    acceptButton.visibility = View.VISIBLE
                    rejectButton.visibility = View.VISIBLE
                    updateStatusButton.visibility = View.GONE
                }
                "ACCEPTED", "PICKED_UP" -> {
                    acceptButton.visibility = View.GONE
                    rejectButton.visibility = View.GONE
                    updateStatusButton.visibility = View.VISIBLE

                    // Update button text based on status
                    updateStatusButton.text = if (delivery.status == "ACCEPTED") "Mark as Picked Up" else "Complete Delivery"
                }
                else -> {
                    acceptButton.visibility = View.GONE
                    rejectButton.visibility = View.GONE
                    updateStatusButton.visibility = View.GONE
                }
            }
        } ?: run {
            currentOrderCard.visibility = View.GONE
        }
    }

    private fun loadCurrentDelivery(deliveryId: String) {
        val deliveryRef = database.reference.child("deliveries").child(deliveryId)

        deliveryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentDelivery = snapshot.getValue(DeliveryStatus::class.java)
                updateDeliveryUI()
                updateMapMarkers()
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Error loading delivery data: ${error.message}")
            }
        })
    }

    private fun updateMapMarkers() {
        currentDelivery?.let { delivery ->
            mapView.overlays.clear()

            // Add pickup marker
            val pickupPoint = GeoPoint(delivery.pickupLatitude, delivery.pickupLongitude)
            val pickupMarker = Marker(mapView)
            pickupMarker.position = pickupPoint
            pickupMarker.title = "Pickup Location"
            mapView.overlays.add(pickupMarker)

            // Add drop marker if available
            if (delivery.dropLatitude != 0.0 && delivery.dropLongitude != 0.0) {
                val dropPoint = GeoPoint(delivery.dropLatitude, delivery.dropLongitude)
                val dropMarker = Marker(mapView)
                dropMarker.position = dropPoint
                dropMarker.title = "Drop Location"
                mapView.overlays.add(dropMarker)
            }

            // Center map on pickup location
            mapView.controller.setCenter(pickupPoint)
            mapView.invalidate()
        }
    }

    private fun loadDeliveryHistory() {
        val driverId = auth.currentUser?.uid ?: return
        val historyRef = database.reference.child("deliveries")
            .orderByChild("driverId")
            .equalTo(driverId)

        historyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val deliveries = mutableListOf<DeliveryStatus>()
                for (deliverySnapshot in snapshot.children) {
                    val delivery = deliverySnapshot.getValue(DeliveryStatus::class.java)
                    if (delivery?.status == "DELIVERED") {
                        deliveries.add(delivery)
                    }
                }
                historyAdapter.submitList(deliveries)
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Error loading history: ${error.message}")
            }
        })
    }

    // ... rest of the existing methods (acceptDelivery, rejectDelivery, updateDeliveryStatus) ...
    private fun updateDriverAvailability(isAvailable: Boolean) {
        val driverId = auth.currentUser?.uid ?: return
        val driverRef = database.reference.child("drivers").child(driverId)
        val updates = HashMap<String, Any>()
        updates["isAvailable"] = isAvailable
        driverRef.updateChildren(updates).addOnFailureListener {
            showError("Failed to update availability: ${it.message}")
        }
    }

    private fun acceptDelivery() {
        currentDelivery?.let { delivery ->
            val deliveryRef = database.reference.child("deliveries").child(delivery.id)
            val updates = HashMap<String, Any>()
            updates["status"] = "ACCEPTED"
            updates["acceptedTimestamp"] = System.currentTimeMillis()

            deliveryRef.updateChildren(updates).addOnSuccessListener {
                updateStatusButton.visibility = View.VISIBLE
                acceptButton.visibility = View.GONE
                rejectButton.visibility = View.GONE
            }.addOnFailureListener {
                showError("Failed to accept delivery: ${it.message}")
            }
        }
    }

    private fun rejectDelivery() {
        currentDelivery?.let { delivery ->
            val deliveryRef = database.reference.child("deliveries").child(delivery.id)
            val updates = HashMap<String, Any>()
            updates["status"] = "REJECTED"

            deliveryRef.updateChildren(updates).addOnSuccessListener {
                // Reset driver availability
                updateDriverAvailability(true)
                currentOrderCard.visibility = View.GONE
            }.addOnFailureListener {
                showError("Failed to reject delivery: ${it.message}")
            }
        }
    }

    private fun updateDeliveryStatus() {
        currentDelivery?.let { delivery ->
            val newStatus = when (delivery.status) {
                "ACCEPTED" -> "PICKED_UP"
                "PICKED_UP" -> "DELIVERED"
                else -> return
            }

            val deliveryRef = database.reference.child("deliveries").child(delivery.id)
            val updates = HashMap<String, Any>()
            updates["status"] = newStatus

            when (newStatus) {
                "PICKED_UP" -> updates["pickedUpTimestamp"] = System.currentTimeMillis()
                "DELIVERED" -> {
                    updates["deliveredTimestamp"] = System.currentTimeMillis()
                    // Update driver statistics
                    updateDriverStatistics(delivery.earnings)
                }
            }

            deliveryRef.updateChildren(updates).addOnFailureListener {
                showError("Failed to update delivery status: ${it.message}")
            }
        }
    }

    private fun updateDriverStatistics(earnings: Double) {
        val driverId = auth.currentUser?.uid ?: return
        val driverRef = database.reference.child("drivers").child(driverId)

        currentDriver?.let { driver ->
            val updates = HashMap<String, Any>()
            updates["completedDeliveries"] = (driver.completedDeliveries + 1)
            updates["totalEarnings"] = (driver.totalEarnings + earnings)
            updates["isAvailable"] = true
            updates["currentDeliveryId"] = ""

            driverRef.updateChildren(updates)
        }
    }


    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

class DeliveryHistoryAdapter :
    RecyclerView.Adapter<DeliveryHistoryAdapter.DeliveryViewHolder>() {

    private var deliveries: List<DeliveryStatus> = emptyList()

    fun submitList(newDeliveries: List<DeliveryStatus>) {
        deliveries = newDeliveries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delivery_history, parent, false)
        return DeliveryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeliveryViewHolder, position: Int) {
        holder.bind(deliveries[position])
    }

    override fun getItemCount() = deliveries.size

    class DeliveryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.deliveryDate)
        private val statusText: TextView = view.findViewById(R.id.deliveryStatus)
        private val earningsText: TextView = view.findViewById(R.id.deliveryEarnings)

        fun bind(delivery: DeliveryStatus) {
            dateText.text = "Date: ${android.text.format.DateFormat.format("MM/dd/yyyy", delivery.deliveredTimestamp)}"
            statusText.text = "Status: ${delivery.status}"
            earningsText.text = "Earnings: $${delivery.earnings}"
        }
    }
}