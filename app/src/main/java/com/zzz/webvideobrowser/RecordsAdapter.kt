package com.zzz.webvideobrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zzz.webvideobrowser.db.BookmarkRecord
import com.zzz.webvideobrowser.db.HistoryRecord

class RecordsAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: ((Any) -> Unit)? = null
) : RecyclerView.Adapter<RecordsAdapter.RecordViewHolder>() {

    private var items: List<Any> = emptyList()

    fun submitList(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtTitle: TextView = itemView.findViewById(R.id.txtRecordTitle)
        private val txtUrl: TextView = itemView.findViewById(R.id.txtRecordUrl)

        fun bind(item: Any) {
            when (item) {
                is HistoryRecord -> {
                    txtTitle.text = item.title.ifEmpty { item.url }
                    txtUrl.text = item.url
                    itemView.setOnClickListener { onItemClick(item.url) }
                    itemView.setOnLongClickListener {
                        onItemLongClick?.invoke(item)
                        true
                    }
                }
                is BookmarkRecord -> {
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
    }
}
