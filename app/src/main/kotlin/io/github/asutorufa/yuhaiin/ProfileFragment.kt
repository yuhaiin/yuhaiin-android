package io.github.asutorufa.yuhaiin

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.browser.customtabs.CustomTabsIntent
import androidx.preference.*
import com.github.logviewer.LogcatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.util.Constants
import io.github.asutorufa.yuhaiin.util.Profile
import io.github.asutorufa.yuhaiin.util.ProfileManager
import io.github.asutorufa.yuhaiin.util.Utility
import java.util.*
import java.util.regex.Pattern

class ProfileFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private val mManager by lazy { ProfileManager(requireActivity().applicationContext) }
    private lateinit var mProfile: Profile
    private lateinit var mFab: FloatingActionButton
    private var mBinder: IVpnService? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            mBinder = IVpnService.Stub.asInterface(binder).also {
                if (it.isRunning) mFab.setImageResource(R.drawable.stop)
                else mFab.setImageResource(R.drawable.play_arrow)
            }
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            mBinder = null
        }
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.INTENT_DISCONNECTED -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: DISCONNECTED")

                    mFab.isEnabled = true
                    mFab.setImageResource(R.drawable.play_arrow)
                    Snackbar.make(
                        requireView(),
                        "yuhaiin disconnected",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(mFab).show()
                }
                Constants.INTENT_CONNECTED -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: CONNECTED")

                    mFab.isEnabled = true
                    mFab.setImageResource(R.drawable.stop)

                    Snackbar.make(
                        requireView(),
                        "yuhaiin connected, listen at: ${mProfile.yuhaiinPort}",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(mFab).show()
                    context.bindService(
                        Intent(context, YuhaiinVpnService::class.java),
                        mConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
                Constants.INTENT_CONNECTING -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: CONNECTING")
                    mFab.isEnabled = false
                }
                Constants.INTENT_DISCONNECTING -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: DISCONNECTING")
                    mFab.isEnabled = false
                }

                Constants.INTENT_ERROR -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: ERROR")
                    intent.getStringExtra("message")?.let {
                        Snackbar.make(
                            requireView(),
                            it,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
    private lateinit var mPrefProfile: ListPreference
    private lateinit var mPrefRoutes: DropDownPreference
    private lateinit var mPrefHttpServerPort: EditTextPreference
    private lateinit var mPrefSocks5ServerPort: EditTextPreference
    private lateinit var mPrefUsername: EditTextPreference
    private lateinit var mPrefPassword: EditTextPreference
    private lateinit var mPrefFakeDnsCidr: EditTextPreference
    private lateinit var mPrefDnsPort: EditTextPreference
    private lateinit var mPrefAppList: MultiSelectListPreference
    private lateinit var mPrefUserpw: SwitchPreferenceCompat
    private lateinit var mPrefPerApp: SwitchPreferenceCompat
    private lateinit var mPrefAppBypass: SwitchPreferenceCompat
    private lateinit var mPrefIPv6: SwitchPreferenceCompat
    private lateinit var mPrefAllowLan: SwitchPreferenceCompat
    private lateinit var mPrefAuto: SwitchPreferenceCompat
    private lateinit var mPrefYuhaiinPort: EditTextPreference
    private lateinit var mPrefSaveLogcat: SwitchPreferenceCompat
    private lateinit var mPrefRuleProxy: EditTextPreference
    private lateinit var mPrefRuleDirect: EditTextPreference
    private lateinit var mPrefRuleBlock: EditTextPreference


    override fun onStart() {
        super.onStart()
        if (mBinder == null)
            context?.bindService(
                Intent(context, YuhaiinVpnService::class.java),
                mConnection,
                Context.BIND_AUTO_CREATE
            )


        val f = IntentFilter().apply {
            addAction(Constants.INTENT_DISCONNECTED)
            addAction(Constants.INTENT_CONNECTED)
            addAction(Constants.INTENT_CONNECTING)
            addAction(Constants.INTENT_DISCONNECTING)
        }
        requireContext().registerReceiver(bReceiver, f)
    }

    override fun onCreate(saveInstanceState: Bundle?) {
        super.onCreate(saveInstanceState)
        addPreferencesFromResource(R.xml.settings)
        setHasOptionsMenu(true)
        initPreferences()
        reload()
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mFab = container?.findViewById<FloatingActionButton>(R.id.floatingActionButton)!!.also {
            it.setOnClickListener {
                mBinder?.also { it2 ->
                    if (it2.isRunning) stopVpn()
                    else startVpn()
                } ?: startVpn()
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            requireContext().unregisterReceiver(bReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
        inflater.inflate(R.menu.main, menu)
    }

    @SuppressLint("NonConstantResourceId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.prof_add -> {
                addProfile()
                true
            }
            R.id.prof_del -> {
                removeProfile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onPreferenceChange(p: Preference, newValue: Any): Boolean {
        return when (p) {
            mPrefProfile -> {
                val name = newValue.toString()
                mProfile = mManager.getProfile(name) ?: mManager.default
                mManager.switchDefault(name)
                reload()
                true
            }
            mPrefHttpServerPort -> {
                if (newValue.toString().isEmpty()) return false
                mProfile.httpServerPort = newValue.toString().toInt()
                resetTextN(mPrefHttpServerPort, newValue)
                true
            }
            mPrefSocks5ServerPort -> {
                if (newValue.toString().isEmpty()) return false
                mProfile.socks5ServerPort = newValue.toString().toInt()
                resetTextN(mPrefSocks5ServerPort, newValue)
                true
            }

            mPrefUserpw -> {
                val value = newValue as Boolean
                mProfile.isUserPw = value
                resetAuthVisible(value)
                true
            }
            mPrefUsername -> {
                mProfile.username = newValue.toString()
                resetTextN(mPrefUsername, newValue)
                true
            }
            mPrefPassword -> {
                mProfile.password = newValue.toString()
                resetTextN(mPrefPassword, newValue)
                true
            }
            mPrefRoutes -> {
                mProfile.route = newValue.toString()
                true
            }
            mPrefFakeDnsCidr -> {
                mProfile.fakeDnsCidr = newValue.toString()
                resetTextN(mPrefFakeDnsCidr, newValue)
                true
            }
            mPrefDnsPort -> {
                if (newValue.toString().isEmpty()) return false
                mProfile.dnsPort = newValue.toString().toInt()
                resetTextN(mPrefDnsPort, newValue)
                true
            }
            mPrefPerApp -> {
                mProfile.isPerApp = newValue as Boolean
                true
            }
            mPrefAppBypass -> {
                mProfile.isBypassApp = newValue as Boolean
                true
            }
            mPrefAppList -> {
                @Suppress("UNCHECKED_CAST")
                mProfile.appList = newValue as Set<String>
                true
            }
            mPrefIPv6 -> {
                mProfile.hasIPv6 = newValue as Boolean
                true
            }
            mPrefAuto -> {
                mProfile.autoConnect = newValue as Boolean
                true
            }
            mPrefYuhaiinPort -> {
                mProfile.yuhaiinPort = newValue.toString().toInt()
                resetTextN(mPrefYuhaiinPort, newValue)
                true
            }
            mPrefSaveLogcat -> {
                mProfile.saveLogcat = newValue as Boolean
                true
            }
            mPrefAllowLan -> {
                mProfile.allowLan = newValue as Boolean
                true
            }
            mPrefRuleProxy -> {
                mProfile.ruleProxy = newValue.toString().trim()
                mPrefRuleProxy.text = newValue.toString().trim()
                true
            }
            mPrefRuleDirect -> {
                mProfile.ruleDirect = newValue.toString().trim()
                mPrefRuleDirect.text = newValue.toString().trim()
                true
            }
            mPrefRuleBlock -> {
                mProfile.ruleBlock = newValue.toString().trim()
                mPrefRuleBlock.text = newValue.toString().trim()
                true
            }
            else -> {
                false
            }
        }
    }

    private fun initPreferences() {
        mPrefProfile = findPreferenceAndSetListener(Constants.PREF_PROFILE)!!

        mPrefYuhaiinPort =
            findPreferenceAndSetListener<EditTextPreference>(Constants.PREF_YUHAIIN_PORT)!!.also {
                it.setOnBindEditTextListener { editText: EditText ->
                    editText.inputType =
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
            }
        mPrefHttpServerPort =
            findPreferenceAndSetListener<EditTextPreference>(Constants.PREF_HTTP_SERVER_PORT)!!.also {
                it.setOnBindEditTextListener { editText: EditText ->
                    editText.inputType =
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
            }
        mPrefSocks5ServerPort = findPreferenceAndSetListener(Constants.PREF_SOCKS5_SERVER_PORT)!!
        mPrefSocks5ServerPort.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        mPrefUserpw =
            findPreferenceAndSetListener<SwitchPreferenceCompat>(Constants.PREF_AUTH_USERPW)!!
        mPrefUsername = findPreferenceAndSetListener(Constants.PREF_AUTH_USERNAME)!!
        mPrefPassword = findPreferenceAndSetListener(Constants.PREF_AUTH_PASSWORD)!!

        mPrefRoutes = findPreferenceAndSetListener(Constants.PREF_ADV_ROUTE)!!
        mPrefFakeDnsCidr = findPreferenceAndSetListener(Constants.PREF_ADV_FAKE_DNS_CIDR)!!
        mPrefDnsPort = findPreferenceAndSetListener(Constants.PREF_ADV_DNS_PORT)!!
        mPrefDnsPort.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        mPrefPerApp = findPreferenceAndSetListener(Constants.PREF_ADV_PER_APP)!!
        mPrefAppBypass = findPreferenceAndSetListener(Constants.PREF_ADV_APP_BYPASS)!!
        mPrefAppList = findPreferenceAndSetListener(Constants.PREF_ADV_APP_LIST)!!
        mPrefAppList.setOnPreferenceClickListener {
            updateAppList()
            false
        }

        mPrefIPv6 = findPreferenceAndSetListener(Constants.PREF_IPV6_PROXY)!!
        mPrefAuto = findPreferenceAndSetListener(Constants.PREF_ADV_AUTO_CONNECT)!!
        mPrefAllowLan = findPreferenceAndSetListener(Constants.PREF_ALLOW_LAN)!!

        mPrefRuleProxy =
            findPreferenceAndSetListener<EditTextPreference>(requireContext().resources.getString(R.string.rule_proxy))!!
        mPrefRuleDirect =
            findPreferenceAndSetListener(requireContext().resources.getString(R.string.rule_direct))!!
        mPrefRuleBlock =
            findPreferenceAndSetListener(requireContext().resources.getString(R.string.rule_block))!!

        mPrefSaveLogcat = findPreferenceAndSetListener(Constants.PREF_SAVE_LOGCAT)!!

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
                if (mBinder == null || mBinder?.isRunning == false) {
                    Snackbar.make(
                        requireView(),
                        "yuhaiin is not running",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(mFab).show()
                    return@setOnPreferenceClickListener true
                }

                CustomTabsIntent.Builder().apply {
                    setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                    setStartAnimations(
                        requireContext(),
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                    setExitAnimations(
                        requireContext(),
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                }.build().launchUrl(
                    requireContext(),
                    Uri.parse("http://127.0.0.1:${mProfile.yuhaiinPort}")
                )
                true
            }
        }
    }


    private fun <T : Preference?> findPreferenceAndSetListener(key: CharSequence): T? {
        return findPreference<T>(key)?.apply {
            onPreferenceChangeListener = this@ProfileFragment
        }
    }

    private fun reload() {
        mProfile = mManager.default

        mPrefProfile.entries = mManager.profiles
        mPrefProfile.entryValues = mManager.profiles
        mPrefProfile.value = mProfile.name
        mPrefRoutes.value = mProfile.route
        resetList(mPrefProfile, mPrefRoutes)

        mPrefUserpw.isChecked = mProfile.isUserPw
        resetAuthVisible(mProfile.isUserPw)
        mPrefUsername.text = mProfile.username
        mPrefPassword.text = mProfile.password

        mPrefPerApp.isChecked = mProfile.isPerApp
        mPrefAppBypass.isChecked = mProfile.isBypassApp

        mPrefIPv6.isChecked = mProfile.hasIPv6
        mPrefAuto.isChecked = mProfile.autoConnect

        mPrefHttpServerPort.text = mProfile.httpServerPort.toString()
        mPrefSocks5ServerPort.text = mProfile.socks5ServerPort.toString()
        mPrefFakeDnsCidr.text = mProfile.fakeDnsCidr
        mPrefDnsPort.text = mProfile.dnsPort.toString()
        mPrefYuhaiinPort.text = mProfile.yuhaiinPort.toString()

        mPrefSaveLogcat.isChecked = mProfile.saveLogcat

        mPrefRuleBlock.text = mProfile.ruleBlock
        mPrefRuleDirect.text = mProfile.ruleDirect
        mPrefRuleProxy.text = mProfile.ruleProxy

        resetText(
            mPrefHttpServerPort,
            mPrefSocks5ServerPort,
            mPrefUsername,
            mPrefPassword,
            mPrefFakeDnsCidr,
            mPrefDnsPort,
            mPrefYuhaiinPort
        )
    }


    private fun resetAuthVisible(enabled: Boolean) {
        if (enabled) {
            mPrefUsername.isVisible = true
            mPrefPassword.isVisible = true
        } else {
            mPrefUsername.isVisible = false
            mPrefPassword.isVisible = false
        }
    }

    private fun updateAppList() {
        val titles = mutableListOf<String>()
        val packageNames = mutableListOf<String>()
        var index = 0

        for ((key, value) in packages.toList().sortedBy { it.second }.toMap()) {
            if (mProfile.appList.contains(key)) {
                packageNames.add(index, key)
                titles.add(index, value)
                index++
            } else {
                packageNames.add(key)
                titles.add(value)
            }
        }

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
            val apps = TreeMap<String, String>()

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

    private fun resetList(vararg pref: ListPreference) {
        for (p in pref) p.summary = p.entry
    }

    private fun resetText(vararg pref: EditTextPreference) {
        for (p in pref) {
            if (p.key != "auth_password") {
                p.summary = p.text
            } else {
                if ((p.text?.length ?: 0) > 0) p.summary =
                    String.format(Locale.US, String.format(Locale.US, "%%0%dd", p.text!!.length), 0)
                        .replace("0", "*") else p.summary = ""
            }
        }
    }

    private fun resetTextN(pref: EditTextPreference, newValue: Any) {
        if (pref.key != "auth_password") {
            pref.summary = newValue.toString()
        } else {
            val text = newValue.toString()
            if (text.isNotEmpty())
                pref.summary =
                    String.format(Locale.US, String.format(Locale.US, "%%0%dd", text.length), 0).replace("0", "*")
            else
                pref.summary = ""
        }
    }

    private fun addProfile() {
        val e = EditText(activity).apply { isSingleLine = true }

        AlertDialog.Builder(requireActivity()).setTitle(R.string.prof_add).setView(e)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val name = e.text.toString().trim()
                if (name.isNotEmpty())
                    mManager.addProfile(name)?.let {
                        mProfile = it
                        reload()
                        return@setPositiveButton
                    }

                Toast.makeText(activity, String.format(getString(R.string.err_add_prof), name), Toast.LENGTH_SHORT)
                    .show()
            }.setNegativeButton(
                android.R.string.cancel
            ) { _: DialogInterface?, _: Int -> }.create().show()
    }

    private fun removeProfile() {
        AlertDialog.Builder(requireActivity()).setTitle(R.string.prof_del)
            .setMessage(String.format(getString(R.string.prof_del_confirm), mProfile.name))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                if (!mManager.removeProfile(mProfile.name))
                    Toast.makeText(activity, getString(R.string.err_del_prof, mProfile.name), Toast.LENGTH_SHORT).show()
                else {
                    mProfile = mManager.default
                    reload()
                }
            }.setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }.create().show()
    }


    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    private var startVpnLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) Utility.startVpn(requireActivity(), mProfile)
        }

    private fun startVpn() =
        VpnService.prepare(activity)?.also { startVpnLauncher.launch(it) } ?: Utility.startVpn(
            requireActivity(),
            mProfile
        )

    private fun stopVpn() = mBinder?.stop()
}