package com.github.logviewer;

import android.util.Log;

import androidx.annotation.NonNull;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Pattern;

class ReadLogcat {
    private final List<Pattern> mExcludeList = new ArrayList<>();
    private ArrayList<String> excludeList;
    private final LogcatAdapter mAdapter = new LogcatAdapter();
    private boolean mReading = false;
    private Date time = null;

    public LogcatAdapter getAdapter() {
        return mAdapter;
    }

    public ArrayList<String> getExcludeList() {
        return excludeList;
    }

    public void setExcludeList(@NonNull ArrayList<String> list) {
        mExcludeList.clear();
        excludeList = list;
        for (String pattern : list) {
            try {
                mExcludeList.add(Pattern.compile(pattern));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startReadLogcat(LogcatViewerActivityLogcatBinding mBinding) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                mReading = true;
                try {
                    ArrayList<String> cmd = new ArrayList<>(Arrays.asList("logcat", "-v", "threadtime"));
                    if (time != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.mmm", Locale.getDefault());
                        cmd.add("-T");
                        cmd.add(sdf.format(time));
                    }
                    Log.d("logcat", "run: " + cmd);
                    Process process = new ProcessBuilder(cmd).start();
                    Scanner reader = new Scanner(process.getInputStream());
                    while (mReading && reader.hasNext()) {
                        String line = reader.nextLine();
                        if (LogItem.IGNORED_LOG.contains(line) || skip(mExcludeList, line)) {
                            continue;
                        }
                        try {
                            final LogItem item = new LogItem(line);
                            time = item.time;
                            mBinding.list.post(() -> mAdapter.append(item));
                        } catch (ParseException | NumberFormatException | IllegalStateException e) {
                            e.printStackTrace();
                        }
                    }
                    process.destroy();
                    reader.close();
                    mReading = false;
                    Log.d("logcat", "read logcat exit");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private boolean skip(@NonNull List<Pattern> mExcludeList, String line) {
        for (Pattern pattern : mExcludeList) {
            if (pattern.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean running() {
        return mReading;
    }

    public void stopReadLogcat() {
        mReading = false;
    }
}