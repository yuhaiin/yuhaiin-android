package io.github.asutorufa.yuhaiin

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.preference.*
import com.github.logviewer.LogcatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.takisoft.preferencex.SimpleMenuPreference
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.database.Manager.profile
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener

class ProfileFragment : PreferenceFragmentCompat() {
    private val refreshPreferences = ArrayList<() -> Unit>()
    private val mainActivity by lazy { requireActivity() as MainActivity }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        setPreferencesFromResource(R.xml.settings, rootKey)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)

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

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.append_http_proxy_to_vpn))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.appendHttpProxyToSystem = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = profile.appendHttpProxyToSystem }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.auth_userpw_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.isUserPw = newValue as Boolean
                reload()
            }
            refreshPreferences.add { it.isChecked = profile.isUserPw }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.auth_username_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.username = newValue.toString()
            }
            refreshPreferences.add {
                it.text = profile.username
                it.isVisible = profile.isUserPw
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.auth_password_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.password = newValue.toString()
            }
            refreshPreferences.add {
                it.text = profile.password
                it.isVisible = profile.isUserPw
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
        findPreference<MultiSelectListPreference>(resources.getString(R.string.adv_app_list_key))!!.also {
            it.setOnPreferenceClickListener { _ ->
                updateAppList(it)
                false
            }

            @Suppress("Unchecked_Cast")
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.appList = newValue as Set<String>
            }

            refreshPreferences.add { it.values = profile.appList }
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

        findPreference<Preference>(resources.getString(R.string.adv_dns_Key))?.let { it ->
            it.setOnPreferenceClickListener {
                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToDnsFragment())
//                    val extras = FragmentNavigatorExtras(e.itemView to "shared_element_dns")
//                    findNavController().navigate(
//                        R.id.action_profileFragment_to_dnsFragment,
//                        null,
//                        null,
//                        extras
//                    )
                true
            }
        }


        findPreference<Preference>(resources.getString(R.string.ports_key))?.let {
            it.setOnPreferenceClickListener {
                val view =
                    requireActivity().layoutInflater.inflate(R.layout.ports_dialog, null, false)
                val socks5 = view.findViewById<TextInputEditText>(R.id.socks5)
                val http = view.findViewById<TextInputEditText>(R.id.http)
                val yuhaiin = view.findViewById<TextInputEditText>(R.id.yuhaiin)

                socks5.setText(profile.socks5ServerPort.toString())
                http.setText(profile.httpServerPort.toString())
                yuhaiin.setText(profile.yuhaiinPort.toString())
                showAlertDialog(
                    R.string.ports_title,
                    view,
                    null
                ) {
                    profile.socks5ServerPort = socks5.text.toString().toInt()
                    profile.httpServerPort = http.text.toString().toInt()
                    profile.yuhaiinPort = yuhaiin.text.toString().toInt()
                    Manager.db.updateProfile(profile)
                }
                true
            }
        }
    }

    private fun reload() = refreshPreferences.forEach { it() }

    private fun updateAppList(mPrefAppList: MultiSelectListPreference) {
        val titles = mutableListOf<String>()
        val packageNames = mutableListOf<String>()
        var index = 0

        packages.toList().sortedBy { it.second }.forEach {
            if (profile.appList.contains(it.first)) {
                packageNames.add(index, it.first)
                titles.add(index, it.second)
                index++
            } else {
                packageNames.add(it.first)
                titles.add(it.second)
            }
        }

        mPrefAppList.values = profile.appList
        mPrefAppList.entries = titles.toTypedArray()
        mPrefAppList.entryValues = packageNames.toTypedArray()
    }

    private val PackageInfo.hasInternetPermission: Boolean
        get() {
            val permissions = requestedPermissions
            return permissions?.any { it == Manifest.permission.INTERNET } ?: false
        }

    private val packages: Map<String, String>
        get() {
            val packageManager = requireContext().packageManager
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    packageManager.getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(
                            PackageManager.GET_PERMISSIONS.toLong()
                        )
                    )
                else
                    packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            val apps = HashMap<String, String>()

            packages.forEach {
                if (!it.hasInternetPermission && it.packageName != "android") return@forEach

                val applicationInfo = it.applicationInfo

                val appName = applicationInfo.loadLabel(packageManager).toString()
                // val appIcon = applicationInfo.loadIcon(packageManager)
                // val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) > 0

                apps[it.packageName] = "$appName\n${it.packageName}"
            }

            return apps
        }


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
}
