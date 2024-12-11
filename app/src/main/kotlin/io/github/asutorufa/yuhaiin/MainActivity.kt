package io.github.asutorufa.yuhaiin

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceDataStore
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.databinding.MainActivityBinding
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State


class MainActivity : AppCompatActivity() {
    val tag: String = this.javaClass.simpleName
    val dataStore = BBoltDataStore()

    private val mainBinding: MainActivityBinding by lazy {
        MainActivityBinding.inflate(layoutInflater).apply {
            floatingActionButton.setOnClickListener {
                if (mBinder != null && mBinder!!.isRunning) mBinder!!.stop()
                else startService()
            }

            floatingActionButtonOpen.setOnClickListener {
                CustomTabsIntent.Builder().apply {
                    setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                }.build().apply {
                    intent.data =
                        Uri.parse("http://127.0.0.1:${MainApplication.store.getInt("yuhaiin_port")}")
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
                    setBackgroundColor(com.google.android.material.R.attr.backgroundColor)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderEffect.createBlurEffect(
            20f, 20f, Shader.TileMode.CLAMP
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
            Intent(this, YuhaiinVpnService::class.java), mConnection, Context.BIND_AUTO_CREATE
        )

        val intentFilter = IntentFilter().apply {
            addAction(State.CONNECTING.toString())
            addAction(State.CONNECTED.toString())
            addAction(State.DISCONNECTING.toString())
            addAction(State.DISCONNECTED.toString())
            addAction(State.ERROR.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(
            bReceiver, intentFilter, RECEIVER_EXPORTED
        )
        else registerReceiver(bReceiver, intentFilter)
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
            findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT
        ).apply {
            anchorView =
                if (isFabVisible) mainBinding.floatingActionButtonOpen else mainBinding.extendedFloatingButton
            show()
        }
    }

    private val vpnPermissionDialogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startService(
                    Intent(this, YuhaiinVpnService::class.java)
                )
            }
        }

    private fun startService() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0
            )
        }

        // prepare to get vpn permission
        VpnService.prepare(this)?.apply {
            vpnPermissionDialogLauncher.launch(this)
        } ?: startService(Intent(this, YuhaiinVpnService::class.java))
    }

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
