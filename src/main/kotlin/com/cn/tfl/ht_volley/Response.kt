package com.cn.tfl.ht_volley


/**
 * Created by Happiness on 2017/5/22.
 */
//响应对象
open class Response<T> {

    //错误
    var error: VolleyError? = null

    //响应结果,在出现错误的情况下为空
    var result: T?

    //如果这个回应是一个软过期的一个,另一个会来的时候变为true。
    var intermediate: Boolean? = false

    //缓存实体
    var cacheEntry: Cache.Entry? = null

    //构造方法
    constructor(error: VolleyError) {
        this.result = null
        this.cacheEntry = null
        this.error = error
    }

    //构造方法
    constructor(result: T, cacheEntry: Cache.Entry) {
        this.result = result
        this.cacheEntry = cacheEntry
        this.error = null
    }

    //用于传递解析响应的回调界面
    interface Listener<in T> {
        fun onResponse(response: T)
    }

    //用于传递错误响应的回调界面
    interface ErrorListener {
        fun onErrorListener(error: VolleyError)
    }


    //返回包含给定错误代码和可选的错误响应
    companion object {

        fun error(error: VolleyError): Response<Any> {
            return Response(error)
        }

        //成功响应
        fun <T> success(result: T, cacheEntry: Cache.Entry): Response<T> {
            return Response(result, cacheEntry)
        }
    }

    //返回此响应是否被认为是成功的。
    fun isSuccess(): Boolean {
        return error == null
    }
}