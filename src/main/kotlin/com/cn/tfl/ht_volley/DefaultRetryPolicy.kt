package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/23.
 */
class DefaultRetryPolicy @JvmOverloads constructor(

        //当前超时时间（以毫秒为单位）
        var mCurrentTimeoutMs: Int = DefaultRetryPolicy.DEFAULT_TIMEOUT_MS,

        //最大尝试次数
        val mMaxNumRetries: Int = DefaultRetryPolicy.DEFAULT_MAX_RETRIES,

        //策略基数
        val mBackoffMultiplier: Float = DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) : RetryPolicy {

    //当前重试次数
    var mCurrentRetryCount: Int = 0

    companion object {
        val DEFAULT_TIMEOUT_MS = 5500
        val DEFAULT_MAX_RETRIES = 1
        val DEFAULT_BACKOFF_MULT = 1f
    }

    //返回当前超时
    override fun getCurrentTimeout(): Int {
        return mCurrentTimeoutMs
    }

    //返回当前重试次数
    override fun getCurrentRetryCount(): Int {
        return mCurrentRetryCount
    }

    //返回策略的倍数
    fun getBackoffMultiplier(): Float {
        return mBackoffMultiplier
    }

    @Throws(VolleyError::class)
    override fun retry(error: VolleyError) {
        mCurrentRetryCount++
        mCurrentTimeoutMs += (mCurrentTimeoutMs * mBackoffMultiplier).toInt()
        if (!hasAttemptRemaining()) {
            throw error
        }
    }

    //如果此策略有剩余尝试，则返回true，否则返回false
    fun hasAttemptRemaining(): Boolean {
        return mCurrentRetryCount <= mMaxNumRetries
    }

}