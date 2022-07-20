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
import io.github.asutorufa.yuhaiin.database.Bypass
import io.github.asutorufa.yuhaiin.database.Manager.profile
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import yuhaiin.App


class RuleFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        setPreferencesFromResource(R.xml.rule, rootKey)

    private var updating = false
    private val mainActivity by lazy { requireActivity() as MainActivity }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.preferenceDataStore = mainActivity.dataStore

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
                        if (mainActivity.mBinder != null)
                            mainActivity.mBinder?.saveNewBypass(
                                newValue
                            ).let {
                                if (it?.isNotEmpty() == true) throw Exception(it)
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
                    mainActivity.showSnackBar(message)
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

        findPreference<SimpleMenuPreference>(resources.getString(R.string.bypass_tcp))!!.also {
            it.value = bypassTypeToStr(Bypass.Type.fromInt(profile.bypass.tcp))
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.bypass.tcp = strToBypassType(newValue as String).value
            }
        }

        findPreference<SimpleMenuPreference>(resources.getString(R.string.bypass_udp))!!.also {
            it.value = bypassTypeToStr(Bypass.Type.fromInt(profile.bypass.udp))
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.bypass.udp = strToBypassType(newValue as String).value
            }
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