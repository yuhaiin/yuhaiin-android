package io.github.asutorufa.yuhaiin

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

class EmptyPreference : Preference {
    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)
    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        ctx,
        attrs,
        defStyleAttr
    )

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        ctx,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    private var bindViewHolderListener: OnBindViewHolderListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        bindViewHolderListener?.onBindViewHolder(holder)
        super.onBindViewHolder(holder)
    }

    public fun setOnBindViewHolderListener(l: OnBindViewHolderListener) {
        bindViewHolderListener = l
    }

    fun interface OnBindViewHolderListener {
        fun onBindViewHolder(holder: PreferenceViewHolder)
    }
}