package com.cn.tfl.ht_volley

import android.os.Process
import java.util.concurrent.BlockingQueue


/**
 * Created by Happiness on 2017/5/23.
 */
//用于在请求队列上执行缓存分类的线程。
class CacheDispatcher : Thread {

    /** 请求队列进行分类。  */
    private var mCacheQueue: BlockingQueue<Request<*>>? = null

    /** 发送到网络的请求队列。  */
    private var mNetworkQueue: BlockingQueue<Request<*>>? = null

    /** 从缓存读取。 */
    private var mCache: Cache? = null

    /** 发送响应。 */
    private var mDelivery: ResponseDelivery? = null

    /** 用于判断线程是否死亡。  */
    private var mQuit = false

    constructor(cacheQueue: BlockingQueue<Request<*>>, networkQueue: BlockingQueue<Request<*>>,
                cache: Cache, delivery: ResponseDelivery) {
        this.mCacheQueue = cacheQueue
        this.mNetworkQueue = networkQueue
        this.mCache = cache
        this.mDelivery = delivery
    }

    fun quit() {
        mQuit = true
        interrupt()
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
//        mCache!!.initialize()
        while (true) {
            try {
                val request = mCacheQueue!!.take()
                //如果请求已被取消，请勿继续发送。
                if (request.isCanceled()!!) {
                    request.finish("cache-discard-canceled")
                    continue
                }
                //尝试从缓存中检索
                val entry = mCache!!.get(request.getCacheKey())
                if (entry == null) {
                    mNetworkQueue!!.put(request)
                    continue
                }
                //如果过期，只需将其发送到网络。
                if (entry.isExpired()) {
                    request.setCacheEntry(entry)
                    mNetworkQueue!!.put(request)
                    continue
                }
                //如果有缓存, 解析其数据以传送回请求。
                val response = request.parseNetworkResponse(
                        NetworkResponse(entry.data!!, entry.responseHeaders))

                if (!entry.refreshNeeded()) {
                    // 完全未到达的缓存命中。 只是提供回应。
                    mDelivery!!.postResponse(request, response)
                } else {
                    //软过期缓存命中。 我们可以提供缓存的响应,但是我们还需要将请求发送到网络刷新
                    request.setCacheEntry(entry)
                    // 标记为过期
                    response.intermediate = true
                    //将中间响应发回给用户并拥有发送然后将请求转发到网络。
                    mDelivery!!.postResponse(request, response, Runnable {
                        try {
                            mNetworkQueue!!.put(request)
                        } catch (e: InterruptedException) {
                            // 什么也不做
                        }
                    })
                }
            } catch (e: InterruptedException) {
                if (mQuit) {
                    return
                }
            }
        }
    }
}