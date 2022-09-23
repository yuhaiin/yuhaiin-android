package com.github.logviewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.view.WindowInsetsController
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding
import kotlinx.coroutines.DelicateCoroutinesApi


class LogcatActivity : AppCompatActivity() {
    private lateinit var readLogcat: ReadLogcat
    private val excludeList by lazy { intent.getStringArrayListExtra(INTENT_EXCLUDE_LIST)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogcatViewerActivityLogcatBinding.inflate(layoutInflater).apply {
            setContentView(root)
            readLogcat = ReadLogcat(this@LogcatActivity, this, excludeList) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(applicationContext)
                ) overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                else startFloatService()
            }
            initBinding(this)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            toolbar.setOnMenuItemClickListener(readLogcat)
        }

        initTheme(applicationContext, window)
    }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(applicationContext)
            ) startFloatService()
        }

    private fun startFloatService() {
        FloatingLogcatService.launch(applicationContext, excludeList)
        finish()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()
        FloatingLogcatService.stop(applicationContext)
        readLogcat.start()
    }

    override fun onPause() {
        super.onPause()
        readLogcat.stop()
    }

    companion object {
        const val INTENT_EXCLUDE_LIST = "exclude_list"
        fun start(excludeList: ArrayList<String>, activity: Activity) {
            val starter = Intent(activity, LogcatActivity::class.java)
                .putStringArrayListExtra(INTENT_EXCLUDE_LIST, excludeList)
            activity.startActivity(starter)
        }

        fun intent(excludeList: ArrayList<String>, activity: Activity) =
            Intent(activity, LogcatActivity::class.java)
                .putStringArrayListExtra(INTENT_EXCLUDE_LIST, excludeList)


        fun initTheme(context: Context, window: Window) {
            when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {}
                Configuration.UI_MODE_NIGHT_NO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController!!.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    window.insetsController!!.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        }

        fun initBinding(mBinding: LogcatViewerActivityLogcatBinding) {
            mBinding.list.apply {
                transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
                isStackFromBottom = true
            }
            mBinding.toolbar.inflateMenu(R.menu.logcat)
        }
    }
}