package com.example.igp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RegisterFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var addressEditText: AutoCompleteTextView
    private lateinit var useCurrentLocation: Button
    private lateinit var registerButton: Button
    private lateinit var backToLogin: TextView
    private lateinit var addressAdapter: ArrayAdapter<String>
    private val addressSuggestions = mutableListOf<String>()
    private val executor = Executors.newSingleThreadExecutor()

    // Firebase Auth and Database
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "IGP")  // Replace with your app name
                .build()
            chain.proceed(request)
        }
        .build()

    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var pincodeEditText: EditText
    private lateinit var termsCheckBox: CheckBox

    private val TAG = "RegisterFragment"
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize views
        initializeViews(view)
        setupAddressAutocomplete()
        setupClickListeners()

        return view
    }

    private fun initializeViews(view: View) {
        usernameEditText = view.findViewById(R.id.username1)
        emailEditText = view.findViewById(R.id.emaill)
        phoneEditText = view.findViewById(R.id.phoneno)
        passwordEditText = view.findViewById(R.id.setPassword)
        addressEditText = view.findViewById(R.id.address)
        pincodeEditText = view.findViewById(R.id.pincode)
        termsCheckBox = view.findViewById(R.id.checkBox)
        useCurrentLocation = view.findViewById(R.id.useCurrentLocation)
        registerButton = view.findViewById(R.id.button6)
        backToLogin = view.findViewById(R.id.backToLogin)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun setupClickListeners() {
        useCurrentLocation.setOnClickListener {
            getCurrentLocation()
        }

        registerButton.setOnClickListener {
            attemptRegister()
        }

        backToLogin.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(LoginFragment())
        }
    }

    // ... [keeping the address autocomplete functions unchanged] ...

    private fun setupAddressAutocomplete() {
        // Use Android's built-in dropdown layout
        addressAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            addressSuggestions
        )

        addressEditText.setAdapter(addressAdapter)
        addressEditText.threshold = 3  // Show suggestions after 3 characters

        // Ensure dropdown width matches parent
        addressEditText.dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
        addressEditText.dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT

        addressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 3) {
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable {
                        fetchAddressSuggestions(query)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                } else {
                    // Clear suggestions if text is less than 3 characters
                    addressSuggestions.clear()
                    addressAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Force show dropdown when clicking on the field
        addressEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && addressEditText.text.length >= 3 && addressSuggestions.isNotEmpty()) {
                addressEditText.showDropDown()
            }
        }

        // Add click listener to show dropdown
        addressEditText.setOnClickListener {
            if (addressEditText.text.length >= 3 && addressSuggestions.isNotEmpty()) {
                addressEditText.showDropDown()
            }
        }
    }

    private fun fetchAddressSuggestions(query: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder()
                    .url("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&addressdetails=1")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Address search failed: ${response.code}")
                        return@use
                    }

                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        val jsonArray = JSONArray(body)
                        val newSuggestions = mutableListOf<String>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            newSuggestions.add(obj.getString("display_name"))
                        }

                        withContext(Dispatchers.Main) {
                            addressSuggestions.clear()
                            if (newSuggestions.isNotEmpty()) {
                                addressSuggestions.addAll(newSuggestions)
                                addressAdapter.notifyDataSetChanged()

                                // Force show dropdown with new suggestions
                                if (addressEditText.hasFocus()) {
                                    addressEditText.showDropDown()
                                }
                            }

                            // Log for debugging
                            Log.d(TAG, "Suggestions updated: ${addressSuggestions.size} items")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching address suggestions", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error fetching address suggestions",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun attemptRegister() {
        val username = usernameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val address = addressEditText.text.toString().trim()
        val pincode = pincodeEditText.text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || phone.isEmpty() ||
            password.isEmpty() || address.isEmpty() || pincode.isEmpty()
        ) {
            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!termsCheckBox.isChecked) {
            Toast.makeText(context, "Please agree to the terms and conditions", Toast.LENGTH_SHORT).show()
            return
        }

        registerWithFirebase(username, email, phone, password, address, pincode)
    }

    private fun registerWithFirebase(
        username: String,
        email: String,
        phone: String,
        password: String,
        address: String,
        pincode: String
    ) {
        // Show loading indicator
        val loadingToast = Toast.makeText(context, "Creating account...", Toast.LENGTH_LONG)
        loadingToast.show()

        // Create user with email and password
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                loadingToast.cancel()

                if (task.isSuccessful) {
                    // Registration successful
                    Log.d(TAG, "createUserWithEmail:success")

                    // Get the newly created user's UID
                    val userId = auth.currentUser?.uid

                    if (userId != null) {
                        // Create a User object with all collected information
                        val user = User(
                            username = username,
                            email = email,
                            phone = phone,
                            password = "", // Don't store actual password in database
                            street = address,
                            pincode = pincode,
                            score = 0,
                            donations = 0
                        )

                        // Save user data to Firebase Realtime Database
                        saveUserToDatabase(userId, user)
                    } else {
                        Log.w(TAG, "Failed to get user ID after registration")
                        Toast.makeText(context, "Registration error: Could not get user ID", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // If registration fails
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    Toast.makeText(
                        context,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun saveUserToDatabase(userId: String, user: User) {
        val databaseRef = database.reference.child("users").child(userId)

        // Convert User object to Map to avoid storing empty fields
        val userMap = mapOf(
            "username" to user.username,
            "email" to user.email,
            "phone" to user.phone,
            "address" to user.street,
            "pincode" to user.pincode,
            "score" to user.score,
            "donations" to user.donations
        )

        databaseRef.setValue(userMap)
            .addOnSuccessListener {
                Log.d(TAG, "User data saved to database")
                Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.navigateToFragment(LoginFragment())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving user data to database", e)
                Toast.makeText(
                    context,
                    "Registration successful but failed to save user details: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                (activity as? MainActivity)?.navigateToFragment(LoginFragment())
            }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Fetching your location...", Toast.LENGTH_SHORT).show()

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        resolveAddressFromLocation(it)
                    } ?: run {
                        Toast.makeText(
                            context,
                            "Could not get your location. Please enter manually.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error getting location", e)
                    Toast.makeText(
                        context,
                        "Error getting location: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun resolveAddressFromLocation(location: Location) {
        executor.execute {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                requireActivity().runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        addressEditText.setText(address)
                        addressEditText.setSelection(address.length)
                        Toast.makeText(context, "Location found!", Toast.LENGTH_SHORT).show()
                        saveAddressToJson(address)
                    } else {
                        Toast.makeText(
                            context,
                            "No address found for this location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        context,
                        "Failed to get address: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveAddressToJson(address: String) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("address", address)

            val jsonFile = File(requireContext().filesDir, "user_address.json")
            jsonFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}