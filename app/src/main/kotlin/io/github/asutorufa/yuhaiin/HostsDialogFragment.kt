package io.github.asutorufa.yuhaiin

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.asutorufa.yuhaiin.databinding.HostsDialogBinding
import io.github.asutorufa.yuhaiin.databinding.ItemRecyclerHostsBinding

class HostsDialogFragment : DialogFragment() {

    private val adapter = HostsListAdapter()
    private val mainActivity by lazy { requireActivity() as MainActivity }

    override fun onPause() {
        Log.d("appListFragment", "onPause: ${adapter.hostsMap}")
        adapter.hostsMap.let {
            MainApplication.store.putStringMap("hosts", it)
        }
        super.onPause()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val hostsDialogFragmentBinding = HostsDialogBinding.inflate(inflater, container, false)

        hostsDialogFragmentBinding.hostsListRecyclerview.apply {
            layoutManager = LinearLayoutManager(hostsDialogFragmentBinding.root.context)
            adapter = this@HostsDialogFragment.adapter

            val hostsSet = MainApplication.store.getStringMap("hosts")
            this@HostsDialogFragment.adapter.setHostsList(hostsSet.toMutableMap())
        }

        mainActivity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return hostsDialogFragmentBinding.root
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) =
            menuInflater.inflate(R.menu.hosts, menu)

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
            when (menuItem.itemId) {
                R.id.hosts_add -> {
                    val e = AppCompatEditText(requireContext()).apply { isSingleLine = true }
                    showAlertDialog(R.string.dns_hosts_add_from, e, null) {
                        val name = e.text.toString()
                        if (name.isEmpty()) return@showAlertDialog
                        try {
                            adapter.addHosts(name)
                        } catch (e: Exception) {
                            e.message?.let { mainActivity.showSnackBar(it) }
                        }
                    }
                    true
                }

                else -> false
            }
    }


    fun showAlertDialog(
        title: Int,
        view: View?,
        message: String?,
        positiveFun: () -> Unit
    ) =
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(title)
            view?.let { setView(it) }
            message?.let { setMessage(it) }
            setPositiveButton(android.R.string.ok) { _, _ -> positiveFun() }
            setNegativeButton(android.R.string.cancel) { _, _ -> }
            show()
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
}


class HostsListAdapter : RecyclerView.Adapter<HostsListAdapter.HostsListViewHolder>() {

    private var hosts: ArrayList<String> = ArrayList()
    lateinit var hostsMap: MutableMap<String, String>

    fun addHosts(from: String) {
        hostsMap[from] = from
        hosts.add(from)
        notifyItemInserted(itemCount - 1)
    }

    fun setHostsList(hosts: MutableMap<String, String>) {
        hosts.forEach { (key, _) -> this.hosts.add(key) }
        this.hostsMap = hosts
        notifyItemRangeInserted(0, itemCount)
    }

    inner class HostsListViewHolder(private val itemBinding: ItemRecyclerHostsBinding) :
        RecyclerView.ViewHolder(itemBinding.root), View.OnClickListener {

        private lateinit var hostsFrom: String

        fun bind(hostsFrom: String) {
            this.hostsFrom = hostsFrom
            itemBinding.from.setEndIconOnClickListener {
                Log.d("end icon click", "end icon click")
                val index = hosts.indexOf(hostsFrom)
                hostsMap.remove(hostsFrom)
                hosts.remove(hostsFrom)
                notifyItemRemoved(index)
            }
            itemBinding.fromText.setText(hostsFrom)
            itemBinding.toText.setText(hostsMap[hostsFrom])
            itemBinding.toText.doAfterTextChanged {
                hostsMap[hostsFrom] = itemBinding.toText.text.toString()
                Log.d("hosts dialog", "bind: $hostsMap")
            }
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

    override fun getItemCount(): Int = hosts.size

    override fun onBindViewHolder(holder: HostsListViewHolder, position: Int) =
        hosts.let { holder.bind(it[position]) }

}