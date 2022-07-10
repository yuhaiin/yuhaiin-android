package com.github.logviewer;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.concurrent.Executors;

public class LogcatActivity extends AppCompatActivity {

    public static void start(Context context, ArrayList<String> excludeList) {
        @SuppressLint("InlinedApi")
        Intent starter = new Intent(context, LogcatActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putStringArrayListExtra("exclude_list", excludeList);
        context.startActivity(starter);
    }

    private static final int REQUEST_SCREEN_OVERLAY = 23453;
    private LogcatViewerActivityLogcatBinding mBinding;
    private final ReadLogcat readLogcat = new ReadLogcat();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = LogcatViewerActivityLogcatBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());


        switch (getApplicationContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
                break;
            case Configuration.UI_MODE_NIGHT_NO:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getWindow().getInsetsController().setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                    getWindow().getInsetsController().setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
        }

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        readLogcat.setExcludeList(getIntent().getStringArrayListExtra("exclude_list"));

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.logcat_viewer_logcat_spinner, R.layout.logcat_viewer_item_logcat_dropdown);
        spinnerAdapter.setDropDownViewResource(R.layout.logcat_viewer_item_logcat_dropdown);
        mBinding.spinner.setAdapter(spinnerAdapter);
        mBinding.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String filter = getResources().getStringArray(R.array.logcat_viewer_logcat_spinner)[position];
                readLogcat.getAdapter().getFilter().filter(filter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mBinding.list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mBinding.list.setStackFromBottom(true);
        mBinding.list.setAdapter(readLogcat.getAdapter());
        mBinding.list.setOnItemClickListener(
                (parent, view, position, id) ->
                        LogcatDetailActivity.launch(LogcatActivity.this, readLogcat.getAdapter().getItem(position)));
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.logcat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.clear) {
            readLogcat.getAdapter().clear();
            return true;
        } else if (item.getItemId() == R.id.export) {
            Executors.newSingleThreadExecutor().execute(new ExportLogFileTask(readLogcat.getAdapter().getData(), mBinding, getApplicationContext()));
            return true;
        } else if (item.getItemId() == R.id.floating) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(getApplicationContext())) {
                FloatingLogcatService.launch(getApplicationContext(), readLogcat.getExcludeList());
                finish();
                return true;
            }

            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            if (getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                Snackbar.make(mBinding.root, R.string.logcat_viewer_not_support_on_this_device, Snackbar.LENGTH_SHORT).show();
            } else {
                overlayLauncher.launch(intent);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
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
}
