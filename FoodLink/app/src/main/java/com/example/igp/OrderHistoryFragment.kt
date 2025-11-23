package com.example.igp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class OrderHistoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: View
    private lateinit var emptyStateText: TextView

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private var donationHistoryListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupRecyclerView()
        fetchDonationHistory()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.orderHistoryRecyclerView)
        emptyStateView = view.findViewById(R.id.emptyStateLayout)
        emptyStateText = view.findViewById(R.id.emptyStateText)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = DonationHistoryAdapter(emptyList()) // Set empty adapter initially
    }

    private fun fetchDonationHistory() {
        val currentUserId = auth.currentUser?.uid ?: run {
            showEmptyState()
            return
        }

        donationHistoryListener = database.child("donation_history")
            .orderByChild("userId")
            .equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val donations = mutableListOf<DonationHistory>()

                        for (childSnapshot in snapshot.children) {
                            childSnapshot.getValue(DonationHistory::class.java)?.let {
                                donations.add(it)
                            }
                        }

                        donations.sortByDescending { it.timestamp }

                        if (donations.isEmpty()) {
                            showEmptyState()
                        } else {
                            showDonations(donations)
                        }
                    } catch (e: Exception) {
                        showError("Error loading donations")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Failed to load donation history")
                }
            })
    }

    private fun showEmptyState() {
        if (!isAdded) return  // Check if fragment is attached
        recyclerView.visibility = View.GONE
        emptyStateView.visibility = View.VISIBLE
        emptyStateText.text = "No donations yet.\nStart making a difference today!"
    }

    private fun showDonations(donations: List<DonationHistory>) {
        if (!isAdded) return  // Check if fragment is attached
        recyclerView.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
        recyclerView.adapter = DonationHistoryAdapter(donations)
    }

    private fun showError(message: String) {
        if (!isAdded) return // Check if fragment is attached
        context?.let { safeContext ->
            Toast.makeText(safeContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        donationHistoryListener?.let {
            database.child("donation_history").removeEventListener(it)
        }
    }
}

data class DonationHistory(
    val id: String = "",
    val userId: String = "",
    val donationId: String = "",
    val foodType: String = "",
    val quantity: Int = 0,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING"
) {
    // Required empty constructor for Firebase
    constructor() : this("", "", "", "", 0, "", 0L, "")
}

class DonationHistoryAdapter(
    private val donations: List<DonationHistory>
) : RecyclerView.Adapter<DonationHistoryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.donationCard)
        val donationId: TextView = itemView.findViewById(R.id.donationId)
        val items: TextView = itemView.findViewById(R.id.donationItems)
        val status: TextView = itemView.findViewById(R.id.donationStatus)
        val date: TextView = itemView.findViewById(R.id.donationDate)
        val recipient: TextView = itemView.findViewById(R.id.donationRecipient)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donation_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val donation = donations[position]
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = Date(donation.timestamp)

            holder.donationId.text = "Donation #${donation.donationId}"
            holder.items.text = "${donation.foodType} - ${donation.quantity} servings"
            holder.status.text = formatStatus(donation.status)
            holder.date.text = dateFormat.format(date)
            holder.recipient.text = "Description: ${donation.description}"

            val cardColor = when (donation.status.toLowerCase(Locale.ROOT)) {
                "completed" -> R.color.orange
                "in_transit", "in transit" -> R.color.orange
                "assigned" -> R.color.blue
                "pending_driver" -> R.color.blue
                else -> R.color.secondary_text
            }
            holder.cardView.setCardBackgroundColor(
                holder.itemView.context.getColor(cardColor)
            )
        } catch (e: Exception) {
            // Handle binding errors gracefully
            holder.donationId.text = "Error loading donation"
        }
    }

    private fun formatStatus(status: String): String {
        return status.replace("_", " ").capitalize(Locale.ROOT)
    }

    override fun getItemCount() = donations.size
}