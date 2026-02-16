package im.zoe.flutter_automate.core

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.*
import java.nio.charset.Charset

/**
 * 文件操作工具
 */
object FileUtils {
    
    private const val TAG = "FileUtils"
    
    // ==================== 读取 ====================
    
    /**
     * 读取文本文件
     */
    fun read(path: String, charset: Charset = Charsets.UTF_8): String? {
        return try {
            File(path).readText(charset)
        } catch (e: Exception) {
            Log.e(TAG, "Read failed: $path", e)
            null
        }
    }
    
    /**
     * 读取文件字节
     */
    fun readBytes(path: String): ByteArray? {
        return try {
            File(path).readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "ReadBytes failed: $path", e)
            null
        }
    }
    
    /**
     * 按行读取文件
     */
    fun readLines(path: String, charset: Charset = Charsets.UTF_8): List<String>? {
        return try {
            File(path).readLines(charset)
        } catch (e: Exception) {
            Log.e(TAG, "ReadLines failed: $path", e)
            null
        }
    }
    
    // ==================== 写入 ====================
    
    /**
     * 写入文本文件
     */
    fun write(path: String, text: String, charset: Charset = Charsets.UTF_8): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(text, charset)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write failed: $path", e)
            false
        }
    }
    
    /**
     * 写入字节
     */
    fun writeBytes(path: String, bytes: ByteArray): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "WriteBytes failed: $path", e)
            false
        }
    }
    
    /**
     * 追加文本
     */
    fun append(path: String, text: String, charset: Charset = Charsets.UTF_8): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.appendText(text, charset)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Append failed: $path", e)
            false
        }
    }
    
    /**
     * 按行写入
     */
    fun writeLines(path: String, lines: List<String>, charset: Charset = Charsets.UTF_8): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(lines.joinToString("\n"), charset)
            true
        } catch (e: Exception) {
      Log.e(TAG, "WriteLines failed: $path", e)
            false
        }
    }
    
    // ==================== 文件操作 ====================
    
    /**
     * 检查文件是否存在
     */
    fun exists(path: String): Boolean {
        return File(path).exists()
    }
    
    /**
     * 检查是否是文件
     */
    fun isFile(path: String): Boolean {
        return File(path).isFile
    }
    
    /**
     * 检查是否是目录
     */
    fun isDir(path: String): Boolean {
        return File(path).isDirectory
    }
    
    /**
     * 创建文件
     */
    fun create(path: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "Create failed: $path", e)
            false
        }
    }
    
    /**
     * 创建目录
     */
    fun createDir(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "CreateDir failed: $path", e)
            false
        }
    }
    
    /**
     * 删除文件或目录
     */
    fun remove(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remove failed: $path", e)
            false
        }
    }
    
    /**
     * 复制文件
     */
    fun copy(src: String, dst: String): Boolean {
        return try {
            val srcFile = File(src)
            val dstFile = File(dst)
            dstFile.parentFile?.mkdirs()
            srcFile.copyTo(dstFile, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: $src -> $dst", e)
            false
        }
    }
    
    /**
     * 移动文件
     */
    fun move(src: String, dst: String): Boolean {
        return try {
            val srcFile = File(src)
            val dstFile = File(dst)
            dstFile.parentFile?.mkdirs()
            srcFile.renameTo(dstFile)
        } catch (e: Exception) {
            Log.e(TAG, "Move failed: $src -> $dst", e)
            false
        }
    }
    
    /**
     * 重命名
     */
    fun rename(path: String, newName: String): Boolean {
        return try {
            val file = File(path)
            val newFile = File(file.parent, newName)
            file.renameTo(newFile)
        } catch (e: Exception) {
            Log.e(TAG, "Rename failed: $path -> $newName", e)
            false
        }
    }
    
    /**
     * 列出目录内容
     */
    fun listDir(path: String): List<String>? {
        return try {
            File(path).list()?.toList()
        } catch (e: Exception) {
            Log.e(TAG, "ListDir failed: $path", e)
            null
        }
    }
    
    /**
     * 列出目录内容（包含完整路径）
     */
    fun listFiles(path: String): List<String>? {
        return try {
            File(path).listFiles()?.map { it.absolutePath }
        } catch (e: Exception) {
            Log.e(TAG, "ListFiles failed: $path", e)
            null
        }
    }
    
    /**
     * 获取文件大小
     */
    fun getSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * 获取文件最后修改时间
     */
    fun getLastModified(path: String): Long {
        return try {
            File(path).lastModified()
        } catch (e: Exception) {
            -1
        }
    }
    
    // ==================== 路径工具 ====================
    
    /**
     * 获取文件名
     */
    fun getName(path: String): String {
        return File(path).name
    }
    
    /**
     * 获取文件扩展名
     */
    fun getExtension(path: String): String {
        return File(path).extension
    }
    
    /**
     * 获取父目录
     */
    fun getParent(path: String): String? {
        return File(path).parent
    }
    
    /**
     * 获取绝对路径
     */
    fun getAbsolutePath(path: String): String {
        return File(path).absolutePath
    }
    
    /**
     * 拼接路径
     */
    fun join(vararg paths: String): String {
        return paths.fold(File("")) { acc, path -> 
            File(acc, path) 
        }.path
    }
    
    // ==================== 特殊路径 ====================
    
    /**
     * 获取外部存储根目录
     */
    fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }
    
    /**
     * 获取应用私有目录
     */
    fun getAppDataPath(context: Context): String {
        return context.filesDir.absolutePath
    }
    
    /**
     * 获取应用缓存目录
     */
    fun getAppCachePath(context: Context): String {
        return context.cacheDir.absolutePath
    }
    
    /**
     * 获取外部应用目录
     */
    fun getExternalAppPath(context: Context): String? {
        return context.getExternalFilesDir(null)?.absolutePath
    }
    
    /**
     * 获取下载目录
     */
    fun getDownloadsPath(): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath
    }
    
    /**
     * 获取图片目录
     */
    fun getPicturesPath(): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        ).absolutePath
    }
}
