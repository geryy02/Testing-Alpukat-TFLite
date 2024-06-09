package com.dicoding.testingalpukat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.LinearLayoutManager
import com.dicoding.testingalpukat.adapter.ResultDetectionAdapter
import com.dicoding.testingalpukat.databinding.ActivityHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.dicoding.testingalpukat.data.Result
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class HistoryActivity : AppCompatActivity() {
    private var _binding: ActivityHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var resultDetectionAdapter: ResultDetectionAdapter
    private val listResult: MutableList<Result> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val actionBar: ActionBar? = supportActionBar
        actionBar?.title = "History"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize Firestore
        db = Firebase.firestore

        // Initialize RecyclerView
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        resultDetectionAdapter = ResultDetectionAdapter(listResult)
        binding.rvHistory.adapter = resultDetectionAdapter

        //read data
        reviewDataResult()
    }

    private fun reviewDataResult() {
        db.collection("result_detection")
            .get()
            .addOnSuccessListener { result ->
                listResult.clear() // Clear the list to avoid duplicates
                for (document in result) {
                    val result = document.toObject(Result::class.java)
                    result.id = document.id // Set the document ID to the story object
                    listResult.add(result)
                }
                resultDetectionAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("MainActivity", "Error getting documents.", exception)
            }
    }

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
}