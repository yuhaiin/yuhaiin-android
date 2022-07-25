package io.github.asutorufa.yuhaiin

import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowInsetsController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceDataStore
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import java.util.concurrent.ConcurrentHashMap


class MainActivity : AppCompatActivity() {
    val tag: String = this.javaClass.simpleName
    val dataStore = DataStore()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }

        when (applicationContext.resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> {}

            Configuration.UI_MODE_NIGHT_NO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    window.insetsController?.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        }

        findViewById<Toolbar>(R.id.toolbar).also {
            setSupportActionBar(it)
            it.setupWithNavController(
                supportFragmentManager.findFragmentById(R.id.fragment_container)!!
                    .findNavController()
            )
        }
    }


    // floating action button
    var mBinder: IYuhaiinVpnBinder? = null

    private val mFab: FloatingActionButton by lazy {
        findViewById<FloatingActionButton>(R.id.floatingActionButton)!!
            .apply {
                setOnClickListener {
                    if (mBinder != null && mBinder!!.isRunning) mBinder!!.stop()
                    else startService()
                }
            }
    }

    override fun onStart() {
        super.onStart()

        bindService(
            Intent(this, YuhaiinVpnService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )

        registerReceiver(bReceiver, IntentFilter().apply {
            addAction(YuhaiinVpnService.INTENT_DISCONNECTED)
            addAction(YuhaiinVpnService.INTENT_CONNECTED)
            addAction(YuhaiinVpnService.INTENT_CONNECTING)
            addAction(YuhaiinVpnService.INTENT_DISCONNECTING)
            addAction(YuhaiinVpnService.INTENT_ERROR)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bReceiver)
            unbindService(mConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            mBinder = IYuhaiinVpnBinder.Stub.asInterface(binder).also {
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
            Log.d(tag, "onReceive: ${intent.action}")

            when (intent.action) {
                YuhaiinVpnService.INTENT_DISCONNECTED, YuhaiinVpnService.INTENT_CONNECTED -> {
                    mFab.isEnabled = true
                    if (intent.action == YuhaiinVpnService.INTENT_CONNECTED) {
                        mFab.setImageResource(R.drawable.stop)
                        showSnackBar("yuhaiin connected")
                    } else {
                        mFab.setImageResource(R.drawable.play_arrow)
                        showSnackBar("yuhaiin disconnected")
                    }
                }

                YuhaiinVpnService.INTENT_CONNECTING, YuhaiinVpnService.INTENT_DISCONNECTING -> {
                    mFab.isEnabled = false
                }

                YuhaiinVpnService.INTENT_ERROR -> {
                    intent.getStringExtra("message")?.let { showSnackBar(it) }
                }
            }
        }
    }

    fun showSnackBar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_SHORT
        ).setAnchorView(mFab).show()
    }

    private val vpnPermissionDialogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startService(
                Intent(
                    this,
                    YuhaiinVpnService::class.java
                )
            )
        }

    private fun startService() =
        // prepare to get vpn permission
        VpnService.prepare(this)?.apply {
            vpnPermissionDialogLauncher.launch(this)
        } ?: startService(Intent(this, YuhaiinVpnService::class.java))


    inner class DataStore : PreferenceDataStore() {
        private val store = ConcurrentHashMap<String, Any>()
        private fun put(key: String?, value: Any?) {
            if (key != null && value != null)
                store[key] = value
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: Set<String?>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)

        override fun getString(key: String?, defValue: String?): String? =
            store[key]?.toString() ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String?>? =
            store[key] as Set<String?>? ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            store[key]?.toString()?.toInt() ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            store[key]?.toString()?.toLong() ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            store[key]?.toString()?.toFloat() ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            store[key]?.toString()?.toBoolean() ?: defValue
    }
}