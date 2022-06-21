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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService


class MainActivity : AppCompatActivity() {
    val tag: String = this.javaClass.simpleName
    val profile = Manager.profile

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
    var mBinder: YuhaiinVpnService.VpnBinder? = null

    private val mFab: FloatingActionButton by lazy {
        findViewById<FloatingActionButton>(R.id.floatingActionButton)!!
            .apply {
                setOnClickListener {
                    if (mBinder != null && mBinder!!.isRunning()) mBinder!!.stop()
                    else startVpn()
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
            mBinder = (binder as YuhaiinVpnService.VpnBinder).also {
                if (it.isRunning()) mFab.setImageResource(R.drawable.stop)
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

    private fun showSnackBar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_SHORT
        ).setAnchorView(mFab).show()
    }

    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    private var startVpnLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startService(
                Intent(
                    this,
                    YuhaiinVpnService::class.java
                )
            )
        }

    private fun startVpn() =
        VpnService.prepare(this)?.also { startVpnLauncher.launch(it) }
            ?: startService(Intent(this, YuhaiinVpnService::class.java))
}