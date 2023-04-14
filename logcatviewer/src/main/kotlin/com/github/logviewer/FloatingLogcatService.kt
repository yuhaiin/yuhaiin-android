package com.github.logviewer

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.*
import android.view.View.OnTouchListener
import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.math.abs

class FloatingLogcatService : Service() {
    private lateinit var mBinding: LogcatViewerActivityLogcatBinding
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var readLogcat: ReadLogcat? = null
    private lateinit var context: Context

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setTheme(R.style.AppTheme)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (readLogcat?.running() == true) return super.onStartCommand(intent, flags, startId)
        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        context = inflater.context
        mBinding = LogcatViewerActivityLogcatBinding.inflate(inflater)
        LogcatActivity.initBinding(mBinding)
        readLogcat = ReadLogcat(
            context,
            mBinding,
            intent.getStringArrayListExtra(LogcatActivity.INTENT_EXCLUDE_LIST)!!,
        ).apply { start() }

        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
        ) mBinding.getRoot().setBackgroundColor(typedValue.data)
        initViews()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        windowManager.removeView(mBinding.root)
        readLogcat?.stop()
        super.onDestroy()
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() {
        val params = WindowManager.LayoutParams(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).also {
            it.alpha = .8f
            it.dimAmount = 0f
            it.gravity = Gravity.CENTER
            it.windowAnimations = android.R.style.Animation_Dialog

            val height: Int
            val width: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                windowManager.currentWindowMetrics.bounds.apply {
                    height = height()
                    width = width()
                }
            else
                Point().apply {
                    windowManager.defaultDisplay.getSize(this)
                    height = y
                    width = x
                }

            if (height > width) {
                it.width = (width * .7).toInt()
                it.height = (height * .5).toInt()
            } else {
                it.width = (width * .7).toInt()
                it.height = (height * .8).toInt()
            }
            windowManager.addView(mBinding.root, it)
        }

        mBinding.toolbar.apply {
            layoutParams.height = resources.getDimensionPixelSize(R.dimen.floating_toolbar_height)
            setNavigationOnClickListener { stopSelf() }
            setOnMenuItemClickListener(readLogcat)
            setOnTouchListener(object : OnTouchListener {
                var mIntercepted = false
                var mLastX = 0
                var mLastY = 0
                var mFirstX = 0
                var mFirstY = 0
                val mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val totalDeltaX = mLastX - mFirstX
                    val totalDeltaY = mLastY - mFirstY
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            mLastX = event.rawX.toInt()
                            mLastY = event.rawY.toInt()
                            mFirstX = mLastX
                            mFirstY = mLastY
                        }
                        MotionEvent.ACTION_UP -> if (!mIntercepted) v.performClick()
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX.toInt() - mLastX
                            val deltaY = event.rawY.toInt() - mLastY
                            mLastX = event.rawX.toInt()
                            mLastY = event.rawY.toInt()
                            if ((abs(totalDeltaX) >= mTouchSlop || abs(totalDeltaY) >= mTouchSlop) && event.pointerCount == 1) {
                                params.x += deltaX
                                params.y += deltaY
                                mIntercepted = true
                                windowManager.updateViewLayout(mBinding.root, params)
                            } else {
                                mIntercepted = false
                            }
                        }
                    }
                    return mIntercepted
                }
            })
        }
    }

    companion object {
        fun launch(context: Context, excludeList: ArrayList<String>?) {
            context.startService(
                Intent(context, FloatingLogcatService::class.java).putStringArrayListExtra(
                    LogcatActivity.INTENT_EXCLUDE_LIST,
                    excludeList
                )
            )
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, FloatingLogcatService::class.java))
    }
}