package com.example.myapplication

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.InventoryItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProductsOverlayFragment : DialogFragment() {
    private lateinit var categoryName: String
    private var products: ArrayList<InventoryItem> = arrayListOf()
    private lateinit var adapter: ProductAdapter
    private var productUpdateListener: ProductUpdateListener? = null
    private lateinit var pickImageResult: ActivityResultLauncher<Intent>
    private lateinit var takePictureResult: ActivityResultLauncher<Uri>
    private lateinit var requestCameraPermission: ActivityResultLauncher<String>
    private lateinit var requestStoragePermission: ActivityResultLauncher<String>
    private var uploadedImageUri: Uri? = null
    private var lastIvUploadedImage: ImageView? = null
    private var temporaryCameraImageUri: Uri? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImageResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { sourceUri ->
                    // Create a permanent copy of the selected image
                    lifecycleScope.launch {
                        try {
                            val destinationUri = createPermanentImageUri()
                            copyImageToDestination(sourceUri, destinationUri)
                            uploadedImageUri = destinationUri
                            lastIvUploadedImage?.visibility = View.VISIBLE
                            lastIvUploadedImage?.setImageURI(uploadedImageUri)
                        } catch (e: Exception) {
                            showErrorDialog("Failed to save image: ${e.localizedMessage}")
                        }
                    }
                }
            }
        }
        requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePicture()
            } else {
                showCameraPermissionDialog()
            }
        }

        requestStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pickImageFromGallery()
            } else {
                showStoragePermissionDialog()
            }
        }

        takePictureResult = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                uploadedImageUri = temporaryCameraImageUri  // Use the stored URI
                lastIvUploadedImage?.visibility = View.VISIBLE
                lastIvUploadedImage?.setImageURI(uploadedImageUri)
            }
        }
    }

    interface ProductUpdateListener {
        fun onProductsUpdated()
        suspend fun checkWarehouseCapacity(additionalItems: Int): Boolean  // Add this new method
    }

    companion object {
        fun newInstance(category: InventoryCategory): ProductsOverlayFragment {
            val fragment = ProductsOverlayFragment()
            val args = Bundle()
            args.putString("categoryName", category.name)
            args.putSerializable("products", ArrayList(category.items))
            fragment.arguments = args
            return fragment
        }
    }

    fun setProductUpdateListener(listener: DashboardFragment) {
        productUpdateListener = listener
    }

    private fun updateProductQuantity(product: InventoryItem, newQuantity: Int) {
        lifecycleScope.launch {
            val quantityDifference = newQuantity - product.quantity

            // Check capacity for quantity increase
            if (quantityDifference > 0) {
                val hasCapacity = productUpdateListener?.checkWarehouseCapacity(quantityDifference) ?: true
                if (!hasCapacity) {
                    return@launch
                }
            }

            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).inventoryDao()
                    .updateProductQuantity(product.productId, newQuantity)
            }

            // Update local product list
            val index = products.indexOfFirst { it.productId == product.productId }
            if (index != -1) {
                products[index] = product.copy(quantity = newQuantity)
                adapter.notifyItemChanged(index)
            }

            // Notify parent fragment to update UI
            productUpdateListener?.onProductsUpdated()
        }
    }


    private fun updateProductReorderLevel(product: InventoryItem, newReorderLevel: Int){
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).inventoryDao().updateProductReorderLevel(product.productId, newReorderLevel)
            }
            val index = products.indexOfFirst { it.productId == product.productId }

            if (index != -1) {
                products[index] = product.copy(reorderLevel = newReorderLevel)
                adapter.notifyItemChanged(index)
            }
            productUpdateListener?.onProductsUpdated()
        }
    }

    private fun updateProductPrice(product: InventoryItem, price: Float){
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).inventoryDao().updateProductPrice(product.productId, price)
            }
            val updatedProduct = product.copy(price = price)
            val index = products.indexOfFirst { it.productId == product.productId }
            if (index != -1) {
                products[index] = updatedProduct
                adapter.notifyItemChanged(index)
            }
            productUpdateListener?.onProductsUpdated()
        }
    }
    private fun deleteProduct(product: InventoryItem) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).inventoryDao().deleteProduct(product.productId)
                products.removeAll { it.productId == product.productId }
            }
            adapter.notifyDataSetChanged()
            productUpdateListener?.onProductsUpdated()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_products_overlay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryName = arguments?.getString("categoryName") ?: ""
        products = (arguments?.getSerializable("products") as? ArrayList<InventoryItem>) ?: arrayListOf()

        val rvProducts = view.findViewById<RecyclerView>(R.id.rvProducts)
        rvProducts.layoutManager = LinearLayoutManager(context)
        adapter = ProductAdapter(products,
            onEditClick = { product -> showEditQuantityDialog(product) },
            onDeleteClick = { product -> showDeleteConfirmationDialog(product) },
            inventoryDao = AppDatabase.getInstance(requireContext()).inventoryDao()
        )
        rvProducts.adapter = adapter

        view.findViewById<TextView>(R.id.tvCategoryName).text = categoryName
        view.findViewById<View>(R.id.btnAdd).setOnClickListener { showAddProductDialog() }

        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }

    private fun showAddProductDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etProductName = dialogView.findViewById<TextInputEditText>(R.id.etProductName)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)
        val etReorderLevel = dialogView.findViewById<TextInputEditText>(R.id.etReorderLevel)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice) // **NEW**
        val ivUploadedImage = dialogView.findViewById<ImageView>(R.id.ivUploadedImage) // **NEW**
        val btnUploadImage = dialogView.findViewById<Button>(R.id.btnUploadImage)


        etProductName.hint = "Enter Product Name"
        etPrice.hint = "Enter Price" // **NEW**
        etQuantity.hint = "Enter Quantity"
        etReorderLevel.hint = "Enter Reorder Level"
        etPrice.inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL // **NEW**

        btnUploadImage.setOnClickListener { // **NEW**
            uploadImage(ivUploadedImage)
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Add New Product")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val productName = etProductName.text.toString()
                val quantity = etQuantity.text.toString().toIntOrNull()?: 0
                val reorderLevel = etReorderLevel.text.toString().toIntOrNull()?: 0
                val price = etPrice.text.toString().toFloatOrNull()?: 0f // **NEW**
                val imageUri = uploadedImageUri // **NEW**

                if (productName.isNotBlank() && imageUri!= null) {
                    addNewProduct(productName, quantity, reorderLevel, price, imageUri)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPermanentImageUri(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "product_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YourAppName")
            }
        }
        return requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: throw IllegalStateException("Failed to create image URI")
    }

    private suspend fun copyImageToDestination(sourceUri: Uri, destinationUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                    requireContext().contentResolver.openOutputStream(destinationUri)?.use { output ->
                        input.copyTo(output)
                    }
                }

                // Update IS_PENDING flag on Android Q and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    requireContext().contentResolver.update(
                        destinationUri,
                        contentValues,
                        null,
                        null
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun checkStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // No need for storage permission on Android 10 and above
                pickImageFromGallery()
            }
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                pickImageFromGallery()
            }
            shouldShowRequestPermissionRationale(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) -> {
                showStoragePermissionRationaleDialog()
            }
            else -> {
                requestStoragePermission.launch(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    private fun showStoragePermissionRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage Permission Required")
            .setMessage("Storage permission is required to select images from gallery. Would you like to grant permission?")
            .setPositiveButton("Yes") { _, _ ->
                requestStoragePermission.launch(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showStoragePermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage Permission Required")
            .setMessage("Storage permission is required to select images. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageResult.launch(intent)
    }

    private fun uploadImage(ivUploadedImage: ImageView) {
        lastIvUploadedImage = ivUploadedImage
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Upload Image")
            .setMessage("Choose an option")
            .setPositiveButton("Take Photo") { _, _ ->
                checkCameraPermission()
                ivUploadedImage.visibility = View.VISIBLE
            }
            .setNegativeButton("Choose from Gallery") { _, _ ->
                checkStoragePermission()
                ivUploadedImage.visibility = View.VISIBLE
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun createImageUri(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "image_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: throw IllegalStateException("Failed to create image URI")
    }

    private fun takePicture() {
        try {
            temporaryCameraImageUri = createImageUri()  // Store the URI temporarily
            takePictureResult.launch(temporaryCameraImageUri!!)
        } catch (e: Exception) {
            // Show error dialog to user
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Error")
                .setMessage("Failed to access camera: ${e.localizedMessage}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted, proceed with taking picture
                takePicture()
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA) -> {
                // Show rationale dialog if needed
                showCameraPermissionRationaleDialog()
            }
            else -> {
                // Request permission
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun showCameraPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Camera Permission Required")
            .setMessage("Camera permission is required to take photos. Would you like to grant permission?")
            .setPositiveButton("Yes") { _, _ ->
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            }
            .setNegativeButton("No") { _, _ ->
                // Handle permission denied
            }
            .show()
    }

    private fun showCameraPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Camera Permission Required")
            .setMessage("Camera permission is required to take photos. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
            startActivity(this)
        }
    }

    // **NEW** - Add image URI and price to the product entity
    private fun addNewProduct(
        productName: String,
        quantity: Int,
        reorderLevel: Int,
        price: Float,
        imageUri: Uri
    ) {
        lifecycleScope.launch {
            // Check warehouse capacity before adding
            val hasCapacity = productUpdateListener?.checkWarehouseCapacity(quantity)?: true
            if (!hasCapacity) {
                return@launch
            }

            withContext(Dispatchers.IO) {
                val newProduct = InventoryItem(
                    productId = UUID.randomUUID().toString(),
                    name = productName,
                    sku = generateSKU(categoryName, productName),
                    category = categoryName,
                    quantity = quantity,
                    reorderLevel = reorderLevel,
                    price = price, // **NEW**
                    imageUrl = uploadedImageUri.toString()
                )
                AppDatabase.getInstance(requireContext()).inventoryDao().addProduct(newProduct)

                // Update local list
                products.add(newProduct)

                // Switch to main thread for UI updates
                withContext(Dispatchers.Main) {
                    adapter.notifyItemInserted(products.size - 1)
                    productUpdateListener?.onProductsUpdated()
                }
            }
        }
    }

    private fun showEditQuantityDialog(product: InventoryItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)
        val etProductName = dialogView.findViewById<TextInputEditText>(R.id.etProductName)
        val etReorderLevel = dialogView.findViewById<TextInputEditText>(R.id.etReorderLevel)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)


        etQuantity.inputType = InputType.TYPE_CLASS_NUMBER
        etQuantity.hint = "Enter new quantity"
        etQuantity.setText(product.quantity.toString())
        etReorderLevel.hint = "Enter new reorder level"
        etReorderLevel.setText(product.reorderLevel.toString())
        etPrice.setText(product.price.toString())
        etProductName.visibility = View.GONE

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Edit Quantity")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val newQuantity = etQuantity.text.toString().toIntOrNull()
                if (newQuantity != null) {
                    updateProductQuantity(product, newQuantity)
                }
                if(etReorderLevel.text.toString().isNotBlank()){
                    updateProductReorderLevel(product, etReorderLevel.text.toString().toInt())
                }
                if(etPrice.text.toString().isNotBlank()){
                    updateProductPrice(product, etPrice.text.toString().toFloat())
                }
                (parentFragment as? DashboardFragment)?.fetchDataAndUpdateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(product: InventoryItem) {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete ${product.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteProduct(product)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateSKU(category: String, productName: String): String {
        val categoryPart = if (category.length >= 3) category.substring(0, 3) else category
        val productPart = if (productName.length >= 3) productName.substring(0, 3) else productName
        val productHash = productName.hashCode().toString().takeLast(4).replace("-", "")
        return "${categoryPart.uppercase()}-${productPart.uppercase()}-$productHash"
    }
}