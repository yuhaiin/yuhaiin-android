package io.github.asutorufa.yuhaiin

import android.os.Bundle
import android.view.View
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import yuhaiin.App

class RulePreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = (activity as MainActivity).dataStore
        setPreferencesFromResource(R.xml.rule, rootKey)
    }

    private val mainActivity by lazy { requireActivity() as MainActivity }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.transitionName = "transition_common"
        super.onViewCreated(view, savedInstanceState)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true).apply {
            duration = 500L
        }
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false).apply {
            duration = 500L
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_update_bypass_file))?.apply {
            setOnPreferenceChangeListener { _, newValue ->

                val dialog = MaterialAlertDialogBuilder(context).setMessage("Updating...").create()
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)
                dialog.show()

                GlobalScope.launch(Dispatchers.IO) {
                    val message: String = try {
                        if (mainActivity.mBinder != null)
                            mainActivity.mBinder?.saveNewBypass(
                                newValue as String
                            ).let {
                                if (it?.isNotEmpty() == true) throw Exception(it)
                            }
                        else App().saveNewBypass(newValue as String)
                        "Update Bypass File Successful"
                    } catch (e: Exception) {
                        "Update Bypass File Failed: ${e.message}"
                    }

                    dialog.dismiss()
                    requireActivity().runOnUiThread {
                        MaterialAlertDialogBuilder(context).setMessage(message)
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }.show()
                    }
                }

                true
            }
        }

    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference, is EditTextPreference, is MultiSelectListPreference ->
                showDialog(preference)

            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}