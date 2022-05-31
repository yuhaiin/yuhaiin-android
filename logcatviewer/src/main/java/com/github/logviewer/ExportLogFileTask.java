package com.github.logviewer;


import android.content.Context;
import android.content.Intent;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExportLogFileTask implements Runnable {
    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    private final LogItem[] mLogs;
    private final LogcatViewerActivityLogcatBinding mBinding;
    private final Context mContext;

    ExportLogFileTask(LogItem[] logs, LogcatViewerActivityLogcatBinding binding, Context context) {
        mLogs = logs;
        mBinding = binding;
        mContext = context;
    }

    protected File doInBackground() {
        if (mContext == null || mContext.getExternalCacheDir().isFile() || mLogs == null || mLogs.length == 0) {
            return null;
        }

        File logFile = new File(mContext.getExternalCacheDir(), DATE_FORMAT.format(new Date()) + ".log");
        if (logFile.exists() && !logFile.delete()) {
            return null;
        }

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile)));
            for (LogItem log : mLogs) {
                writer.write(log.origin + "\n");
            }
            writer.close();
            return logFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    void onPostExecute(File file) {
        if (file == null) {
            Snackbar.make(mBinding.root, R.string.logcat_viewer_create_log_file_failed, Snackbar.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_STREAM, LogcatFileProvider.getUriForFile(mContext, mContext.getPackageName() + ".logcat_fileprovider", file));

        if (mContext.getPackageManager().queryIntentActivities(shareIntent, 0).isEmpty()) {
            Snackbar.make(mBinding.root, R.string.logcat_viewer_not_support_on_this_device, Snackbar.LENGTH_SHORT).show();
            return;
        }

        mContext.startActivity(shareIntent);
    }

    @Override
    public void run() {
        onPostExecute(doInBackground());
    }
}
