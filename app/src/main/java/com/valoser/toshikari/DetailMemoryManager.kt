package com.valoser.toshikari

import android.content.Context
import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * DetailViewModel のメモリ使用量を監視し、段階的なクリーンアップを行うマネージャー。
 *
 * 責務:
 * - 適応的メモリ使用量監視（段階的なクリーンアップ）
 * - NG フィルタキャッシュの管理
 * - Coil キャッシュのクリーンアップ
 */
internal class DetailMemoryManager(
    private val appContext: Context,
    private val plainTextCacheRef: MutableStateFlow<Map<String, String>>,
) {
    private val TAG = "DetailMemoryManager"

    // NGフィルタ結果のキャッシュ（動的サイズ調整）
    val ngFilterCache = LruCache<Pair<List<DetailContent>, List<NgRule>>, List<DetailContent>>(
        calculateOptimalCacheSize()
    )

    // 適応的メモリ監視
    private var lastMemoryCheck = 0L
    private var memoryCheckIntervalMs = 30000L
    private var consecutiveHighMemoryCount = 0

    /** デバイスメモリに基づいた最適なキャッシュサイズを計算。 */
    private fun calculateOptimalCacheSize(): Int {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        return ((maxMemory / 1024 / 1024 / 100).toInt()).coerceIn(20, 200)
    }

    /** NGフィルタ結果キャッシュをクリアする。 */
    fun clearNgFilterCache() {
        ngFilterCache.evictAll()
    }

    /**
     * 適応的メモリ使用量監視。
     * メモリ使用率に応じて監視間隔を動的調整し、高負荷時はキャッシュサイズも縮小する。
     */
    fun checkMemoryUsage() {
        val now = System.currentTimeMillis()
        if (now - lastMemoryCheck < memoryCheckIntervalMs) return
        lastMemoryCheck = now

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()

        val memoryUsagePercent = (memoryUsageRatio * 100).toInt()
        Log.d(TAG, "Memory usage: $memoryUsagePercent% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")

        when {
            memoryUsageRatio > 0.90f -> {
                Log.w(TAG, "Extreme memory usage ($memoryUsagePercent%), performing aggressive cleanup")
                consecutiveHighMemoryCount++
                clearNgFilterCache()
                plainTextCacheRef.value = emptyMap()
                MyApplication.clearCoilImageCache(appContext)
                memoryCheckIntervalMs = 5000L
                if (consecutiveHighMemoryCount >= 3) {
                    Log.d(TAG, "Resetting high memory counter after aggressive cleanup")
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.85f -> {
                Log.w(TAG, "Critical memory usage ($memoryUsagePercent%), performing aggressive cleanup")
                consecutiveHighMemoryCount++
                clearNgFilterCache()
                plainTextCacheRef.value = emptyMap()
                MyApplication.clearCoilImageCache(appContext)
                memoryCheckIntervalMs = 10000L
                if (consecutiveHighMemoryCount >= 5) {
                    Log.d(TAG, "Resetting high memory counter to prevent overflow")
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.75f -> {
                Log.w(TAG, "High memory usage ($memoryUsagePercent%), performing selective cleanup")
                consecutiveHighMemoryCount++
                clearNgFilterCache()
                val currentPlainCache = plainTextCacheRef.value
                if (currentPlainCache.size > 20) {
                    val reducedCache = currentPlainCache.toList().takeLast(10).toMap()
                    plainTextCacheRef.value = reducedCache
                }
                MyApplication.clearCoilImageCache(appContext)
                memoryCheckIntervalMs = 15000L
                if (consecutiveHighMemoryCount >= 5) {
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.70f -> {
                consecutiveHighMemoryCount++
                memoryCheckIntervalMs = 20000L
                if (consecutiveHighMemoryCount >= 3) {
                    Log.w(TAG, "Sustained memory pressure, clearing image cache")
                    MyApplication.clearCoilImageCache(appContext)
                    clearNgFilterCache()
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.60f -> {
                memoryCheckIntervalMs = 25000L
                consecutiveHighMemoryCount = 0
            }
            else -> {
                memoryCheckIntervalMs = 30000L
                consecutiveHighMemoryCount = 0
            }
        }
    }

    /** メモリ使用量を即時に測定し、デバッグ表示用の概要文字列を返す。 */
    fun forceMemoryCheck(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()

        val coilInfo = MyApplication.getCoilCacheInfo(appContext)

        return "Memory: ${(memoryUsageRatio * 100).toInt()}% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)\nCoil: $coilInfo"
    }
}
