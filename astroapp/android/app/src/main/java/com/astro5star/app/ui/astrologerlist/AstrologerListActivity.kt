package com.astro5star.app.ui.astrologerlist

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.databinding.ActivityAstrologerListBinding

/**
 * Premium Astrologer List Screen with Skeleton Shimmer and high-performance RecyclerView.
 * Follows MVVM architecture and industry best practices.
 */
class AstrologerListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAstrologerListBinding
    private val viewModel: AstrologerViewModel by viewModels()
    private lateinit var adapter: AstrologerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAstrologerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeViewModel()
        
        // Initial load
        viewModel.loadAstrologers(isInitial = true)
        
        binding.btnRetry.setOnClickListener {
            viewModel.loadAstrologers(isInitial = true)
        }
    }

    private fun setupRecyclerView() {
        adapter = AstrologerAdapter(
            onChat = { /* Handle Chat Click */ },
            onCall = { /* Handle Call Click */ },
            onVideoCall = { /* Handle Video Call Click */ }
        )

        binding.rvAstrologers.apply {
            layoutManager = LinearLayoutManager(this@AstrologerListActivity)
            // Optimization: setHasFixedSize(true) since card size is consistent
            setHasFixedSize(true)
            this.adapter = this@AstrologerListActivity.adapter
            
            // Pagination / Infinite Scrolling Logic
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lm = layoutManager as LinearLayoutManager
                    if (dy > 0 && lm.findLastVisibleItemPosition() >= lm.itemCount - 5) {
                        viewModel.loadAstrologers(isInitial = false)
                    }
                }
            })
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    showLoading(true)
                    binding.errorContainer.visibility = View.GONE
                }
                is UiState.Success -> {
                    showLoading(false)
                    binding.rvAstrologers.visibility = View.VISIBLE
                    binding.errorContainer.visibility = View.GONE
                    
                    // ListAdapter handles smooth transitions automatically
                    adapter.submitList(state.data)
                }
                is UiState.Error -> {
                    showLoading(false)
                    binding.rvAstrologers.visibility = View.GONE
                    binding.errorContainer.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.shimmerContainer.visibility = View.VISIBLE
            binding.rvAstrologers.visibility = View.GONE
        } else {
            binding.shimmerContainer.visibility = View.GONE
        }
    }
}
