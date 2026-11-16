package com.usj.news_application.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.usj.news_application.R
import com.usj.news_application.models.User

class UserAdapter(
    private var users: List<User>,
    private val onSelectUser: (User) -> Unit = {},
    private val onDeleteUser: (User) -> Unit = {}
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserId: TextView = itemView.findViewById(R.id.tvUserId)
        val tvFirstName: TextView = itemView.findViewById(R.id.tvFirstName)
        val tvLastName: TextView = itemView.findViewById(R.id.tvLastName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val tvPassword: TextView = itemView.findViewById(R.id.tvPassword)
        val tvBirthDate: TextView = itemView.findViewById(R.id.tvBirthDate)
        val btnSelect: Button = itemView.findViewById(R.id.btnSelectUserItem)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteUserItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvUserId.text = "ID #${user.id}"
        holder.tvFirstName.text = user.firstName
        holder.tvLastName.text = user.lastName
        holder.tvEmail.text = "📧 ${user.email}"
        holder.tvBirthDate.text = "🎂 DOB: ${user.birthdate}"
        holder.tvPassword.text = "🔑 ${user.password}"

        holder.itemView.setOnClickListener { onSelectUser(user) }
        holder.btnSelect.setOnClickListener { onSelectUser(user) }
        holder.btnDelete.setOnClickListener { onDeleteUser(user) }
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
