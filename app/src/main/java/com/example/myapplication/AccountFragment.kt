package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView

class AccountFragment : Fragment() {

    private lateinit var btnEditName: Button
    private lateinit var btnEditEmail: Button
    private lateinit var btnEditPhoneNumber: Button
    private lateinit var btnChangePassword: Button
    private lateinit var userId: String
    private lateinit var userNameEditText: EditText
    private lateinit var userEmailEditText: EditText
    private lateinit var userPhoneNumberEditText: EditText
    private lateinit var userProfileImageView: CircleImageView
    private lateinit var editImageButton: Button
    private lateinit var btnLogout: ImageButton


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account, container, false)

        userId = (activity as MainActivity).getUserId()
        userNameEditText = view.findViewById(R.id.etUserName)
        userEmailEditText = view.findViewById(R.id.etUserEmail)
        userPhoneNumberEditText = view.findViewById(R.id.etUserPhoneNumber)
        btnEditName = view.findViewById(R.id.btnEditName)
        btnEditEmail = view.findViewById(R.id.btnEditEmail)
        btnEditPhoneNumber = view.findViewById(R.id.btnEditPhoneNumber)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
        userProfileImageView = view.findViewById(R.id.ivUserProfile)
        editImageButton = view.findViewById(R.id.btnEditImage)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Set on click listener for logout button
        btnLogout.setOnClickListener {
            logout()
        }

        // Set initial focus to false for all edit texts
        userNameEditText.focusable = View.NOT_FOCUSABLE
        userEmailEditText.focusable = View.NOT_FOCUSABLE
        userPhoneNumberEditText.focusable = View.NOT_FOCUSABLE

        // Set on click listeners for edit buttons
        btnEditName.setOnClickListener {
            userNameEditText.focusable = View.FOCUSABLE
            userNameEditText.isFocusableInTouchMode = true
            userNameEditText.requestFocus()
            btnEditName.visibility = View.GONE
        }

        btnEditEmail.setOnClickListener {
            userEmailEditText.focusable = View.FOCUSABLE
            userEmailEditText.isFocusableInTouchMode = true
            userEmailEditText.requestFocus()
            btnEditEmail.visibility = View.GONE
        }

        btnEditPhoneNumber.setOnClickListener {
            userPhoneNumberEditText.focusable = View.FOCUSABLE
            userPhoneNumberEditText.isFocusableInTouchMode = true
            userPhoneNumberEditText.requestFocus()
            btnEditPhoneNumber.visibility = View.GONE
        }

        // Set on click listener for change password button
        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        editImageButton.setOnClickListener {
            // Open gallery to select a new image
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 1000)
        }



        extractUserInformation()
        return view
    }

    private fun logout() {
        // Show a confirmation dialog before logging out
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performLogout() {
        // Logout from Firebase Auth
        FirebaseAuth.getInstance().signOut()

        // Clear shared preferences (if any)
        val sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // Navigate to the login activity
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun extractUserInformation() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            userNameEditText.setText(user.displayName ?: "")
            userEmailEditText.setText(user.email ?: "")
            userPhoneNumberEditText.setText(user.phoneNumber ?: "")

            // Extract user's profile picture
            if (user.photoUrl != null) {
                // Use Glide or Picasso to load the image
                Glide.with(requireContext())
                    .load(user.photoUrl)
                    .into(userProfileImageView)
            } else {
                // Set a default image if no profile picture is available
                userProfileImageView.setImageResource(R.drawable.inventory_account)
            }

            // If you want to fetch more information, you can use the user's ID to fetch from your database
            // For example, using Firebase Firestore:
            // val db = FirebaseFirestore.getInstance()
            // db.collection("users").document(userId).get().addOnCompleteListener { task ->
            //     if (task.isSuccessful) {
            //         val document = task.result
            //         if (document.exists()) {
            //             val userData = document.data
            //             // Populate the fields with the fetched data
            //             userNameEditText.setText(userData["name"].toString())
            //             userEmailEditText.setText(userData["email"].toString())
            //             userPhoneNumberEditText.setText(userData["phone_number"].toString())
            //         }
            //     }
            // }
        }
    }

    private fun showChangePasswordDialog() {
        val builder =
            MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordEditText =
            dialogView.findViewById<TextInputEditText>(R.id.currentPasswordEditText)
        val newPasswordEditText =
            dialogView.findViewById<TextInputEditText>(R.id.newPasswordEditText)
        val confirmNewPasswordEditText =
            dialogView.findViewById<TextInputEditText>(R.id.confirmNewPasswordEditText)

        builder.setView(dialogView)
            .setTitle("Change Password")
            .setPositiveButton("Change") { dialog, _ ->
                val currentPassword = currentPasswordEditText.text.toString()
                val newPassword = newPasswordEditText.text.toString()
                val confirmNewPassword = confirmNewPasswordEditText.text.toString()

                if (validateChangePasswordInputs(
                        currentPassword,
                        newPassword,
                        confirmNewPassword
                    )
                ) {
                    changePassword(currentPassword, newPassword)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please fill in all fields correctly",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        builder.create().show()
    }

    private fun validateChangePasswordInputs(
        currentPassword: String,
        newPassword: String,
        confirmNewPassword: String
    ): Boolean {
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            return false
        }

        if (newPassword != confirmNewPassword) {
            Toast.makeText(
                requireContext(),
                "New password and confirm new password do not match",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (newPassword.length < 6) {
            Toast.makeText(
                requireContext(),
                "New password must be at least 6 characters long",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
            user.reauthenticate(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        user.updatePassword(newPassword)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Password updated successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Failed to update password",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed to reauthenticate user",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1000 && resultCode == Activity.RESULT_OK && data != null) {
            // Get the selected image URI
            val selectedImageUri = data.data

            // Update the user's profile picture
            userProfileImageView.setImageURI(selectedImageUri)

            // Upload the new image to Firebase Storage
            if (selectedImageUri != null) {
                uploadImageToFirebaseStorage(selectedImageUri)
            }
        }
    }

    private fun uploadImageToFirebaseStorage(imageUri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val storageReference = FirebaseStorage.getInstance().reference
                .child("user_profiles/${user.uid}")

            storageReference.putFile(imageUri)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Get the download URL of the uploaded image
                        storageReference.downloadUrl
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val downloadUrl = task.result
                                    // Update the user's profile picture URL in Firebase Auth
                                    val profileUpdates = UserProfileChangeRequest.Builder()
                                        .setPhotoUri(Uri.parse(downloadUrl.toString()))
                                        .build()
                                    user.updateProfile(profileUpdates)
                                }
                            }
                    }
                }
        }

    }
}