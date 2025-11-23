package com.example.igp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.File

class LoginFragment : Fragment() {
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordLink: TextView
    private lateinit var registerLink: TextView
    private lateinit var becomeDriverLink: TextView

    private lateinit var auth: FirebaseAuth

    private val TAG = "LoginFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()

        usernameEditText = view.findViewById(R.id.username21)
        passwordEditText = view.findViewById(R.id.setPassword1)
        loginButton = view.findViewById(R.id.button)
        forgotPasswordLink = view.findViewById(R.id.forgotPasswordLink)
        registerLink = view.findViewById(R.id.registerLink)
        becomeDriverLink = view.findViewById(R.id.becomeDriverLink)

        loginButton.setOnClickListener {
            val email = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(activity, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            } else {
                loginWithFirebase(email, password)
            }
        }

        registerLink.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(RegisterFragment())
        }

        forgotPasswordLink.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(ForgotPasswordFragment())
        }

        becomeDriverLink.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(DriverRegisterFragment())
        }

        return view
    }

    private fun loginWithFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    saveLoginDataToJson(email)
                    Toast.makeText(activity, "Login successful!", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.checkUserAndNavigate()
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(activity, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveLoginDataToJson(email: String) {
        try {
            val jsonObject = JSONObject().apply {
                put("email", email)
                put("uid", auth.currentUser?.uid)
            }
            val jsonFile = File(requireContext().filesDir, "login_data.json")
            jsonFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving login data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}