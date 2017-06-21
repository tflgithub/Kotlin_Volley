package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/24.
 */
open class ServerError : VolleyError {

    constructor(networkResponse: NetworkResponse) : super(networkResponse)

    constructor() : super()

}