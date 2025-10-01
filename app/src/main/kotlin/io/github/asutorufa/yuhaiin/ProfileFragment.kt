package io.github.asutorufa.yuhaiin

import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.github.logviewer.LogcatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.asutorufa.yuhaiin.databinding.PortsDialogBinding
import java.util.Locale

class ProfileFragment : PreferenceFragmentCompat() {
    private val refreshPreferences = ArrayList<() -> Unit>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = BBoltDataStore()
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPreferences()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reload()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference, is EditTextPreference, is MultiSelectListPreference ->
                showDialog(preference)

            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun showAlertDialog(
        title: Int,
        view: View?,
        message: String?,
        positiveFun: () -> Unit
    ) =
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(title)
            view?.let { setView(it) }
            message?.let { setMessage(it) }
            setPositiveButton(android.R.string.ok) { _, _ -> positiveFun() }
            setNegativeButton(android.R.string.cancel) { _, _ -> }
            show()
        }


    private fun initPreferences() {
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
                    ".*eglMakeCurrent:.*",
                    ".*NotificationManager: io.github.asutorufa.yuhaiin: notify.*",
                    ".*InputEventReceiver_DOT: IER.scheduleInputVsync.*",
                    ".*ViewRootImpl@.*[.*]: .*"
                )

                startActivity(LogcatActivity.intent(logcatExcludeRules, requireActivity()))
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.ports_key))?.let {
            it.setOnPreferenceClickListener {

                val bind = PortsDialogBinding.inflate(requireActivity().layoutInflater, null, false)

                bind.http.setText(
                    String.format(
                        Locale.ENGLISH,
                        "%d",
                        MainApplication.store.getInt("http_port")
                    )
                )
                bind.yuhaiin.setText(
                    String.format(
                        Locale.ENGLISH,
                        MainApplication.store.getInt("yuhaiin_port").toString()
                    )
                )
                showAlertDialog(
                    R.string.ports_title,
                    bind.root,
                    null
                ) {
                    MainApplication.store.putInt("http_port", bind.http.text.toString().toInt())
                }
                true
            }
        }
    }

    private fun reload() = refreshPreferences.forEach { it() }


    inner class BBoltDataStore : PreferenceDataStore() {
        override fun putString(key: String?, value: String?) =
            MainApplication.store.putString(key, value)

        override fun putStringSet(key: String?, values: Set<String?>?) =
            MainApplication.store.putStringSet(key, values)

        override fun putInt(key: String?, value: Int) = MainApplication.store.putInt(key, value)
        override fun putLong(key: String?, value: Long) = MainApplication.store.putLong(key, value)

        override fun putFloat(key: String?, value: Float) =
            MainApplication.store.putFloat(key, value)

        override fun putBoolean(key: String?, value: Boolean) =
            MainApplication.store.putBoolean(key, value)

        override fun getString(key: String?, defValue: String?): String =
            MainApplication.store.getString(key)

        override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String> =
            MainApplication.store.getStringSet(key)

        override fun getInt(key: String?, defValue: Int): Int = MainApplication.store.getInt(key)

        override fun getLong(key: String?, defValue: Long): Long =
            MainApplication.store.getLong(key)

        override fun getFloat(key: String?, defValue: Float): Float =
            MainApplication.store.getFloat(key)

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            MainApplication.store.getBoolean(key)
    }
}
