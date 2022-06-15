package io.github.asutorufa.yuhaiin

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatEditText
import androidx.browser.customtabs.CustomTabsIntent
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import com.github.logviewer.LogcatActivity
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener
import io.github.asutorufa.yuhaiin.database.Profile
import io.github.asutorufa.yuhaiin.util.DataStore
import java.util.regex.Pattern

class ProfileFragment : PreferenceFragmentCompat() {
    private val refreshPreferences = ArrayList<() -> Unit>()
    private val profile: Profile
        get() = (activity as MainActivity).profile
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.preferenceDataStore = DataStore()
        initPreferences()
        reload()
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
        inflater.inflate(R.menu.main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.prof_add -> {
                val e = AppCompatEditText(requireContext()).apply { isSingleLine = true }

                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.prof_add)
                    .setView(e)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        val name = e.text.toString().trim()
                        if (name.isEmpty()) {
                            return@setPositiveButton
                        }

                        try {
                            Manager.addProfile(name)
                        } catch (e: Exception) {
                            e.message?.let {
                                Snackbar.make(
                                    requireActivity().findViewById(android.R.id.content),
                                    it,
                                    Snackbar.LENGTH_SHORT
                                ).setAnchorView(R.id.floatingActionButton).show()
                            }
                        }
                        reload()
                    }
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
                true
            }
            R.id.prof_del -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.prof_del)
                    .setMessage(String.format(getString(R.string.prof_del_confirm), profile.name))
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        try {
                            Manager.deleteProfile()
                            reload()
                        } catch (e: Exception) {
                            Snackbar.make(
                                requireActivity().findViewById(android.R.id.content),
                                e.message ?: "",
                                Snackbar.LENGTH_SHORT
                            ).setAnchorView(R.id.floatingActionButton).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

        findPreference<EditTextPreference>(resources.getString(R.string.yuhaiin_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.yuhaiinPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = profile.yuhaiinPort.toString() }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.http_server_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.httpServerPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = profile.httpServerPort.toString() }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.socks5_server_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.socks5ServerPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = profile.socks5ServerPort.toString() }
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

        findPreference<DropDownPreference>(resources.getString(R.string.adv_route_Key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                profile.route = newValue as String
            }
            refreshPreferences.add { it.value = profile.route }
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

        findPreference<Preference>(resources.getString(R.string.logcat))?.apply {
            setOnPreferenceClickListener {
                val logcatExcludeRules = listOf(
                    Pattern.compile(".*]: processMotionEvent MotionEvent \\{ action=ACTION_.*"),
                    Pattern.compile(".*]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_.*"),
                    Pattern.compile(".*Davey! duration=.*")
                )
                LogcatActivity.start(context, logcatExcludeRules)
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.open_yuhaiin_page))?.apply {
            setOnPreferenceClickListener {
                if ((activity as MainActivity).mBinder?.isRunning() == false) {
                    Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        "yuhaiin is not running",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(R.id.floatingActionButton).show()
                    return@setOnPreferenceClickListener true
                }

                CustomTabsIntent.Builder()
                    .apply {
                        setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                    }.build()
                    .launchUrl(
                        requireActivity(),
                        Uri.parse("http://localhost:${profile.yuhaiinPort}")
                    )
//                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToPageFragment())
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.adv_dns_Key))?.let {
            it.setOnPreferenceClickListener {
                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToDnsFragment())
                true
            }
        }
    }

    private fun reload() {
        for (refresh in refreshPreferences) refresh()
    }

    private fun updateAppList(mPrefAppList: MultiSelectListPreference) {
        val titles = mutableListOf<String>()
        val packageNames = mutableListOf<String>()
        var index = 0

        for ((key, value) in packages.toList().sortedBy { it.second }.toMap()) {
            if (profile.appList.contains(key)) {
                packageNames.add(index, key)
                titles.add(index, value)
                index++
            } else {
                packageNames.add(key)
                titles.add(value)
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
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val apps = HashMap<String, String>()

            for (pkg in packages) {
                if (!pkg.hasInternetPermission && pkg.packageName != "android") continue

                val applicationInfo = pkg.applicationInfo

                val appName = applicationInfo.loadLabel(packageManager).toString()
                // val appIcon = applicationInfo.loadIcon(packageManager)
                // val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) > 0

                apps[pkg.packageName] = "$appName\n${pkg.packageName}"
            }

            return apps
        }
}
