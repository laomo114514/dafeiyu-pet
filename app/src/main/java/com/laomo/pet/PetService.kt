package com.laomo.pet

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.sin

class PetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var petView: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var baseY = 600
    private var animator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val lines = listOf(
        "别戳我，痒（尾巴甩了一下）",
        "本鲸鱼在看着你呢",
        "今天也要好好的",
        "饿了…有白饭吗",
        "困了，别吵",
        "你刚才是不是在偷看我"
    )

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPet()
        startFloat()
    }

    private fun buildNotification(): Notification {
        val channelId = "pet_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "桌宠运行中",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            return Notification.Builder(this, channelId)
                .setContentTitle("大肥鱼桌宠")
                .setContentText("小人正在屏幕上陪你")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        } else {
            @Suppress("DEPRECATION")
            return Notification.Builder(this)
                .setContentTitle("大肥鱼桌宠")
                .setContentText("小人正在屏幕上陪你")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        }
    }

    private fun showPet() {
        val size = (140 * resources.displayMetrics.density).toInt()

        petView = ImageView(this).apply {
            setImageResource(R.drawable.pet)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = baseY
        }

        petView.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        animator?.cancel()
                        baseY = params.y
                        startX = params.x
                        startY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - downX).toInt()
                        params.y = startY + (event.rawY - downY).toInt()
                        windowManager.updateViewLayout(petView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - downX) < 12 && abs(event.rawY - downY) < 12) {
                            showBubble()
                        }
                        baseY = params.y
                        startFloat()
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(petView, params)
    }

    private fun startFloat() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                params.y = baseY + (sin(v) * 10).toInt()
                petView.rotation = sin(v) * 4f
                try {
                    windowManager.updateViewLayout(petView, params)
                } catch (e: Exception) {
                }
            }
            start()
        }
    }

    private fun showBubble() {
        val text = lines.random()

        if (bubbleView == null) {
            bubbleView = TextView(this).apply {
                setTextColor(Color.parseColor("#333333"))
                textSize = 14f
                setPadding(36, 20, 36, 20)
                background = GradientDrawable().apply {
                    cornerRadius = 32f
                    setColor(Color.parseColor("#F2FFFFFF"))
                }
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            bubbleParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                windowManager.addView(bubbleView, bubbleParams)
            } catch (e: Exception) {
            }
        }

        bubbleView?.text = text
        bubbleParams?.let { bp ->
            bp.x = params.x
            bp.y = (params.y - 110).coerceAtLeast(0)
            try {
                windowManager.updateViewLayout(bubbleView, bp)
            } catch (e: Exception) {
            }
        }
        bubbleView?.visibility = View.VISIBLE

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            bubbleView?.visibility = View.GONE
        }, 2500)
    }

    override fun onDestroy() {
        super.onDestroy()
        animator?.cancel()
        handler.removeCallbacksAndMessages(null)
        try {
            bubbleView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        if (this::petView.isInitialized) {
            try {
                windowManager.removeView(petView)
            } catch (e: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
