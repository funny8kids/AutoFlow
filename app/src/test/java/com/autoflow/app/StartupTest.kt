package com.autoflow.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 启动冒烟测试：在 JVM 里真实创建 Application 与 MainActivity，
 * 用于捕获“装到手机打不开”这类启动崩溃。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = AutoFlowApp::class)
class StartupTest {

    @Test
    fun applicationInitializes() {
        val app = ApplicationProvider.getApplicationContext<AutoFlowApp>()
        assertNotNull(app.store)
        assertNotNull(app.engine)
        assertNotNull(app.triggers)
    }

    @Test
    fun mainActivityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
        }
    }
}
