# 5. 原生宿主、数据注入与入口

## 旧实现情况

- `Screen` 密封类注册的页面都在主界面脚手架内，没有全屏沉浸的机制
- 已有独立 Activity 的先例：`CrashReportActivity`、`DataRecoveryActivity`
- 依赖中已有 `androidx.webkit`，可以使用 `WebViewAssetLoader`
- 聊天分享图片已有保存到相册的实现 `saveShareImageToGallery`
- 侧边栏头部只有品牌名和网络状态，右侧是空的

## 意图

新增 `ui/features/annualreport/`：

- `AnnualReportActivity`：不导出，全屏沉浸，内嵌 WebView；配置变化时不重建，保住翻页进度
- `AnnualReportLauncher.launch(context, year, dark)`：唯一的启动方法；`AnnualReportEdition.YEAR` 是当前打包的年份
- `AnnualReportWebSupport.kt`：资源与数据的路径处理、离线拦截和原生桥
- `entry/AnnualReportEntryBadge.kt`：侧边栏入口徽章；`entry/AnnualReportBadgeArt.kt` 由脚本生成

### 页面加载与数据注入

- 用 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/annual-report/index.html` 加载前端，只开放 `assets/annual-report/` 目录，拒绝路径里的 `..`
- `/report-data/user.json`：Activity 创建时就开始采集，handler 在 WebView 的后台线程上等待采集完成再响应；采集失败返回带原因的 500，页面重试时重新采集
- `/report-data/market.json`：用户点开插件市场卡片时才联网查询
- 安装包里缺少前端资源时，首页返回一段说明，提示先运行 `npm run build:annualreport`

### 安全与隐私

- `shouldInterceptRequest` 中，资源加载器之外的请求一律返回 403；`shouldOverrideUrlLoading` 拦下所有跳转
- 页面带 CSP：`default-src 'self'`
- 关闭 WebView 的文件访问和内容访问
- 用户数据只在内存中，不写缓存文件；海报截图写在应用缓存目录，通过 FileProvider 交给相册或分享

### 原生桥

`OperitAnnualReport` 只有三个方法：

- `close()`：关闭报告
- `haptic(kind)`：`tick`、`tap`、`success`、`heavy` 对应 API 26 可用的触感常量
- `capturePoster(action, left, top, width, height, ratio)`：按页面给出的 CSS 坐标和像素比，用 `PixelCopy` 截取海报区域，保存到相册或打开系统分享，结果回调给页面

### 入口

入口放在侧边栏头部，品牌名和网络状态的右侧：一张代码绘制的手绘徽章，写着 `Operit`、年度报告和年份，带纸胶带、波浪线、箭头和闪光。

- 侧边栏打开时播放动画：线条在三个手绘版本之间轮换，闪光一明一暗，纸片轻轻摇摆；收起时停在第一帧，不占用绘制
- 按下有弹性缩放，点按启动报告；报告跟随应用当前的深浅色
- 无障碍描述为"打开 2026 年度报告"
- 2027-01-03 00:00 UTC 之前徽章不出现，由 `AnnualReportEdition.UNLOCK_AT` 控制，判断用设备系统时钟，随抽屉开合重算
- 开发测试入口：关于页连续点击顶部的 Operit 图标 10 次，直接打开报告，不受解锁时刻限制；实现在 `entry/AnnualReportDevEntry.kt`

## 期待的新实现情况

[DONE] 点侧边栏徽章即可全屏打开报告，报告在飞行模式下除插件市场卡片外完整可用。编译和打包已在发布版构建中验证过，见 [验证](7_verification.md)。
