package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.*
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.util.Constants
import io.github.asutorufa.yuhaiin.util.Profile
import io.github.asutorufa.yuhaiin.util.ProfileManager
import io.github.asutorufa.yuhaiin.util.Utility
import java.util.*

class ProfileFragment() : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {
    private val ctx by lazy { requireActivity().applicationContext }
    private val mManager by lazy { ProfileManager(requireActivity().applicationContext) }
    private lateinit var mProfile: Profile
    private lateinit var mSwitch: SwitchCompat
    private var mBinder: IVpnService? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            mBinder = IVpnService.Stub.asInterface(binder)
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
                    mSwitch.isChecked = false
                    mSwitch.isEnabled = true
                }
                Constants.INTENT_CONNECTED -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: CONNECTED")
                    mSwitch.isChecked = true
                    mSwitch.isEnabled = true
                    requireActivity().bindService(
                        Intent(requireContext(), YuhaiinVpnService::class.java),
                        mConnection,
                        0
                    )
                }
                Constants.INTENT_CONNECTING -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: CONNECTING")
                    mSwitch.isEnabled = false
                }
                Constants.INTENT_DISCONNECTING -> {
                    if (DEBUG) Log.d("yuhaiin", "onReceive: DISCONNECTING")
                    mSwitch.isEnabled = false
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
    private lateinit var mPrefUserpw: SwitchPreference
    private lateinit var mPrefPerApp: SwitchPreference
    private lateinit var mPrefAppBypass: SwitchPreference
    private lateinit var mPrefIPv6: SwitchPreference
    private lateinit var mPrefAuto: SwitchPreference
    private lateinit var mPrefYuhaiinHost: EditTextPreference


    override fun onStart() {
        super.onStart()
        if (mBinder == null)
            requireActivity().bindService(Intent(activity, YuhaiinVpnService::class.java), mConnection, 0)

        val f = IntentFilter().apply {
            addAction(Constants.INTENT_DISCONNECTED)
            addAction(Constants.INTENT_CONNECTED)
            addAction(Constants.INTENT_CONNECTING)
            addAction(Constants.INTENT_DISCONNECTING)
        }
        ctx.registerReceiver(bReceiver, f)
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
        assert(container != null)
        //        FloatingActionButton fab = container.findViewById(R.id.floatingActionButton);
//        fab.setOnClickListener(v -> Log.d("yuhaiin", "onClick: float button"));
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ctx.unregisterReceiver(bReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.main, menu)
        val s = menu.findItem(R.id.switch_main)
        mSwitch = s.actionView as SwitchCompat
        mSwitch.isChecked = mBinder?.isRunning ?: false
        mSwitch.setOnCheckedChangeListener(this)
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

    override fun onPreferenceClick(p: Preference): Boolean {
        // TODO: Implement this method
        return false
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
                mProfile.setIsUserpw(java.lang.Boolean.parseBoolean(newValue.toString()))
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
                resetListN(mPrefRoutes, newValue)
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
                mProfile.isPerApp = java.lang.Boolean.parseBoolean(newValue.toString())
                true
            }
            mPrefAppBypass -> {
                mProfile.isBypassApp = java.lang.Boolean.parseBoolean(newValue.toString())
                true
            }
            mPrefAppList -> {
                @Suppress("UNCHECKED_CAST")
                mProfile.appList = newValue as HashSet<String>
                updateAppList()
                if (DEBUG) Log.d("yuhaiin", "appList:\n${mProfile.appList}".trimIndent())
                true
            }
            mPrefIPv6 -> {
                mProfile.setHasIPv6(java.lang.Boolean.parseBoolean(newValue.toString()))
                true
            }
            mPrefAuto -> {
                mProfile.setAutoConnect(java.lang.Boolean.parseBoolean(newValue.toString()))
                true
            }
            mPrefYuhaiinHost -> {
                mProfile.yuhaiinHost = newValue.toString()
                resetTextN(mPrefYuhaiinHost, newValue)
                true
            }
            else -> {
                false
            }
        }
    }

    override fun onCheckedChanged(p1: CompoundButton, checked: Boolean) {
        if (checked) startVpn() else stopVpn()
    }

    private fun initPreferences() {
        mPrefProfile = findPreference(Constants.PREF_PROFILE)!!
        mPrefYuhaiinHost = findPreference(Constants.PREF_YUHAIIN_HOST)!!
        mPrefYuhaiinHost.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        mPrefHttpServerPort = findPreference(Constants.PREF_HTTP_SERVER_PORT)!!
        mPrefHttpServerPort.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        mPrefSocks5ServerPort = findPreference(Constants.PREF_SOCKS5_SERVER_PORT)!!
        mPrefSocks5ServerPort.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        mPrefUserpw = findPreference(Constants.PREF_AUTH_USERPW)!!
        mPrefUsername = findPreference(Constants.PREF_AUTH_USERNAME)!!
        mPrefPassword = findPreference(Constants.PREF_AUTH_PASSWORD)!!
        mPrefRoutes = findPreference(Constants.PREF_ADV_ROUTE)!!
        mPrefFakeDnsCidr = findPreference(Constants.PREF_ADV_FAKE_DNS_CIDR)!!
        mPrefDnsPort = findPreference(Constants.PREF_ADV_DNS_PORT)!!
        mPrefDnsPort.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        mPrefPerApp = findPreference(Constants.PREF_ADV_PER_APP)!!
        mPrefAppBypass = findPreference(Constants.PREF_ADV_APP_BYPASS)!!
        mPrefAppList = findPreference(Constants.PREF_ADV_APP_LIST)!!
        mPrefIPv6 = findPreference(Constants.PREF_IPV6_PROXY)!!
        mPrefAuto = findPreference(Constants.PREF_ADV_AUTO_CONNECT)!!
        mPrefProfile.onPreferenceChangeListener = this
        mPrefYuhaiinHost.onPreferenceChangeListener = this
        mPrefHttpServerPort.onPreferenceChangeListener = this
        mPrefSocks5ServerPort.onPreferenceChangeListener = this
        mPrefUserpw.onPreferenceChangeListener = this
        mPrefUsername.onPreferenceChangeListener = this
        mPrefPassword.onPreferenceChangeListener = this
        mPrefRoutes.onPreferenceChangeListener = this
        mPrefFakeDnsCidr.onPreferenceChangeListener = this
        mPrefDnsPort.onPreferenceChangeListener = this
        mPrefPerApp.onPreferenceChangeListener = this
        mPrefAppBypass.onPreferenceChangeListener = this
        mPrefAppList.onPreferenceChangeListener = this
        mPrefIPv6.onPreferenceChangeListener = this
        mPrefAuto.onPreferenceChangeListener = this
    }

    private fun reload() {
        mProfile = mManager.default

        mPrefProfile.entries = mManager.profiles
        mPrefProfile.entryValues = mManager.profiles
        mPrefProfile.value = mProfile.name
        mPrefRoutes.value = mProfile.route
        resetList(mPrefProfile, mPrefRoutes)
        mPrefUserpw.isChecked = mProfile.isUserPw
        mPrefPerApp.isChecked = mProfile.isPerApp
        mPrefAppBypass.isChecked = mProfile.isBypassApp
        mPrefIPv6.isChecked = mProfile.hasIPv6()
        mPrefAuto.isChecked = mProfile.autoConnect()
        mPrefHttpServerPort.text = mProfile.httpServerPort.toString()
        mPrefSocks5ServerPort.text = mProfile.socks5ServerPort.toString()
        mPrefUsername.text = mProfile.username
        mPrefPassword.text = mProfile.password
        mPrefFakeDnsCidr.text = mProfile.fakeDnsCidr
        mPrefDnsPort.text = mProfile.dnsPort.toString()
        mPrefYuhaiinHost.text = mProfile.yuhaiinHost
        resetText(
            mPrefHttpServerPort,
            mPrefSocks5ServerPort,
            mPrefUsername,
            mPrefPassword,
            mPrefFakeDnsCidr,
            mPrefDnsPort,
            mPrefYuhaiinHost
        )
        updateAppList()
    }

    private fun updateAppList() {
        val selectedAndExistsApps: MutableSet<String> = TreeSet()
        val titles: MutableList<CharSequence> = ArrayList()
        val packageNames: MutableList<CharSequence> = ArrayList()

        for ((key, value) in packages) {
            if (!mProfile.appList.contains(value)) continue

            titles.add(key)
            selectedAndExistsApps.add(value)
            packageNames.add(value)
        }

        for ((key, value) in packages) {
            if (mProfile.appList.contains(value)) continue

            titles.add(key)
            packageNames.add(value)

        }

        mPrefAppList.entries = titles.toTypedArray()
        mPrefAppList.entryValues = packageNames.toTypedArray()
        mProfile.appList = selectedAndExistsApps
    }

    private val packages: Map<String, String>
        get() {
            val packages: MutableMap<String, String> = TreeMap()
            val packageinfos = ctx.packageManager.getInstalledPackages(0)

            for (info in packageinfos)
                info.applicationInfo.loadLabel(ctx.packageManager).toString().let { appName ->
                    info.packageName.let { packageName ->
                        packages["$appName\n$packageName"] = packageName
                    }
                }

            return packages
        }

    private fun resetList(vararg pref: ListPreference) {
        for (p in pref) p.summary = p.entry
    }

    private fun resetListN(pref: ListPreference, newValue: Any) {
        pref.summary = newValue.toString()
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
            if (result.resultCode == Activity.RESULT_OK) Utility.startVpn(requireActivity())
        }

    private fun startVpn() =
        VpnService.prepare(requireContext())?.also { startVpnLauncher.launch(it) } ?: Utility.startVpn(requireContext())

    private fun stopVpn() = mBinder?.stop()
}