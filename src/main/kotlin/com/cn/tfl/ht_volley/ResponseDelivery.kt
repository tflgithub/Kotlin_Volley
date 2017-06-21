package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/23.
 */
interface ResponseDelivery {
    /**
     * 解析来自网络或缓存的响应并传递.
     */
    fun  postResponse(request: Request<*>, response: Response<*>)

    /**
     * 解析来自网络或缓存的响应并传递。 提供的Runnable执行。
     */
    fun  postResponse(request: Request<*>, response: Response<*>, runnable: Runnable?)

    /**
     * 发送请求错误.
     */
    fun  postError(request: Request<*>, error: VolleyError)
}