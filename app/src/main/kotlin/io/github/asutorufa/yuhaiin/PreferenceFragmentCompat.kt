package io.github.asutorufa.yuhaiin

import android.os.Bundle
import androidx.preference.*

fun PreferenceFragmentCompat.showDialog(preference: Preference) {
    val dialogFragment = when (preference) {
        is EditTextPreference -> MaterialEditTextPreferenceDialogFragment()
        is ListPreference -> MaterialListPreferenceDialogFragment()
        is MultiSelectListPreference -> MaterialMultiSelectListPreferenceDialogFragment()
        else -> throw Throwable("unknown preference")
    }

    dialogFragment.apply {
        arguments = Bundle(1).apply {
            putString("key", preference.key)
        }
    }
    dialogFragment.setTargetFragment(this, 0)
    dialogFragment.show(
        parentFragmentManager,
        "androidx.preference.PreferenceFragment.DIALOG"
    )
}