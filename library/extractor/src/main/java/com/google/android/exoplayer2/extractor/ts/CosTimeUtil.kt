package com.google.android.exoplayer2.extractor.ts

import android.util.Log

class CosTimeUtil {
    companion object{
        fun start(funcName: String): Long {
            val startTime = System.currentTimeMillis()
            Log.i("wangyao", "$funcName start ")
            return startTime
        }

        fun end(funcName: String, startTime: Long) {
            val endTime = System.currentTimeMillis()
            Log.i("wangyao", "$funcName end " + ", cosTime: " + (endTime - startTime))
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