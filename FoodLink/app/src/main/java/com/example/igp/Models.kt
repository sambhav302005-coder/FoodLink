package com.example.igp

// Models.kt

data class Driver(
    var id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val rating: Float = 0.0f,
    val vehicleType: String = "",
    val vehicleNumber: String = "",
    val licenseNumber: String = "",
    val serviceRadius: Int = 0,
    val isAvailable: Boolean = true,
    val currentDeliveryId: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    val completedDeliveries: Int = 0,
    val totalEarnings: Double = 0.0
)

data class DeliveryStatus(
    var id: String = "",
    val donationId: String = "",
    val driverId: String = "",
    val donationCenterId: String = "",
    var status: String = "PENDING",
    val pickupLatitude: Double = 0.0,
    val pickupLongitude: Double = 0.0,
    val dropLatitude: Double = 0.0,
    val dropLongitude: Double = 0.0,
    val assignedTimestamp: Long = 0,
    val acceptedTimestamp: Long = 0,
    val pickedUpTimestamp: Long = 0,
    val deliveredTimestamp: Long = 0,
    val earnings: Double = 0.0
)