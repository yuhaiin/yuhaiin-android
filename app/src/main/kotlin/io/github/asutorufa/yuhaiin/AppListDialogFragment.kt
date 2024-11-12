package io.github.asutorufa.yuhaiin

import android.Manifest
import android.app.Dialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.logviewer.ReadLogcat.Companion.ignore
import io.github.asutorufa.yuhaiin.databinding.ApplistDialogFragmentBinding
import kotlinx.coroutines.*

class AppListDialogFragment : DialogFragment() {

    private val adapter = AppListAdapter()
    private var loadPackages: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val appListBinding = ApplistDialogFragmentBinding.inflate(inflater, container, false)
        appListBinding.appListRecyclerview.apply {
            layoutManager = LinearLayoutManager(appListBinding.root.context)
            adapter = this@AppListDialogFragment.adapter

            loadPackages = GlobalScope.launch(Dispatchers.IO) {
                ignore {
                    val list = packages
                    requireActivity().runOnUiThread {
                        val checkedApps = MainApplication.store.getStringSet("app_list")
                        this@AppListDialogFragment.adapter.setAppList(list, checkedApps)
                        appListBinding.appListProgressIndicator.visibility = View.GONE
                    }
                }
            }
        }
        return appListBinding.root
    }


    override fun onDestroy() {
        Log.d("appListFragment", "onDestroy")
        loadPackages?.cancel()
        super.onDestroy()
    }

    override fun onPause() {
        Log.d("appListFragment", "onPause: ${adapter.checkedApps}")
        adapter.checkedApps?.let {
            MainApplication.store.putStringSet("app_list", it)
        }
        super.onPause()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

    private val PackageInfo.hasInternetPermission: Boolean
        get() {
            val permissions = requestedPermissions
            return permissions?.any { it == Manifest.permission.INTERNET } ?: false
        }

    private val packages: List<AppList>
        get() {
            val packageManager = requireContext().packageManager
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_PERMISSIONS.toLong()
                    )
                )
                else packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            val checkedApps = MainApplication.store.getStringSet("app_list")
            val apps = mutableListOf<AppList>()

            packages.sortBy { it.applicationInfo?.loadLabel(packageManager).toString() }

            var index = 0
            packages.forEach {
                if (!it.hasInternetPermission && it.packageName != "android") return@forEach

                it.applicationInfo?.apply {
                    val app = AppList(
                        this.loadLabel(packageManager).toString(), // app name
                        it.packageName, this.loadIcon(packageManager), // icon
                        (this.flags and ApplicationInfo.FLAG_SYSTEM) > 0, // is system
                        checkedApps.contains(it.packageName)
                    )
                    if (app.isChecked) apps.add(index++, app)
                    else apps.add(app)
                }

            }
            return apps
        }
}