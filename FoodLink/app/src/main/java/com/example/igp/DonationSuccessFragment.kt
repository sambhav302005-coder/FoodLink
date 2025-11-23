// DonationSuccessFragment.kt
package com.example.igp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.*

class DonationSuccessFragment : Fragment() {
    private var deliveryId: String = ""
    private var driverId: String = ""
    private var driverName: String = ""

    companion object {
        fun newInstance(deliveryId: String, driverId: String, driverName: String): DonationSuccessFragment {
            return DonationSuccessFragment().apply {
                arguments = Bundle().apply {
                    putString("deliveryId", deliveryId)
                    putString("driverId", driverId)
                    putString("driverName", driverName)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_donation_success, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get delivery details from arguments
        deliveryId = arguments?.getString("deliveryId") ?: ""
        driverId = arguments?.getString("driverId") ?: ""
        driverName = arguments?.getString("driverName") ?: ""

        // Hide bottom navigation
        (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.visibility = View.GONE

        // Update success message (using existing TextView with id textView3)
        view.findViewById<TextView>(R.id.textView3).text = "Your donation was successful!"

        // Update appreciation message (using existing TextView with id textView9)
        view.findViewById<TextView>(R.id.textView9).text = "You are an amazing human 🤗"

        // Set up OK button
        view.findViewById<Button>(R.id.buttonOk).setOnClickListener {
            completeDelivery()
        }
    }

    private fun completeDelivery() {
        val database = FirebaseDatabase.getInstance()
        val updates = HashMap<String, Any>()

        // Update delivery status
        updates["/deliveries/$deliveryId/status"] = "COMPLETED"
        updates["/deliveries/$deliveryId/completedTimestamp"] = ServerValue.TIMESTAMP

        // Update driver status
        updates["/drivers/$driverId/isAvailable"] = true
        updates["/drivers/$driverId/currentDeliveryId"] = ""

        // Update donation history status
        database.reference.child("donation_history")
            .orderByChild("deliveryId")
            .equalTo(deliveryId)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { historySnapshot ->
                    updates["/donation_history/${historySnapshot.key}/status"] = "COMPLETED"
                    updates["/donation_history/${historySnapshot.key}/completedTimestamp"] = ServerValue.TIMESTAMP
                }

                // Perform all updates atomically
                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        // Navigate back to home
                        (activity as? MainActivity)?.let { mainActivity ->
                            mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
                            mainActivity.navigateToHome()
                        }
                    }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure bottom navigation is visible when fragment is destroyed
        (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
    }
}