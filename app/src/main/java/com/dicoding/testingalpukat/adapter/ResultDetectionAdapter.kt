package com.dicoding.testingalpukat.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dicoding.testingalpukat.data.Result
import com.dicoding.testingalpukat.databinding.ItemHistoryBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class ResultDetectionAdapter(private val listResult: MutableList<Result>) : RecyclerView.Adapter<ResultDetectionAdapter.ResultViewHolder>() {
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ResultViewHolder(binding)
    }

    class ResultViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = listResult[position]
        holder.binding.tvHasilKematangan.text = result.kematangan
        holder.binding.tvSkor.text = result.score

        // Load image using Glide
        Glide.with(holder.binding.ivHasilGambarDeteksi.context)
            .load(result.image_url)
            .into(holder.binding.ivHasilGambarDeteksi)

        holder.binding.btnDelete.setOnClickListener {
            deleteResult(holder.adapterPosition)
        }
    }

    private fun deleteResult(position: Int) {
        val result = listResult[position]
        val db = Firebase.firestore

        // Delete the document from Firestore using the document ID
        db.collection("result_detection").document(result.id)
            .delete()
            .addOnSuccessListener {
                listResult.removeAt(position) // Remove the item from the list
                notifyItemRemoved(position) // Notify the adapter of item removal
                Log.d("ResultDetectionAdapter", "DocumentSnapshot successfully deleted!")
            }
            .addOnFailureListener { e ->
                Log.w("ResultDetectionAdapter", "Error deleting document", e)
            }
    }

    override fun getItemCount(): Int = listResult.size
}