package io.github.asutorufa.yuhaiin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.asutorufa.yuhaiin.databinding.ItemRecyclerApplistBinding

class AppListAdapter : RecyclerView.Adapter<AppListAdapter.AppListViewHolder>() {

    private var apps: List<AppList>? = null
    var checkedApps: HashSet<String>? = null

    fun setAppList(apps: List<AppList>, checkedApps: Set<String>) {
        this.apps = apps
        this.checkedApps = HashSet(checkedApps)
        notifyItemRangeInserted(0, itemCount)
    }

    inner class AppListViewHolder(private val itemBinding: ItemRecyclerApplistBinding) :
        RecyclerView.ViewHolder(itemBinding.root), View.OnClickListener {

        private lateinit var info: AppList

        fun bind(app: AppList) {
            this.info = app
            itemBinding.name.text = app.appName
            itemBinding.packageName.text = app.packageName
            itemBinding.checkbox.isChecked = app.isChecked
            itemBinding.appListItemIcon.setImageDrawable(app.appIcon)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            checkedApps?.let {
                if (it.contains(info.packageName)) {
                    it.remove(info.packageName)
                    itemBinding.checkbox.isChecked = false
                } else {
                    it.add(info.packageName)
                    itemBinding.checkbox.isChecked = true
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
        return AppListViewHolder(
            ItemRecyclerApplistBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount(): Int = apps?.size ?: 0

    override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
        apps?.let {
            holder.bind(it[position])
        }
    }

}