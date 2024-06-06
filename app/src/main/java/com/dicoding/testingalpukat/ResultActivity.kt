package com.dicoding.testingalpukat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.lifecycle.lifecycleScope
import com.dicoding.testingalpukat.databinding.ActivityResultBinding
import com.dicoding.testingalpukat.ml.AvocadoClassification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResultActivity : AppCompatActivity() {
    private var _binding: ActivityResultBinding? = null
    private val binding get() = _binding!!

    val imageSize = 224
    private lateinit var resize: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val actionBar: ActionBar? = supportActionBar
        actionBar?.title = "Results"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        resultImage()
    }

    // Hasil gambar, kematangan, dan skor
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
                    return String.format("%.2f%%", this * 100)
                }

                // Display the results (e.g., in a TextView)
                withContext(Dispatchers.Main) {
                    binding.tvLabel.text = "$label"
                    binding.tvScore.text = "${maxConfidence.formatToString()} %"
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
        binding.hasilGambar.setImageURI(uri)

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

    // Tombol button back
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
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
