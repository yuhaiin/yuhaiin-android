package com.github.logviewer;


import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

public class LogcatActivity extends AppCompatActivity {

    public static void start(Context context, ArrayList<String> excludeList) {
        Intent starter = new Intent(context, LogcatActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putStringArrayListExtra("exclude_list", excludeList);
        context.startActivity(starter);
    }

    private static final int REQUEST_SCREEN_OVERLAY = 23453;
    private LogcatViewerActivityLogcatBinding mBinding;
    private final ReadLogcat readLogcat = new ReadLogcat();

    public static void InitTheme(Context context, Window window) {
        switch (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
                break;
            case Configuration.UI_MODE_NIGHT_NO:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.getInsetsController().setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                    window.getInsetsController().setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = LogcatViewerActivityLogcatBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        InitTheme(getApplicationContext(), getWindow());

        InitBinding(mBinding, this, readLogcat);
        mBinding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        mBinding.toolbar.setOnMenuItemClickListener(new MenuClickListener(getApplicationContext(), readLogcat, mBinding, () -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(getApplicationContext())) {
                FloatingLogcatService.launch(getApplicationContext(), readLogcat.getExcludeList());
                finish();
                return;
            }

            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            if (getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                Snackbar.make(mBinding.root, R.string.logcat_viewer_not_support_on_this_device, Snackbar.LENGTH_SHORT).show();
            } else {
                overlayLauncher.launch(intent);
            }
        }));

        readLogcat.setExcludeList(getIntent().getStringArrayListExtra("exclude_list"));
    }

    ActivityResultLauncher<Intent> overlayLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == REQUEST_SCREEN_OVERLAY
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && Settings.canDrawOverlays(getApplicationContext())) {
                    FloatingLogcatService.launch(getApplicationContext(), readLogcat.getExcludeList());
                    finish();
                }
            });

    @Override
    protected void onResume() {
        super.onResume();
        readLogcat.startReadLogcat(mBinding);
    }

    @Override
    protected void onPause() {
        super.onPause();
        readLogcat.stopReadLogcat();
    }

    public static void InitBinding(@NonNull LogcatViewerActivityLogcatBinding mBinding, Context context, @NonNull ReadLogcat readLogcat) {
        mBinding.toolbar.inflateMenu(R.menu.logcat);

        mBinding.list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mBinding.list.setStackFromBottom(true);
        mBinding.list.setAdapter(readLogcat.getAdapter());
        mBinding.list.setOnItemClickListener(
                (parent, view, position, id) ->
                        LogcatDetailActivity.launch(context, readLogcat.getAdapter().getItem(position)));
    }
}
