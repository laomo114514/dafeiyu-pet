package com.laomo.pet

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.math.abs

class PetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var assetLoader: WebViewAssetLoader

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        prepareModel()

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler(
                "/model/",
                WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "model"))
            )
            .build()

        showPet()
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
                .setContentText("鲸鱼娘正在桌面上")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        } else {
            @Suppress("DEPRECATION")
            return Notification.Builder(this)
                .setContentTitle("大肥鱼桌宠")
                .setContentText("鲸鱼娘正在桌面上")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        }
    }

    private fun prepareModel() {
        val target = File(filesDir, "model")

        if (findModelJson(target) == null || !hasMotionFiles(target)) {
            target.deleteRecursively()
            target.mkdirs()
            extractZip("UTF-8", target)
            if (!hasMotionFiles(target)) {
                target.deleteRecursively()
                target.mkdirs()
                extractZip("GBK", target)
            }
        }

        val json = findModelJson(target)
        if (json != null) {
            val err = patchModelJson(json)
            val msg = if (err.isEmpty()) modelInfo(json) else err
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun extractZip(charsetName: String, target: File) {
        try {
            assets.open("model.zip").use { input ->
                ZipInputStream(input, Charset.forName(charsetName)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(target, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var len = zis.read(buffer)
                                while (len > 0) {
                                    fos.write(buffer, 0, len)
                                    len = zis.read(buffer)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun isAscii(s: String): Boolean {
        for (c in s) {
            if (c.code > 127) return false
        }
        return true
    }

    private fun patchModelJson(jsonFile: File): String {
        return try {
            val raw = jsonFile.readText().trimStart('\uFEFF')
            val root = JSONObject(raw)

            val motionsDir = File(jsonFile.parentFile, "motions")
            val motionFiles = motionsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".motion3.json") }
                ?.sortedBy { it.name } ?: emptyList()

            val idleList = JSONArray()
            val tapList = JSONArray()

            motionFiles.forEachIndexed { index, f ->
                var fileName = f.name
                if (!isAscii(fileName)) {
                    val newName = "m" + index + ".motion3.json"
                    if (f.renameTo(File(f.parentFile, newName))) fileName = newName
                }
                val item = JSONObject().put("File", "motions/" + fileName)
                if (fileName.startsWith("idle")) idleList.put(item) else tapList.put(item)
            }

            val motions = JSONObject()
            if (idleList.length() > 0) motions.put("Idle", idleList)
            if (tapList.length() > 0) motions.put("TapBody", tapList)
            if (motions.length() > 0) root.put("Motions", motions)

            val expFiles = jsonFile.parentFile?.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".exp3.json") }
                ?.sortedBy { it.name } ?: emptyList()

            val exps = JSONArray()
            expFiles.forEachIndexed { index, f ->
                var fileName = f.name
                val displayName = f.name.removeSuffix(".exp3.json")
                if (!isAscii(fileName)) {
                    val newName = "e" + index + ".exp3.json"
                    if (f.renameTo(File(f.parentFile, newName))) fileName = newName
                }
                exps.put(JSONObject().put("Name", displayName).put("File", fileName))
            }
            if (exps.length() > 0) root.put("Expressions", exps)

            jsonFile.writeText(root.toString())
            ""
        } catch (e: Exception) {
            "patch失败: " + (e.message ?: e.toString())
        }
    }

    private fun modelInfo(jsonFile: File): String {
        return try {
            val root = JSONObject(jsonFile.readText().trimStart('\uFEFF'))
            val m = root.optJSONObject("Motions")?.length() ?: 0
            val e = root.optJSONArray("Expressions")?.length() ?: 0
            "动作组=$m 表情=$e"
        } catch (e: Exception) {
            "读取json失败"
        }
    }

    private fun findModelJson(dir: File): File? {
        if (!dir.exists()) return null
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.isFile && f.name.endsWith(".model3.json")) return f
        }
        for (f in files) {
            if (f.isDirectory) {
                val found = findModelJson(f)
                if (found != null) return found
            }
        }
        return null
    }

    private fun hasMotionFiles(dir: File): Boolean {
        if (!dir.exists()) return false
        val files = dir.listFiles() ?: return false
        for (f in files) {
            if (f.isFile && f.name.endsWith(".motion3.json")) return true
            if (f.isDirectory && hasMotionFiles(f)) return true
        }
        return false
    }

    private fun modelRelPath(): String {
        val root = File(filesDir, "model")
        val json = findModelJson(root) ?: return ""
        return json.relativeTo(root).path.replace("\\", "/")
    }

    private fun bounce() {
        val startY = params.y
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 320
        anim.addUpdateListener { a ->
            val v = a.animatedValue as Float
            params.y = startY - (Math.sin(v * Math.PI) * 30).toInt()
            try {
                windowManager.updateViewLayout(webView, params)
            } catch (e: Exception) {
            }
        }
        anim.start()
    }

    private fun showPet() {
        val width = (170 * resources.displayMetrics.density).toInt()
        val height = (240 * resources.displayMetrics.density).toInt()

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            clearCache(true)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 600
        }

        val rel = modelRelPath()
        val url = "https://appassets.androidplatform.net/assets/live2d.html?model=" +
            Uri.encode(rel)
        webView.loadUrl(url)

        webView.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - downX).toInt()
                        params.y = startY + (event.rawY - downY).toInt()
                        windowManager.updateViewLayout(webView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - downX) < 12 && abs(event.rawY - downY) < 12) {
                            bounce()
                            webView.evaluateJavascript(
                                "window.tapPet && window.tapPet()",
                                null
                            )
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(webView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            webView.destroy()
        } catch (e: Exception) {
        }
        if (this::webView.isInitialized) {
            try {
                windowManager.removeView(webView)
            } catch (e: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
