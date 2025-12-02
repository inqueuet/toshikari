package com.valoser.toshikari.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * このプラットフォームバージョンでアプリが必要とするメディア系パーミッションのセットを返します。
     * 現在はシステムピッカーを利用しており追加のメディア権限は不要なため、空配列を返します。
     */
    fun requiredMediaPermissions(): Array<String> {
        return emptyArray()
    }

    /**
     * すべての必要なメディア系パーミッションが付与されているかどうか。
     */
    fun hasAllMediaPermissions(context: Context): Boolean =
        missingMediaPermissions(context).isEmpty()

    /**
     * 未付与のメディア系パーミッション一覧を返します。
     */
    fun missingMediaPermissions(context: Context): Array<String> =
        requiredMediaPermissions().filterNot { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}
