package io.github.asutorufa.yuhaiin

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.preference.ListPreferenceDialogFragmentCompat

class MaterialListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    private val materialPreferenceDialogFragment = MaterialPreferenceDialogFragment()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        materialPreferenceDialogFragment.onCreateDialog(
            requireActivity(),
            preference,
            ::onCreateDialogView,
            ::onBindDialogView,
            ::onPrepareDialogBuilder,
        )

    override fun onClick(dialog: DialogInterface, which: Int) =
        materialPreferenceDialogFragment.onClick(dialog, which)

    override fun onDismiss(dialog: DialogInterface) {
        materialPreferenceDialogFragment.onDismiss()
        super.onDismiss(dialog)
    }

    override fun onDialogClosed(positiveResult: Boolean) =
        super.onDialogClosed(materialPreferenceDialogFragment.onDialogClosed(positiveResult))
}