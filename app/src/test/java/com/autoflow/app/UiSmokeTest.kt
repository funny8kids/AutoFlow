package com.autoflow.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.core.ConstraintSpec
import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.Macro
import com.autoflow.app.core.TriggerSpec
import com.autoflow.app.core.Variable
import com.autoflow.app.ui.AiScreen
import com.autoflow.app.ui.AiState
import com.autoflow.app.ui.AutoFlowTheme
import com.autoflow.app.ui.BackupScreen
import com.autoflow.app.ui.BlocksScreen
import com.autoflow.app.ui.GlobalVarsScreen
import com.autoflow.app.ui.MacroEditorScreen
import com.autoflow.app.ui.QuickPanelScreen
import com.autoflow.app.ui.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 界面冒烟：逐个渲染主要页面，捕获 Compose 组合期异常。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = AutoFlowApp::class)
class UiSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sampleMacro() = Macro(
        name = "测试宏",
        description = "冒烟用",
        triggers = listOf(TriggerSpec("manual", emptyMap())),
        actions = listOf(
            ActionSpec("toast", mapOf("text" to "hi")),
            ActionSpec("loop", mapOf("times" to "2")),
            ActionSpec("logMessage", mapOf("text" to "x")),
            ActionSpec("endloop"),
        ),
        constraints = listOf(ConstraintSpec("screenOn", emptyMap())),
        variables = listOf(Variable("x", value = "1")),
    )

    @Test
    fun macroEditorRenders() {
        compose.setContent {
            AutoFlowTheme { MacroEditorScreen(sampleMacro(), onBack = {}, onSave = {}) }
        }
        compose.waitForIdle()
    }

    @Test
    fun quickPanelRenders() {
        compose.setContent {
            AutoFlowTheme { QuickPanelScreen(listOf(sampleMacro()), onRun = {}, onBack = {}) }
        }
        compose.waitForIdle()
    }

    @Test
    fun blocksScreenRenders() {
        val block = sampleMacro().copy(name = "块", isBlock = true)
        compose.setContent {
            AutoFlowTheme { BlocksScreen(listOf(block), onCreate = {}, onOpen = {}, onBack = {}) }
        }
        compose.waitForIdle()
    }

    @Test
    fun globalVarsRenders() {
        compose.setContent {
            AutoFlowTheme {
                GlobalVarsScreen(
                    vars = listOf(Variable("a", value = "1")),
                    onSave = {}, onDelete = {}, onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun backupScreenRenders() {
        compose.setContent {
            AutoFlowTheme {
                BackupScreen(
                    lastMessage = "ok",
                    onExportText = { "" },
                    onImport = { _, _ -> },
                    onMessage = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun settingsScreenRenders() {
        compose.setContent {
            AutoFlowTheme {
                SettingsScreen(
                    initialKey = "",
                    initialBaseUrl = "https://api.deepseek.com",
                    initialModel = "",
                    models = listOf("m1", "m2"),
                    modelsLoading = false,
                    onFetchModels = { _, _ -> },
                    onSave = { _, _, _ -> },
                    onOpenBackup = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aiScreensRender() {
        compose.setContent {
            AutoFlowTheme {
                AiScreen(
                    state = AiState.Success(sampleMacro(), "测试"),
                    configured = true,
                    onGenerate = {},
                    onGenerateTemplates = {},
                    onSaveAll = {},
                    onSave = {},
                    onEdit = {},
                    onOpenSettings = {},
                    onBack = {},
                    onReset = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aiIdleWithoutKeyRenders() {
        compose.setContent {
            AutoFlowTheme {
                AiScreen(
                    state = AiState.Idle,
                    configured = false,
                    onGenerate = {},
                    onGenerateTemplates = {},
                    onSaveAll = {},
                    onSave = {},
                    onEdit = {},
                    onOpenSettings = {},
                    onBack = {},
                    onReset = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun applicationInstanceIsUsable() {
        val app = ApplicationProvider.getApplicationContext<AutoFlowApp>()
        app.logs.info("test", "hello")
        app.store.macros.value
    }
}
