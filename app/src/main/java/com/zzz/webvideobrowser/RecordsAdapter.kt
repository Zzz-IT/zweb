package com.zzz.webvideobrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zzz.webvideobrowser.db.BookmarkRecord
import com.zzz.webvideobrowser.db.HistoryRecord

sealed class RecordItem {
    abstract val title: String
    abstract val url: String

    data class History(val record: HistoryRecord) : RecordItem() {
        override val title get() = record.title
        override val url get() = record.url
    }

    data class Bookmark(val record: BookmarkRecord) : RecordItem() {
        override val title get() = record.title
        override val url get() = record.url
    }
}

class RecordsAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: ((RecordItem) -> Unit)? = null
) : RecyclerView.Adapter<RecordsAdapter.RecordViewHolder>() {

    private var items: List<RecordItem> = emptyList()

    fun submitList(newItems: List<RecordItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtTitle: TextView = itemView.findViewById(R.id.txtRecordTitle)
        private val txtUrl: TextView = itemView.findViewById(R.id.txtRecordUrl)

        fun bind(item: RecordItem) {
            txtTitle.text = item.title.ifEmpty { item.url }
            txtUrl.text = item.url
            itemView.setOnClickListener { onItemClick(item.url) }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(item)
                true
            }
        }
    }
}
