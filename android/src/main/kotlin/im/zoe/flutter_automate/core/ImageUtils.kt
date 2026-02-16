package im.zoe.flutter_automate.core

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 图像处理工具
 * 找图、找色、图像操作
 */
object ImageUtils {
    
    private const val TAG = "ImageUtils"
    
    /**
     * 颜色匹配结果
     */
    data class Point(val x: Int, val y: Int)
    
    /**
     * 找图结果
     */
    data class MatchResult(
        val x: Int,
        val y: Int,
        val similarity: Float
    )
    
    // ==================== 找色 ====================
    
    /**
     * 在图片中查找指定颜色
     * @param bitmap 源图片
     * @param color 目标颜色 (ARGB)
     * @param threshold 颜色相似度阈值 (0-255)
     * @param region 搜索区域 [x, y, width, height]，null 表示全图
     * @return 找到的第一个点，或 null
     */
    fun findColor(
        bitmap: Bitmap,
        color: Int,
        threshold: Int = 4,
        region: IntArray? = null
    ): Point? {
        val startX = region?.get(0) ?: 0
        val startY = region?.get(1) ?: 0
        val endX = if (region != null) startX + region[2] else bitmap.width
        val endY = if (region != null) startY + region[3] else bitmap.height
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = bitmap.getPixel(x, y)
                if (colorMatch(pixel, color, threshold)) {
                    return Point(x, y)
                }
            }
        }
        return null
    }
    
    /**
     * 在图片中查找所有指定颜色的点
     */
    fun findAllColors(
        bitmap: Bitmap,
        color: Int,
        threshold: Int = 4,
        region: IntArray? = null,
        maxCount: Int = 1000
    ): List<Point> {
        val result = mutableListOf<Point>()
        val startX = region?.get(0) ?: 0
        val startY = region?.get(1) ?: 0
        val endX = if (region != null) startX + region[2] else bitmap.width
        val endY = if (region != null) startY + region[3] else bitmap.height
        
        outer@ for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = bitmap.getPixel(x, y)
                if (colorMatch(pixel, color, threshold)) {
                    result.add(Point(x, y))
                    if (result.size >= maxCount) break@outer
                }
            }
        }
        return result
    }
    
    /**
     * 多点找色
     * @param bitmap 源图片
     * @param firstColor 第一个颜色
     * @param colorOffsets 其他颜色的偏移量 [[dx, dy, color], ...]
     * @param threshold 颜色相似度阈值
     * @param region 搜索区域
     * @return 第一个颜色的位置，或 null
     */
    fun findMultiColors(
        bitmap: Bitmap,
        firstColor: Int,
        colorOffsets: List<IntArray>,
        threshold: Int = 4,
        region: IntArray? = null
    ): Point? {
        val startX = region?.get(0) ?: 0
        val startY = region?.get(1) ?: 0
        val endX = if (region != null) startX + region[2] else bitmap.width
        val endY = if (region != null) startY + region[3] else bitmap.height
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = bitmap.getPixel(x, y)
                if (!colorMatch(pixel, firstColor, threshold)) continue
                
                // 检查其他颜色
                var allMatch = true
                for (offset in colorOffsets) {
                    val ox = x + offset[0]
                    val oy = y + offset[1]
                    val targetColor = offset[2]
                    
                    if (ox < 0 || ox >= bitmap.width || oy < 0 || oy >= bitmap.height) {
                        allMatch = false
                        break
                    }
                    
                    val otherPixel = bitmap.getPixel(ox, oy)
                    if (!colorMatch(otherPixel, targetColor, threshold)) {
                        allMatch = false
                        break
                    }
                }
                
                if (allMatch) {
                    return Point(x, y)
                }
            }
        }
        return null
    }
    
    /**
     * 检测指定位置是否为某颜色
     */
    fun detectsColor(
        bitmap: Bitmap,
        color: Int,
        x: Int,
        y: Int,
        threshold: Int = 16
    ): Boolean {
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) {
            return false
        }
        val pixel = bitmap.getPixel(x, y)
        return colorMatch(pixel, color, threshold)
    }
    
    /**
     * 获取指定位置的颜色
     */
    fun getPixel(bitmap: Bitmap, x: Int, y: Int): Int {
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) {
            return 0
        }
        return bitmap.getPixel(x, y)
    }
    
    // ==================== 找图 ====================
    
    /**
     * 在大图中查找小图
     * @param source 源图片（大图）
     * @param template 模板图片（小图）
     * @param threshold 相似度阈值 (0.0-1.0)
     * @param region 搜索区域
     * @return 匹配结果，或 null
     */
    fun findImage(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.9f,
        region: IntArray? = null
    ): MatchResult? {
        val startX = region?.get(0) ?: 0
        val startY = region?.get(1) ?: 0
        val endX = if (region != null) {
            minOf(startX + region[2] - template.width, source.width - template.width)
        } else {
            source.width - template.width
        }
        val endY = if (region != null) {
            minOf(startY + region[3] - template.height, source.height - template.height)
        } else {
            source.height - template.height
        }
        
        var bestMatch: MatchResult? = null
        var bestSimilarity = 0f
        
        // 采样步长（加速搜索）
        val step = if (template.width > 50 || template.height > 50) 2 else 1
        
        for (y in startY..endY step step) {
            for (x in startX..endX step step) {
                val similarity = calculateSimilarity(source, template, x, y)
                if (similarity >= threshold && similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestMatch = MatchResult(x, y, similarity)
                    
                    // 如果相似度足够高，直接返回
                    if (similarity >= 0.99f) {
                        return bestMatch
                    }
                }
            }
        }
        
        return bestMatch
    }
    
    /**
     * 查找所有匹配的图片
     */
    fun findAllImages(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.9f,
        region: IntArray? = null,
        maxCount: Int = 10
    ): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        val startX = region?.get(0) ?: 0
        val startY = region?.get(1) ?: 0
        val endX = if (region != null) {
            minOf(startX + region[2] - template.width, source.width - template.width)
        } else {
            source.width - template.width
        }
        val endY = if (region != null) {
            minOf(startY + region[3] - template.height, source.height - template.height)
        } else {
            source.height - template.height
        }
        
        val step = 3
        
        outer@ for (y in startY..endY step step) {
            for (x in startX..endX step step) {
                val similarity = calculateSimilarity(source, template, x, y)
                if (similarity >= threshold) {
                    // 检查是否与已有结果重叠
                    val overlaps = results.any { r ->
                        abs(r.x - x) < template.width / 2 && abs(r.y - y) < template.height / 2
                    }
                    if (!overlaps) {
                        results.add(MatchResult(x, y, similarity))
                        if (results.size >= maxCount) break@outer
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * 计算两个图片区域的相似度
     */
    private fun calculateSimilarity(
        source: Bitmap,
        template: Bitmap,
        offsetX: Int,
        offsetY: Int
    ): Float {
        val sampleStep = when {
            template.width * template.height > 10000 -> 4
            template.width * template.height > 2500 -> 2
            else -> 1
        }
        
        var totalDiff = 0.0
        var sampleCount = 0
        
        for (ty in 0 until template.height step sampleStep) {
            for (tx in 0 until template.width step sampleStep) {
                val sx = offsetX + tx
                val sy = offsetY + ty
                
                if (sx >= source.width || sy >= source.height) continue
                
                val sourcePixel = source.getPixel(sx, sy)
                val templatePixel = template.getPixel(tx, ty)
                
                // 跳过透明像素
                if (Color.alpha(templatePixel) < 128) continue
                
                val diff = colorDifference(sourcePixel, templatePixel)
                totalDiff += diff
                sampleCount++
            }
        }
        
        if (sampleCount == 0) return 0f
        
        val avgDiff = totalDiff / sampleCount
        // 将差异转换为相似度 (0-1)
        return (1.0 - avgDiff / 441.67).toFloat().coerceIn(0f, 1f) // 441.67 = sqrt(3 * 255^2)
    }
    
    // ==================== 图像处理 ====================
    
    /**
     * 裁剪图片
     */
    fun clip(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        val safeX = x.coerceIn(0, bitmap.width - 1)
        val safeY = y.coerceIn(0, bitmap.height - 1)
        val safeWidth = width.coerceIn(1, bitmap.width - safeX)
        val safeHeight = height.coerceIn(1, bitmap.height - safeY)
        return Bitmap.createBitmap(bitmap, safeX, safeY, safeWidth, safeHeight)
    }
    
    /**
     * 缩放图片
     */
    fun scale(bitmap: Bitmap, scaleX: Float, scaleY: Float): Bitmap {
        val newWidth = (bitmap.width * scaleX).toInt()
        val newHeight = (bitmap.height * scaleY).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 调整图片大小
     */
    fun resize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    
    /**
     * 灰度化
     */
    fun grayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 + 
                           Color.green(pixel) * 0.587 + 
                           Color.blue(pixel) * 0.114).toInt()
                result.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        return result
    }
    
    /**
     * 二值化
     */
    fun threshold(bitmap: Bitmap, thresholdValue: Int = 128): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 + 
                           Color.green(pixel) * 0.587 + 
                           Color.blue(pixel) * 0.114).toInt()
                val value = if (gray > thresholdValue) 255 else 0
                result.setPixel(x, y, Color.rgb(value, value, value))
            }
        }
        return result
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 检查两个颜色是否匹配
     */
    private fun colorMatch(color1: Int, color2: Int, threshold: Int): Boolean {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        
        return abs(r1 - r2) <= threshold &&
               abs(g1 - g2) <= threshold &&
               abs(b1 - b2) <= threshold
    }
    
    /**
     * 计算两个颜色的差异（欧氏距离）
     */
    private fun colorDifference(color1: Int, color2: Int): Double {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        
        return sqrt(
            ((r1 - r2) * (r1 - r2) +
             (g1 - g2) * (g1 - g2) +
             (b1 - b2) * (b1 - b2)).toDouble()
        )
    }
    
    /**
     * 解析颜色字符串
     * 支持格式: "#RRGGBB", "#AARRGGBB", "RRGGBB"
     */
    fun parseColor(colorString: String): Int {
        return try {
            Color.parseColor(
                if (colorString.startsWith("#")) colorString
                else "#$colorString"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Invalid color: $colorString")
            0
        }
    }
}
