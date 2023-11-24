package com.google.android.exoplayer2.util

import android.util.Log

class CosTimeUtil {
    companion object{
        fun start(funcName: String): Long {
            val createCodecStartTime = System.currentTimeMillis()
            Log.i("wangyao", "$funcName start : $createCodecStartTime")
            return createCodecStartTime
        }

        fun end(funcName: String, startTime: Long) {
            val createCodecEndTime = System.currentTimeMillis()
            Log.i("wangyao", "$funcName end : " + createCodecEndTime + ", cosTime: " + (createCodecEndTime - startTime))
        }

        var mainStepTime = 0L

        fun mainStepStart() {
            mainStepTime = System.currentTimeMillis()
        }

        fun mainStep(funcName: String) {
            val curStepTime = System.currentTimeMillis()
            val cosTime = curStepTime - mainStepTime //算出和上个步骤的间隔时间
            mainStepTime = curStepTime
            Log.i("wangyao", "$funcName step : $cosTime")
        }
    }
}