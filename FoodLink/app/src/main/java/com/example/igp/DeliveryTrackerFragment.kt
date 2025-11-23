// DeliveryTrackerFragment.kt
package com.example.igp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.igp.models.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.preference.PreferenceManager
import android.content.Context
import android.graphics.Color
import android.location.Geocoder
import java.util.Locale
import android.os.Handler
import android.os.Looper
import org.osmdroid.util.BoundingBox
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

class DeliveryTrackerFragment : Fragment() {
    private lateinit var mapView: MapView
    private lateinit var statusTextView: TextView
    private lateinit var estimatedTimeTextView: TextView
    private lateinit var deliveryPartnerTextView: TextView
    private lateinit var contactButton: Button
    private var deliveryId: String = ""
    private var currentRoute: Polyline? = null
    private var driverMarker: Marker? = null
    private var pickupMarker: Marker? = null
    private var dropMarker: Marker? = null
    private val mapUpdateHandler = Handler(Looper.getMainLooper())
    private var isTracking = false

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_delivery_tracker, container, false)
        initializeViews(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        // Start tracking after map is fully initialized
        mapView.post {
            startTracking()
        }
    }

    private fun initializeViews(view: View) {
        mapView = view.findViewById(R.id.mapView)
        statusTextView = view.findViewById(R.id.statusTextView)
        estimatedTimeTextView = view.findViewById(R.id.estimatedTimeTextView)
        deliveryPartnerTextView = view.findViewById(R.id.deliveryPartnerTextView)
        contactButton = view.findViewById(R.id.contactDeliveryButton)
        deliveryId = arguments?.getString("deliveryId") ?: ""

        contactButton.setOnClickListener {
            // Implement contact functionality
            handleContactButtonClick()
        }
    }

    private fun handleContactButtonClick() {
        // Implement your contact logic here
        // For example, show a dialog with contact options
        // or directly initiate a call/message
    }

    private fun setupMap() {
        if (!::mapView.isInitialized) return

        val ctx = requireContext()
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = ctx.packageName

        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }

    private fun startTracking() {
        if (!::mapView.isInitialized || deliveryId.isEmpty()) return

        isTracking = true

        val deliveryRef = database.reference.child("deliveries").child(deliveryId)
        deliveryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val delivery = snapshot.getValue(DeliveryStatus::class.java)
                delivery?.let {
                    updateUI(it)
                    fetchDeliveryDetails(it)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                handleDatabaseError(error)
            }
        })
    }

    private fun handleDatabaseError(error: DatabaseError) {
        // Implement error handling
        // For example, show an error message to the user
    }

    private fun fetchDeliveryDetails(delivery: DeliveryStatus) {
        if (!::mapView.isInitialized) return

        database.reference.child("drivers").child(delivery.driverId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val driver = snapshot.getValue(Driver::class.java)
                    driver?.let {
                        updateDriverUI(it)
                        updateMapWithRoute(delivery, it)
                        updateETA(delivery, it)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    handleDatabaseError(error)
                }
            })
    }

    private fun updateUI(delivery: DeliveryStatus) {
        if (!isAdded || !::statusTextView.isInitialized) return

        val formattedStatus = formatDeliveryStatus(delivery.status)
        statusTextView.text = "Status: $formattedStatus"
    }

    private fun formatDeliveryStatus(status: String): String {
        return when (status) {
            "ACCEPTED" -> "Driver Assigned"
            "PICKED_UP" -> "Order Picked Up"
            "DELIVERED" -> "Delivered"
            else -> status.replace("_", " ").capitalize()
        }
    }

    private fun updateDriverUI(driver: Driver) {
        if (!isAdded || !::deliveryPartnerTextView.isInitialized) return

        deliveryPartnerTextView.text = "Driver: ${driver.name}"
    }

    private fun updateMapWithRoute(delivery: DeliveryStatus, driver: Driver) {
        if (!::mapView.isInitialized || !isAdded) return

        // Clear existing overlays
        mapView.overlays.clear()

        addDriverMarker(driver)
        addLocationMarkers(delivery)
        drawRoute(delivery, driver)

        val bounds = calculateMapBounds(delivery, driver)
        mapView.zoomToBoundingBox(bounds, true)
        mapView.invalidate()
    }

    private fun addDriverMarker(driver: Driver) {
        if (!::mapView.isInitialized || !isAdded) return

        val driverLat = driver.latitude ?: return
        val driverLng = driver.longitude ?: return

        try {
            val driverPosition = GeoPoint(driverLat, driverLng)
            driverMarker = Marker(mapView).apply {
                position = driverPosition
                title = "Driver Location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = resources.getDrawable(R.drawable.ic_delivery_truck, null)
            }
            mapView.overlays.add(driverMarker)
        } catch (e: Exception) {
            // Handle marker creation exception
        }
    }

    private fun addLocationMarkers(delivery: DeliveryStatus) {
        if (!::mapView.isInitialized || !isAdded) return

        try {
            if (delivery.pickupLatitude != null && delivery.pickupLongitude != null) {
                pickupMarker = Marker(mapView).apply {
                    position = GeoPoint(delivery.pickupLatitude, delivery.pickupLongitude)
                    title = "Pickup Location"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = resources.getDrawable(R.drawable.ic_pickup_location, null)
                }
                mapView.overlays.add(pickupMarker)
            }

            if (delivery.dropLatitude != null && delivery.dropLongitude != null) {
                dropMarker = Marker(mapView).apply {
                    position = GeoPoint(delivery.dropLatitude, delivery.dropLongitude)
                    title = "Drop Location"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = resources.getDrawable(R.drawable.ic_drop_location, null)
                }
                mapView.overlays.add(dropMarker)
            }
        } catch (e: Exception) {
            // Handle marker creation exception
        }
    }

    private fun drawRoute(delivery: DeliveryStatus, driver: Driver) {
        if (!::mapView.isInitialized || !isAdded) return

        if (currentRoute != null) {
            mapView.overlays.remove(currentRoute)
        }

        val routePoints = mutableListOf<GeoPoint>()

        // Safely get driver location
        val driverLat = driver.latitude
        val driverLng = driver.longitude
        if (driverLat != null && driverLng != null) {
            routePoints.add(GeoPoint(driverLat, driverLng))
        }

        if (delivery.status != "PICKED_UP" && delivery.status != "DELIVERED") {
            delivery.pickupLatitude?.let { lat ->
                delivery.pickupLongitude?.let { lon ->
                    routePoints.add(GeoPoint(lat, lon))
                }
            }
        }

        delivery.dropLatitude?.let { lat ->
            delivery.dropLongitude?.let { lon ->
                routePoints.add(GeoPoint(lat, lon))
            }
        }

        if (routePoints.size >= 2) {
            try {
                currentRoute = Polyline().apply {
                    setPoints(routePoints)
                    color = Color.BLUE
                    width = 5f
                }
                mapView.overlays.add(currentRoute)
            } catch (e: Exception) {
                // Handle polyline creation exception
            }
        }
    }

    private fun calculateMapBounds(delivery: DeliveryStatus, driver: Driver): BoundingBox {
        val points = mutableListOf<GeoPoint>()

        // Safely get driver location
        val driverLat = driver.latitude
        val driverLng = driver.longitude
        if (driverLat != null && driverLng != null) {
            points.add(GeoPoint(driverLat, driverLng))
        }

        delivery.pickupLatitude?.let { lat ->
            delivery.pickupLongitude?.let { lon ->
                points.add(GeoPoint(lat, lon))
            }
        }

        delivery.dropLatitude?.let { lat ->
            delivery.dropLongitude?.let { lon ->
                points.add(GeoPoint(lat, lon))
            }
        }

        if (points.isEmpty()) {
            return BoundingBox(0.0, 0.0, 0.0, 0.0)
        }

        var north = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var west = Double.POSITIVE_INFINITY

        points.forEach { point ->
            north = max(north, point.latitude)
            south = min(south, point.latitude)
            east = max(east, point.longitude)
            west = min(west, point.longitude)
        }

        val padding = 0.01 // Approximately 1km padding
        return BoundingBox(north + padding, east + padding, south - padding, west - padding)
    }

    private fun updateETA(delivery: DeliveryStatus, driver: Driver) {
        if (!::mapView.isInitialized || !::estimatedTimeTextView.isInitialized || !isAdded) return

        // Safely get driver location
        val driverLat = driver.latitude ?: return
        val driverLng = driver.longitude ?: return

        val destination = when (delivery.status) {
            "ACCEPTED" -> {
                val pickupLat = delivery.pickupLatitude ?: return
                val pickupLng = delivery.pickupLongitude ?: return
                GeoPoint(pickupLat, pickupLng)
            }
            "PICKED_UP" -> {
                val dropLat = delivery.dropLatitude ?: return
                val dropLng = delivery.dropLongitude ?: return
                GeoPoint(dropLat, dropLng)
            }
            else -> return
        }

        val driverLocation = GeoPoint(driverLat, driverLng)
        val distance = calculateDistance(driverLocation, destination)
        val averageSpeed = 30.0 // km/h
        val estimatedMinutes = (distance / averageSpeed * 60).toInt()

        estimatedTimeTextView.text = "Estimated arrival in $estimatedMinutes minutes"
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(point2.latitude - point1.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = sin(dLat/2) * sin(dLat/2) +
                cos(Math.toRadians(point1.latitude)) * cos(Math.toRadians(point2.latitude)) *
                sin(dLon/2) * sin(dLon/2)

        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return earthRadius * c
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
            if (!isTracking) {
                mapView.post {
                    startTracking()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        isTracking = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mapView.isInitialized) {
            mapView.onDetach()
        }
    }

    companion object {
        fun newInstance(deliveryId: String): DeliveryTrackerFragment {
            return DeliveryTrackerFragment().apply {
                arguments = Bundle().apply {
                    putString("deliveryId", deliveryId)
                }
            }
        }
    }
}