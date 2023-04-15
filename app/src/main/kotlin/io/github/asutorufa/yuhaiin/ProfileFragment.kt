package io.github.asutorufa.yuhaiin

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.github.logviewer.LogcatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.takisoft.preferencex.SimpleMenuPreference
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.database.Manager.profile
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener
import io.github.asutorufa.yuhaiin.databinding.PortsDialogBinding

class ProfileFragment : PreferenceFragmentCompat() {
    private val refreshPreferences = ArrayList<() -> Unit>()
    private val mainActivity by lazy { requireActivity() as MainActivity }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        setPreferencesFromResource(R.xml.settings, rootKey)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)

        initPreferences()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.transitionName = "transition_common"

        mainActivity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        preferenceManager.preferenceDataStore = mainActivity.dataStore
        reload()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference, is EditTextPreference, is MultiSelectListPreference ->
                showDialog(preference)

            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    fun showAlertDialog(
        title: Int,
        view: View?,
        message: String?,
        PositiveFun: () -> Unit
    ) =
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(title)
            view?.let { setView(it) }
            message?.let { setMessage(it) }
            setPositiveButton(android.R.string.ok) { _, _ -> PositiveFun() }
            setNegativeButton(android.R.string.cancel) { _, _ -> }
            show()
        }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) =
            menuInflater.inflate(R.menu.main, menu)

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
            when (menuItem.itemId) {
                R.id.prof_add -> {
                    val e = AppCompatEditText(requireContext()).apply { isSingleLine = true }
                    showAlertDialog(R.string.prof_add, e, null) {
                        val name = e.text.toString()
                        if (name.isEmpty()) return@showAlertDialog
                        try {
                            Manager.addProfile(name)
                        } catch (e: Exception) {
                            e.message?.let { mainActivity.showSnackBar(it) }
                        }
                        reload()
                    }
                    true
                }

                R.id.prof_del -> {
                    showAlertDialog(
                        R.string.prof_del,
                        null,
                        String.format(getString(R.string.prof_del_confirm), profile.name)
                    ) {
                        try {
                            Manager.deleteProfile()
                            reload()
                        } catch (e: Exception) {
                            e.message?.let { mainActivity.showSnackBar(it) }
                        }
                    }
                    true
                }

                else -> false
            }
    }

    private fun initPreferences() {
        findPreference<ListPreference>(resources.getString(R.string.profile_key))!!.also {
            refreshPreferences.add {
                it.value = profile.name
                val profiles = Manager.getProfileNames()
                it.entries = profiles.toTypedArray()
                it.entryValues = profiles.toTypedArray()
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                Manager.switchProfile(newValue as String)
                reload()
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.append_http_proxy_to_vpn_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.appendHttpProxyToSystem = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.appendHttpProxyToSystem }
        }
//
//        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.auth_userpw_key))!!.also {
//            setOnPreferenceChangeListener(it) { _, newValue ->
//                profile.isUserPw = newValue as Boolean
//                reload()
//            }
//            refreshPreferences.add { it.isChecked = profile.isUserPw }
//        }
//
//        findPreference<EditTextPreference>(resources.getString(R.string.auth_username_key))!!.also {
//            setOnPreferenceChangeListener(it) { _, newValue ->
//                profile.username = newValue.toString()
//            }
//            refreshPreferences.add {
//                it.text = profile.username
//                it.isVisible = profile.isUserPw
//            }
//        }
//
//        findPreference<EditTextPreference>(resources.getString(R.string.auth_password_key))!!.also {
//            it.setOnBindEditTextListener { editText: EditText ->
//                editText.inputType =
//                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
//            }
//
//            setOnPreferenceChangeListener(it) { _, newValue ->
//                profile.password = newValue.toString()
//            }
//            refreshPreferences.add {
//                it.text = profile.password
//                it.isVisible = profile.isUserPw
//            }
//        }

        findPreference<ListPreference>(resources.getString(R.string.adv_tun_driver_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.tunDriver = strToTunDriver(newValue.toString())
            }

            refreshPreferences.add {
                it.value = tunDriverToStr(profile.tunDriver)
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.adv_per_app_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.isPerApp = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.isPerApp }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.adv_app_bypass_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.isBypassApp = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.isBypassApp }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.ipv6_proxy_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.hasIPv6 = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.hasIPv6 }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.adv_auto_connect_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.autoConnect = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.autoConnect }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.allow_lan_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.allowLan = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.allowLan }
        }
        findPreference<Preference>(resources.getString(R.string.rule))!!.also {
            it.setOnPreferenceClickListener {
                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToRuleFragment())
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.save_logcat))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.saveLogcat = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.saveLogcat }
        }

        findPreference<SimpleMenuPreference>(resources.getString(R.string.log_level))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.logLevel = strToLogLevel(newValue.toString())
            }

            refreshPreferences.add { it.value = logLevelToStr(profile.logLevel) }
        }

        findPreference<Preference>(resources.getString(R.string.logcat))?.apply {
            setOnPreferenceClickListener {
                val logcatExcludeRules = arrayListOf(
                    ".*]: processMotionEvent MotionEvent \\{ action=ACTION_.*",
                    ".*]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_.*",
                    ".*Davey! duration=.*",
                    // android popup window select text debug log
                    ".*Attempted to finish an input event but the input event receiver has already been disposed.*",
                    ".*endAllActiveAnimators on .* with handle.*",
                    ".*Initializing SystemTextClassifier,.*",
                    ".*TextClassifier called on main thread.*",
                    ".*android added item .*",
                    ".*No package ID .* found for ID.*",
                    ".*eglMakeCurrent:.*"
                )

                startActivity(LogcatActivity.intent(logcatExcludeRules, requireActivity()))
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.adv_dns_Key))?.let {
            it.setOnPreferenceClickListener {
                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToDnsFragment())
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.ports_key))?.let {
            it.setOnPreferenceClickListener {

                val bind = PortsDialogBinding.inflate(requireActivity().layoutInflater, null, false)

                bind.socks5.setText(profile.socks5ServerPort.toString())
                bind.http.setText(profile.httpServerPort.toString())
                bind.yuhaiin.setText(profile.yuhaiinPort.toString())
                showAlertDialog(
                    R.string.ports_title,
                    bind.root,
                    null
                ) {
                    profile.socks5ServerPort = bind.socks5.text.toString().toInt()
                    profile.httpServerPort = bind.http.text.toString().toInt()
                    profile.yuhaiinPort = bind.yuhaiin.text.toString().toInt()
                    Manager.db.updateProfile(profile)
                }
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.adv_new_app_list_key))?.let {
            it.setOnPreferenceClickListener {
                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToAppListFragment())
                true
            }
        }
    }

    private fun reload() = refreshPreferences.forEach { it() }


    private fun strToLogLevel(str: String): Int {
        return when (str) {
            resources.getString(R.string.log_level_verbose) -> 0
            resources.getString(R.string.log_level_debug) -> 1
            resources.getString(R.string.log_level_info) -> 2
            resources.getString(R.string.log_level_warning) -> 3
            resources.getString(R.string.log_level_error) -> 4
            resources.getString(R.string.log_level_fatal) -> 5
            else -> 2
        }
    }

    private fun logLevelToStr(type: Int): String {
        return when (type) {
            0 -> resources.getString(R.string.log_level_verbose)
            1 -> resources.getString(R.string.log_level_debug)
            2 -> resources.getString(R.string.log_level_info)
            3 -> resources.getString(R.string.log_level_warning)
            4 -> resources.getString(R.string.log_level_error)
            5 -> resources.getString(R.string.log_level_fatal)
            else -> resources.getString(R.string.log_level_info)
        }
    }


    private fun strToTunDriver(str: String): Int {
        return when (str) {
            resources.getString(R.string.tun_driver_fdbased) -> 0
            resources.getString(R.string.tun_driver_channel) -> 1
            resources.getString(R.string.tun_driver_system_gvisor) -> 2
            else -> 0
        }
    }

    private fun tunDriverToStr(type: Int): String {
        return when (type) {
            0 -> resources.getString(R.string.tun_driver_fdbased)
            1 -> resources.getString(R.string.tun_driver_channel)
            2 -> resources.getString(R.string.tun_driver_system_gvisor)
            else -> resources.getString(R.string.tun_driver_fdbased)
        }
    }
}
