package com.dicoding.testingalpukat

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.dicoding.testingalpukat.databinding.ActivityMainBinding
import com.dicoding.testingalpukat.utils.getImageUri
import com.yalantis.ucrop.UCrop
import java.io.File

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    //image uri
    private var currentImageUri: Uri? = null
    private var croppedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGalery.setOnClickListener {
            startGaleri()
        }

        binding.btnCamera.setOnClickListener{
            startKamera()
        }

        binding.btnScan.setOnClickListener{
            currentImageUri?.let {
                analyzeImage()
            } ?: run {
                showToast(getString(R.string.image_classifier_failed))
            }
        }
    }

    //Proses scan
    private fun analyzeImage() {
        // TODO: Menganalisa gambar yang berhasil ditampilkan.
        val intent = Intent(this, ResultActivity::class.java)
        croppedImageUri?.let { uri ->
            intent.putExtra(ResultActivity.EXTRA_IMAGE_URI, uri.toString())
            startActivityForResult(intent, REQUEST_RESULT)
        } ?: showToast(getString(R.string.image_classifier_failed))
    }

    //Fungsi Kamera
    private fun startKamera() {
        currentImageUri = getImageUri(this)
        launcherIntentCamera.launch(currentImageUri)
    }

    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { isSuccess ->
        if (isSuccess) {
            currentImageUri?.let { uri ->
                showImage()
                startUCrop(uri)
            }?: showToast("Failed to capture image URI")
        }
    }

    // Fungsi Galeri
    private fun startGaleri() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Choose a Picture")
        launcherIntentGallery.launch(chooser)
    }

    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val selectedImg = result.data?.data
            selectedImg?.let { uri ->
                currentImageUri = uri
                showImage()
                startUCrop(uri)
            } ?: showToast("Failed to get image URI")
        }
    }

    private fun startUCrop(sourceUri: Uri) {
        val fileName = "cropped_image_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(cacheDir, fileName))
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1000, 1000)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == AppCompatActivity.RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let {
                showCroppedImage(resultUri)
            } ?: showToast("Failed to crop image")
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            showToast("Crop error: ${cropError?.message}")
        }
    }

    private fun showImage() {
        currentImageUri?.let { uri ->
            Log.d(TAG, "Displaying image: $uri")
            binding.hasilGambar.setImageURI(uri)
        } ?: Log.d(TAG, "No image to display")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showCroppedImage(uri: Uri) {
        binding.hasilGambar.setImageURI(uri)
        croppedImageUri = uri
    }

    companion object {
        const val TAG = "ImagePicker"
        private const val REQUEST_RESULT = 1001
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}