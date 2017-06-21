package com.cn.tfl.ht_volley

import android.net.Uri
import java.net.URLEncoder
import java.util.*


/**
 * Created by Happiness on 2017/5/23.
 */
abstract class Request<T> @JvmOverloads constructor(

        val mMethod: Int,

        val mUrl: String,

        val mErrorListener: Response.ErrorListener,

        var mDefaultTrafficStatsTag: Int

) : Comparable<Request<T>> {

    //默认参数编码为utf-8
    val DEFAULT_PARAMS_ENCODING = "UTF-8"

    interface Method {
        companion object {
            val DEPRECATED_GET_OR_POST = -1
            val GET = 0
            val POST = 1
            val PUT = 2
            val DELETE = 3
            val HEAD = 4
            val OPTIONS = 5
            val TRACE = 6
            val PATCH = 7
        }
    }

    constructor(method: Int, url: String, listener: Response.ErrorListener) : this(method, url, listener, 0)

    constructor(url: String, listener: Response.ErrorListener) : this(Method.DEPRECATED_GET_OR_POST, url, listener)


    var mTag: Any? = null

    var mRetryPolicy: RetryPolicy? = null

    var mCacheEntry: Cache.Entry? = null

    //该请求是否被取消
    var mCanceled: Boolean = false

    //是否缓存
    var mShouldCache = true

    // 在HTTP 5xx（服务器）错误的情况下是否应重试该请求。
    var mShouldRetryServerErrors = false

    //请求是否已经响应
    var mResponseDelivered = false

    //请求的序列号，用于强制执行FIFO排序
    var mSequence: Int = 0

    //与该请求相关联的请求队列。
    var mRequestQueue: RequestQueue? = null

    init {
        setRetryPolicy(DefaultRetryPolicy())
        this.mDefaultTrafficStatsTag = findDefaultTrafficStatsTag(this.mUrl)
    }

    //返回URL主机组件的哈希码，如果没有，则返回0
    fun findDefaultTrafficStatsTag(url: String): Int {
        val host = Uri.parse(url)!!.host
        if (host != null) return host.hashCode() else return 0
    }

    //返回此请求的方法
    fun getMethod(): Int {
        return mMethod
    }

    //返回与此请求一起附加的额外的HTTP头列表。
    @Throws(AuthFailureError::class)
    fun getHeaders(): Map<String, String> {
        return Collections.emptyMap()
    }

    //将此请求与给定队列相关联。 当请求完成时，将通知请求队列。
    fun setRequestQueue(requestQueue: RequestQueue): Request<T> {
        mRequestQueue = requestQueue
        return this
    }

    //设置重试策略
    fun setRetryPolicy(retryPolicy: RetryPolicy): Request<*> {
        mRetryPolicy = retryPolicy
        return this
    }

    //设置请求标签
    fun setTag(tag: Any): Request<*> {
        mTag = tag
        return this
    }

    //返回请求标签
    fun getTag(): Any? {
        return mTag
    }

    //返回请求错误{@link com.cn.tfl.ht_volley.Response.ErrorListener}
    fun getErrorListener(): Response.ErrorListener {
        return mErrorListener
    }

    //返回一个用于使用的标签
    fun getTrafficStatsTag(): Int {
        return mDefaultTrafficStatsTag
    }

    //设置请求序列
    fun setSequence(sequence: Int): Request<*> {
        mSequence = sequence
        return this
    }

    //返回请求序列
    fun getSequence(): Int {
        if (mSequence == null) {
            throw IllegalStateException("getSequence called before setSequence")
        }
        return mSequence
    }

    //返回请求地址
    fun getUrl(): String {
        return mUrl
    }

    //返回缓存key值，key一般就是请求地址+参数
    fun getCacheKey(): String {
        return getUrl()
    }

    //设置缓存
    fun setCacheEntry(cacheEntry: Cache.Entry): Request<*> {
        mCacheEntry = cacheEntry
        return this
    }

    //返回缓存
    fun getCacheEntry(): Cache.Entry? {
        return mCacheEntry
    }

    //取消请求
    fun cancel() {
        mCanceled = true
    }

    //返回请求是否被取消
    fun isCanceled(): Boolean? {
        return mCanceled
    }

    //返回参数编码
    fun getParamsEncoding(): String? {
        return DEFAULT_PARAMS_ENCODING
    }

    //返回请求正文的内容类型
    fun getBodyContentType(): String? {
        return "application/x-www-form-urlencoded; charset=" + getParamsEncoding()
    }

    @Throws(AuthFailureError::class)
    protected fun getParams(): Map<String, String>? {
        return null
    }

    //返回要发送请求的内容
    @Throws(AuthFailureError::class)
    open fun getBody(): ByteArray? {
        val params = getParams()
        if (params != null && params.size > 0) {
            return encodeParameters(params, getParamsEncoding())
        }
        return null
    }

    //将参数转换为应用程序 x-www-form-urlencoded编码字符串
    fun encodeParameters(params: Map<String, String>, paramsEncoding: String?): ByteArray? {
        var encodedParams = StringBuffer()
        for (param in params) {
            encodedParams.append(URLEncoder.encode(param.key, paramsEncoding))
            encodedParams.append('=')
            encodedParams.append(URLEncoder.encode(param.value, paramsEncoding))
            encodedParams.append('&')
        }
        return encodedParams.toString().toByteArray()
    }

    //设置缓存
    fun setShouldCache(shouldCache: Boolean): Request<T> {
        mShouldCache = shouldCache
        return this
    }

    //返回是否缓存
    fun shouldCache(): Boolean {
        return mShouldCache
    }

    //设置服务器错误时，重试
    fun setShouldRetryServerErrors(shouldRetryServerErrors: Boolean): Request<T> {
        mShouldRetryServerErrors = shouldRetryServerErrors
        return this
    }

    //返回服务器错误是否重试
    fun shouldRetryServerErrors(): Boolean {
        return mShouldRetryServerErrors
    }

    //请求优先级
    enum class Priority {
        LOW,
        NORMAL,
        HIGH,
        IMMEDIATE
    }

    //返回优先级
    fun getPriority(): Priority {
        return Priority.NORMAL
    }

    //返回重试超时时间
    fun getTimeoutMs(): Int {
        return mRetryPolicy!!.getCurrentTimeout()
    }

    //返回应用于请求的重试策略
    fun getRetryPolicy(): RetryPolicy? {
        return mRetryPolicy
    }

    //标记请求已响应
    fun markDelivered() {
        mResponseDelivered = true
    }

    //返回请求是否已经响应
    fun hasHadResponseDelivered(): Boolean {
        return mResponseDelivered
    }

    //解析网络请求响应
    abstract fun parseNetworkResponse(response: NetworkResponse): Response<T>

    //解析网络请求相应错误
    fun parseNetworkError(volleyError: VolleyError): VolleyError {
        return volleyError
    }

    //请求响应
    abstract fun deliverResponse(response: Any)

    //响应错误
    fun deliverError(error: VolleyError) {
        mErrorListener.onErrorListener(error)
    }

    //从高到低排列,序列号FIFO排序
    override fun compareTo(other: Request<T>): Int {
        val left = this.getPriority()
        val right = other.getPriority()
        return if (left === right)
            this.mSequence - other.mSequence
        else
            right.ordinal - left.ordinal
    }

    override fun toString(): String {
        val trafficStatsTag = "0x" + Integer.toHexString(getTrafficStatsTag())
        return (if (mCanceled) "[X] " else "[ ] ") + getUrl() + " " + trafficStatsTag + " " + getPriority() + " " + mSequence
    }

    fun finish(tag: String) {
        if (mRequestQueue != null) {
            mRequestQueue!!.finish(this)
        }
    }
}