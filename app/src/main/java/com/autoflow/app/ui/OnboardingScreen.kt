package com.autoflow.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoflow.app.platform.AutoAccessibilityService
import com.autoflow.app.core.CapabilityDetector
import com.autoflow.app.core.TemplateInfo
import com.autoflow.app.core.Templates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 新手引导：三页向导（欢迎 → 权限 → 推荐模板）。
 * 仅首次启动展示；完成后写入 prefs，不再出现。
 */
@Composable
fun OnboardingScreen(
    installedNames: Set<String>,
    onInstallTemplate: (TemplateInfo) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }

    // 权限状态实时刷新
    LaunchedEffect(pagerState.currentPage, tick) {
        // 单元测试里禁止无限轮询：Compose 永远等不到 idle，会让 CI 挂十几分钟
        if (!AnimationPolicy.infiniteAnimationsEnabled) return@LaunchedEffect
        while (true) {
            tick++
            delay(2000)
        }
    }

    val a11yOk = remember(tick) {
        runCatching { AutoAccessibilityService.isEnabled(context) }.getOrDefault(false)
    }
    val notifOk = remember(tick) {
        runCatching { CapabilityDetector.notificationListenerEnabled(context) }.getOrDefault(false)
    }
    val buttonLabel = when (pagerState.currentPage) {
        0 -> "开始"
        1 -> "下一步"
        else -> "开始使用 AutoFlow"
    }

    fun openA11y() = context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    fun openNotif() = context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0C1226), Color(0xFF070B18))),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：跳过
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = onDone) { Text("跳过", color = Color(0xFF9AA6C8)) }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> PermissionPage(a11yOk, notifOk, { openA11y() }, { openNotif() })
                    else -> TemplatePage(installedNames, onInstallTemplate)
                }
            }

            // 页点 + 按钮
            Row(
                Modifier.fillMaxWidth().padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 5.dp)
                            .size(if (pagerState.currentPage == i) 9.dp else 6.dp)
                            .background(
                                if (pagerState.currentPage == i) Brand.Primary else Color(0xFF39415E),
                                CircleShape,
                            ),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.weight(1f),
                    ) { Text("上一步") }
                }
                Button(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage < 2) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else onDone()
                        }
                    },
                    modifier = Modifier.weight(2f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(buttonLabel)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 第 1 页：品牌欢迎（点阵波场背景，与启动页同语言）。 */
@Composable
private fun WelcomePage() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1.1f))
        Box(
            Modifier
                .size(96.dp)
                .background(
                    Brush.linearGradient(listOf(Brand.Primary, Brand.Secondary)),
                    RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text("AutoFlow", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "用「触发器 → 条件 → 动作」\n把手机上的重复操作变成自动化",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9AA6C8),
        )
        Spacer(Modifier.weight(1.6f))
    }
}

/** 第 2 页：权限一键引导。 */
@Composable
private fun PermissionPage(
    a11yOk: Boolean,
    notifOk: Boolean,
    openA11y: () -> Unit,
    openNotif: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("开启两项权限", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "AutoFlow 的大部分能力依赖下面两项系统权限。现在开启，后面的宏才能顺利运行。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA6C8),
            )
            Spacer(Modifier.height(14.dp))
        }
        item {
            PermRow(
                title = "无障碍服务",
                desc = "UI 点击、滑动、读屏类动作需要它",
                ok = a11yOk,
                onClick = openA11y,
            )
        }
        item {
            PermRow(
                title = "通知使用权",
                desc = "监听通知的接收与移除",
                ok = notifOk,
                onClick = openNotif,
            )
        }
        item {
            Text(
                "日历、位置等权限会在用到对应功能时再请求。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6B7899),
            )
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) Color(0xFF12352C) else Color(0xFF161F3A),
        ),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        if (ok) Color(0xFF33E1C4) else Color(0xFF2A3557),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Default.Check else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (ok) Color.Black else Color(0xFF9AA6C8),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA6C8))
            }
            Text(
                if (ok) "已开启" else "去开启",
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) Brand.Secondary else Brand.Primary,
            )
        }
    }
}

/** 第 3 页：推荐模板一键安装。 */
@Composable
private fun TemplatePage(installedNames: Set<String>, onInstall: (TemplateInfo) -> Unit) {
    val picks = remember {
        listOf(
            Templates.all.first { it.id == "torch_blink" },
            Templates.all.first { it.id == "silent_at_night" },
            Templates.all.first { it.id == "office_wifi_silent" },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Brand.Primary)
                Spacer(Modifier.width(8.dp))
                Text("先装三个常用宏", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "一键安装，随时可以在宏列表里改。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA6C8),
            )
            Spacer(Modifier.height(12.dp))
        }
        items(picks.size) { i ->
            val info = picks[i]
            val installed = info.name in installedNames
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F3A)),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !installed) { onInstall(info) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(info.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(info.description, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA6C8))
                    }
                    Text(
                        if (installed) "已安装 ✓" else "安装",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (installed) Brand.Secondary else Brand.Primary,
                    )
                }
            }
        }
    }
}
