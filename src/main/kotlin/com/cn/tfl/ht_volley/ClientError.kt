package com.cn.tfl.ht_volley

/**
 * Created by Happiness on 2017/5/24.
 */
class ClientError : ServerError {

    constructor(networkResponse: NetworkResponse) : super(networkResponse)

    constructor() : super()
}