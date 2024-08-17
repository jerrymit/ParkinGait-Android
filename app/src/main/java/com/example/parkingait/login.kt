package com.example.parkingait

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class LogInActivity : AppCompatActivity() {

    // Firebase Auth instance
    private lateinit var auth: FirebaseAuth

    // Login page
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        val etEmail: EditText = findViewById(R.id.etEmail)
        val etPassword: EditText = findViewById(R.id.etPassword)
        val btnLogIn: Button = findViewById(R.id.btnSignin)

        // Log in with email and password
        btnLogIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Firebase Authentication to log in with email and password
            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // Sign in success, update UI with the signed-in user's information
                            Toast.makeText(baseContext, "Login successful.", Toast.LENGTH_SHORT).show()
                            // Navigate to another activity or do something else
                            goToHomePage()
                        } else {
                            // If sign in fails, display a message to the user.
                            Toast.makeText(baseContext, "Login failed: ${task.exception?.message}",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Email and Password cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        // Sign up button
        val btnSignUp: Button = findViewById(R.id.btnCreateUser) // Assume you have a signup button
        btnSignUp.setOnClickListener {
            goToSignUpPage()
        }
    }
    private fun goToHomePage() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun goToSignUpPage() {
        val intent = Intent(this, SignUpActivity::class.java)
        startActivity(intent)
    }

}