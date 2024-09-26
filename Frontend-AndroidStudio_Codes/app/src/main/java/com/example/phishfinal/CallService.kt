package com.example.phishfinal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import okhttp3.*
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.IOException

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat

class CallService : Service() {

    private val CHANNEL_ID = "CallServiceChannel"
    private val DANGER_NOTIFICATION_ID = 1
    val REQUEST_PERMISSION_CODE = 0

    private lateinit var notificationManager: NotificationManager
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private var isCallActive = false

    private var lastProcessedNumber: String? = null
    private var lastProcessedTime: Long = 0

    private var phoneNumber_key = "Hello, world!"
    private var filename_key = "Hello, storage!"

    private lateinit var webSocketClient : WebSocketClient
    private val webSocketUrl = "wss://o4hctkhc90.execute-api.ap-south-1.amazonaws.com/production/"

    // Create an OkHttpClient instance
    private val client = OkHttpClient()

    // 알림 채널
    private val CHANNEL_ID_NOTIFICATION = "simple_notification_channel"



    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        webSocketClient = WebSocketClient(this)
        Log.d("CallService", "Service created")
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_CALL" -> {
                val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
                Log.d("CallService", "Starting to handle call from: $phoneNumber")
                isCallActive = true
                handleCall(phoneNumber)
            }
            "ANSWER_CALL" -> {
                Log.d("CallService", "Call is now active")
            }
            "END_CALL" -> {
                Log.d("CallService", "Received END_CALL action")
                handleEndCall()

            }
            else -> {
                val phoneNumber = intent?.getStringExtra("PHONE_NUMBER")
                Log.d("CallService", "Handling phone call from: $phoneNumber")
                handleCall(phoneNumber)
            }
        }
        return START_NOT_STICKY
    }

    // (STEP ONE) once phone is called
    private fun handleCall(phoneNumber: String?) {
        phoneNumber?.let { number ->
            Log.d("CallService", "Handling call for number: $number")
            val currentTime = System.currentTimeMillis()
            if (number != lastProcessedNumber || currentTime - lastProcessedTime > 5000) {
                Log.d("CallService", "Processing call for number: $number")
                scope.launch {
                    checkPhoneNumber(number)
                }
                lastProcessedNumber = number
                lastProcessedTime = currentTime
            } else {
                Log.d("CallService", "Skipping duplicate call for: $number")
            }
        }
    }

    // (STEP ONE) once phone call has ended
    private fun handleEndCall() {
        if (isCallActive) {
            Log.d("CallService", "Call is active, ending now")
            isCallActive = false
            Log.d("CallService", "Call ended, uploading file")
// 여기인 듯
        webSocketClient.connect(webSocketUrl)
        val latestFile = getLatestFileFromDownloads()
        if (latestFile != null) { // 파일이 발견되면
            Log.d("CallService", "$latestFile")
            Log.d("CallService", "latestFile.absolutePath")
            // Presigned URL 요청
            requestPresignedUrl(latestFile)
        } else {
            Log.d("CallService", "No files found in Download folder")
        }

        } else {
            Log.d("CallService", "No active call to end")
        }
// 여기서 웹소켓 연결할꺼임.
        webSocketClient.sendFileName(filename_key)
        Log.d("CallService", "WebSocket send success: " + filename_key)
    }

    // (STEP ONE) sent phone number through http request (api gateway) + check for http response
    private suspend fun checkPhoneNumber(phoneNumber: String) {
        try {
            // Get the current time in the desired format
            val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            Log.d("CallService", "CurrentTime is " + currentTime)

            // Construct the API Gateway URL with the query parameters
            val apiGatewayUrl = "https://b073hnq5pd.execute-api.ap-south-1.amazonaws.com/checknumber"
            val url = URL("$apiGatewayUrl?call=$phoneNumber&time=$currentTime")

            // Log the request details for debugging purposes
            Log.d("CheckPhoneNumber", "Checking phone number: $phoneNumber with time: $currentTime")

            phoneNumber_key = "$phoneNumber" + "-" + "$currentTime"
            Log.d("CallService", "$phoneNumber_key")

            // Build the HTTP GET request
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            // Execute the request and handle the response
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Log an error if the response is not successful
                    Log.e("CheckPhoneNumberError", "Unexpected response code: ${response.code}")
                } else {
                    // Read the response body as a string
                    val responseBody = response.body?.string()
                    Log.d("CheckPhoneNumber", "Response: $responseBody")

                    // Process the response body if it's not null
                    responseBody?.let {
                        try {
                            // Parse the response JSON
                            val jsonResponse = JSONObject(it)
                            val totalCalls = jsonResponse.optInt("totalCall", 0)
                            val numPhishing = jsonResponse.optInt("numPhishing", 0)

                            // Log the parsed values for debugging
                            Log.d("ProcessResponse", "Total Calls: $totalCalls, Num Phishing: $numPhishing")

                            // Show a notification with the parsed values
                            showDangerNotification(phoneNumber, totalCalls, numPhishing)
                        } catch (e: JSONException) {
                            // Log an error if there's an issue with JSON parsing
                            Log.e("ProcessResponseError", "Failed to process response", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log an error if there's an issue with the request or response handling
            Log.e("CheckPhoneNumberError", "Failed to check phone number", e)
        }
    }

    // (STEP ONE) set up the notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Call Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    // (STEP ONE) : Show notification
    private fun showDangerNotification(phoneNumber: String, totalCalls: Int, numPhishing: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = "Phish - 보이스피싱 분석 기록"
        val contentText = "$phoneNumber - $numPhishing / $totalCalls"
        val bigText = """
        $phoneNumber - 분석 기록
        보이스피싱 의심 횟수: $numPhishing 회
        전체 통화 횟수: $totalCalls 회
    """.trimIndent()

        // Build the notification with big text style
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                .bigText(bigText))
            .build()

        notificationManager.notify(DANGER_NOTIFICATION_ID, notification)
    }

   // (STEP TWO) : find latest file to upload
    private fun getLatestFileFromDownloads(): File? {
        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadsFolder.listFiles() ?: return null

        return files.maxByOrNull { it.lastModified() }
    }

    private fun requestPresignedUrl(file: File) {
        filename_key = phoneNumber_key + '.' + file.name.split('.')[1]
        val url = "https://fn950cxh6k.execute-api.ap-south-1.amazonaws.com/S3_URL?filename=${filename_key}"
        Log.d("CallService", url)
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("CallService", "Upload Fail")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    // JSON 파싱하여 URL 추출
                    val jsonObject = JSONObject(responseBody)
                    val presignedUrl = jsonObject.getString("url")

                    uploadFileToS3(presignedUrl, file)
                }
            }
        })
    }

    private fun uploadFileToS3(presignedUrl: String, file: File) {
        val requestBody = RequestBody.create(null, file)
        val request = Request.Builder()
            .url(presignedUrl)
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("CallService", "Upload Fail!")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("CallService", "Try Upload to S3")
            }
        })
    }
// 여기부터
}