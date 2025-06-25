package com.github.logviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding
import com.github.logviewer.databinding.LogcatViewerItemLogcatBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class LogcatAdapter(
    private val float: Boolean = true,
    private val binding: LogcatViewerActivityLogcatBinding
) :
    RecyclerView.Adapter<LogcatAdapter.Holder>() {
    private val mData: ArrayList<LogItem> = ArrayList()
    private var mFilteredData: ArrayList<LogItem>? = null
    private var mFilter: String? = null

    init {
        binding.floatingActionButton.setOnClickListener {
            if (itemCount - (binding.list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() >= 500)
                binding.list.scrollToPosition(itemCount - 1)
            else binding.list.smoothScrollToPosition(itemCount - 1)
        }
    }

    fun append(item: LogItem) {
        binding.list.post {
            mData.add(item)

            if (mFilter != null && mFilteredData != null) {
                if (!item.isFiltered(mFilter)) return@post
                mFilteredData!!.add(item)
                notifyItemInserted(itemCount - 1)
            } else notifyItemInserted(itemCount - 1)
        }
    }

    fun clear() {
        binding.list.post {
            val count = itemCount
            mData.clear()
            mFilteredData = null
            notifyItemRangeRemoved(0, count)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            float,
            LogcatViewerItemLogcatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.parse(mFilteredData?.get(position) ?: mData[position])

    override fun getItemCount(): Int = mFilteredData?.size ?: mData.size

    fun setFilter(f: String) {
        binding.list.post {
            mFilter = f
            mFilteredData?.clear()
            mFilteredData = ArrayList<LogItem>().apply {
                mData.forEach { if (it.isFiltered(f)) add(it) }
            }
            notifyDataSetChanged()
        }
    }

    class Holder(
        private val float: Boolean,
        private val mBinding: LogcatViewerItemLogcatBinding
    ) :
        RecyclerView.ViewHolder(mBinding.root), View.OnClickListener {

        private lateinit var item: LogItem
        fun parse(data: LogItem) {
            item = data
            mBinding.time.text = String.format(
                Locale.getDefault(), "%s %d-%d/%s",
                SimpleDateFormat(ReadLogcat.DATE_FORMAT, Locale.getDefault())
                    .format(data.time), data.processId, data.threadId, data.tag
            )
            mBinding.content.text = data.content
            mBinding.tag.text = data.priority
            mBinding.tag.setBackgroundResource(data.colorRes!!)
            if (!float) itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            item.apply {
                val text = String.format(
                    Locale.getDefault(),
                    ReadLogcat.CONTENT_TEMPLATE,
                    SimpleDateFormat(
                        "MM-dd hh:mm:ss.SSS",
                        Locale.getDefault()
                    ).format(time), processId, threadId, priority, tag, content
                )
                MaterialAlertDialogBuilder(mBinding.root.context)
                    .setMessage(text)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show().apply {
                        findViewById<View?>(android.R.id.message).let {
                            if (it is TextView)
                                it.setTextIsSelectable(true)
                        }
                    }
            }
        }
    }
}