package io.github.asutorufa.yuhaiin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.asutorufa.yuhaiin.databinding.HostsDialogBinding
import io.github.asutorufa.yuhaiin.databinding.ItemRecyclerHostsBinding

class HostsDialogFragment : DialogFragment() {

    private val adapter = HostsListAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val hostsDialogFragmentBinding = HostsDialogBinding.inflate(inflater, container, false)

        hostsDialogFragmentBinding.hostsListRecyclerview.apply {
            layoutManager = LinearLayoutManager(hostsDialogFragmentBinding.root.context)
            adapter = this@HostsDialogFragment.adapter

            this@HostsDialogFragment.adapter.setHostsList(buildList {
                add(HostsList("1", "2"))
                add(HostsList("3", "4"))
                add(HostsList("5", "6"))
                add(HostsList("7", "8"))
                add(HostsList("9", "10"))
            })
        }

        return hostsDialogFragmentBinding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
}


data class HostsList(val from: String, val to: String)

class HostsListAdapter : RecyclerView.Adapter<HostsListAdapter.HostsListViewHolder>() {

    private var hosts: List<HostsList>? = null

    fun setHostsList(hosts: List<HostsList>) {
        this.hosts = hosts
        notifyItemRangeInserted(0, itemCount)
    }

    inner class HostsListViewHolder(private val itemBinding: ItemRecyclerHostsBinding) :
        RecyclerView.ViewHolder(itemBinding.root), View.OnClickListener {

        lateinit var info: HostsList

        fun bind(hosts: HostsList) {
            this.info = hosts
            itemBinding.fromText.setText(hosts.from)
            itemBinding.toText.setText(hosts.to)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {}
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostsListViewHolder {
        return HostsListViewHolder(
            ItemRecyclerHostsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = hosts?.size ?: 0

    override fun onBindViewHolder(holder: HostsListViewHolder, position: Int) {
        hosts?.let {
            holder.bind(it[position])
        }
    }

}