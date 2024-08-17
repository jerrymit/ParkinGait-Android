package com.example.parkingait

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore


// SignUp page activity
class SignUpActivity : AppCompatActivity() {
    // Firebase Auth and Firestore instance
    private lateinit var auth: FirebaseAuth
    //Firebase db
    private lateinit var db: DatabaseReference
    // UI elements
    private lateinit var etemail: EditText
    private lateinit var etpassword: EditText
    private lateinit var etname: EditText
    private lateinit var etheight: EditText
    private lateinit var btnRegisterButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The layout xml file name is activity_signup
        setContentView(R.layout.activity_signup)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // This is related to the Realtime Firebase rules
        db = FirebaseDatabase.getInstance().getReference("users")

        etemail = findViewById(R.id.etSignupEmail)
        etpassword = findViewById(R.id.etSignupPassword)
        etname = findViewById(R.id.etname)
        etheight = findViewById(R.id.etheight)
        btnRegisterButton = findViewById(R.id.btnRegister)

        // Set the button click listener
        btnRegisterButton.setOnClickListener {
            saveDataToFirebase()  // Save the data when the button is clicked
            // You can now navigate the user to another activity or do other things
            goToLogInPage()
        }
    }

    private fun saveDataToFirebase() {
        val userEmail = etemail.text.toString().trim()
        val password = etpassword.text.toString().trim()
        val userId = userEmail.replace(".", "~")
        val name = etname.text.toString().trim()
        val height = etheight.text.toString().trim().toDoubleOrNull() ?: 0.0

        if (userEmail.isNotEmpty() && password.isNotEmpty()) {
            // Create a new user info object
            val userinfo = Info(userEmail, name, height)
            // Firebase Realtime Database userInfo path
            val userInfoRef = db.child(userId).child("Info")
            // This is just for the debugging purpose
            val infoId = userInfoRef.key  // Generate a unique key for the bill

            // Print to the console
            println("User Info Reference: $userInfoRef")
            println("Generated Info ID: $infoId")
            Log.d("UserInfoRef", userInfoRef.toString())
            Log.d("infoId", infoId ?: "null")

            // Save the user info to the Firebase Realtime Database
            infoId?.let {
                userInfoRef.setValue(userinfo)
                    .addOnSuccessListener {
                        Toast.makeText(this, "New user created successfully", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create new user", Toast.LENGTH_LONG).show()
                    }
            }

            // Create a new user with email and password
            auth.createUserWithEmailAndPassword(userEmail, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign up success
                        Toast.makeText(this, "Create new user successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        // If sign up fails, display a message to the user
                        Toast.makeText(this, "Failed to create new user: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "Email and Password cannot be empty.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToLogInPage() {
        val intent = Intent(this, LogInActivity::class.java)
        startActivity(intent)
        finish()
    }
}