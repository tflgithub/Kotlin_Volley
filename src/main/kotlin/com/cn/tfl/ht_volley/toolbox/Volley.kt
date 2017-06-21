package com.cn.tfl.ht_volley.toolbox

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cn.tfl.ht_volley.RequestQueue
import java.io.File


/**
 * Created by Happiness on 2017/5/22.
 */
object Volley {

    /** 默认磁盘缓存目录。  */

    private val DEFAULT_CACHE_DIR = "volley"

    /**
     * 创建工作池的默认实例并调用它[RequestQueue.start]。

     * @param context A [Context] 用于创建缓存目录。
     * *
     * @param stack An [HttpStack] 用于网络，或默认为null。
     * *
     * @return A started [RequestQueue] 实例
     */
    fun newRequestQueue(context: Context, stack: HttpStack?): RequestQueue {
        var stack = stack
        val cacheDir = File(context.cacheDir, DEFAULT_CACHE_DIR)

        var userAgent = "volley/0"
        try {
            val packageName = context.packageName
            val info = context.packageManager.getPackageInfo(packageName, 0)
            userAgent = packageName + "/" + info.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
        }

        if (stack == null) {
            if (Build.VERSION.SDK_INT >= 9) {
                stack = HurlStack()
            } else {
                // 在Gingerbread(蜂巢（android 3.0）)之前，HttpUrlConnection是不可靠的。
                // See: http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                //stack = HttpClientStack(AndroidHttpClient.newInstance(userAgent))
            }
        }

        val network = BasicNetwork(stack!!)

        val queue = RequestQueue(DiskBasedCache(cacheDir), network)
        queue.start()

        return queue
    }

    /**
     * 创建工作池的默认实例并调用它[RequestQueue.start]。

     * @param context A [Context] 用于创建缓存目录。
     * *
     * @return A started [RequestQueue] 实例
     */
    fun newRequestQueue(context: Context): RequestQueue {
        return newRequestQueue(context, null)
    }
}