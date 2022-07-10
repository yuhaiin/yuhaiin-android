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
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;

import java.util.ArrayList;

public class FloatingLogcatService extends Service {

    public static void launch(Context context, ArrayList<String> excludeList) {
        context.startService(new Intent(context, FloatingLogcatService.class)
                .putStringArrayListExtra("exclude_list", excludeList));
    }

    @Nullable
    private LogcatViewerActivityLogcatBinding mBinding = null;
    private Context mThemedContext;
    private final ReadLogcat readLogcat = new ReadLogcat();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mThemedContext = new ContextThemeWrapper(this, R.style.AppTheme);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (readLogcat.running()) {
            return super.onStartCommand(intent, flags, startId);
        }

        mBinding = LogcatViewerActivityLogcatBinding.inflate(LayoutInflater.from(mThemedContext));
        TypedValue typedValue = new TypedValue();
        if (mBinding != null && mThemedContext.getTheme().resolveAttribute(
                android.R.attr.windowBackground, typedValue, true)) {
            int colorWindowBackground = typedValue.data;
            mBinding.getRoot().setBackgroundColor(colorWindowBackground);
        }

        readLogcat.setExcludeList(intent.getStringArrayListExtra("exclude_list"));
        initViews();
        readLogcat.startReadLogcat(mBinding);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null && mBinding != null) {
            wm.removeView(mBinding.root);
        }

        readLogcat.stopReadLogcat();
        super.onDestroy();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        final WindowManager.LayoutParams params;
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null || mBinding == null) {
            return;
        } else {
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;

            params = new WindowManager.LayoutParams(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,

                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,

                    PixelFormat.TRANSLUCENT);
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
        }

        mBinding.toolbar.getLayoutParams().height = getResources().getDimensionPixelSize(
                R.dimen.floating_toolbar_height);
        mBinding.toolbar.setNavigationOnClickListener(v -> stopSelf());

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(mThemedContext,
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
        mBinding.list.setOnItemClickListener((parent, view, position, id) ->
                LogcatDetailActivity.launch(getApplicationContext(), readLogcat.getAdapter().getItem(position)));

        mBinding.toolbar.setOnTouchListener(new View.OnTouchListener() {

            boolean mIntercepted = false;
            int mLastX;
            int mLastY;
            int mFirstX;
            int mFirstY;
            final int mTouchSlop = ViewConfiguration.get(getApplicationContext()).getScaledTouchSlop();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int totalDeltaX = mLastX - mFirstX;
                int totalDeltaY = mLastY - mFirstY;

                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();
                        mFirstX = mLastX;
                        mFirstY = mLastY;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!mIntercepted) {
                            v.performClick();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) event.getRawX() - mLastX;
                        int deltaY = (int) event.getRawY() - mLastY;
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();

                        if (Math.abs(totalDeltaX) >= mTouchSlop || Math.abs(totalDeltaY) >= mTouchSlop) {
                            if (event.getPointerCount() == 1) {
                                params.x += deltaX;
                                params.y += deltaY;
                                mIntercepted = true;
                                wm.updateViewLayout(mBinding.root, params);
                            }
                            else{
                                mIntercepted = false;
                            }
                        }else{
                            mIntercepted = false;
                        }
                        break;
                    default:
                        break;
                }
                return mIntercepted;
            }
        });
    }
}
