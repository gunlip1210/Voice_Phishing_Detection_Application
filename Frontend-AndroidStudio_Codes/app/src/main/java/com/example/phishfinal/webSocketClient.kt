package com.example.phishfinal

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService

class WebSocketClient(private val context: Context) { // Context를 전달받을 수 있도록 수정

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep the connection alive indefinitely
        .build()

    private val CHANNEL_ID = "websocket_notification_channel" // 알림 채널 ID
    private lateinit var notificationManager: NotificationManager
    private lateinit var sharedPreferences : SharedPreferences

    init {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel() // 객체 초기화 시 채널 생성
        sharedPreferences = context.getSharedPreferences("analysis", Context.MODE_PRIVATE)
    }

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Log.d("WebSocket", "WebSocket Opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d("WebSocket", "Received message: $text")
                handleIncomingMessage(text)

                val editor = sharedPreferences.edit()
                editor.putString("last", text)
                editor.apply()

                // 알림 보내기
                showNotification("New WebSocket Message", text)

                val temp = sharedPreferences.getString("last", "default_value")
                Log.d("WebSocket_sharedPreferences", "$temp")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.d("WebSocket", "WebSocket error: ${t.message}")
            }
        })
    }

    fun sendFileName(fileName: String) {
        webSocket.send(fileName)
    }

    private fun handleIncomingMessage(message: String) {
        try {
            // Parse JSON message
            val jsonObject = JSONObject(message)
            val percent = jsonObject.optDouble("percent", 0.0)
            val reasons = jsonObject.optJSONArray("reasons")?.let {
                List(it.length()) { i -> it.getString(i) }
            } ?: emptyList()

            // Process data (e.g., update UI, notify user, etc.)
            Log.d("WebSocket", "Percent: $percent, Reasons: $reasons")
            // Example: Show notification or update UI
        } catch (e: Exception) {
            Log.e("WebSocket", "Error processing message: ${e.message}")
        }
    }

    // 알림 채널을 생성하는 함수 (Android 8.0 이상에서 필요)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WebSocket Notification"
            val descriptionText = "Notifications for WebSocket messages."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 알림을 생성하고 표시하는 함수
    private fun showNotification(title: String, message: String) {
        val jsonObject = JSONObject(message)
        val phone_number = jsonObject.optString("number")
        val percent = jsonObject.optDouble("percent", 0.0)
        val reasons = jsonObject.optJSONArray("reasons")?.let {
            List(it.length()) { i -> it.getString(i) }
        } ?: emptyList()
        val reason = reasons.joinToString("\n")

        val title = "Phish - 보이스피싱 분석 결과"
        val contentText = "$phone_number - $percent%"
        val bigText = """
    $phone_number - $percent%
$reason
        """.trimIndent()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // 알림 아이콘 설정
            .setContentTitle(title) // 알림 제목 설정
            .setContentText(contentText) // 알림 내용 설정
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText))// 알림 우선순위 설정

        with(NotificationManagerCompat.from(context)) {
            notificationManager.notify(1, builder.build()) // 알림 표시
        }
    }
}
