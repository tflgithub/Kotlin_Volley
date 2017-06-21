package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/22.
 */
interface Network {
    @Throws(VolleyError::class)
    fun performRequest(request: Request<*>): NetworkResponse
}