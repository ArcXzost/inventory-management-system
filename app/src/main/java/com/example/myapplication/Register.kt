package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.airbnb.lottie.LottieAnimationView
import com.example.myapplication.databinding.ActivityRegisterBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var credentialManager: CredentialManager
    private lateinit var lottieProgressBar: LottieAnimationView


    companion object {
        private const val TAG = "RegisterActivity"
        private const val WEB_CLIENT_ID = "889370238083-k9pt377befi1a6smj336gtqu83vs94r7.apps.googleusercontent.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)
        lottieProgressBar = findViewById(R.id.lottieProgressBar)
        credentialManager = CredentialManager.create(this)


        // Handle Sign-Up Button Click
        binding.signUpButton.setOnClickListener {
            showProgressBar()
            signUpWithEmailPassword()
            hideProgressBar()


        }

        // Handle Google Sign-Up Button Click
        binding.googleSignUpButton.setOnClickListener{
            showProgressBar()
            signUpWithGoogle()
            hideProgressBar()

        }


        // Handle Facebook Sign-Up Button Click
        binding.facebookSignUpButton.setOnClickListener {
            // Implement Facebook sign-up logic here
            Toast.makeText(this, "Facebook sign-up not implemented", Toast.LENGTH_SHORT).show()
        }

        // Handle Twitter Sign-Up Button Click
        binding.twitterSignUpButton.setOnClickListener {
            // Implement Twitter sign-up logic here
            Toast.makeText(this, "Twitter sign-up not implemented", Toast.LENGTH_SHORT).show()
        }

        // Handle Already Have Account Click
        binding.alreadyHaveAccountTextView.setOnClickListener {
            finish() // Go back to LoginActivity
        }
    }

    private fun showProgressBar() {
        lottieProgressBar.visibility = View.VISIBLE
        lottieProgressBar.playAnimation()
    }

    private fun hideProgressBar() {
        lottieProgressBar.pauseAnimation()
        lottieProgressBar.visibility = View.GONE
    }

    private fun signUpWithEmailPassword() {
        val name = binding.nameEditText.text.toString()
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
            return
        }

        val auth = FirebaseAuth.getInstance()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Update user profile with name
                    val user = auth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                navigateToMainActivity()
                            } else {
                                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Sign-up failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun signUpWithGoogle() {
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .setNonce(generateNonce()) // Implement this method to generate a secure nonce
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@RegisterActivity
                )
                handleSignUpResult(result)
            } catch (e: GetCredentialException) {
                Log.e(RegisterActivity.TAG, "Error getting credential", e)
                Toast.makeText(this@RegisterActivity, "Sign-up failed "+e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignUpResult(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is androidx.credentials.CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)
                        // Send googleIdTokenCredential.idToken to your server to verify and authenticate
                        verifyGoogleIdTokenOnServerAndCreateAccount(googleIdTokenCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Error parsing Google ID token", e)
                        Toast.makeText(this, "Sign-up failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type")
                    Toast.makeText(this, "Sign-up failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun verifyGoogleIdTokenOnServerAndCreateAccount(idToken: String) {
        // Implement server-side verification of the Google ID token
        // This typically involves sending the token to your backend server
        // The server should verify the token with Google's servers and create a session for the user
        // For this example, we'll just simulate a successful verification
        Log.d(TAG, "Verifying Google ID token on server: $idToken")
        // Simulating server verification
        navigateToMainActivity()
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun generateNonce(): String {
        val nonce = ByteArray(16)
        SecureRandom().nextBytes(nonce)
        return nonce.joinToString("") { String.format("%02x", it) }
    }
}
