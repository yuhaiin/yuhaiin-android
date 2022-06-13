package io.github.asutorufa.yuhaiin

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.database.Manager
import yuhaiin.App

class RuleFragment : PreferenceFragmentCompat() {
    private val profile = Manager.profile

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.rule, rootKey)
    }

    private var mBinder: IVpnService? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            mBinder = IVpnService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            mBinder = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            activity?.unbindService(mConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        context?.bindService(
            Intent(context, YuhaiinVpnService::class.java),
            mConnection,
            android.content.Context.BIND_AUTO_CREATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference<EditTextPreference>(resources.getString(R.string.rule_update_bypass_file))?.apply {
            text = profile.ruleUpdateBypassUrl
            Manager.setOnPreferenceChangeListener(this) { _, newValue ->
                profile.ruleUpdateBypassUrl = newValue as String
                
                val message: String = try {
                    if (mBinder != null) mBinder?.SaveNewBypass(newValue)?.let {
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
            Manager.setOnPreferenceChangeListener(it) { _, newValue ->
                profile.ruleProxy = newValue.toString()
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_direct))!!.also {
            it.text = profile.ruleDirect
            Manager.setOnPreferenceChangeListener(it) { _, newValue ->
                profile.ruleDirect = newValue.toString()
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_block))!!.also {
            it.text = profile.ruleBlock
            Manager.setOnPreferenceChangeListener(it) { _, newValue ->
                profile.ruleBlock = newValue.toString()
            }
        }
    }
}