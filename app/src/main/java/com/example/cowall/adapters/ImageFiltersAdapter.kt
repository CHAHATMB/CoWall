package com.example.cowall.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.cowall.R
import com.example.cowall.data.ImageFilter
import com.example.cowall.databinding.ItemContainerFilterBinding
import com.example.cowall.listeners.ImageFilterListener

class ImageFiltersAdapter(private val imageFilters: List<ImageFilter>, private val imageFilterListener: ImageFilterListener) :
 RecyclerView.Adapter<ImageFiltersAdapter.ImageFilterViewHolder>() {

    private var selectedFilterPosition = 0
    private var previouslySelectedPosition = 0

    override fun onCreateViewHolder (parent: ViewGroup, viewType: Int): ImageFilterViewHolder {
        val binding = ItemContainerFilterBinding.inflate(
            LayoutInflater. from(parent.context), parent, false
        )
        return ImageFilterViewHolder (binding)
         }
    override fun onBindViewHolder(holder: ImageFilterViewHolder, position: Int) {
        with(holder) {
            with(imageFilters[position]) {
                binding.imageFilterPreview.setImageBitmap (filterPreview)
                binding.textFilterName.text = name
                binding.root.setOnClickListener{
                    if( position != selectedFilterPosition ){

                        imageFilterListener.onFilterSelected(this)
                        previouslySelectedPosition = selectedFilterPosition
                        selectedFilterPosition = position
                        with(this@ImageFiltersAdapter){
                            notifyItemChanged(previouslySelectedPosition, Unit)
                            notifyItemChanged(selectedFilterPosition, Unit)
                        }
                    }
                }
            }
            binding.textFilterName.setTextColor(
                ContextCompat.getColor(
                    binding.textFilterName.context,
                    if (selectedFilterPosition == position)
                        R.color.black
                    else
                        R.color.white
                )
            )
        }
    }

    override fun getItemCount() = imageFilters.size

    inner class ImageFilterViewHolder (val binding: ItemContainerFilterBinding) :
        RecyclerView.ViewHolder(binding.root)

 }