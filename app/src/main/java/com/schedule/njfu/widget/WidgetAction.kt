package com.schedule.njfu.widget

import androidx.glance.action.ActionParameters

object WidgetAction {
    const val OPEN_APP = "com.schedule.njfu.action.OPEN_APP"

    /**
     * glance 1.1 的 [androidx.glance.action.actionStartActivity] 没有 actionName 参数
     * （1.2 才有），动作名通过 ActionParameters 携带。
     */
    val OPEN_APP_NAME_KEY = ActionParameters.Key<String>("open_app")
}
