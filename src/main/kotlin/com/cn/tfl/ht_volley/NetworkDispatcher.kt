package com.cn.tfl.ht_volley

import android.annotation.TargetApi
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.BlockingQueue

/**
 * Created by Happiness on 2017/5/23.
 */
//从请求队列执行网络调度的线程。
class NetworkDispatcher : Thread {

    /** 服务请求队列。  */
    private var mQueue: BlockingQueue<Request<*>>? = null
    /** 用于处理请求的网络接口。  */
    private var mNetwork: Network? = null
    /** 缓存写入。  */
    private var mCache: Cache? = null
    /** 发送回复和错误。  */
    private var mDelivery: ResponseDelivery? = null
    /** 用于判断线程是否死亡  */
    private var mQuit = false

    constructor(queue: BlockingQueue<Request<*>>, network: Network, cache: Cache,
                delivery: ResponseDelivery) {
        this.mQueue = queue
        this.mNetwork = network
        this.mCache = cache
        this.mDelivery = delivery
    }

    //线程退出
    fun quit() {
        mQuit = true
        interrupt()
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private fun addTrafficStatsTag(request: Request<*>) {
        // Tag the request (if API >= 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TrafficStats.setThreadStatsTag(request.getTrafficStatsTag())
        }
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        while (true) {
            var startTimeMs = SystemClock.elapsedRealtime()
            var request: Request<*>
            try {
                request = mQueue!!.take()
            } catch(e: InterruptedException) {
                if (mQuit) {
                    return
                }
                continue
            }
            try {

                if (request.isCanceled()!!) {
                    request.finish("network-discard-cancelled")
                    continue
                }

                addTrafficStatsTag(request)
                val networkResponse = mNetwork!!.performRequest(request)

                //如果服务器返回304，我们已经发送了一个响应
                if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                    request.finish("not-modified")
                    continue
                }

                //解析这里的工作线程的响应。
                val response = request.parseNetworkResponse(networkResponse)
                //缓存
                if (request.shouldCache()) {
                    mCache!!.put(request.getCacheKey(), response.cacheEntry!!)
                }
                request.markDelivered()
                mDelivery!!.postResponse(request, response)

            } catch (volleyError: VolleyError) {
                volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs)
                parseAndDeliverNetworkError(request, volleyError)
            } catch (e: Exception) {
                val volleyError = VolleyError(e)
                volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs)
                mDelivery!!.postError(request, volleyError)
            }
        }
    }

    private fun parseAndDeliverNetworkError(request: Request<*>, error: VolleyError) {
        var error = error
        error = request.parseNetworkError(error)
        mDelivery!!.postError(request, error)
    }
}