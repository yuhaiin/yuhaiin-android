package com.github.logviewer;

import android.content.Context;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;

import java.util.concurrent.Executors;

public class MenuClickListener implements Toolbar.OnMenuItemClickListener {
    private final Context context;
    private final ReadLogcat readLogcat;
    private final LogcatViewerActivityLogcatBinding mBinding;
    private final FloatingWindowLauncher floatingWindowLauncher;

    public interface FloatingWindowLauncher {
        void launchFloatingWindow();
    }

    MenuClickListener(Context context, ReadLogcat readLogcat, LogcatViewerActivityLogcatBinding mBinding, FloatingWindowLauncher floatingWindowLauncher) {
        this.context = context;
        this.readLogcat = readLogcat;
        this.mBinding = mBinding;
        this.floatingWindowLauncher = floatingWindowLauncher;
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            readLogcat.getAdapter().clear();
        } else if (id == R.id.export) {
            Executors.newSingleThreadExecutor().execute(new ExportLogFileTask(readLogcat.getAdapter().getData(), mBinding, context));
        } else if (id == R.id.floating) {
            floatingWindowLauncher.launchFloatingWindow();
        } else if (id == R.id.Verbose || id == R.id.Debug || id == R.id.Info || id == R.id.Warning || id == R.id.Error || id == R.id.Fatal) {
            readLogcat.getAdapter().getFilter().filter(item.getTitle());
        } else {
            return false;
        }

        return true;
    }
}
