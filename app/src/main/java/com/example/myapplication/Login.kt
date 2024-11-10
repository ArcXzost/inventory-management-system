package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.example.myapplication.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.SecureRandom
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var credentialManager: CredentialManager
    private lateinit var lottieProgressBar: LottieAnimationView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "LoginActivity"
        private const val WEB_CLIENT_ID = "889370238083-k9pt377befi1a6smj336gtqu83vs94r7.apps.googleusercontent.com"
        private const val PREF_NAME = "LoginPrefs"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SESSION_TIMESTAMP = "session_timestamp"
        // Session duration in milliseconds (e.g., 7 days)
        private const val SESSION_DURATION = 7 * 24 * 60 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        auth.setLanguageCode("en") // Set language for Firebase emails

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        lottieProgressBar = findViewById(R.id.lottieProgressBar)
        lottieProgressBar.setAnimation(R.raw.inventory_loader)

        credentialManager = CredentialManager.create(this)

        // Check for existing session
        checkExistingSession()

        // Handle Remember Me checkbox
        binding.rememberMeCheckBox.isChecked = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false)
        if (binding.rememberMeCheckBox.isChecked) {
            binding.emailEditText.setText(sharedPreferences.getString(KEY_EMAIL, ""))
            binding.passwordEditText.setText(sharedPreferences.getString(KEY_PASSWORD, ""))
        }

        // Handle Sign-In Button Click
        binding.signInButton.setOnClickListener {
            showProgressBar()
            signInWithEmailPassword()
        }

        // Existing button click handlers...
        setupClickListeners()
    }

    private fun checkExistingSession() {
        val currentUser = auth.currentUser
        val sessionTimestamp = sharedPreferences.getLong(KEY_SESSION_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()

        if (currentUser != null &&
            sessionTimestamp != 0L &&
            (currentTime - sessionTimestamp) < SESSION_DURATION) {
            // Valid session exists
            navigateToMainActivity()
            return
        } else if (currentUser != null) {
            // Session expired, sign out
            auth.signOut()
            clearSessionData()
        }

        // Check for remembered credentials
        if (binding.rememberMeCheckBox.isChecked) {
            val email = sharedPreferences.getString(KEY_EMAIL, "")
            val password = sharedPreferences.getString(KEY_PASSWORD, "")
            if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
                binding.emailEditText.setText(email)
                binding.passwordEditText.setText(password)
            }
        }
    }

    private fun signInWithEmailPassword() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (!validateInputs(email, password)) {
            hideProgressBar()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    handleSuccessfulLogin(email, password)
                } else {
                    handleFailedLogin(task.exception)
                }
                hideProgressBar()
            }
    }

    private fun handleSuccessfulLogin(email: String, password: String) {
        // Save credentials if Remember Me is checked
        if (binding.rememberMeCheckBox.isChecked) {
            saveCredentials(email, password)
        } else {
            clearCredentials()
        }

        // Update session timestamp
        updateSessionTimestamp()

        // Navigate to main activity
        navigateToMainActivity()
    }

    private fun handleFailedLogin(exception: Exception?) {
        Log.w(TAG, "signInWithEmail:failure", exception)
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> "No account found with this email"
            is FirebaseAuthInvalidCredentialsException -> "Invalid password"
            else -> "Authentication failed"
        }
        Toast.makeText(baseContext, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveCredentials(email: String, password: String) {
        sharedPreferences.edit().apply {
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_REMEMBER_ME, true)
            apply()
        }
    }

    private fun clearCredentials() {
        sharedPreferences.edit().apply {
            remove(KEY_EMAIL)
            remove(KEY_PASSWORD)
            putBoolean(KEY_REMEMBER_ME, false)
            apply()
        }
    }

    private fun updateSessionTimestamp() {
        sharedPreferences.edit().apply {
            putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    private fun clearSessionData() {
        sharedPreferences.edit().apply {
            remove(KEY_SESSION_TIMESTAMP)
            apply()
        }
    }

    private fun setupClickListeners() {
        // Google Sign-In
        binding.googleLoginButton.setOnClickListener {
            showProgressBar()
            signInWithGoogle()
        }

        // Sign Up
        binding.signUpTextView.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Forgot Password
        binding.forgotPasswordTextView.setOnClickListener {
            showForgotPasswordDialog()
        }

        // Other social login buttons...
        setupSocialLoginButtons()
    }

    private fun setupSocialLoginButtons() {
        binding.facebookLoginButton.setOnClickListener {
            Toast.makeText(this, "Facebook sign-in not implemented", Toast.LENGTH_SHORT).show()
        }

        binding.TwitterLoginButton.setOnClickListener {
            Toast.makeText(this, "Twitter sign-in not implemented", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showProgressBar() {
        findViewById<FrameLayout>(R.id.progressBarContainer).visibility = View.VISIBLE
        lottieProgressBar.visibility = View.VISIBLE
        lottieProgressBar.playAnimation()
    }

    private fun hideProgressBar() {
        lottieProgressBar.pauseAnimation()
        lottieProgressBar.visibility = View.GONE
        findViewById<FrameLayout>(R.id.progressBarContainer).visibility = View.GONE
    }

    private fun signInWithGoogle() {
        val googleIdOption:  GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
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
                    context = this@LoginActivity
                )
                handleSignInResult(result)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Error getting credential", e)
                Toast.makeText(this@LoginActivity, "Sign-in failed "+e.message, Toast.LENGTH_SHORT).show()
            }
            finally {
                hideProgressBar()
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is androidx.credentials.CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        // Send googleIdTokenCredential.idToken to your server to verify and authenticate
                        verifyGoogleIdTokenOnServer(googleIdTokenCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Error parsing Google ID token", e)
                        Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type")
                    Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential type")
                Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verifyGoogleIdTokenOnServer(idToken: String) {
        // Implement server-side verification of the Google ID token
        // This typically involves sending the token to your backend server
        // The server should verify the token with Google's servers and create a session for the user
        // For this example, we'll just simulate a successful verification

        Log.d(TAG, "Verifying Google ID token on server: $idToken")

        // Update session timestamp
        updateSessionTimestamp()

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // User signed in, navigate to MainActivity
                    navigateToMainActivity()
                } else {
                    // Handle sign-in error
                }
            }
    }

    private fun navigateToMainActivity() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("USER_ID", user.uid)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun generateNonce(): String {
        val nonce = ByteArray(16)
        SecureRandom().nextBytes(nonce)
        return nonce.joinToString("") { String.format("%02x", it) }
    }

    private fun showForgotPasswordDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.CustomMaterialAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = dialogView.findViewById<TextInputEditText>(R.id.resetEmailEditText)

        builder.setView(dialogView)
            .setTitle("Reset Password")
            .setPositiveButton("Reset") { dialog, _ ->
                val email = emailEditText.text.toString()
                if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    showProgressBar()
                    sendPasswordResetEmail(email)
                } else {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        builder.create().show()
    }

    private fun sendPasswordResetEmail(email: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                hideProgressBar()
                if (task.isSuccessful) {
                    showResetEmailSentDialog()
                } else {
                    showResetEmailErrorDialog(task.exception?.message)
                }
            }
    }

    private fun showResetEmailSentDialog() {
        MaterialAlertDialogBuilder(this, R.style.CustomMaterialAlertDialog)
            .setTitle("Reset Email Sent")
            .setMessage("We've sent you an email with instructions to reset your password. Please check your inbox and spam folder.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showResetEmailErrorDialog(errorMessage: String?) {
        MaterialAlertDialogBuilder(this, R.style.CustomMaterialAlertDialog)
            .setTitle("Error")
            .setMessage(errorMessage ?: "An error occurred while sending the reset email. Please try again later.")
            .setPositiveButton("OK", null)
            .show()
    }
}