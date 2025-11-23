package com.example.igp.models

import com.google.android.gms.maps.model.LatLng
import java.util.*

data class DonationCenter(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val contact: String = "",
    val state: String = "",
    val city: String = "",
    val website: String = ""
)

data class Driver(
    val id: String = "",
    val name: String = "",
    var phone: String = "",
    var email: String = "",
    val isAvailable: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val currentDeliveryId: String? = null,
//    val rating: Float = 0f,
//    val vehicleType: String = "",
//    val phoneNumber: String = ""
)

data class DeliveryStatus(
    var id: String = UUID.randomUUID().toString(),
    val donationId: String = "",
    val driverId: String = "",
    val donationCenterId: String = "",
    val status: String = "PENDING", // PENDING, ASSIGNED, ACCEPTED, REJECTED, PICKED_UP, DELIVERED
    val pickupLatitude: Double = 0.0,
    val pickupLongitude: Double = 0.0,
    val dropLatitude: Double = 0.0,
    val dropLongitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val assignedTimestamp: Long = 0,
    val acceptedTimestamp: Long = 0,
    val pickedUpTimestamp: Long = 0,
    val deliveredTimestamp: Long = 0
)

data class Donation(
    var id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val type: String = "",
    val quantity: Int = 0,
    val description: String = "",
    val isPerishable: Boolean = false,
    val expiryDate: Date? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)