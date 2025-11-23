package com.example.igp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.igp.databinding.FragmentProfileBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null")

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private var profileImageUri: Uri? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var addressEditText: AutoCompleteTextView
    private lateinit var addressAdapter: ArrayAdapter<String>
    private val addressSuggestions = mutableListOf<String>()
    private val executor = Executors.newSingleThreadExecutor()

    private val TAG = "ProfileFragment"
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (!isAdded) return@registerForActivityResult

        uri?.let {
            profileImageUri = it
            binding.imageView4.setImageURI(it)
            saveProfileImage(it)
        }
    }

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load user data from Firebase
        loadUserProfile()

        // Set click listeners
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.apply {
            add.setOnClickListener {
                // Handle the "Your Info" button click
                // Add your implementation here
            }

            info.setOnClickListener {
                showEditAddressDialog()
            }

            imageView4.setOnClickListener {
                imagePickerLauncher.launch("image/*")
            }

            logoutButton.setOnClickListener {
                logoutUser()
            }
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            activity?.let { activity ->
                (activity as? MainActivity)?.navigateToFragment(LoginFragment())
            }
            return
        }

        val userRef = database.reference.child("users").child(currentUser.uid)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                if (snapshot.exists()) {
                    try {
                        val userData = getUserDataFromSnapshot(snapshot)
                        updateProfileInfo(userData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user data", e)
                        context?.let {
                            Toast.makeText(it, "Error loading profile data", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.d(TAG, "No user data found")
                    context?.let {
                        Toast.makeText(it, "No profile data found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return

                Log.e(TAG, "Database error: ${error.message}")
                context?.let {
                    Toast.makeText(it, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private data class UserData(
        val username: String,
        val email: String,
        val phone: String,
        val address: String,
        val pincode: String,
        val score: Int,
        val donations: Int
    )

    private fun getUserDataFromSnapshot(snapshot: DataSnapshot): UserData {
        return UserData(
            username = snapshot.child("username").getValue(String::class.java) ?: "",
            email = snapshot.child("email").getValue(String::class.java) ?: "",
            phone = snapshot.child("phone").getValue(String::class.java) ?: "",
            address = snapshot.child("address").getValue(String::class.java) ?: "",
            pincode = snapshot.child("pincode").getValue(String::class.java) ?: "",
            score = snapshot.child("score").getValue(Int::class.java) ?: 0,
            donations = snapshot.child("donations").getValue(Int::class.java) ?: 0
        )
    }

    private fun updateProfileInfo(userData: UserData) {
        binding.apply {
            textView.text = "Score: ${userData.score}"
            textView2.text = "Donations: ${userData.donations}"
            info24.text = "Name: ${userData.username}"
            info20.text = "Mobile No: ${userData.phone}"
            info14.text = "Username: ${userData.username}"
            info21.text = "Password: ********"  // Hidden for security
            info41.text = "Email: ${userData.email}"
            info00.text = "Street: ${userData.address}"
            info77.text = "City: Not specified"
            info1.text = "State: Not specified"
            info2.text = "Pin Code: ${userData.pincode}"
        }

        context?.let { ctx ->
            val profileImageFile = File(ctx.filesDir, "profile_image.jpg")
            if (profileImageFile.exists()) {
                binding.imageView4.setImageURI(Uri.fromFile(profileImageFile))
            } else {
                binding.imageView4.setImageResource(R.drawable.pfp)
            }
        }
    }

    private fun saveProfileImage(uri: Uri) {
        if (!isAdded) return

        try {
            context?.let { ctx ->
                val inputStream = ctx.contentResolver.openInputStream(uri)
                val file = File(ctx.filesDir, "profile_image.jpg")
                val outputStream = FileOutputStream(file)

                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Profile image saved successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile image", e)
            context?.let {
                Toast.makeText(it, "Error saving profile image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutUser() {
        if (!isAdded) return

        try {
            // Sign out from Firebase
            auth.signOut()

            // Clear local profile image
            context?.let { ctx ->
                val profileImageFile = File(ctx.filesDir, "profile_image.jpg")
                if (profileImageFile.exists()) {
                    profileImageFile.delete()
                }

                // Navigate to login screen
                (activity as? MainActivity)?.navigateToFragment(LoginFragment())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out user", e)
            context?.let {
                Toast.makeText(it, "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditAddressDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_address, null)
        addressEditText = dialogView.findViewById(R.id.addressEditText)
        val useCurrentLocationButton: Button = dialogView.findViewById(R.id.useCurrentLocationButton)

        setupAddressAutocomplete()

        useCurrentLocationButton.setOnClickListener {
            getCurrentLocation()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Address")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                saveNewAddress(addressEditText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupAddressAutocomplete() {
        addressAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            addressSuggestions
        )
        addressEditText.setAdapter(addressAdapter)
        addressEditText.threshold = 3

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
                    addressSuggestions.clear()
                    addressAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        addressEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && addressEditText.text.length >= 3 && addressSuggestions.isNotEmpty()) {
                addressEditText.showDropDown()
            }
        }

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

                                if (addressEditText.hasFocus()) {
                                    addressEditText.showDropDown()
                                }
                            }

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

    private fun saveNewAddress(newAddress: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userRef = database.reference.child("users").child(currentUser.uid)
            userRef.child("address").setValue(newAddress)
                .addOnSuccessListener {
                    Toast.makeText(context, "Address updated successfully", Toast.LENGTH_SHORT).show()
                    loadUserProfile() // Reload user profile to reflect changes
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to update address: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}

