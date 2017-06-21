package com.cn.tfl.ht_volley.toolbox

import java.util.*

/**
 * Created by Happiness on 2017/5/24.
 */

/**
 * 对象的源码和存储库
 * 其目的是将这些缓冲区提供给需要在短时间内使用它们的消费者处理它们。
 * 简单地以常规方式创建和布置这些缓冲器可以把Android上存在相当大的堆积和垃圾收集延迟，
 * 缺乏良好的管理短命的堆对象。 以一种形式来折算一些记忆可能是有利的
  *永久分配缓冲区池，以获得堆性能改进
 *
 */
class ByteArrayPool {


    /**缓冲池，由最后使用和缓冲区大小排列  */
    private val mBuffersByLastUse = LinkedList<ByteArray>()

    private val mBuffersBySize = ArrayList<ByteArray>(64)

    /** 池中缓冲区的总大小  */
    private var mCurrentSize = 0

    /**
     * 池中缓冲区的最大聚合大小。 旧的缓冲区被丢弃以保持在这个极限。
     */
    private var mSizeLimit: Int = 0

    /** 按大小比较缓冲区 */
    val BUF_COMPARATOR: Comparator<ByteArray> = Comparator { lhs, rhs -> lhs.size - rhs.size }

    /**
     * @param sizeLimit 池的最大大小（以字节为单位）
     */
    constructor(sizeLimit: Int) {
        mSizeLimit = sizeLimit
    }

    /**
     * 如果一个缓冲区可用于请求的大小，则从池中返回缓冲区，或者如果一个池不可用则分配一个缓冲区。
     *
     * @param len 请求的缓冲区的最小大小（以字节为单位）。 返回的缓冲区可能较大
     *
     * @return 始终返回一个byte []缓冲区.
     */
    @Synchronized fun getBuf(len: Int): ByteArray {

        for (i in 0..mBuffersBySize.size - 1) {
            val buf = mBuffersBySize[i]
            if (buf.size >= len) {
                mCurrentSize -= buf.size
                mBuffersBySize.removeAt(i)
                mBuffersByLastUse.remove(buf)
                return buf
            }
        }
        return ByteArray(len)
    }


    /**返回缓冲区到池，如果池超过其allotte大小，丢弃旧缓冲区。
     * @param buf 缓冲区返回到池。
     */
    @Synchronized fun returnBuf(buf: ByteArray?) {
        if (buf == null || buf.size > mSizeLimit) {
            return
        }
        mBuffersByLastUse.add(buf)
        var pos = Collections.binarySearch(mBuffersBySize, buf, BUF_COMPARATOR)
        if (pos < 0) {
            pos = -pos - 1
        }
        mBuffersBySize.add(pos, buf)
        mCurrentSize += buf.size
        trim()
    }


    /**
     * 从池中删除缓冲区，直到其大小限制。
     */
    @Synchronized private fun trim() {
        while (mCurrentSize > mSizeLimit) {
            val buf = mBuffersByLastUse.removeAt(0)
            mBuffersBySize.remove(buf)
            mCurrentSize -= buf.size
        }
    }
}