package com.github.logviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.github.logviewer.databinding.LogcatViewerItemLogcatBinding
import java.text.SimpleDateFormat
import java.util.*

class LogcatAdapter internal constructor() : BaseAdapter(), Filterable {
    private val mData: ArrayList<LogItem> = ArrayList()
    private var mFilteredData: ArrayList<LogItem>? = null
    private var mFilter: String? = null
    fun append(item: LogItem) {
        synchronized(LogcatAdapter::class.java) {
            mData.add(item)
            mFilter?.let {
                if (item.isFiltered(it)) mFilteredData?.add(item)
            }
            notifyDataSetChanged()
        }
    }

    fun clear() {
        synchronized(LogcatAdapter::class.java) {
            mData.clear()
            mFilteredData = null
            notifyDataSetChanged()
        }
    }

    val data: Array<LogItem>
        get() {
            synchronized(LogcatAdapter::class.java) { return mData.toTypedArray() }
        }

    override fun getCount(): Int = mFilteredData?.size ?: mData.size
    override fun getItem(position: Int): LogItem = mFilteredData?.get(position) ?: mData[position]
    override fun getItemId(position: Int): Long = position.toLong()


    override fun getView(position: Int, cv: View?, parent: ViewGroup): View {
        cv?.let {
            (it.tag as Holder).parse(getItem(position))
            return it
        } ?: let {
            val binding = LogcatViewerItemLogcatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            Holder(binding).parse(getItem(position))
            return binding.root
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                synchronized(LogcatAdapter::class.java) {
                    val filtered = ArrayList<LogItem>().apply {
                        mData.forEach {
                            if (it.isFiltered(constraint[0].toString())) add(it)
                        }
                    }
                    return FilterResults().apply {
                        values = filtered
                        count = filtered.size
                    }
                }
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                @Suppress("Unchecked_Cast")
                mFilteredData = if (results.values == null) null
                else results.values as ArrayList<LogItem>
                notifyDataSetChanged()
            }
        }
    }

    class Holder internal constructor(private val mBinding: LogcatViewerItemLogcatBinding) {
        fun parse(data: LogItem) {
            mBinding.time.text = String.format(
                Locale.getDefault(), "%s %d-%d/%s",
                SimpleDateFormat(ReadLogcat.DATE_FORMAT, Locale.getDefault())
                    .format(data.time), data.processId, data.threadId, data.tag
            )
            mBinding.content.text = data.content
            mBinding.tag.text = data.priority
            mBinding.tag.setBackgroundResource(data.colorRes!!)
        }

        init {
            mBinding.root.tag = this
        }
    }

}