package com.example.cowall.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cowall.R
import com.example.cowall.adapters.RoomJoinHistoryAdapter
import com.example.cowall.data.AppDatabase
import com.example.cowall.data.RoomJoinRecord
import com.example.cowall.databinding.ActivityRoomJoinHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomJoinHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomJoinHistoryBinding
    private val records = mutableListOf<RoomJoinRecord>()
    private lateinit var historyAdapter: RoomJoinHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomJoinHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = RoomJoinHistoryAdapter(records)
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.historyRecyclerView.adapter = historyAdapter
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@RoomJoinHistoryActivity)
                    .roomJoinRecordDao()
                    .getAll()
            }
            records.clear()
            records.addAll(loaded)
            historyAdapter.notifyDataSetChanged()
            updateUi()
        }
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
}
