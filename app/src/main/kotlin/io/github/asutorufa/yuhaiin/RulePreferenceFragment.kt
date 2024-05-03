package io.github.asutorufa.yuhaiin

import android.os.Bundle
import android.view.View
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialSharedAxis
import io.github.asutorufa.yuhaiin.database.Bypass
import io.github.asutorufa.yuhaiin.database.Manager.profile
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import yuhaiin.App


class RulePreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        setPreferencesFromResource(R.xml.rule, rootKey)

    private val mainActivity by lazy { requireActivity() as MainActivity }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.transitionName = "transition_common"
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

        preferenceManager.preferenceDataStore = mainActivity.dataStore

        findPreference<ListPreference>(resources.getString(R.string.adv_route_Key))!!.also {
            it.value = profile.route
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.route = newValue as String
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_update_bypass_file))?.apply {
            text = profile.ruleUpdateBypassUrl
            setOnPreferenceChangeListener(this) { _, newValue ->

                profile.ruleUpdateBypassUrl = newValue as String

                val dialog = MaterialAlertDialogBuilder(context).setMessage("Updating...").create()
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)
                dialog.show()

                GlobalScope.launch(Dispatchers.IO) {
                    val message: String = try {
                        if (mainActivity.mBinder != null)
                            mainActivity.mBinder?.saveNewBypass(
                                newValue
                            ).let {
                                if (it?.isNotEmpty() == true) throw Exception(it)
                            }
                        else App().saveNewBypass(newValue)
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

        findPreference<ListPreference>(resources.getString(R.string.bypass_tcp))!!.also {
            it.value = bypassTypeToStr(Bypass.Type.fromInt(profile.bypass.tcp))
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.bypass.tcp = strToBypassType(newValue as String).value
            }
        }

        findPreference<ListPreference>(resources.getString(R.string.bypass_udp))!!.also {
            it.value = bypassTypeToStr(Bypass.Type.fromInt(profile.bypass.udp))
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.bypass.udp = strToBypassType(newValue as String).value
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

    private fun strToBypassType(str: String): Bypass.Type {
        return when (str) {
            resources.getString(R.string.bypass_bypass) -> Bypass.Type.Bypass
            resources.getString(R.string.bypass_direct) -> Bypass.Type.Direct
            resources.getString(R.string.bypass_block) -> Bypass.Type.Block
            resources.getString(R.string.bypass_proxy) -> Bypass.Type.Proxy
            else -> Bypass.Type.Bypass
        }
    }

    private fun bypassTypeToStr(type: Bypass.Type): String {
        return when (type) {
            Bypass.Type.Bypass -> resources.getString(R.string.bypass_bypass)
            Bypass.Type.Direct -> resources.getString(R.string.bypass_direct)
            Bypass.Type.Block -> resources.getString(R.string.bypass_block)
            Bypass.Type.Proxy -> resources.getString(R.string.bypass_proxy)
        }
    }
}