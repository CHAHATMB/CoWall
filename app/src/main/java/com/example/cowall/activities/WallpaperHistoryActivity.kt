package com.example.cowall.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cowall.FireBaseConnector
import com.example.cowall.R
import com.example.cowall.adapters.WallpaperHistoryAdapter
import com.example.cowall.data.WallpaperRecord
import com.example.cowall.databinding.ActivityWallpaperHistoryBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson

class WallpaperHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWallpaperHistoryBinding
    private val records = mutableListOf<WallpaperRecord>()
    private lateinit var historyAdapter: WallpaperHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWallpaperHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = WallpaperHistoryAdapter(this, records) { record ->
            openWallpaperPreview(record)
        }
        binding.historyRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.historyRecyclerView.adapter = historyAdapter
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun loadHistory() {
        val roomId = FireBaseConnector.roomId
        val userId = FireBaseConnector.userUniqueId

        FirebaseDatabase.getInstance().getReference("wallpaperHistory/$roomId/$userId")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val json = child.getValue(String::class.java) ?: continue
                        val record = Gson().fromJson(json, WallpaperRecord::class.java) ?: continue
                        records.add(0, record) // newest first
                    }
                    historyAdapter.notifyDataSetChanged()
                    updateUi()
                }

                override fun onCancelled(error: DatabaseError) {
                    updateUi()
                }
            })
    }

    private fun updateUi() {
        if (records.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.historyRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.historyRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun openWallpaperPreview(record: WallpaperRecord) {
        val uri = Uri.parse(record.imageUri)
        startActivity(Intent(this, WallpaperPreviewActivity::class.java).apply {
            putExtra(WallpaperPreviewActivity.EXTRA_IMAGE_URI, uri)
        })
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
