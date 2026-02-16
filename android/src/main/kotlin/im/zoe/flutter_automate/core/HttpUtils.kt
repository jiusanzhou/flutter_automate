package im.zoe.flutter_automate.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP 请求工具
 */
object HttpUtils {
    
    private const val TAG = "HttpUtils"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    /**
     * HTTP 响应
     */
    data class Response(
        val code: Int,
        val body: String,
        val headers: Map<String, String>,
        val success: Boolean = code in 200..299
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "code" to code,
            "body" to body,
            "headers" to headers,
            "success" to success
        )
    }
    
    /**
     * GET 请求
     */
    fun get(
        url: String,
        headers: Map<String, String>? = null,
        params: Map<String, String>? = null
    ): Response {
        val urlBuilder = url.toHttpUrlOrNull()?.newBuilder()
            ?: return Response(-1, "Invalid URL", emptyMap(), false)
        
        params?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        
        val request = Request.Builder()
            .url(urlBuilder.build())
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .get()
            .build()
        
        return execute(request)
    }
    
    /**
     * POST 请求（表单）
     */
    fun post(
        url: String,
        data: Map<String, String>? = null,
        headers: Map<String, String>? = null
    ): Response {
        val formBuilder = FormBody.Builder()
        data?.forEach { (key, value) ->
            formBuilder.add(key, value)
        }
        
        val request = Request.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .post(formBuilder.build())
            .build()
        
        return execute(request)
    }
    
    /**
     * POST JSON 请求
     */
    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String>? = null
    ): Response {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .post(body)
            .build()
        
        return execute(request)
    }
    
    /**
     * POST 文件上传
     */
    fun postMultipart(
        url: String,
        files: Map<String, String>, // fieldName -> filePath
        data: Map<String, String>? = null,
        headers: Map<String, String>? = null
    ): Response {
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        
        // 添加表单字段
        data?.forEach { (key, value) ->
            multipartBuilder.addFormDataPart(key, value)
        }
        
        // 添加文件
        files.forEach { (fieldName, filePath) ->
            val file = File(filePath)
            if (file.exists()) {
                val mediaType = guessMimeType(filePath).toMediaType()
                multipartBuilder.addFormDataPart(
                    fieldName,
                    file.name,
                    file.asRequestBody(mediaType)
                )
            }
        }
        
        val request = Request.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .post(multipartBuilder.build())
            .build()
        
        return execute(request)
    }
    
    /**
     * PUT 请求
     */
    fun put(
        url: String,
        json: String,
        headers: Map<String, String>? = null
    ): Response {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .put(body)
            .build()
        
        return execute(request)
    }
    
    /**
     * DELETE 请求
     */
    fun delete(
        url: String,
        headers: Map<String, String>? = null
    ): Response {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .delete()
            .build()
        
        return execute(request)
    }
    
    /**
     * 下载文件
     */
    fun download(
        url: String,
        savePath: String,
        headers: Map<String, String>? = null
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .get()
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                
                val file = File(savePath)
                file.parentFile?.mkdirs()
                
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $url", e)
            false
        }
    }
    
    /**
     * 执行请求
     */
    private fun execute(request: Request): Response {
        return try {
            client.newCall(request).execute().use { response ->
                val headers = mutableMapOf<String, String>()
                response.headers.forEach { (name, value) ->
                    headers[name] = value
                }
                
                Response(
                    code = response.code,
                    body = response.body?.string() ?: "",
                    headers = headers
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Request failed: ${request.url}", e)
            Response(-1, e.message ?: "Network error", emptyMap(), false)
        }
    }
    
    /**
     * 异步执行请求
     */
    suspend fun executeAsync(request: Request): Response = withContext(Dispatchers.IO) {
        execute(request)
    }
    
    /**
     * 猜测 MIME 类型
     */
    private fun guessMimeType(path: String): String {
        return when {
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".gif", true) -> "image/gif"
            path.endsWith(".webp", true) -> "image/webp"
            path.endsWith(".mp4", true) -> "video/mp4"
            path.endsWith(".mp3", true) -> "audio/mpeg"
            path.endsWith(".json", true) -> "application/json"
            path.endsWith(".xml", true) -> "application/xml"
            path.endsWith(".txt", true) -> "text/plain"
            path.endsWith(".html", true) -> "text/html"
            path.endsWith(".pdf", true) -> "application/pdf"
            path.endsWith(".zip", true) -> "application/zip"
            path.endsWith(".apk", true) -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * URL 编码
     */
    fun encodeUrl(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
    
    /**
     * URL 解码
     */
    fun decodeUrl(value: String): String {
        return java.net.URLDecoder.decode(value, "UTF-8")
    }
    
    // 扩展函数
    private fun String.toHttpUrlOrNull(): HttpUrl? {
        return try {
            HttpUrl.Builder().parse(null, this).build()
        } catch (e: Exception) {
            try {
                // 尝试直接解析
                okhttp3.HttpUrl.parse(this)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
