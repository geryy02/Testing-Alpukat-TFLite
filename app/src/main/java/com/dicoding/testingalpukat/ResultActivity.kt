package com.dicoding.testingalpukat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.lifecycle.lifecycleScope
import com.dicoding.testingalpukat.databinding.ActivityResultBinding
import com.dicoding.testingalpukat.ml.AvocadoClassification
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class ResultActivity : AppCompatActivity() {
    private var _binding: ActivityResultBinding? = null
    private val binding get() = _binding!!

    val imageSize = 224
    private lateinit var resize: Bitmap
    private var currentImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val actionBar: ActionBar? = supportActionBar
        actionBar?.title = "Results"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        resultImage()

        binding.btnSave.setOnClickListener {
            uploadData()
        }
    }

    // Hasil gambar
    private fun resultImage() {
        val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            displayImage(imageUri)
            classifications()
        } else {
            Log.e(TAG, "No image URI provided")
            finish()
        }
    }

    //Proses penguji klasifikasi
    private fun classifications() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Model
                val model = AvocadoClassification.newInstance(this@ResultActivity)

                // Creates inputs for reference.
                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, imageSize, imageSize, 3), DataType.FLOAT32)

                val intValues = IntArray(resize.width * resize.height)
                resize.getPixels(intValues, 0, resize.width, 0, 0, resize.width, resize.height)

                val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
                byteBuffer.order(ByteOrder.nativeOrder())

                var pixel = 0

                for (i in 0 until imageSize) {
                    for (j in 0 until imageSize) {
                        val `val` = intValues[pixel++] // RGB
                        byteBuffer.putFloat(((`val` shr 16) and 0xFF).toFloat() * (1f / 255f))
                        byteBuffer.putFloat(((`val` shr 8) and 0xFF).toFloat() * (1f / 255f))
                        byteBuffer.putFloat((`val` and 0xFF).toFloat() * (1f / 255f))
                    }
                }

                inputFeature0.loadBuffer(byteBuffer)

                // Runs model inference and gets result.
                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                // Get labels and scores
                val confidences = outputFeature0.floatArray

                val labelMap = mapOf(
                    0 to "Belum Matang",
                    1 to "Setengah Matang",
                    2 to "Matang"
                )

                // Find the top prediction
                var maxPos = 0
                var maxConfidence = 0f
                for (i in confidences.indices) {
                    if (confidences[i] > maxConfidence) {
                        maxConfidence = confidences[i]
                        maxPos = i
                    }
                }

                // Get the label using the map
                val label = labelMap[maxPos] ?: "Unknown"

                fun Float.formatToString(): String {
                    return String.format("%.0f%%", this * 100)
                }

                // Determine the color based on the label
                val color = when (label) {
                    "Belum Matang" -> R.color.belum_matang
                    "Setengah Matang" -> R.color.setengah_matang
                    "Matang" -> R.color.matang
                    else -> android.R.color.black // default color
                }

                // Display the results (e.g., in a TextView)
                withContext(Dispatchers.Main) {
                    binding.tvLabel.text = "$label"
                    binding.tvLabel.setTextColor(resources.getColor(color, null))
                    binding.tvScore.text = "${maxConfidence.formatToString()}"
                }

                // Releases model resources if no longer used.
                model.close()

            } catch (e: Exception) {
                Log.e("Error", "Error during image processing", e)
            }
        }
    }

    // Display gambar
    private fun displayImage(uri: Uri) {
        Log.d(TAG, "Displaying image: $uri")
        binding.ivHasilGambar.setImageURI(uri)

        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(this.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream)
            }
            resize = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding image", e)
            finish()
        }
    }

    //Upload data
    private fun uploadData() {
        // Get references to the ImageView and TextView elements
        val imageView = binding.ivHasilGambar
        val label = binding.tvLabel.text.toString()
        val score = binding.tvScore.text.toString()

        // Define Firebase Storage and Firestore references
        val storage = Firebase.storage
        val db = Firebase.firestore

        // Make the view visible and set the recommendation text based on the label
        binding.tvRekomendasi.apply {
            visibility = View.VISIBLE
            text = if (label == "Belum Matang") "" else getString(R.string.rekomendasi)
        }

        // Convert ImageView to byte array
        val bitmap = (imageView.drawable as BitmapDrawable).bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        // Show a Toast message when the upload starts
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()

        // Create a unique filename for the image
        val imageFileName = "images/${System.currentTimeMillis()}_hasilDeteksiImages.jpg"
        val imageRef = storage.reference.child(imageFileName)

        // Upload the image to Firebase Storage
        val uploadTask = imageRef.putBytes(data)
        uploadTask.addOnSuccessListener {
            // Get the download URL of the uploaded image
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                val imageUrl = uri.toString()

                // Show a Toast message when the image upload is successful
                Toast.makeText(this, "Image uploaded successfully!", Toast.LENGTH_SHORT).show()

                // Create a new document in the Firestore collection "hasil_deteksi"
                val detectionResult = hashMapOf(
                    "image_url" to imageUrl,
                    "label" to label,
                    "score" to score
                )

                db.collection("result_detection")
                    .add(detectionResult)
                    .addOnSuccessListener { documentReference ->
                        // Show a Toast message when the document is successfully added
                        Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        // Show a Toast message if adding the document fails
                        Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Error adding document", e)
                    }
            }.addOnFailureListener { exception ->
                // Show a Toast message if getting the download URL fails
                Toast.makeText(this, "Error getting download URL: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Error getting download URL", exception)
            }
        }.addOnFailureListener { exception ->
            // Show a Toast message if the image upload fails
            Toast.makeText(this, "Error uploading image: ${exception.message}", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Error uploading image", exception)
        }
    }

    // Tombol kembali
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val TAG = "imagePicker"
    }
}
