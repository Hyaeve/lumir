package com.hyaeve.lumir

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private companion object {
        const val TAG = "LumirAuth"
        const val STORAGE_PERMISSION_REQUEST = 1001
        const val EXIT_CONFIRMATION_WINDOW_MS = 2_000L
        const val DEFAULT_IMAGE_CACHE_MB = 256
        const val MIN_IMAGE_CACHE_MB = 128
        const val MAX_IMAGE_CACHE_MB = 5120
        const val IMAGE_CACHE_STEP_MB = 128
        const val IMAGE_CACHE_LIMIT_KEY = "image.cache.limit.mb"
        const val AVATAR_CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1000L
        const val LATEST_RELEASE_URL =
            "https://github.com/Hyaeve/lumir/releases/latest"
        const val RELEASE_TAG_PREFIX =
            "https://github.com/Hyaeve/lumir/releases/tag/v"
        const val RELEASE_DOWNLOAD_PREFIX =
            "https://github.com/Hyaeve/lumir/releases/download/v"
    }

    private data class ReleaseUpdate(
        val version: String,
        val downloadUrl: String,
        val fileName: String
    )

    private data class PendingDownload(
        val url: String,
        val fileName: String,
        val mimeType: String?,
        val userAgent: String?
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("lumir", Context.MODE_PRIVATE) }
    private val imageCache by lazy { ImageCache(this) }

    private val credentialKeyAlias = "lumir.credentials"
    private val savedPasswordKey = "password.encrypted"
    private val rememberPasswordKey = "password.remember"
    private var webView: WebView? = null
    private var pullRefreshEnabled = false
    private var serverInput: EditText? = null
    private var usernameInput: EditText? = null
    private var passwordInput: EditText? = null
    private var rememberPasswordInput: CheckBox? = null
    private var loginButton: Button? = null
    private var loading: ProgressBar? = null
    private var leavingWebApp = false
    private var showingCacheSettings = false
    private var pendingDownload: PendingDownload? = null
    private var lastExitBackPressedAt = 0L

    private val green = Color.rgb(113, 155, 127)
    private val ink = Color.rgb(52, 67, 63)
    private val muted = Color.rgb(135, 147, 142)
    private val background = Color.rgb(245, 247, 242)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        showSplash()
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }
        val image = ImageView(this).apply {
            setImageResource(com.hyaeve.lumir.R.drawable.lumic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        root.addView(image, LinearLayout.LayoutParams(210.dp, 210.dp))
        val name = TextView(this).apply {
            text = "Lumir"
            textSize = 25f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(name, LinearLayout.LayoutParams(-1, 48.dp))
        setContentView(root)
        root.postDelayed({ tryAutoLogin() }, 850)
    }

    private fun showLogin(message: String? = null) {
        pullRefreshEnabled = false
        showingCacheSettings = false
        webView?.apply {
            stopLoading()
            removeJavascriptInterface("Lumir")
            destroy()
        }
        webView = null
        leavingWebApp = false
        val savedPassword = decryptPassword().orEmpty()
        val root = FrameLayout(this).apply {
            setPadding(30.dp)
            setBackgroundColor(this@MainActivity.background)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp)
            background = rounded(Color.WHITE, 0xFFE1E8E1.toInt(), 16f)
            elevation = 8.dp.toFloat()
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 4.dp)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.lumic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        brandRow.addView(icon, LinearLayout.LayoutParams(72.dp, 72.dp))
        brandRow.addView(brandTitle(), LinearLayout.LayoutParams(0, 58.dp).apply {
            weight = 1f
            leftMargin = 12.dp
        })
        panel.addView(brandRow, LinearLayout.LayoutParams(-1, 76.dp))

        serverInput = input("服务器地址", preferences.getString("server", "http://127.0.0.1:15500")!!, false)
        usernameInput = input("账号", preferences.getString("username", "") ?: "", false)
        passwordInput = input("密码", savedPassword, true)
        panel.addView(serverInput, fieldParams())
        panel.addView(usernameInput, fieldParams())
        panel.addView(passwordRow(), fieldParams())

        rememberPasswordInput = CheckBox(this).apply {
            text = "记住密码"
            textSize = 14f
            setTextColor(ink)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            isChecked = preferences.getBoolean(rememberPasswordKey, savedPassword.isNotEmpty())
            setOnCheckedChangeListener { _, checked ->
                if (!checked) preferences.edit().remove(savedPasswordKey).apply()
            }
        }
        panel.addView(rememberPasswordInput, marginParams(-1, 32, 0, 0, 0))

        val error = TextView(this).apply {
            text = message ?: ""
            textSize = 12f
            setTextColor(Color.rgb(183, 95, 88))
        }
        panel.addView(error, marginParams(-1, 18, 0, 8, 0))
        loginButton = Button(this).apply {
            text = "登录 Lumir"
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            background = rounded(green, Color.TRANSPARENT, 8f)
            setOnClickListener { login(error) }
        }
        panel.addView(loginButton, LinearLayout.LayoutParams(-1, 48.dp))
        loading = ProgressBar(this).apply { visibility = View.GONE }
        panel.addView(loading, marginParams(-1, 18, 0, 0, 0).apply { gravity = Gravity.CENTER })
        root.addView(
            panel,
            FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        )
        root.addView(
            TextView(this).apply {
                text = "关于 Lumir · 版本与更新"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(green)
                setPadding(24.dp, 12.dp, 24.dp, 12.dp)
                background = rounded(0xFFF0F5F0.toInt(), 0xFFDCE9DD.toInt(), 16f)
                isClickable = true
                isFocusable = true
                setOnClickListener { showAboutDialog() }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 48.dp).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 8.dp
            }
        )
        root.addView(
            TextView(this).apply {
                text = "⚙"
                textSize = 23f
                gravity = Gravity.CENTER
                contentDescription = "图片缓存设置"
                setTextColor(green)
                background = rounded(0xFFF0F5F0.toInt(), 0xFFDCE9DD.toInt(), 16f)
                isClickable = true
                isFocusable = true
                setOnClickListener { showCacheSettings() }
            },
            FrameLayout.LayoutParams(48.dp, 48.dp).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = 12.dp
                bottomMargin = 8.dp
            }
        )
        setContentView(root)
    }

    private fun brandTitle(): FrameLayout = FrameLayout(this).apply {
        val title = TextView(this@MainActivity).apply {
            text = "Lumic · 拾光"
            textSize = 23f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        addView(title, FrameLayout.LayoutParams(-1, -1))

        val stars = listOf(
            Triple(0xFF8DC8F7.toInt(), 104, 1),
            Triple(0xFFB69AEA.toInt(), 126, 41),
            Triple(0xFFF2C96D.toInt(), 150, 5),
            Triple(0xFFAEDCF4.toInt(), 166, 37)
        )
        stars.forEach { (color, left, top) ->
            addView(TextView(this@MainActivity).apply {
                text = "✦"
                textSize = 8f
                gravity = Gravity.CENTER
                setTextColor(color)
                alpha = 0.9f
            }, FrameLayout.LayoutParams(14.dp, 14.dp).apply {
                leftMargin = left.dp
                topMargin = top.dp
            })
        }
    }

    private fun showAboutDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20.dp, 4.dp, 20.dp, 0)
        }
        content.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.lumic)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            LinearLayout.LayoutParams(76.dp, 76.dp)
        )
        content.addView(TextView(this).apply {
            text = "Lumir"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, 30.dp))
        content.addView(TextView(this).apply {
            text = "版本 ${BuildConfig.VERSION_NAME}"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, 26.dp))
        val status = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, 26.dp))

        val dialog = AlertDialog.Builder(this)
            .setTitle("关于")
            .setView(content)
            .setNegativeButton("关闭", null)
            .setPositiveButton("检查更新", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(rounded(Color.WHITE, 0xFFE1E8E1.toInt(), 18f))
            dialog.window?.setLayout(320.dp, ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.findViewById<TextView>(android.R.id.alertTitle)?.setTextColor(ink)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(green)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                checkForUpdates(dialog, status)
            }
        }
        dialog.show()
    }

    private fun checkForUpdates(dialog: AlertDialog, status: TextView) {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        status.text = "正在检查更新..."
        executor.execute {
            val result = runCatching { fetchLatestRelease() }
            runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                button.isEnabled = true
                result.onSuccess { update ->
                    if (isNewerVersion(update.version, BuildConfig.VERSION_NAME)) {
                        status.text = "发现新版本 ${update.version}"
                        showUpdateDialog(update)
                    } else {
                        status.text = "当前已是最新版本"
                    }
                }.onFailure {
                    Log.w(TAG, "Update check failed: ${it.message}")
                    status.text = "检查失败，请稍后重试"
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseUpdate {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Lumir/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
            }
            val releaseUrl = connection.url.toString().trimEnd('/')
            if (!releaseUrl.startsWith(RELEASE_TAG_PREFIX)) {
                throw IllegalStateException("Unexpected GitHub release URL")
            }
            val version = releaseUrl.removePrefix(RELEASE_TAG_PREFIX)
            if (!version.matches(Regex("[0-9]+(?:\\.[0-9]+)*"))) {
                throw IllegalStateException("Invalid release version")
            }
            val fileName = "Lumir.v$version.apk"
            return ReleaseUpdate(
                version = version,
                downloadUrl = "$RELEASE_DOWNLOAD_PREFIX$version/$fileName",
                fileName = fileName
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val difference = candidateParts.getOrElse(index) { 0 } -
                currentParts.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun showUpdateDialog(update: ReleaseUpdate) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("发现新版本 ${update.version}")
            .setMessage("下载完成后，点击系统通知即可安装更新。")
            .setNegativeButton("暂不更新", null)
            .setPositiveButton("下载更新") { _, _ ->
                requestDownload(
                    PendingDownload(
                        url = update.downloadUrl,
                        fileName = update.fileName,
                        mimeType = "application/vnd.android.package-archive",
                        userAgent = "Lumir/${BuildConfig.VERSION_NAME}"
                    )
                )
            }
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(rounded(Color.WHITE, 0xFFE1E8E1.toInt(), 20f))
            dialog.window?.setLayout(332.dp, ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                setTextColor(muted)
                isAllCaps = false
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(green)
                isAllCaps = false
            }
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                setTextColor(ink)
                setPadding(24.dp, 4.dp, 24.dp, 8.dp)
            }
        }
        dialog.show()
    }

    private fun input(hint: String, value: String, password: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(muted)
        maxLines = 1
        setPadding(14.dp, 0, 14.dp, 0)
        background = rounded(0xFFFBFCFA.toInt(), 0xFFDCE4DC.toInt(), 8f)
        if (password) {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
    }

    private fun passwordRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(0xFFFBFCFA.toInt(), 0xFFDCE4DC.toInt(), 8f)
        val field = requireNotNull(passwordInput)
        field.background = null
        addView(field, LinearLayout.LayoutParams(0, -1, 1f))
        addView(ImageButton(this@MainActivity).apply {
            setImageResource(R.drawable.ic_password_visibility_off)
            contentDescription = "显示密码"
            setColorFilter(muted)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(13.dp)
            setOnClickListener {
                val field = passwordInput ?: return@setOnClickListener
                val hidden = field.transformationMethod is PasswordTransformationMethod
                field.transformationMethod = if (hidden) {
                    HideReturnsTransformationMethod.getInstance()
                } else {
                    PasswordTransformationMethod.getInstance()
                }
                setImageResource(
                    if (hidden) R.drawable.ic_password_visibility
                    else R.drawable.ic_password_visibility_off
                )
                contentDescription = if (hidden) "隐藏密码" else "显示密码"
                setColorFilter(if (hidden) green else muted)
                field.setSelection(field.text.length)
            }
        }, LinearLayout.LayoutParams(50.dp, -1))
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, 46.dp).apply { bottomMargin = 10.dp }

    private fun login(error: TextView) {
        val rawServer = serverInput?.text?.toString()?.trim().orEmpty().trimEnd('/')
        val username = usernameInput?.text?.toString()?.trim().orEmpty()
        val password = passwordInput?.text?.toString().orEmpty()
        val server = normalizeServer(rawServer)
        if (server == null) { error.text = "请输入有效的服务器地址"; return }
        if (username.isBlank() || password.isBlank()) { error.text = "请输入账号和密码"; return }
        loginButton?.isEnabled = false
        loading?.visibility = View.VISIBLE
        error.text = ""
        val rememberPassword = rememberPasswordInput?.isChecked == true
        executor.execute {
            try {
                authenticate(server, username, password)
                saveCredentials(server, username, password, rememberPassword)
                Log.i(TAG, "Interactive login succeeded; rememberPassword=$rememberPassword")
                runOnUiThread { showWebApp(server) }
            } catch (exception: Exception) {
                Log.w(TAG, "Interactive login failed: ${exception.javaClass.simpleName}: ${exception.message}")
                runOnUiThread {
                    error.text = exception.message ?: "连接失败，请检查服务器地址"
                    loginButton?.isEnabled = true
                    loading?.visibility = View.GONE
                }
            }
        }
    }

    private fun tryAutoLogin() {
        val server = normalizeServer(preferences.getString("server", "").orEmpty())
        val username = preferences.getString("username", "").orEmpty()
        val password = decryptPassword()
        if (server == null || username.isBlank() || password.isNullOrBlank()) {
            Log.i(TAG, "Auto-login skipped: encrypted credentials are incomplete")
            showLogin()
            return
        }

        Log.i(TAG, "Auto-login started for saved server and username")
        executor.execute {
            try {
                authenticate(server, username, password)
                Log.i(TAG, "Auto-login succeeded")
                runOnUiThread { showWebApp(server) }
            } catch (exception: Exception) {
                Log.w(TAG, "Auto-login failed: ${exception.javaClass.simpleName}: ${exception.message}")
                runOnUiThread {
                    showLogin("自动登录失败，请检查服务器连接或重新登录")
                }
            }
        }
    }

    private fun authenticate(server: String, username: String, password: String) {
        val connection = (URL("$server/api/login").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use {
                it.write(
                    JSONObject().put("username", username).put("password", password)
                        .toString().toByteArray(StandardCharsets.UTF_8)
                )
            }
            val response = connection.responseCode
            val cookies = connection.headerFields["Set-Cookie"].orEmpty()
            Log.i(TAG, "Authentication response: status=$response, setCookie=${cookies.isNotEmpty()}")
            if (response !in 200..299 || cookies.isEmpty()) {
                throw IllegalStateException("账号或密码不正确，或服务器无法连接")
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookies.forEach { cookieManager.setCookie(server, it.substringBefore(';')) }
            cookieManager.flush()
        } finally {
            connection.disconnect()
        }
    }

    private fun saveCredentials(server: String, username: String, password: String, rememberPassword: Boolean) {
        preferences.edit().apply {
            putString("server", server)
            putString("username", username)
            putBoolean(rememberPasswordKey, rememberPassword)
            if (rememberPassword) putString(savedPasswordKey, encryptPassword(password))
            else remove(savedPasswordKey)
        }.apply()
    }

    private fun encryptPassword(password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, credentialKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        return "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
    }

    private fun decryptPassword(): String? {
        val stored = preferences.getString(savedPasswordKey, null) ?: return null
        return try {
            val parts = stored.split(":", limit = 2)
            if (parts.size != 2) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                credentialKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT))
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT)), StandardCharsets.UTF_8)
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to decrypt saved password; credentials will be requested again")
            preferences.edit().remove(savedPasswordKey).apply()
            null
        }
    }

    private fun credentialKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(credentialKeyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                credentialKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun showWebApp(server: String) {
        leavingWebApp = false
        lastExitBackPressedAt = 0L
        val view = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.flush()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(SessionBridge(server), "Lumir")
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (url.startsWith("blob:")) {
                    Toast.makeText(this@MainActivity, "正在准备图片，请稍候重试", Toast.LENGTH_SHORT).show()
                    return@setDownloadListener
                }
                requestDownload(
                    PendingDownload(
                        url = url,
                        fileName = URLUtil.guessFileName(url, contentDisposition, mimeType),
                        mimeType = mimeType,
                        userAgent = userAgent
                    )
                )
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (!request.isForMainFrame && imageCache.shouldCache(url, server)) {
                        return imageCache.load(url)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (isTrustedServerUrl(url, server)) return false
                    openExternalUrl(url)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (isTrustedServerUrl(url, server)) {
                        installSessionObserver(view)
                    }
                }
            }
        }
        installPullRefreshObserver(view)
        pullRefreshEnabled = false
        webView = view
        setContentView(view)
        view.loadUrl(server)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installPullRefreshObserver(view: WebView) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val refreshDistance = 72.dp.toFloat()
        var tracking = false
        var verticalGesture = false
        var downX = 0f
        var downY = 0f
        var maximumPull = 0f

        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracking = pullRefreshEnabled && !touchedView.canScrollVertically(-1)
                    verticalGesture = false
                    downX = event.x
                    downY = event.y
                    maximumPull = 0f
                }

                MotionEvent.ACTION_POINTER_DOWN -> tracking = false

                MotionEvent.ACTION_MOVE -> if (tracking) {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    if (!verticalGesture && maxOf(abs(deltaX), abs(deltaY)) > touchSlop) {
                        if (abs(deltaX) >= abs(deltaY)) {
                            tracking = false
                        } else {
                            verticalGesture = true
                        }
                    }
                    if (deltaY < 0 || touchedView.canScrollVertically(-1)) tracking = false
                    if (tracking && verticalGesture) maximumPull = maxOf(maximumPull, deltaY)
                }

                MotionEvent.ACTION_UP -> {
                    val shouldRefresh = tracking && verticalGesture &&
                        maximumPull >= refreshDistance && pullRefreshEnabled
                    tracking = false
                    if (shouldRefresh) view.post { view.reload() }
                }

                MotionEvent.ACTION_CANCEL -> tracking = false
            }
            false
        }
    }

    private fun isTrustedServerUrl(url: String, server: String): Boolean = try {
        val candidate = URI(url)
        val trusted = URI(server)
        candidate.scheme == trusted.scheme && candidate.rawAuthority == trusted.rawAuthority
    } catch (_: Exception) {
        false
    }

    private fun installSessionObserver(view: WebView) {
        view.evaluateJavascript(
            """
            (() => {
              if (window.__lumirSessionObserver) return;
              window.__lumirSessionObserver = true;
              const originalFetch = window.fetch.bind(window);
              window.fetch = async (...args) => {
                const response = await originalFetch(...args);
                try {
                  const url = new URL(typeof args[0] === 'string' ? args[0] : args[0].url, location.href);
                  if (url.origin === location.origin && url.pathname === '/api/logout' && response.ok) {
                    window.Lumir.onSignedOut();
                  } else if (url.origin === location.origin && url.pathname === '/api/session' && response.ok) {
                    const session = await response.clone().json();
                    if (!session.authenticated) window.Lumir.onSignedOut();
                  }
                } catch (_) {}
                return response;
              };
              const originalOpen = window.open.bind(window);
              window.open = (url, target, features) => {
                try {
                  const external = new URL(String(url || ''), location.href);
                  if (external.origin !== location.origin && /^https?:$/.test(external.protocol)) {
                    window.Lumir.copyLink(external.href);
                    return null;
                  }
                } catch (_) {}
                return originalOpen(url, target, features);
              };
            })();
            """.trimIndent(),
            null
        )
    }

    private inner class SessionBridge(private val server: String) {
        @JavascriptInterface
        fun onSignedOut() {
            runOnUiThread { leaveWebApp() }
        }

        @JavascriptInterface
        fun saveFile(url: String, fileName: String) {
            if (!isTrustedServerUrl(url, server)) return
            runOnUiThread {
                requestDownload(
                    PendingDownload(
                        url = url,
                        fileName = safeFileName(fileName),
                        mimeType = java.net.URLConnection.guessContentTypeFromName(fileName),
                        userAgent = webView?.settings?.userAgentString
                    )
                )
            }
        }

        @JavascriptInterface
        fun copyLink(url: String) {
            runOnUiThread { copyLinkToClipboard(url) }
        }

        @JavascriptInterface
        fun setPullRefreshEnabled(enabled: Boolean) {
            runOnUiThread {
                pullRefreshEnabled = enabled
            }
        }
    }

    private fun requestDownload(download: PendingDownload) {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = download
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST
            )
            return
        }
        enqueueDownload(download)
    }

    private fun enqueueDownload(download: PendingDownload) {
        try {
            val request = DownloadManager.Request(Uri.parse(download.url)).apply {
                setTitle(download.fileName)
                setDescription("正在保存 Lumic 文件")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.fileName)
                download.mimeType?.takeIf { it.isNotBlank() }?.let(::setMimeType)
                download.userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(download.url)?.takeIf { it.isNotBlank() }?.let {
                    addRequestHeader("Cookie", it)
                }
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "已加入下载任务：${download.fileName}", Toast.LENGTH_LONG).show()
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to enqueue download: ${exception.message}")
            Toast.makeText(this, "保存失败，请检查下载服务", Toast.LENGTH_SHORT).show()
        }
    }

    private fun safeFileName(value: String): String {
        val cleaned = value.substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.takeIf { it.isNotBlank() }?.take(180) ?: "Lumic-${System.currentTimeMillis()}.jpg"
    }

    private fun openExternalUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "未找到可打开链接的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLinkToClipboard(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("原动态链接", url))
        Toast.makeText(this, "已复制原动态链接", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != STORAGE_PERMISSION_REQUEST) return
        val download = pendingDownload
        pendingDownload = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && download != null) {
            enqueueDownload(download)
        } else {
            Toast.makeText(this, "需要存储权限才能保存到下载目录", Toast.LENGTH_SHORT).show()
        }
    }

    private fun leaveWebApp() {
        if (leavingWebApp) return
        leavingWebApp = true
        pullRefreshEnabled = false
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            runOnUiThread { showLogin("已退出登录") }
        }
    }

    private fun normalizeServer(value: String): String? = try {
        val uri = URI(if (value.contains("://")) value else "http://$value")
        if (uri.host.isNullOrBlank() || (uri.scheme != "http" && uri.scheme != "https")) null
        else "${uri.scheme}://${uri.rawAuthority}".trimEnd('/')
    } catch (_: Exception) { null }

    override fun onBackPressed() {
        if (showingCacheSettings) {
            showLogin()
            return
        }
        val view = webView
        if (view?.canGoBack() == true) {
            view.goBack()
            return
        }
        if (view == null) {
            super.onBackPressed()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (lastExitBackPressedAt > 0L && now - lastExitBackPressedAt <= EXIT_CONFIRMATION_WINDOW_MS) {
            finishAffinity()
            return
        }

        lastExitBackPressedAt = now
        Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        pullRefreshEnabled = false
        webView?.removeJavascriptInterface("Lumir")
        webView?.destroy()
        super.onDestroy()
    }

    private fun showCacheSettings() {
        showingCacheSettings = true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp)
            setBackgroundColor(this@MainActivity.background)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            contentDescription = "返回登录页"
            setTextColor(ink)
            background = rounded(0xFFF0F5F0.toInt(), 0xFFDCE9DD.toInt(), 16f)
            isClickable = true
            isFocusable = true
            setOnClickListener { showLogin() }
        }, LinearLayout.LayoutParams(48.dp, 48.dp))
        header.addView(TextView(this).apply {
            text = "图片缓存设置"
            textSize = 21f
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, 48.dp, 1f).apply { leftMargin = 16.dp })
        root.addView(header, LinearLayout.LayoutParams(-1, 56.dp))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp)
            background = rounded(Color.WHITE, 0xFFE1E8E1.toInt(), 18f)
            elevation = 5.dp.toFloat()
        }
        val value = TextView(this).apply {
            textSize = 18f
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val currentLimit = preferences.getInt(IMAGE_CACHE_LIMIT_KEY, DEFAULT_IMAGE_CACHE_MB)
            .coerceIn(MIN_IMAGE_CACHE_MB, MAX_IMAGE_CACHE_MB)
        val bar = SeekBar(this).apply {
            max = (MAX_IMAGE_CACHE_MB - MIN_IMAGE_CACHE_MB) / IMAGE_CACHE_STEP_MB
            progress = (currentLimit - MIN_IMAGE_CACHE_MB) / IMAGE_CACHE_STEP_MB
            progressTintList = android.content.res.ColorStateList.valueOf(green)
            thumbTintList = android.content.res.ColorStateList.valueOf(green)
        }
        fun selectedMb() = MIN_IMAGE_CACHE_MB + bar.progress * IMAGE_CACHE_STEP_MB
        fun updateValue() { value.text = "图片缓存（MB）  ${selectedMb()}.0" }
        updateValue()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateValue()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                preferences.edit().putInt(IMAGE_CACHE_LIMIT_KEY, selectedMb()).apply()
                executor.execute { imageCache.trimToLimit() }
            }
        })
        card.addView(value, LinearLayout.LayoutParams(-1, 42.dp))
        card.addView(bar, LinearLayout.LayoutParams(-1, 56.dp))
        card.addView(TextView(this).apply {
            text = "可设置范围：128 MB 至 5120 MB。超过上限后按最早缓存时间自动清理。"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 4.dp, 0, 18.dp)
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        val usage = TextView(this).apply {
            text = "已缓存：正在统计…"
            textSize = 15f
            setTextColor(ink)
            setPadding(0, 12.dp, 0, 18.dp)
        }
        card.addView(usage, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(Button(this).apply {
            text = "立即清理"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(green, Color.TRANSPARENT, 10f)
            setOnClickListener {
                isEnabled = false
                executor.execute {
                    imageCache.clear()
                    runOnUiThread {
                        usage.text = "已缓存：0 B"
                        isEnabled = true
                        Toast.makeText(this@MainActivity, "图片缓存已清理", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, 48.dp))
        root.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 20.dp
        })
        setContentView(root)
        executor.execute {
            val size = imageCache.sizeBytes()
            runOnUiThread { if (showingCacheSettings) usage.text = "已缓存：${formatBytes(size)}" }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private inner class ImageCache(context: Context) {
        private val directory = File(context.filesDir, "image-cache").apply { mkdirs() }
        private val lock = Any()

        fun shouldCache(url: String, server: String): Boolean {
            if (!isTrustedServerUrl(url, server)) return false
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val path = uri.path.orEmpty().lowercase()
            return path.startsWith("/preview/") ||
                (path.startsWith("/flow/") && path.substringAfterLast('/').startsWith("avatar."))
        }

        fun load(url: String): WebResourceResponse? = synchronized(lock) {
            val file = File(directory, hash(url))
            val mime = mimeForUrl(url)
            val cacheExpired = file.isFile && System.currentTimeMillis() - file.lastModified() > AVATAR_CACHE_MAX_AGE_MS
            if (cacheExpired) file.delete()
            if (file.isFile && file.length() > 0L) {
                return WebResourceResponse(mime, null, file.inputStream())
            }
            download(url)?.let { bytes ->
                runCatching { writeAtomically(file, bytes) }
                trimToLimitLocked()
                return WebResourceResponse(mime, null, ByteArrayInputStream(bytes))
            }
            null
        }

        fun trimToLimit() = synchronized(lock) { trimToLimitLocked() }

        fun sizeBytes(): Long = synchronized(lock) {
            directory.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        }

        fun clear() = synchronized(lock) { directory.listFiles()?.forEach { it.delete() } }

        private fun download(url: String): ByteArray? {
            val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
            return try {
                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "Lumir/${BuildConfig.VERSION_NAME}")
                CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                    connection.setRequestProperty("Cookie", it)
                }
                if (connection.responseCode !in 200..299) return null
                val mime = connection.contentType?.substringBefore(';')?.lowercase().orEmpty()
                if (!mime.startsWith("image/")) return null
                connection.inputStream.use { it.readBytes() }
            } catch (exception: Exception) {
                Log.w(TAG, "Image cache download failed: ${exception.message}")
                null
            } finally {
                connection.disconnect()
            }
        }

        private fun writeAtomically(target: File, bytes: ByteArray) {
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.outputStream().use { it.write(bytes) }
            if (!temporary.renameTo(target)) temporary.delete()
        }

        private fun trimToLimitLocked() {
            val limit = preferences.getInt(IMAGE_CACHE_LIMIT_KEY, DEFAULT_IMAGE_CACHE_MB)
                .coerceIn(MIN_IMAGE_CACHE_MB, MAX_IMAGE_CACHE_MB).toLong() * 1024L * 1024L
            val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
            var total = files.sumOf { it.length() }
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= limit) return@forEach
                val length = file.length()
                if (file.delete()) total -= length
            }
        }

        private fun mimeForUrl(url: String): String {
            return when (runCatching { URI(url).path.substringAfterLast('.').lowercase() }.getOrDefault("")) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "avif" -> "image/avif"
                else -> "image/jpeg"
            }
        }

        private fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(fill)
        if (stroke != Color.TRANSPARENT) setStroke(1.dp, stroke)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun marginParams(width: Int, height: Int, left: Int, top: Int, right: Int) = LinearLayout.LayoutParams(width, height.dp).apply {
        setMargins(left.dp, top.dp, right.dp, 0)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
