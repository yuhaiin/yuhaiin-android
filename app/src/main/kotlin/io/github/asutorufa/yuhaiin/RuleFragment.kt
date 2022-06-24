package io.github.asutorufa.yuhaiin

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.takisoft.preferencex.SimpleMenuPreference
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener
import io.github.asutorufa.yuhaiin.database.Profile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import yuhaiin.App


class RuleFragment : PreferenceFragmentCompat() {
    private val profile: Profile
        get() = (activity as MainActivity).profile

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.rule, rootKey)
    }

    private var updating = false

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.preferenceDataStore = (activity as MainActivity).dataStore

        findPreference<SimpleMenuPreference>(resources.getString(R.string.adv_route_Key))!!.also {
            it.value = profile.route
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.route = newValue as String
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_update_bypass_file))?.apply {
            text = profile.ruleUpdateBypassUrl
            setOnPreferenceChangeListener(this) { _, newValue ->
                if (updating) {
                    Toast.makeText(context, "bypass file already in updating", Toast.LENGTH_SHORT)
                        .show()
                    throw Exception("Updating")
                }

                profile.ruleUpdateBypassUrl = newValue as String

                updating = true
                Snackbar
                    .make(view!!, "bypass file updating", Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.floatingActionButton)
                    .apply {
                        (view.findViewById<View>(com.google.android.material.R.id.snackbar_text).parent as ViewGroup).addView(
                            ProgressBar(context)
                        )
                        show()
                    }

                GlobalScope.launch(Dispatchers.IO) {
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

                    updating = false
                    Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        message,
                        Snackbar.LENGTH_LONG
                    ).setAnchorView(R.id.floatingActionButton).show()
                }
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