package com.cn.tfl.ht_volley

import android.os.Handler
import java.util.concurrent.Executor

/**
 * Created by Happiness on 2017/5/23.
 */
class ExecutorDelivery : ResponseDelivery {

    override fun postResponse(request: Request<*>, response: Response<*>) {
        postResponse(request, response, null)
    }

    override fun  postResponse(request: Request<*>, response: Response<*>, runnable: Runnable?) {
        request.markDelivered()
        mResponsePoster!!.execute(ResponseDeliveryRunnable(request, response, runnable))
    }

    override fun  postError(request: Request<*>, error: VolleyError) {
        var response: Response<*> = Response.error(error)
        mResponsePoster!!.execute(ResponseDeliveryRunnable(request, response, null))
    }

    /** 用于发送响应，通常是主线程。  */
    private var mResponsePoster: Executor? = null


    constructor(handler: Handler) {
        // 使一个只包装处理程序的执行器。
        mResponsePoster = Executor { command -> handler.post(command) }
    }

    class ResponseDeliveryRunnable : Runnable {
        private var mRequest: Request<*>
        private var mResponse: Response<*>
        private var mRunnable: Runnable? = null

        constructor(request: Request<*>, response: Response<*>, runnable: Runnable?) {
            mRequest = request
            mResponse = response
            mRunnable = runnable
        }

        override fun run() {
            // 如果此请求已取消，请完成并不提供。
            if (mRequest.isCanceled()!!) {
                return
            }
            //取决于提供正常的响应或错误
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result!!)
            } else {
                mRequest.deliverError(mResponse.error!!)
            }

            //如果这是一个中间响应，请添加一个标记，否则我们完成了并且请求可以完成。
            if (mResponse.intermediate!!) {
            } else {
                mRequest.finish("done")
            }

            //如果我们已经提供了一个运送后运行的，运行它。
            if (mRunnable != null)
                mRunnable!!.run()
        }
    }
}

