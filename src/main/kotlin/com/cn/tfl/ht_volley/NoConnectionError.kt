package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/24.
 */
class NoConnectionError : NetworkError {

    constructor() : super()

    constructor(reason: Throwable) : super(reason)
}