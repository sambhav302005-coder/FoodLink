package com.example.igp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.igp.ChatActivity

class HomeFragment : Fragment() {

    private var faqQuestion1: TextView? = null
    private var faqAnswer1: TextView? = null
    private var faqQuestion2: TextView? = null
    private var faqAnswer2: TextView? = null
    private var faqQuestion3: TextView? = null
    private var faqAnswer3: TextView? = null
    private var faqQuestion4: TextView? = null
    private var faqAnswer4: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val fab = view.findViewById<FloatingActionButton>(R.id.chatbotFab)
        setupChatbotFab(fab)

        try {
            val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
            bottomNavigationView?.visibility = View.VISIBLE

            setupFAQViews(view)
            setupActionButtons(view)

        } catch (e: Exception) {
            Log.e("HomeFragment", "Error initializing HomeFragment: ${e.message}")
            e.printStackTrace()
        }

        return view
    }

    private fun setupChatbotFab(fab: FloatingActionButton) {
        // Add click listener
        fab.setOnClickListener {
            fab.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    fab.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    launchChatbot()
                }
                .start()
        }

        // Add glow animation
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.glow)
        fab.startAnimation(animation)
    }

    private fun setupFAQViews(view: View) {
        faqQuestion1 = view.findViewById(R.id.faqQuestion1)
        faqAnswer1 = view.findViewById(R.id.faqAnswer1)
        faqQuestion2 = view.findViewById(R.id.faqQuestion2)
        faqAnswer2 = view.findViewById(R.id.faqAnswer2)
        faqQuestion3 = view.findViewById(R.id.faqQuestion3)
        faqAnswer3 = view.findViewById(R.id.faqAnswer3)
        faqQuestion4 = view.findViewById(R.id.faqQuestion4)
        faqAnswer4 = view.findViewById(R.id.faqAnswer4)

        faqQuestion1?.setOnClickListener { faqAnswer1?.let { answer -> toggleAnswer(answer) } }
        faqQuestion2?.setOnClickListener { faqAnswer2?.let { answer -> toggleAnswer(answer) } }
        faqQuestion3?.setOnClickListener { faqAnswer3?.let { answer -> toggleAnswer(answer) } }
        faqQuestion4?.setOnClickListener { faqAnswer4?.let { answer -> toggleAnswer(answer) } }
    }

    private fun setupActionButtons(view: View) {
        val donateButton = view.findViewById<Button>(R.id.button6)
        donateButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(DonationFragment())
        }

        val orderHistoryCard = view.findViewById<CardView>(R.id.orderHistoryCard)
        orderHistoryCard.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(OrderHistoryFragment())
        }

        val trackLiveOrderCard = view.findViewById<CardView>(R.id.trackLiveOrderCard)
        trackLiveOrderCard.setOnClickListener {
            val sharedPreferences = requireActivity().getSharedPreferences("user_data", android.content.Context.MODE_PRIVATE)
            val latestDonationId = sharedPreferences.getString("latest_donation_id", null)

            if (latestDonationId != null) {
                (activity as? MainActivity)?.navigateToFragment(
                    DeliveryTrackerFragment.newInstance(latestDonationId)
                )
            } else {
                Toast.makeText(context, "No active donations found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchChatbot() {
        try {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error launching chatbot: ${e.message}")
            Toast.makeText(requireContext(), "Unable to launch chatbot", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAnswer(answerTextView: TextView) {
        try {
            val currentVisibility = answerTextView.visibility
            val newVisibility = if (currentVisibility == View.GONE) View.VISIBLE else View.GONE
            answerTextView.visibility = newVisibility
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error toggling answer: ${e.message}")
            e.printStackTrace()
        }
    }
}