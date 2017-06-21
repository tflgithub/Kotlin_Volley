package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/23.
 */
//重试请求的策略
interface RetryPolicy {

    //返回当前超时
    fun getCurrentTimeout(): Int

    //返回当前重试计数
    fun getCurrentRetryCount(): Int

    //重试
    @Throws(VolleyError::class)
    fun retry(error: VolleyError)
}