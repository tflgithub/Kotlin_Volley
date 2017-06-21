package com.cn.tfl.ht_volley

import android.os.Handler
import android.os.Looper
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList


/**
 * Created by Happiness on 2017/5/23.
 */
//请求队列（线程池调度）
class RequestQueue {

    interface RequestFinishedListener {

        //当请求完成处理时调用
        fun onRequestFinished(request: Request<*>)
    }

    /** 用于产生单调递增序列号的请求。  */
    private val mSequenceGenerator = AtomicInteger()

    //请求等待集合
    private var mWaitingRequests = HashMap<String, Queue<Request<*>>>()

    //当前请求集合
    private var mCurrentRequests = HashSet<Request<*>>()

    //用于缓存分流请求
    private var mCacheQueue = PriorityBlockingQueue<Request<*>>()

    //网络请求队列
    private var mNetworkQueue = PriorityBlockingQueue<Request<*>>()

    companion object {
        //默认线程池容量
        val DEFAULT_NETWORK_THREAD_POOL_SIZE = 4
    }

    //缓存
    private var mCache: Cache

    //用于执行请求的网络接口
    private var mNetWork: Network

    //响应传递机制
    private var mDelivery: ResponseDelivery

    //用于网络调度
    private var mDispatchers: Array<NetworkDispatcher?>

    //用于缓存调度
    private var mCacheDispatcher: CacheDispatcher? = null

    private val mFinishedListeners = ArrayList<RequestFinishedListener>()

    @JvmOverloads constructor(cache: Cache, network: Network, threadPoolSize: Int,
                              delivery: ResponseDelivery) {
        mCache = cache
        mNetWork = network
        mDispatchers = arrayOfNulls<NetworkDispatcher>(threadPoolSize)
        mDelivery = delivery
    }

    constructor(cache: Cache, network: Network, threadPoolSize: Int) : this(cache, network, threadPoolSize, ExecutorDelivery(Handler(Looper.getMainLooper())))

    constructor(cache: Cache, network: Network) : this(cache, network, DEFAULT_NETWORK_THREAD_POOL_SIZE)

    /**
     * 启动队列中的调度程序。
     */
    fun start() {
        stop()
        // 确保所有当前运行的调度程序都已停止。
        // 创建缓存调度程序并启动它。
        mCacheDispatcher = CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery)
        mCacheDispatcher!!.start()

        // 创建网络调度程序（和相应的线程）达到池大小。
        for (i in 0..mDispatchers.size - 1) {
            val networkDispatcher = NetworkDispatcher(mNetworkQueue, mNetWork,
                    mCache, mDelivery)
            mDispatchers[i] = networkDispatcher
            networkDispatcher.start()
        }
    }

    /**
     * 停止缓存和网络调度程序。
     */
    fun stop() {
        mCacheDispatcher?.quit()
        for (mDispatcher in mDispatchers) {
            mDispatcher?.quit()
        }
    }

    /**
     * 获取序列号。
     */
    fun getSequenceNumber(): Int {
        return mSequenceGenerator.incrementAndGet()
    }

    /**
     * 获取正在使用的[Cache]实例。
     */
    fun getCache(): Cache {
        return mCache
    }

    /**
     * 用于请求的简单谓词或过滤器接口，供以后使用
     * [RequestQueue.cancelAll].
     */
    interface RequestFilter {
        fun apply(request: Request<*>): Boolean
    }

    /**
     * 取消适用给定过滤器的队列中的所有请求。
     * @param filter The filtering function to use
     */
    fun cancelAll(filter: RequestFilter) {
        synchronized(mCurrentRequests) {
            mCurrentRequests
                    .filter { filter.apply(it) }
                    .forEach { it.cancel() }
        }
    }

    /**
     * 使用给定的标签取消此队列中的所有请求。 标签必须是非空的，而且相等是通过身份。
     */
    fun cancelAll(tag: Any?) {
        if (tag == null) {
            throw IllegalArgumentException("Cannot cancelAll with a null tag")
        }
        cancelAll(object : RequestFilter {
            override fun apply(request: Request<*>): Boolean {
                return request.getTag() === tag
            }
        })
    }


    /**
     * 向调度队列添加请求。
     * @param request 请求服务
     * *
     * @return 传入的请求
     */
    fun <T> add(request: Request<T>): Request<T> {

        // 将请求标记为属于此队列，并将其添加到当前请求集中。
        request.setRequestQueue(this)
        synchronized(mCurrentRequests) {
            mCurrentRequests.add(request)
        }

        // 按照添加的顺序处理请求。
        request.setSequence(getSequenceNumber())

        //如果请求不可缓存，请跳过缓存队列并直接进入网络。
        if (!request.shouldCache()) {
            mNetworkQueue.add(request)
            return request
        }

        //如果已经有请求与运行行中具有相同的缓存键，则将请求插入阶段。
        synchronized(mWaitingRequests) {
            val cacheKey = request.getCacheKey()
            if (mWaitingRequests.containsKey(cacheKey)) {
                // 排队请求
                var stagedRequests = mWaitingRequests[cacheKey]
                if (stagedRequests == null) {
                    stagedRequests = LinkedList()
                }
                stagedRequests.add(request)
                mWaitingRequests.put(cacheKey, stagedRequests)
            } else {
                //为此cacheKey插入'null'队列，表示现在有一个请求在飞行中。

                //          mWaitingRequests.put(cacheKey, null!!)
                mCacheQueue.add(request)
            }
            return request
        }
    }

    //请求完成
    fun finish(request: Request<*>) {
        // 从当前处理的一组请求中删除.
        synchronized(mCurrentRequests) {
            mCurrentRequests.remove(request)
        }
        synchronized(mFinishedListeners) {
            for (listener in mFinishedListeners) {
                listener.onRequestFinished(request)
            }
        }

        if (request.shouldCache()) {
            synchronized(mWaitingRequests) {
                val cacheKey = request.getCacheKey()
                val waitingRequests = mWaitingRequests.remove(cacheKey)
                if (waitingRequests != null) {
                    mCacheQueue.addAll(waitingRequests)
                }
            }
        }
    }


    /**
     *添加RequestFinishedListener。
     */
    fun addRequestFinishedListener(listener: RequestFinishedListener) {
        synchronized(mFinishedListeners) {
            mFinishedListeners.add(listener)
        }
    }

    /**
     * 删除RequestFinishedListener。 如果以前没有添加侦听器，则不起作用。
     */
    fun removeRequestFinishedListener(listener: RequestFinishedListener) {
        synchronized(mFinishedListeners) {
            mFinishedListeners.remove(listener)
        }
    }
}
