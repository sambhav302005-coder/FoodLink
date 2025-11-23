package com.example.igp.utils

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

object FirebaseUtil {
    private const val TAG = "FirebaseUtil"

    fun initializeDatabase() {
        try {
            // Enable offline persistence
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // This might throw if setPersistenceEnabled was already called
            Log.w(TAG, "Could not set persistence: ${e.message}")
        }
    }

    fun loadDonationCenters(csvData: String) {
        val database = Firebase.database.reference
        val centersRef = database.child("donationCenters")

        // Skip header row if it exists
        val lines = csvData.lines().filter { it.isNotBlank() }
        var startIndex = 0

        // Check if first line is a header
        if (lines.isNotEmpty() && lines[0].contains("Address") && lines[0].contains("Name")) {
            startIndex = 1
        }

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val fields = line.split(",")
                .map { it.trim().removeSurrounding("\"") }

            Log.d(TAG, "Processing line $i: $line")

            try {
                if (fields.size >= 8) {
                    val center = DonationCenter(
                        id = "",
                        name = fields[1],
                        address = fields[0],
                        state = fields[2],
                        city = fields[3],
                        contact = fields[4],
                        website = fields[5],
                        latitude = fields[6].toDoubleOrNull() ?: 0.0,
                        longitude = fields[7].toDoubleOrNull() ?: 0.0
                    )

                    Log.d(TAG, "Parsed center: $center")

                    // Use push() to generate a unique key
                    val newRef = centersRef.push()
                    val centerWithId = center.copy(id = newRef.key ?: "")

                    newRef.setValue(centerWithId)
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully added center with ID: ${newRef.key}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to add center: ${e.message}")
                        }
                } else {
                    Log.e(TAG, "Skipping malformed line (not enough fields): $line")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing line: $line", e)
            }
        }
    }
}
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