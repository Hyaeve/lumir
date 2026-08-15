package com.hyaeve.lumir

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("lumir", Context.MODE_PRIVATE) }
    private var webView: WebView? = null
    private var serverInput: EditText? = null
    private var usernameInput: EditText? = null
    private var passwordInput: EditText? = null
    private var loginButton: Button? = null
    private var loading: ProgressBar? = null

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
        root.postDelayed({ showLogin() }, 850)
    }

    private fun showLogin(message: String? = null) {
        webView?.destroy()
        webView = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30.dp)
            setBackgroundColor(background)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26.dp)
            background = rounded(Color.WHITE, 0xFFE1E8E1.toInt(), 18f)
            elevation = 8.dp.toFloat()
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.lumic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        panel.addView(icon, LinearLayout.LayoutParams(-1, 128.dp))
        val title = TextView(this).apply {
            text = "连接 Lumic"
            textSize = 26f
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        panel.addView(title, marginParams(-1, 42, 0, 8, 0))
        val subtitle = TextView(this).apply {
            text = "登录你的自托管拾光服务"
            textSize = 14f
            setTextColor(muted)
        }
        panel.addView(subtitle, marginParams(-1, 28, 0, 24, 0))

        serverInput = input("服务器地址", preferences.getString("server", "http://127.0.0.1:15500")!!, false)
        usernameInput = input("账号", preferences.getString("username", "") ?: "", false)
        passwordInput = input("密码", "", true)
        panel.addView(serverInput, fieldParams())
        panel.addView(usernameInput, fieldParams())
        panel.addView(passwordInput, fieldParams())

        val error = TextView(this).apply {
            text = message ?: ""
            textSize = 12f
            setTextColor(Color.rgb(183, 95, 88))
        }
        panel.addView(error, marginParams(-1, 20, 0, 12, 0))
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
        root.addView(panel, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun input(hint: String, value: String, password: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(muted)
        singleLine = true
        setPadding(14.dp, 0, 14.dp, 0)
        background = rounded(0xFFFBFCFA.toInt(), 0xFFDCE4DC.toInt(), 8f)
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, 50.dp).apply { bottomMargin = 12.dp }

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
        executor.execute {
            try {
                val connection = (URL("$server/api/login").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.use { it.write(JSONObject().put("username", username).put("password", password).toString().toByteArray()) }
                val response = connection.responseCode
                val cookies = connection.headerFields["Set-Cookie"].orEmpty()
                if (response !in 200..299 || cookies.isEmpty()) throw IllegalStateException("账号或密码不正确，或服务器无法连接")
                val cookieManager = CookieManager.getInstance()
                cookies.forEach { cookieManager.setCookie(server, it.substringBefore(';')) }
                cookieManager.flush()
                preferences.edit().putString("server", server).putString("username", username).apply()
                runOnUiThread { showWebApp(server) }
            } catch (exception: Exception) {
                runOnUiThread {
                    error.text = exception.message ?: "连接失败，请检查服务器地址"
                    loginButton?.isEnabled = true
                    loading?.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebApp(server: String) {
        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.toString().startsWith(server)
                }
            }
            loadUrl(server)
        }
        webView = view
        setContentView(view)
    }

    private fun normalizeServer(value: String): String? = try {
        val uri = URI(if (value.contains("://")) value else "http://$value")
        if (uri.host.isNullOrBlank() || (uri.scheme != "http" && uri.scheme != "https")) null
        else "${uri.scheme}://${uri.rawAuthority}".trimEnd('/')
    } catch (_: Exception) { null }

    override fun onBackPressed() {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        webView?.destroy()
        super.onDestroy()
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
