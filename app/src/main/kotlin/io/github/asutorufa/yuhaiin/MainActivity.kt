package io.github.asutorufa.yuhaiin

import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabsIntent
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceDataStore
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.databinding.MainActivityBinding
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.util.concurrent.ConcurrentHashMap


class MainActivity : AppCompatActivity() {
    val tag: String = this.javaClass.simpleName
    val dataStore = DataStore()

    private val mainBinding: MainActivityBinding by lazy {
        MainActivityBinding.inflate(layoutInflater).apply {
            floatingActionButton.setOnClickListener {
                if (mBinder != null && mBinder!!.isRunning) mBinder!!.stop()
                else startService()
            }

            floatingActionButtonOpen.setOnClickListener {
                CustomTabsIntent.Builder()
                    .apply {
                        setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                    }.build().apply {
                        intent.data = Uri.parse("http://localhost:${Manager.profile.yuhaiinPort}")
                        this@MainActivity.startActivity(intent)
                    }
            }


            extendedFloatingButton.apply {
                setOnClickListener { isFabVisible = !isFabVisible }
                shrink()
            }

            floatingButtonBackground.apply {
                setOnClickListener {
                    isFabVisible = false
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    setBackgroundColor(R.attr.backgroundColor)
                    alpha = 0.5f
                }
            }

            toolbar.let {
                setSupportActionBar(it)
                it.setupWithNavController(
                    fragmentContainer.getFragment<NavHostFragment>().findNavController()
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(mainBinding.root)

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
    }

    // floating action button
    var mBinder: IYuhaiinVpnBinder? = null


    private val blurEffect by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            RenderEffect.createBlurEffect(
                20f,
                20f,
                Shader.TileMode.CLAMP
            )
        else null
    }

    private val onBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            isFabVisible = false
        }
    }

    private var isFabVisible: Boolean = false
        set(value) {
            if (field == value) return

            when (value) {
                true -> {
                    mainBinding.apply {
                        extendedFloatingButton.apply {
                            extend()
                            icon =
                                AppCompatResources.getDrawable(this@MainActivity, R.drawable.close)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            mainView.setRenderEffect(blurEffect)
                        }
                        floatingButtonBackground.visibility = View.VISIBLE

                        if (mBinder?.isRunning == true) floatingActionButtonOpen.show()
                        floatingActionButton.show()
                    }

                    onBackPressedCallback.isEnabled = true
                }

                false -> {
                    mainBinding.apply {
                        floatingActionButtonOpen.hide()
                        floatingActionButton.hide()

                        extendedFloatingButton.shrink()
                        extendedFloatingButton.icon =
                            AppCompatResources.getDrawable(this@MainActivity, R.drawable.add_48)
                        floatingButtonBackground.visibility = View.GONE

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            mainView.setRenderEffect(null)
                        }
                    }
                    onBackPressedCallback.isEnabled = false
                }
            }

            field = value
        }


    override fun onStart() {
        super.onStart()

        bindService(
            Intent(this, YuhaiinVpnService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )

        registerReceiver(bReceiver, IntentFilter().apply {
            addAction(State.CONNECTING.toString())
            addAction(State.CONNECTED.toString())
            addAction(State.DISCONNECTING.toString())
            addAction(State.DISCONNECTED.toString())
            addAction(State.ERROR.toString())
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
                onYuhaiinStatusChanged(it.isRunning)
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
                State.DISCONNECTED.toString(), State.CONNECTED.toString() -> {
                    mainBinding.floatingActionButton.isEnabled = true

                    onYuhaiinStatusChanged(intent.action == State.CONNECTED.toString())
                }

                State.CONNECTING.toString(), State.DISCONNECTING.toString() -> {
                    mainBinding.floatingActionButton.isEnabled = false
                }

                State.ERROR.toString() -> {
                    intent.getStringExtra("message")?.let { showSnackBar(it) }
                }
            }
        }
    }

    fun onYuhaiinStatusChanged(connected: Boolean) {
        if (connected) {
            mainBinding.floatingActionButton.setImageResource(R.drawable.stop)
            if (isFabVisible) mainBinding.floatingActionButtonOpen.show()
        } else {
            mainBinding.floatingActionButton.setImageResource(R.drawable.play_arrow)
            if (isFabVisible) mainBinding.floatingActionButtonOpen.hide()
        }
    }

    fun showSnackBar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_SHORT
        ).apply {
            anchorView =
                if (isFabVisible) mainBinding.floatingActionButtonOpen else mainBinding.extendedFloatingButton
            show()
        }
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