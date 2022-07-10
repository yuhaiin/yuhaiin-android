package com.github.logviewer;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class ReadLogcat {
    private final List<Pattern> mExcludeList = new ArrayList<>();
    private ArrayList<String> excludeList;
    private final LogcatAdapter mAdapter = new LogcatAdapter();
    private boolean mReading = false;

    public LogcatAdapter getAdapter() {
        return mAdapter;
    }

    public ArrayList<String> getExcludeList() {
        return excludeList;
    }

    public void setExcludeList(ArrayList<String> list) {
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
                BufferedReader reader = null;
                try {
                    Process process = new ProcessBuilder("logcat", "-v", "threadtime").start();
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;

                    while (mReading && (line = reader.readLine()) != null) {
                        if (LogItem.IGNORED_LOG.contains(line) || skip(mExcludeList, line)) {
                            continue;
                        }
                        try {
                            final LogItem item = new LogItem(line);
                            mBinding.list.post(() -> mAdapter.append(item));
                        } catch (ParseException | NumberFormatException | IllegalStateException e) {
                            e.printStackTrace();
                        }
                    }
                    stopReadLogcat();
                } catch (IOException e) {
                    e.printStackTrace();
                    stopReadLogcat();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private boolean skip(List<Pattern> mExcludeList, String line) {
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