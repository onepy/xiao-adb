package com.droidrun.portal.appcard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.droidrun.portal.R
import com.google.android.material.button.MaterialButton

/**
 * RecyclerView Adapter for App Cards
 */
class AppCardAdapter(
    private var cards: List<AppCard>,
    private val onEdit: (AppCard) -> Unit,
    private val onDelete: (AppCard) -> Unit
) : RecyclerView.Adapter<AppCardAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.card_title)
        val keywords: TextView = view.findViewById(R.id.card_keywords)
        val editButton: MaterialButton = view.findViewById(R.id.btn_edit_card)
        val deleteButton: MaterialButton = view.findViewById(R.id.btn_delete_card)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        
        holder.title.text = card.title
        holder.keywords.text = "关键词: ${card.keywords.joinToString(", ")}"
        
        holder.editButton.setOnClickListener {
            onEdit(card)
        }
        
        holder.deleteButton.setOnClickListener {
            onDelete(card)
        }
    }
    
    override fun getItemCount() = cards.size
    
    fun updateCards(newCards: List<AppCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}