// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
}

//gson = { group = "com.google.code.gson", name = "gson", version.ref = "gsonVersion" }
//androidx-fragment = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "androidxFragmentVersion" }
//
//
//gsonVersion = "2.8.9"
//androidxFragmentVersion = "1.3.6"  # Correct version for androidx.fragment