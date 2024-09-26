package com.example.phishfinal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.phishfinal.MainActivity
import com.example.phishfinal.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URL

class firstFragment : AppCompatActivity() {

    private val REQUEST_CODE = 100
    private var fileUri: Uri? = null
    private lateinit var btnUploadFile: ImageButton // ImageButton으로 선언
    private lateinit var btnGoBack: ImageButton // ImageButton으로 선언
    private lateinit var txtSelectedFile: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_first)

        // Initialize views
        btnUploadFile = findViewById(R.id.uploadButton) // ImageButton for file selection and upload
        btnGoBack = findViewById(R.id.buttonGoBack1) // ImageButton for going back
        txtSelectedFile = findViewById(R.id.textViewFragment1)

        btnUploadFile.setOnClickListener {
            Log.d("firstFragment", "Upload button clicked.")
            fileUri?.let {
                val fileName = getFileName(it)
                if (fileName.isNotEmpty()) {
                    uploadFile(it, fileName)
                } else {
                    txtSelectedFile.text = "Failed to retrieve file name."
                }
            } ?: run {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                startActivityForResult(intent, REQUEST_CODE)
            }
        }

        btnGoBack.setOnClickListener {
            Log.d("firstFragment", "Go back button clicked.")
            val intentBack = Intent(this, MainActivity::class.java)
            startActivity(intentBack)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                fileUri = uri
                val fileName = getFileName(uri)
                txtSelectedFile.text = fileName
                btnUploadFile.isEnabled = fileName.isNotEmpty()
                Log.d("firstFragment", "File selected: $fileName")
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""

        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                fileName = it.getString(nameIndex)
                Log.d("firstFragment", "File name from cursor: $fileName")
            }
        }

        if (fileName.isEmpty()) {
            val mimeType = contentResolver.getType(uri)
            val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            fileName = "file.${extension ?: "unknown"}"
            Log.d("firstFragment", "File name from MIME type: $fileName")
        }

        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment ?: "unknown_file"
            Log.d("firstFragment", "File name from URI fallback: $fileName")
        }

        return fileName
    }

    private fun uploadFile(uri: Uri, fileName: String) {
        Log.d("firstFragment", "Starting file upload: $fileName")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiGatewayUrl = "https://fn950cxh6k.execute-api.ap-south-1.amazonaws.com/S3_URL"
                val url = URL("$apiGatewayUrl?filename=$fileName")

                Log.d("UploadFile", "Requesting pre-signed URL for file: $fileName")

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("PreSignedUrlError", "Unexpected response code: ${response.code}")
                    throw IOException("Unexpected response code: ${response.code}")
                }

                val responseBody = response.body?.string()
                Log.d("PreSignedUrl", "Response body: $responseBody")

                val jsonResponse = JSONObject(responseBody)
                val presignedUrl = jsonResponse.optString("url")

                if (presignedUrl.isEmpty()) {
                    Log.e("PreSignedUrlError", "Received empty pre-signed URL")
                    throw IOException("Received empty pre-signed URL")
                }

                Log.d("PreSignedUrl", "Received pre-signed URL: $presignedUrl")

                val fileInputStream = contentResolver.openInputStream(uri)
                val fileBytes = fileInputStream?.readBytes() ?: throw IOException("Failed to read file")
                Log.d("UploadFile", "File size: ${fileBytes.size} bytes")

                val uploadRequest = Request.Builder()
                    .url(presignedUrl)
                    .put(fileBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()

                Log.d("UploadFile", "Upload request URL: ${uploadRequest.url}")

                client.newCall(uploadRequest).execute().use { uploadResponse ->
                    if (!uploadResponse.isSuccessful) {
                        val errorResponseBody = uploadResponse.body?.string()
                        Log.e("UploadFileError", "Upload failed with code: ${uploadResponse.code}")
                        Log.e("UploadFileError", "Upload failed with response: $errorResponseBody")
                        throw IOException("File upload failed with code: ${uploadResponse.code}")
                    }

                    Log.d("UploadFile", "File uploaded successfully.")

                    runOnUiThread {
                        txtSelectedFile.text = "File uploaded successfully."
                    }
                }
            } catch (e: Exception) {
                Log.e("UploadFileError", "File upload failed", e)

                runOnUiThread {
                    txtSelectedFile.text = "File upload failed: ${e.localizedMessage}"
                }
            }
        }
    }

}
