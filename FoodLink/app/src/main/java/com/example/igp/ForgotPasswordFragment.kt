package com.example.igp

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ForgotPasswordFragment : Fragment() {
    private lateinit var emailEditText: EditText
    private lateinit var resetPasswordButton: Button
    private lateinit var backToLoginLink: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_forgot_password, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize views
        emailEditText = view.findViewById(R.id.forgotPasswordEmail)
        resetPasswordButton = view.findViewById(R.id.resetPasswordButton)
        backToLoginLink = view.findViewById(R.id.backToLogin)

        setupClickListeners()

        return view
    }

    private fun setupClickListeners() {
        resetPasswordButton.setOnClickListener {
            handlePasswordReset()
        }

        backToLoginLink.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(LoginFragment())
        }
    }

    private fun handlePasswordReset() {
        val email = emailEditText.text.toString().trim()

        // Validate email
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            emailEditText.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Please enter a valid email"
            emailEditText.requestFocus()
            return
        }

        // Show loading state
        resetPasswordButton.isEnabled = false
        resetPasswordButton.text = "Verifying email..."

        // First check if the email exists in the database
        checkEmailExists(email) { emailExists ->
            if (emailExists) {
                sendPasswordResetEmail(email)
            } else {
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "No account found with this email address",
                        Toast.LENGTH_LONG
                    ).show()
                    resetPasswordButton.isEnabled = true
                    resetPasswordButton.text = "Reset Password"
                }
            }
        }
    }

    private fun checkEmailExists(email: String, callback: (Boolean) -> Unit) {
        val usersRef = database.reference.child("users")
        usersRef.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.exists())
                }

                override fun onCancelled(error: DatabaseError) {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            context,
                            "Error checking email: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetPasswordButton.isEnabled = true
                        resetPasswordButton.text = "Reset Password"
                    }
                }
            })
    }

    private fun sendPasswordResetEmail(email: String) {
        activity?.runOnUiThread {
            resetPasswordButton.text = "Sending reset link..."
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                activity?.runOnUiThread {
                    if (task.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Password reset link sent to your email",
                            Toast.LENGTH_LONG
                        ).show()
                        // Navigate back to login
                        (activity as? MainActivity)?.navigateToFragment(LoginFragment())
                    } else {
                        val errorMessage = when (task.exception?.message) {
                            "A network error (such as timeout, interrupted connection or unreachable host) has occurred." ->
                                "Network error. Please check your internet connection"
                            else -> "Failed to send reset email: ${task.exception?.message}"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        resetPasswordButton.isEnabled = true
                        resetPasswordButton.text = "Reset Password"
                    }
                }
            }
    }
}