package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/22.
 */
open class VolleyError: Exception {

    constructor()

    var networkResponse: NetworkResponse? = null

    var networkTimeMs: Long? = 0

    constructor(exceptionMessage: String) : super(exceptionMessage)

    constructor(exceptionMessage: String, reason: Throwable) : super(exceptionMessage, reason)

    constructor(reason: Throwable) : super(reason)

    constructor(response: NetworkResponse) {
        networkResponse = response
    }

    fun setNetworkTimeMs(networkTimeMs: Long) {
        this.networkTimeMs = networkTimeMs
    }

    fun getNetworkTimeMs(): Long {
        return networkTimeMs!!
    }
}