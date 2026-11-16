package com.usj.news_application.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.usj.news_application.R
import com.usj.news_application.models.News
import java.lang.Exception

class NewsAdapter(
    private val isBookmarked: (News) -> Boolean,
    private val onBookmarkToggle: (News) -> Unit,
    private val onItemClick: (News) -> Unit
) : ListAdapter<News, NewsAdapter.NewsViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<News>() {
            override fun areItemsTheSame(oldItem: News, newItem: News): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: News, newItem: News): Boolean =
                oldItem.title == newItem.title &&
                        oldItem.content == newItem.content &&
                        oldItem.datetime == newItem.datetime &&
                        oldItem.mapLocation == newItem.mapLocation &&
                        oldItem.imageUrl == newItem.imageUrl
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.news_page, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val newsItem = getItem(position)
        val context = holder.itemView.context

        holder.title.text = newsItem.title
        holder.description.text = newsItem.content
        holder.location.text = newsItem.location

        val badgeBg = holder.location.background?.mutate() as? GradientDrawable
        val sourceUpper = newsItem.location.uppercase()
        val badgeColorRes = when {
            sourceUpper.contains("LBCI") -> R.color.lbciBadgeBg
            sourceUpper.contains("MTV") -> R.color.mtvBadgeBg
            else -> R.color.intlBadgeBg
        }
        badgeBg?.setColor(ContextCompat.getColor(context, badgeColorRes))

        holder.strikeBadge.visibility = if (newsItem.mapLocation.isNotBlank()) View.VISIBLE else View.GONE

        val cleanDateTime = newsItem.datetime.replace('T', ' ').substringBefore("Z")
        holder.datetime.text = cleanDateTime

        if (!newsItem.imageUrl.isNullOrEmpty()) {
            holder.image.visibility = View.VISIBLE
            Picasso.get().load(newsItem.imageUrl)
                .into(holder.image, object : Callback {
                    override fun onSuccess() {
                        holder.image.visibility = View.VISIBLE
                    }

                    override fun onError(e: Exception?) {
                        holder.image.visibility = View.GONE
                    }
                })
        } else {
            holder.image.visibility = View.GONE
        }

        val saved = isBookmarked(newsItem)
        holder.btnBookmark.setImageResource(
            if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_border
        )

        holder.btnBookmark.setOnClickListener {
            onBookmarkToggle(newsItem)
            notifyItemChanged(holder.bindingAdapterPosition)
        }

        holder.itemView.setOnClickListener {
            onItemClick(newsItem)
        }
    }

    class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.ivNewsImage)
        val title: TextView = itemView.findViewById(R.id.title)
        val description: TextView = itemView.findViewById(R.id.content)
        val location: TextView = itemView.findViewById(R.id.location)
        val strikeBadge: TextView = itemView.findViewById(R.id.tvStrikeBadge)
        val btnBookmark: ImageButton = itemView.findViewById(R.id.btnBookmark)
        val datetime: TextView = itemView.findViewById(R.id.datetime)
    }
}
