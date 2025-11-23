//package com.example.igp
//
//import android.os.Bundle
//import android.util.Log
//import android.view.View
//import android.view.animation.AnimationUtils
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.FragmentManager
//import com.example.igp.utils.FirebaseUtil
//import com.google.android.material.bottomnavigation.BottomNavigationView
//import com.google.android.material.floatingactionbutton.FloatingActionButton
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.database.FirebaseDatabase
//
//class MainActivity : AppCompatActivity() {
//    private lateinit var bottomNavigation: BottomNavigationView
//    private lateinit var auth: FirebaseAuth
//    private lateinit var database: FirebaseDatabase
//
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        initializeFirebase()
//        initializeViews()
//        setupBottomNavigation()
//
//        if (savedInstanceState == null) {
//            checkUserAndNavigate()
//        }
//
//    }
//
//    private fun initializeFirebase() {
//        FirebaseUtil.initializeDatabase()
//        auth = FirebaseAuth.getInstance()
//        database = FirebaseDatabase.getInstance()
//    }
//
//    private fun initializeViews() {
//        bottomNavigation = findViewById(R.id.bottomNavigationView)
//    }
//
//    private fun setupBottomNavigation() {
//        bottomNavigation.setOnItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.navigation_home -> {
//                    navigateToFragment(HomeFragment())
//                    true
//                }
//                R.id.navigation_donate -> {
//                    navigateToFragment(DonationFragment())
//                    true
//                }
//                R.id.navigation_profile -> {
//                    navigateToFragment(ProfileFragment())
//                    true
//                }
//                else -> false
//            }
//        }
//    }
//
//    private fun updateBottomNavigationVisibility(fragment: Fragment) {
//        Log.d("MainActivity", "Updating visibility for fragment: ${fragment.javaClass.simpleName}")
//        bottomNavigation.visibility = when (fragment) {
//            is LoginFragment, is RegisterFragment, is ForgotPasswordFragment, is DriverFragment -> {
//                Log.d("MainActivity", "Setting bottom navigation to GONE")
//                View.GONE
//            }
//            else -> {
//                Log.d("MainActivity", "Setting bottom navigation to VISIBLE")
//                View.VISIBLE
//            }
//        }
//    }
//
//    fun navigateToFragment(fragment: Fragment) {
//        Log.d("MainActivity", "Navigating to fragment: ${fragment.javaClass.simpleName}")
//
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.fragmentContainer, fragment)
//            .commitAllowingStateLoss()
//
//        updateBottomNavigationVisibility(fragment)
//    }
//
//    fun checkUserAndNavigate() {
//        val currentUser = auth.currentUser
//        if (currentUser != null) {
//            // User is signed in, check if they are a driver or regular user
//            database.reference.child("drivers").child(currentUser.uid)
//                .get()
//                .addOnSuccessListener { snapshot ->
//                    if (snapshot.exists()) {
//                        // User is a driver, hide bottom navigation and show driver fragment
//                        bottomNavigation.visibility = View.GONE
//                        navigateToFragment(DriverFragment())
//                    } else {
//                        // User is a regular user, show bottom navigation and home fragment
//                        bottomNavigation.visibility = View.VISIBLE
//                        navigateToFragment(HomeFragment())
//                    }
//                }
//                .addOnFailureListener {
//                    // Error occurred, default to regular user view
//                    bottomNavigation.visibility = View.VISIBLE
//                    navigateToFragment(HomeFragment())
//                }
//        } else {
//            // No user is signed in, show login screen
//            navigateToFragment(LoginFragment())
//        }
//    }
//
//    fun login(email: String, password: String) {
//        auth.signInWithEmailAndPassword(email, password)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    checkUserAndNavigate()
//                } else {
//                    Toast.makeText(
//                        this,
//                        "Authentication failed: ${task.exception?.message}",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//    }
//
//    fun register(
//        username: String,
//        email: String,
//        phone: String,
//        password: String,
//        address: String,
//        pincode: String,
//        isDriver: Boolean
//    ) {
//        auth.createUserWithEmailAndPassword(email, password)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
//                    val userRef = if (isDriver) {
//                        database.reference.child("drivers").child(userId)
//                    } else {
//                        database.reference.child("users").child(userId)
//                    }
//
//                    val userData = hashMapOf(
//                        "username" to username,
//                        "email" to email,
//                        "phone" to phone,
//                        "address" to address,
//                        "pincode" to pincode
//                    )
//
//                    if (isDriver) {
//                        userData["isAvailable"] = "true"
//                        userData["rating"] = "0.0"
//                    }
//
//                    userRef.setValue(userData)
//                        .addOnSuccessListener {
//                            Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
//                            checkUserAndNavigate()
//                        }
//                        .addOnFailureListener { e ->
//                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//                        }
//                } else {
//                    Toast.makeText(
//                        this,
//                        "Registration failed: ${task.exception?.message}",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//    }
//
//    fun resetPassword(email: String) {
//        auth.sendPasswordResetEmail(email)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_SHORT).show()
//                } else {
//                    Toast.makeText(
//                        this,
//                        "Failed to send reset email: ${task.exception?.message}",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//    }
//
//    fun signOut() {
//        auth.signOut()
//        navigateToFragment(LoginFragment())
//    }
//
//    fun navigateToHome() {
//        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
//        // Navigate to home fragment
//        val homeFragment = HomeFragment()
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.fragmentContainer, homeFragment)
//            .commit()
//
//        // Select home item in bottom navigation
//        findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.selectedItemId = R.id.navigation_home
//    }
//
//    override fun onBackPressed() {
//        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
//        when (currentFragment) {
//            is LoginFragment -> {
//                finish()
//            }
//            is RegisterFragment, is ForgotPasswordFragment -> {
//                navigateToFragment(LoginFragment())
//            }
//            is DriverFragment -> {
//                // If driver presses back, sign them out
//                signOut()
//            }
//            else -> {
//                if (auth.currentUser != null) {
//                    super.onBackPressed()
//                } else {
//                    navigateToFragment(LoginFragment())
//                }
//            }
//        }
//    }
//}


package com.example.igp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.igp.utils.FirebaseUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeFirebase()
        initializeViews()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            checkUserAndNavigate()
        }
    }

    private fun loadCSVFromAssets(): String {
        return applicationContext.assets.open("donation_cenetrs.csv").bufferedReader().use { it.readText() }
    }


    private fun initializeFirebase() {
        FirebaseUtil.initializeDatabase()
        val csvData = loadCSVFromAssets()
        FirebaseUtil.loadDonationCenters(csvData)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
    }

    private fun initializeViews() {
        bottomNavigation = findViewById(R.id.bottomNavigationView)
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    navigateToFragment(HomeFragment())
                    true
                }
                R.id.navigation_donate -> {
                    navigateToFragment(DonationFragment())
                    true
                }
                R.id.navigation_profile -> {
                    navigateToFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun updateBottomNavigationVisibility(fragment: Fragment) {
        Log.d("MainActivity", "Updating visibility for fragment: ${fragment.javaClass.simpleName}")
        bottomNavigation.visibility = when (fragment) {
            is LoginFragment, is RegisterFragment, is ForgotPasswordFragment,
            is DriverFragment, is DriverRegisterFragment -> {  // Added DriverRegisterFragment here
                Log.d("MainActivity", "Setting bottom navigation to GONE")
                View.GONE
            }
            else -> {
                Log.d("MainActivity", "Setting bottom navigation to VISIBLE")
                View.VISIBLE
            }
        }
    }

    fun navigateToFragment(fragment: Fragment) {
        Log.d("MainActivity", "Navigating to fragment: ${fragment.javaClass.simpleName}")

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()

        updateBottomNavigationVisibility(fragment)
    }

    fun checkUserAndNavigate() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, check if they are a driver or regular user
            database.reference.child("drivers").child(currentUser.uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        // User is a driver, hide bottom navigation and show driver fragment
                        bottomNavigation.visibility = View.GONE
                        navigateToFragment(DriverFragment())
                    } else {
                        // User is a regular user, show bottom navigation and home fragment
                        bottomNavigation.visibility = View.VISIBLE
                        navigateToFragment(HomeFragment())
                    }
                }
                .addOnFailureListener {
                    // Error occurred, default to regular user view
                    bottomNavigation.visibility = View.VISIBLE
                    navigateToFragment(HomeFragment())
                }
        } else {
            // No user is signed in, show login screen
            navigateToFragment(LoginFragment())
        }
    }

    fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    checkUserAndNavigate()
                } else {
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    fun register(
        username: String,
        email: String,
        phone: String,
        password: String,
        address: String,
        pincode: String,
        isDriver: Boolean
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val userRef = if (isDriver) {
                        database.reference.child("drivers").child(userId)
                    } else {
                        database.reference.child("users").child(userId)
                    }

                    val userData = hashMapOf(
                        "username" to username,
                        "email" to email,
                        "phone" to phone,
                        "address" to address,
                        "pincode" to pincode
                    )

                    if (isDriver) {
                        userData["isAvailable"] = "true"
                        userData["rating"] = "0.0"
                    }

                    userRef.setValue(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                            checkUserAndNavigate()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    fun signOut() {
        auth.signOut()
        navigateToFragment(LoginFragment())
    }

    fun navigateToHome() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        // Navigate to home fragment
        val homeFragment = HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, homeFragment)
            .commit()

        // Select home item in bottom navigation
        findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.selectedItemId = R.id.navigation_home
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        when (currentFragment) {
            is LoginFragment -> {
                finish()
            }
            is RegisterFragment, is ForgotPasswordFragment -> {
                navigateToFragment(LoginFragment())
            }
            is DriverFragment -> {
                // If driver presses back, sign them out
                signOut()
            }
            else -> {
                if (auth.currentUser != null) {
                    super.onBackPressed()
                } else {
                    navigateToFragment(LoginFragment())
                }
            }
        }
    }
}