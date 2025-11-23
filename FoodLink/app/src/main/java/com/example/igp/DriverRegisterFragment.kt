package com.example.igp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

data class DeliveryPartner(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val rating: Float = 0.0f,
    val vehicleType: String = "",
    val vehicleNumber: String = "",
    val licenseNumber: String = "",
    val serviceRadius: Int = 0,
    val isAvailable: Boolean = true,
    val currentLocation: Map<String, Double> = mapOf("latitude" to 0.0, "longitude" to 0.0)
)

class DriverRegisterFragment : Fragment() {

    private lateinit var vehicleTypeSpinner: Spinner
    private lateinit var nameInput: TextInputLayout
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailInput: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordInput: TextInputLayout
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var vehicleNumberInput: TextInputLayout
    private lateinit var vehicleNumberEditText: TextInputEditText
    private lateinit var licenseNumberInput: TextInputLayout
    private lateinit var licenseNumberEditText: TextInputEditText
    private lateinit var phoneNumberInput: TextInputLayout
    private lateinit var phoneNumberEditText: TextInputEditText
    private lateinit var radiusInput: TextInputLayout
    private lateinit var radiusEditText: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var backToLoginButton: Button

    private val TAG = "DeliveryPartnerSignup"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // In DriverRegisterFragment.onCreateView():
        val view = inflater.inflate(R.layout.fragment_driver_register, container, false)
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        initializeViews(view)
        setupSpinner()
        setupListeners()
        checkPermissions()
        return view
    }

    private fun initializeViews(view: View) {
        // Initialize existing views
        vehicleTypeSpinner = view.findViewById(R.id.vehicleTypeSpinner)
        nameInput = view.findViewById(R.id.nameInput)
        nameEditText = view.findViewById(R.id.nameEditText)
        emailInput = view.findViewById(R.id.emailInput)
        emailEditText = view.findViewById(R.id.emailEditText)
        passwordInput = view.findViewById(R.id.passwordInput)
        passwordEditText = view.findViewById(R.id.passwordEditText)
        vehicleNumberInput = view.findViewById(R.id.vehicleNumberInput)
        vehicleNumberEditText = view.findViewById(R.id.vehicleNumberEditText)
        licenseNumberInput = view.findViewById(R.id.licenseNumberInput)
        licenseNumberEditText = view.findViewById(R.id.licenseNumberEditText)
        phoneNumberInput = view.findViewById(R.id.phoneNumberInput)
        phoneNumberEditText = view.findViewById(R.id.phoneNumberEditText)
        radiusInput = view.findViewById(R.id.radiusInput)
        radiusEditText = view.findViewById(R.id.radiusEditText)
        submitButton = view.findViewById(R.id.submitButton)
        progressBar = view.findViewById(R.id.progressBar)

        backToLoginButton = view.findViewById(R.id.backToLoginButton)

        progressBar.visibility = View.GONE
    }

    private fun setupSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.vehicle_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            vehicleTypeSpinner.adapter = adapter
        }
    }

    private fun setupListeners() {
        // Add validation listeners for all fields
        nameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateName()
        }

        emailEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateEmail()
        }

        passwordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePassword()
        }

        vehicleNumberEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateVehicleNumber()
        }

        licenseNumberEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateLicenseNumber()
        }

        phoneNumberEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePhoneNumber()
        }

        radiusEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateRadius()
        }

        submitButton.setOnClickListener {
            if (validateAllFields()) {
                registerDriver()
            }
        }
        backToLoginButton.setOnClickListener {
            // Navigate back to login fragment
            (activity as? MainActivity)?.navigateToFragment(LoginFragment())
        }
    }

    private fun validateName(): Boolean {
        val name = nameEditText.text.toString().trim()
        return when {
            name.isEmpty() -> {
                nameInput.error = "Name is required"
                false
            }
            name.length < 3 -> {
                nameInput.error = "Name must be at least 3 characters long"
                false
            }
            else -> {
                nameInput.error = null
                true
            }
        }
    }

    private fun validateEmail(): Boolean {
        val email = emailEditText.text.toString().trim()
        return when {
            email.isEmpty() -> {
                emailInput.error = "Email is required"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailInput.error = "Invalid email format"
                false
            }
            else -> {
                emailInput.error = null
                true
            }
        }
    }

    private fun validatePassword(): Boolean {
        val password = passwordEditText.text.toString()
        return when {
            password.isEmpty() -> {
                passwordInput.error = "Password is required"
                false
            }
            password.length < 6 -> {
                passwordInput.error = "Password must be at least 6 characters"
                false
            }
            else -> {
                passwordInput.error = null
                true
            }
        }
    }

    private fun validateVehicleNumber(): Boolean {
        val vehicleNumber = vehicleNumberEditText.text.toString()
        return when {
            vehicleNumber.isEmpty() -> {
                vehicleNumberInput.error = "Vehicle number is required"
                false
            }
            else -> {
                vehicleNumberInput.error = null
                true
            }
        }
    }

    private fun validateLicenseNumber(): Boolean {
        val licenseNumber = licenseNumberEditText.text.toString()
        return when {
            licenseNumber.isEmpty() -> {
                licenseNumberInput.error = "License number is required"
                false
            }
            else -> {
                licenseNumberInput.error = null
                true
            }
        }
    }

    private fun validatePhoneNumber(): Boolean {
        val phoneNumber = phoneNumberEditText.text.toString()
        return when {
            phoneNumber.isEmpty() -> {
                phoneNumberInput.error = "Phone number is required"
                false
            }
            !phoneNumber.matches(Regex("[0-9]{10}")) -> {
                phoneNumberInput.error = "Invalid phone number"
                false
            }
            else -> {
                phoneNumberInput.error = null
                true
            }
        }
    }

    private fun validateRadius(): Boolean {
        val radiusStr = radiusEditText.text.toString()
        return when {
            radiusStr.isEmpty() -> {
                radiusInput.error = "Service radius is required"
                false
            }
            radiusStr.toIntOrNull() == null || radiusStr.toInt() <= 0 -> {
                radiusInput.error = "Invalid radius value"
                false
            }
            radiusStr.toInt() > MAX_SERVICE_RADIUS -> {
                radiusInput.error = "Maximum radius is $MAX_SERVICE_RADIUS km"
                false
            }
            else -> {
                radiusInput.error = null
                true
            }
        }
    }

    private fun validateAllFields(): Boolean {
        val vehicleTypeSelected = vehicleTypeSpinner.selectedItemPosition != 0

        if (!vehicleTypeSelected) {
            (vehicleTypeSpinner.selectedView as? TextView)?.error = "Please select a vehicle type"
        }

        return validateName() &&
                validateEmail() &&
                validatePassword() &&
                validateVehicleNumber() &&
                validateLicenseNumber() &&
                validatePhoneNumber() &&
                validateRadius() &&
                vehicleTypeSelected
    }

    private fun registerDriver() {
        progressBar.visibility = View.VISIBLE
        submitButton.isEnabled = false

        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()

        // First create authentication account
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    val userId = auth.currentUser?.uid

                    if (userId != null) {
                        saveDriverToDatabase(userId)
                    } else {
                        Log.w(TAG, "Failed to get user ID after registration")
                        showError("Registration error: Could not get user ID")
                    }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    showError("Registration failed: ${task.exception?.message}")
                }
            }
    }

    private fun saveDriverToDatabase(userId: String) {
        // Create delivery partner object
        val deliveryPartner = DeliveryPartner(
            id = userId,
            name = nameEditText.text.toString().trim(),
            email = emailEditText.text.toString().trim(),
            phone = phoneNumberEditText.text.toString(),
            rating = 0.0f,
            vehicleType = vehicleTypeSpinner.selectedItem.toString(),
            vehicleNumber = vehicleNumberEditText.text.toString(),
            licenseNumber = licenseNumberEditText.text.toString(),
            serviceRadius = radiusEditText.text.toString().toInt(),
            isAvailable = true
        )

        // Save to Firebase Realtime Database
        database.reference.child("drivers").child(userId)
            .setValue(deliveryPartner)
            .addOnSuccessListener {
                Log.d(TAG, "Driver data saved successfully")

                // Save to SharedPreferences
                requireActivity().getSharedPreferences("user_data", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_delivery_partner", true)
                    .putString("partner_id", deliveryPartner.id)
                    .putString("partner_email", deliveryPartner.email)
                    .putString("partner_name", deliveryPartner.name)
                    .apply()

                Toast.makeText(
                    context,
                    "Successfully registered as a delivery partner!",
                    Toast.LENGTH_LONG
                ).show()

                // Navigate to login
                (activity as? MainActivity)?.navigateToFragment(LoginFragment())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving driver data", e)
                showError("Failed to save driver details: ${e.message}")
            }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        submitButton.isEnabled = true
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (!hasRequiredPermissions(permissions)) {
            requestPermissions(permissions, LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasRequiredPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                ) {
                    // Permissions granted
                } else {
                    Toast.makeText(
                        context,
                        "Location permission is required for delivery services",
                        Toast.LENGTH_LONG
                    ).show()
                    activity?.supportFragmentManager?.popBackStack()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
        private const val MAX_SERVICE_RADIUS = 50 // in kilometers
    }
}