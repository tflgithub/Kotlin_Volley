package com.cn.tfl.ht_volley.toolbox

import com.cn.tfl.ht_volley.AuthFailureError
import com.cn.tfl.ht_volley.Request
import org.apache.http.HttpResponse
import java.io.IOException


/**
 * Created by Happiness on 2017/5/24.
 */
//使用给定的参数执行HTTP请求。
interface HttpStack {

    @Throws(IOException::class, AuthFailureError::class)
    fun performRequest(request: Request<*>, additionalHeaders: Map<String, String>): HttpResponse
}