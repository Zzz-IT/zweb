package com.zzz.webvideobrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdBlockRuleAdapter(
    private val rules: MutableList<String>,
    private val onRefresh: (String) -> Unit,
    private val onDelete: (String, Int) -> Unit
) : RecyclerView.Adapter<AdBlockRuleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtRuleUrl: TextView = view.findViewById(R.id.txtRuleUrl)
        val btnRefresh: ImageButton = view.findViewById(R.id.btnRefreshRule)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteRule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_adblock_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = rules[position]
        holder.txtRuleUrl.text = url
        
        holder.btnRefresh.setOnClickListener {
            onRefresh(url)
        }
        
        holder.btnDelete.setOnClickListener {
            onDelete(url, position)
        }
    }

    override fun getItemCount() = rules.size
}
