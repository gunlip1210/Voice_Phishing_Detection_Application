package com.example.phishfinal

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.phishfinal.firstFragment

class MainActivity : AppCompatActivity() {

    // 권한 요청을 위한 ActivityResultLauncher 정의
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 권한 요청을 위한 ActivityResultLauncher 초기화
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermissions = permissions.filter { !it.value }.keys
            if (deniedPermissions.isNotEmpty()) {
                Log.e("MainActivity", "권한 요청이 거부되었습니다: $deniedPermissions")
            }
        }

        // 필요한 권한 요청
        requestPermissions()

        val serviceIntent = Intent(this, CallService::class.java)
        startService(serviceIntent)

        setupButtonClick(R.id.button1, firstFragment::class.java)
        setupButtonClick(R.id.button2, secondFragment::class.java)
    }

    private fun setupButtonClick(buttonId: Int, activityClass: Class<*>) {
        findViewById<View>(buttonId).setOnClickListener {
            startActivity(Intent(this, activityClass))
        }
    }

    // 권한 요청 함수
    private fun requestPermissions() {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        // 이미 권한이 부여되었는지 확인
        val deniedPermissions = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            // 권한 요청
            permissionLauncher.launch(deniedPermissions.toTypedArray())
        }
    }
}
