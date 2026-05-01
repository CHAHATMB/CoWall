package com.example.cowall.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cowall.R

data class OnboardingPage(
    val icon: String,
    val title: String,
    val description: String
)

class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.pageIcon)
        val title: TextView = itemView.findViewById(R.id.pageTitle)
        val description: TextView = itemView.findViewById(R.id.pageDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        holder.icon.text = page.icon
        holder.title.text = page.title
        holder.description.text = page.description
    }

    override fun getItemCount(): Int = pages.size
}
