# MacroDroid 界面结构逆向规格（AutoFlow 复刻依据）

> 来源：`~/rea/apktool_out`（apktool 反编译资源）+ `AndroidManifest.xml` + `res/xml/preferences.xml`
> 本文只记录**信息架构与交互结构**（复刻依据），视觉由 AutoFlow 自行设计。

## 1. 主框架

```
DrawerLayout
├── 左侧抽屉（35 项，见 §2）
├── 主内容 FragmentContainerView
│   ├── Home（Dashboard，Compose）        ← 底部 Tab 1
│   ├── Macros（宏列表）                  ← 底部 Tab 2
│   ├── Templates（模板库）               ← 底部 Tab 3
│   └── Settings（设置）                  ← 底部 Tab 4
└── BottomNavigationView：Home | Macros | Templates | Settings
```

Toolbar 右侧有 **PowerPillView**（供电/启用状态胶囊，显示宏总开关与运行状态）。

## 2. 左侧抽屉（drawer_view.xml，35 项）

分 6 组：

| 组 | 条目 |
|---|---|
| 升级 | Upgrade to Pro |
| 扩展 | Extras · StopClub Earnings |
| 排障 | Troubleshooting |
| 卸载 | Uninstall MacroDroid |
| 功能 | Action Blocks · AI Macro Builder · Auto Backup · Cell Towers · Categories · Export/Import · Forum · Geofences · MacroDroid Drawer · MacroDroid Modes · Notification Bar Options · Plugins · Quick Settings Tiles · Remote Control · Shared Scenes · Stopwatches · System Log · User Log · Variables |
| 关于/帮助 | MacroDroid Videos · Widgets · Wiki · Dontkillmyapp.com · Huawei support thread · Xiaomi support thread · Translations · Invite Friends · Accessibility services · Privacy policy · Version History |

> 复刻取舍：保留 Action Blocks / AI / Backup / Categories / Geofences / Variables / Logs / Modes / Widgets / Plugins / 帮助 等，去掉与商业运营相关的项（Pro 升级、StopClub、Invite Friends）。

## 3. 宏列表页（MacroList）

**顶栏**：Filter and sort ｜ Search ｜ ⋮（Delete all / Import from nearby）

**筛选**（macro_list_state_filter_popup.xml）：All / Enabled only / Disabled only / Errors only / Running only

**排序与显示**（macro_list_menu.xml 完整项）：

- Set max lines
- Expand categories / Collapsed by default
- Show/hide details：Show descriptions · Show last activated · Show last edited · Show macro details
- Card style：Tonal · Tinted · Bold · Header · Outlined · Compact · Compact Tinted · Compact Bold
- Category style：Standard · Filled
- Show filter bar
- Group categories on/off

**条目卡片**：名称 + 描述 + 触发器/动作/约束统计 + 最近激活 + 启用开关

**长按菜单**（edit_macros_menu.xml，32 项）：Disable macro · Save changes · Enter debug mode · AI Assistant · Description · Search · Select category · Add as favourite · Share / Share macro / Share to nearby / Share as image · Clone macro · Delete macro · Test actions / Test macro · Variables / Clear All Local Variables / Clear Clipboard · Settings · Logging level（Full/Invocation only/No logging）· Edit screen style（Tonal/Bold classic/Show line numbers/Local variable display/Use small text）· Show log · Add disabled

## 4. 宏编辑页（activity_edit_macro.xml）

结构（从上到下）：

```
CollapsingToolbarLayout（AppBarLayout）
├── Toolbar（返回）
├── 宏名称（macroNameText）
├── 描述（ExpandableLayout：descriptionExpandable / macroDescription）
├── 搜索条（ExpandableLayout：search_text + 上/下按钮 + 展开/收起）
├── 禁用横幅（disabledLabel + disabledLabelText + disabledLabelEnableSwitch）
└── ComposeView（新版编辑区）

Section: Triggers（edit_macro_trigger_header_item.xml）
├── 标题 "Triggers" + 计数（sectionCount）
├── 粘贴触发器（pasteTriggerButton）
├── 重排（reorderTriggersButton）
└── 添加（edit_macro_addTriggerButton）

Section: Actions（edit_macro_action_header_item.xml）
├── 标题 "Actions" + 计数
├── 粘贴动作 / 重排 / 添加
└── 动作条目（macro_edit_entry*.xml，可拖拽/展开）

Section: Constraints（edit_macro_constraint_header_item.xml）
├── 标题 "Constraints" + 计数
├── **AND/OR 切换**（constraintAndOrSpinner）
├── 粘贴 / 重排 / 添加
└── 空状态（edit_macro_constraint_empty.xml）

Section: Local Variables（edit_macro_local_variable_header_item.xml + empty）
Section: Ask AI（edit_macro_ask_ai_item.xml）
```

**关键洞察**：约束不只是"全部满足"，MacroDroid 支持 **AND / OR 切换**（对应我们 constraintAndOrSpinner）。这是当前 AutoFlow 缺失的。

## 5. 添加触发器 / 动作 / 约束（selectableitemlist）

- Activity：`AddTriggerActivity` / `AddActionActivity` / `AddConstraintActivity`
- 布局：`add_selectable_item_view.xml`（列表 + 分类头 + 帮助面板）
- 顶栏菜单：**Search** ｜ **Toggle help**
- 分类头：`item_category.xml`（categoryName）
- 条目标题下有简介（help text 可开关）

## 6. 设置（preferences.xml，136 项）

顶级顺序（分组见下）：

1. **Pro 设备卡**（商业项，跳过）
2. 电池优化白名单
3. 无障碍服务保活
4. HTTP Server（端口/超时/网页/日志）
5. 深色模式（跟系统/浅色/深色）
6. AMOLED 纯黑
7. **UI Style**：Home screen tiles · Macro list · Category style · Edit macro and action block screens · Action block list · Geofence map dark mode
8. Magic Text 括号风格
9. 指纹手势 / 长按配置
10. Root 功能 / 实验特性 / What's New / 从最近任务隐藏
11. Crash Logging and Analytics
12. Help Cards（恢复/全部隐藏）
13. Edit Categories / MacroDroid Modes
14. TTS 语言 / 音频流（朗读、播放音效）
15. Notification Options（定位警告、通知渠道）
16. **Trigger Options**（按触发器分组的采样率/省电设置）
17. **Action Options**
18. 其余：Widget、插件、远程控制、备份等

## 7. AutoFlow 复刻清单（差异 → 待做）

| # | 项目 | 状态 |
|---|---|---|
| 1 | 底部导航 Home/Macros/Templates/Settings | 待做（当前无 Tab） |
| 2 | 左侧抽屉（功能入口聚合） | 待做 |
| 3 | 宏列表：筛选状态、排序、卡片/分类样式、分组 | 待做 |
| 4 | 宏列表长按菜单（禁用/克隆/分享/测试/日志级别…） | 部分 |
| 5 | 宏编辑：三段式 + 计数 + 粘贴 + 重排 + 添加 | 待做 |
| 6 | 宏编辑：描述展开、搜索定位、禁用横幅 | 待做 |
| 7 | 约束 **AND/OR** 切换 | 待做（引擎需支持） |
| 8 | 添加动作：分类头 + 搜索 + 帮助开关 | 待做 |
| 9 | 设置：分组与 136 项结构 | 部分（仅 AI/备份） |
| 10 | Home Dashboard（运行概览） | 待做 |
| 11 | 模板库（Templates） | 待做 |
