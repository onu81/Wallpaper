package com.onu81.wallpaper

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

// 패키지가 변경되었으므로 R 클래스 임포트가 필요합니다.
import com.onu81.wallpaper.R

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        
        webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 자바스크립트 인터페이스 이름을 AndroidBridge로 설정
            addJavascriptInterface(WebAppInterface(), "AndroidBridge")
        }

        // 로컬 assets 폴더의 index.html 로드
        webView.loadUrl("file:///android_asset/index.html")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val pureBase64 = base64Str.substringAfter(",")
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "Calculation_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CalculationResults")
                }
                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val calculationDir = File(imagesDir, "CalculationResults")
                if (!calculationDir.exists()) calculationDir.mkdirs()
                val image = File(calculationDir, filename)
                fos = FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                runOnUiThread { Toast.makeText(this, "이미지가 갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun shareResult(bitmap: Bitmap, text: String) {
        try {
            val cachePath = File(cacheDir, "images")
            if (!cachePath.exists()) cachePath.mkdirs()
            val file = File(cachePath, "shared_result.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(this, "com.onu81.wallpaper.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "산출 결과 공유하기"))
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "공유 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun saveImage(base64Data: String) {
            val bitmap = base64ToBitmap(base64Data)
            if (bitmap != null) {
                runOnUiThread { saveBitmapToGallery(bitmap) }
            }
        }

        @JavascriptInterface
        fun shareResult(base64Data: String, text: String) {
            val bitmap = base64ToBitmap(base64Data)
            if (bitmap != null) {
                runOnUiThread { shareResult(bitmap, text) }
            }
        }
    }
}
