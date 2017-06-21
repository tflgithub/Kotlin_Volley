package com.cn.tfl.ht_volley.toolbox

import com.cn.tfl.ht_volley.NetworkResponse
import com.cn.tfl.ht_volley.Request
import com.cn.tfl.ht_volley.Response
import java.io.UnsupportedEncodingException


/**
 * Created by Happiness on 2017/5/24.
 * 封装一个用于在给定URL中以String形式检索响应正文的请求。
 */
class StringRequest : Request<String> {
    override fun deliverResponse(response: Any) {
        mListener!!.onResponse(response.toString())
    }

    private var mListener: Response.Listener<String>? = null

    constructor(method: Int, url: String, listener: Response.Listener<String>, errorListener: Response.ErrorListener) : super(method, url, errorListener) {
        this.mListener = listener
    }

    constructor(url: String, listener: Response.Listener<String>, errorListener: Response.ErrorListener) : this(Method.GET, url, listener, errorListener)

    override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
        var parsed: String
        try {
            parsed = String(response.data!!)
        } catch (e: UnsupportedEncodingException) {
            parsed = String(response.data!!)
        }

        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response)!!)
    }
}


