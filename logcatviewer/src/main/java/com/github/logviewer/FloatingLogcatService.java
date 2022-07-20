package com.github.logviewer;


import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;

import java.util.ArrayList;

public class FloatingLogcatService extends Service {
    public static void launch(@NonNull Context context, ArrayList<String> excludeList) {
        context.startService(new Intent(context, FloatingLogcatService.class).putStringArrayListExtra("exclude_list", excludeList));
    }

    private LogcatViewerActivityLogcatBinding mBinding;
    private final ReadLogcat readLogcat = new ReadLogcat();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setTheme(R.style.AppTheme);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (readLogcat.running()) return super.onStartCommand(intent, flags, startId);

        LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        mBinding = LogcatViewerActivityLogcatBinding.inflate(inflater);

        TypedValue typedValue = new TypedValue();
        if (inflater.getContext().getTheme().resolveAttribute(R.attr.colorSurfaceVariant, typedValue, true))
            mBinding.getRoot().setBackgroundColor(typedValue.data);


        readLogcat.setExcludeList(intent.getStringArrayListExtra("exclude_list"));
        readLogcat.startReadLogcat(mBinding);
        initViews();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null && mBinding != null) wm.removeView(mBinding.root);
        readLogcat.stopReadLogcat();
        super.onDestroy();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (wm == null || mBinding == null) return;

        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.alpha = .8f;
        params.dimAmount = 0f;
        params.gravity = Gravity.CENTER;
        params.windowAnimations = android.R.style.Animation_Dialog;

        if (height > width) {
            params.width = (int) (width * .7);
            params.height = (int) (height * .5);
        } else {
            params.width = (int) (width * .7);
            params.height = (int) (height * .8);
        }

        wm.addView(mBinding.root, params);

        LogcatActivity.InitBinding(mBinding, getApplicationContext(), readLogcat);
        mBinding.toolbar.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.floating_toolbar_height);
        mBinding.toolbar.setNavigationOnClickListener(v -> stopSelf());
        mBinding.toolbar.setOnMenuItemClickListener(new MenuClickListener(getApplicationContext(), readLogcat, mBinding, () -> {
        }));
        mBinding.toolbar.setOnTouchListener(new View.OnTouchListener() {
            boolean mIntercepted = false;
            int mLastX, mLastY, mFirstX, mFirstY;
            final int mTouchSlop = ViewConfiguration.get(getApplicationContext()).getScaledTouchSlop();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int totalDeltaX = mLastX - mFirstX;
                int totalDeltaY = mLastY - mFirstY;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();
                        mFirstX = mLastX;
                        mFirstY = mLastY;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!mIntercepted) v.performClick();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) event.getRawX() - mLastX;
                        int deltaY = (int) event.getRawY() - mLastY;
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();

                        if ((Math.abs(totalDeltaX) >= mTouchSlop || Math.abs(totalDeltaY) >= mTouchSlop) && event.getPointerCount() == 1) {
                            params.x += deltaX;
                            params.y += deltaY;
                            mIntercepted = true;
                            wm.updateViewLayout(mBinding.root, params);
                        } else {
                            mIntercepted = false;
                        }
                        break;
                }
                return mIntercepted;
            }
        });
    }
}
