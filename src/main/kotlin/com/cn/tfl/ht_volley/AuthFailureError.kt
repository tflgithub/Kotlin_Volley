package com.cn.tfl.ht_volley

import android.content.Intent


/**
 * Created by Happiness on 2017/5/23.
 */
class AuthFailureError : VolleyError {

    constructor()

    var mResolutionIntent: Intent? = null

    constructor(message: String) : super(message)

    constructor(networkResponse: NetworkResponse) : super(networkResponse)

    constructor(message: String, reason: Throwable) : super(message, reason)

    constructor(resolutionIntent: Intent) {
        mResolutionIntent = resolutionIntent
    }

    fun getResolutionIntent(): Intent? {
        return mResolutionIntent
    }

    override fun getLocalizedMessage(): String {
        if (mResolutionIntent != null) {
            return "User needs to (re)enter credentials."
        }
        return super.getLocalizedMessage()
    }
}