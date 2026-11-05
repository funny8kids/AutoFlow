package com.autoflow.app.ui

/**
 * 无限动效 / 无限轮询开关。
 *
 * Robolectric JVM 单元测试中，Compose 的 waitForIdle() 与 ActivityScenario
 * 需要主线程与组合进入 idle 状态；无限动画、无限重组会持续投递帧与重组，
 * 导致测试等待十几到几十分钟（v2.16.0 起 CI 回归）。
 *
 * 单元测试通过 system property 关闭（见 app/build.gradle.kts 的 testOptions），
 * 正式 APK 不设置该属性，视觉效果完全不受影响。
 */
object AnimationPolicy {
    val infiniteAnimationsEnabled: Boolean
        get() = System.getProperty("autoflow.disableInfiniteAnimations") != "true"
}
