package com.laomo.pet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "大肥鱼桌宠"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val tip = TextView(this).apply {
            text = "① 先授权悬浮窗\n② 再启动桌宠"
            textSize = 16f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }

        val btnPermission = Button(this).apply {
            text = "① 授权悬浮窗"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        val btnStart = Button(this).apply {
            text = "② 启动桌宠"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    tip.text = "还没授权悬浮窗，先点上面那个按钮"
                } else {
                    val intent = Intent(this@MainActivity, PetService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
        }

        val btnStop = Button(this).apply {
            text = "停止桌宠"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PetService::class.java))
            }
        }

        layout.addView(title)
        layout.addView(tip)
        layout.addView(btnPermission)
        layout.addView(btnStart)
        layout.addView(btnStop)
        setContentView(layout)
    }
}
