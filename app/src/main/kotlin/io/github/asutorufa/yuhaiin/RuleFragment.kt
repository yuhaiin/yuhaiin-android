package io.github.asutorufa.yuhaiin

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener
import io.github.asutorufa.yuhaiin.database.Profile
import yuhaiin.App

class RuleFragment : PreferenceFragmentCompat() {
    private val profile: Profile
        get() = (activity as MainActivity).profile

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.rule, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference<EditTextPreference>(resources.getString(R.string.rule_update_bypass_file))?.apply {
            text = profile.ruleUpdateBypassUrl
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.ruleUpdateBypassUrl = newValue as String

                val message: String = try {
                    if (activity is MainActivity && (activity as MainActivity).mBinder != null)
                        (activity as MainActivity).mBinder?.saveNewBypass(
                            newValue
                        )?.let {
                            if (it.isNotEmpty()) throw Exception(it)
                        }
                    else App().saveNewBypass(
                        newValue,
                        context.getExternalFilesDir("yuhaiin")!!.absolutePath
                    )
                    "Update Bypass File Successful"
                } catch (e: Exception) {
                    "Update Bypass File Failed: ${e.message}"
                }
                Snackbar.make(
                    requireActivity().findViewById(android.R.id.content),
                    message,
                    Snackbar.LENGTH_LONG
                ).setAnchorView(R.id.floatingActionButton).show()
            }
        }


        findPreference<EditTextPreference>(resources.getString(R.string.rule_proxy))!!.also {
            it.text = profile.ruleProxy
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.ruleProxy = newValue.toString()
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_direct))!!.also {
            it.text = profile.ruleDirect
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.ruleDirect = newValue.toString()
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_block))!!.also {
            it.text = profile.ruleBlock
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.ruleBlock = newValue.toString()
            }
        }
    }
}