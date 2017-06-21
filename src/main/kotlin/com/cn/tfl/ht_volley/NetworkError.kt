package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/24.
 */
open class NetworkError : VolleyError {

    constructor(cause: Throwable) : super(cause)

    constructor(networkResponse: NetworkResponse) : super(networkResponse)

    constructor() : super()
}