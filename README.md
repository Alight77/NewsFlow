# NewsFlow · 中文新闻聚合

一个使用 Kotlin、Jetpack Compose、Retrofit 和 Room 实现的中文新闻聚合阅读 App。项目以“完成一个可解释、可测试的网络数据闭环”为目标，重点处理请求竞争、Refresh / Pagination 状态、网络错误映射、Remote + Local 数据组合与受控缓存，而不是单纯完成一次 REST API 调用。

## 功能概览

- 首页聚合 `综合 / 科技 / 商业 / 科学 / 健康` 五类中文新闻。
- 支持下拉刷新与手写分页；分页加载、失败重试、尾页和重复数据分别建模，已有内容不会因追加失败被清空。
- 支持中文关键词自动搜索，包含空白输入过滤、`400 ms` debounce、旧请求取消与 latest-wins 结果保护。
- 新闻详情展示标题、来源、发布时间、摘要和内容预览，并可跳转浏览器查看原文。
- 支持 Home、Search、Detail、Favorites 多入口收藏；收藏快照通过 Room 持久化，可离线查看。
- 按分类缓存最近一次成功获取的首页第一页；缓存可先展示，再根据 `2 h` freshness 规则决定是否刷新网络。
- 对连接失败、超时、非法请求、鉴权 / 配额、限流、JSON / 数据异常等错误统一映射为业务错误。
- 支持浅色 / 深色主题，并针对旋转、返回页面、软键盘与错误状态编写设备 / Compose UI 回归测试。


## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- MVVM
- Kotlin Coroutines + Flow / StateFlow / SharedFlow
- Navigation Compose
- Retrofit + OkHttp + Moshi
- Room + SQLite + KSP
- Coil Compose
- JUnit + kotlinx-coroutines-test + MockWebServer
- Room Instrumented Test + Compose UI Test + Android Lint

## 架构与数据流

```text
                        GNews API
                           ↓
                 Retrofit / OkHttp / Moshi
                           ↓
                    GNewsRepository
                           ↓
                  Article / NewsPageResult
                     ↙             ↘
             Home / Search       Room
               ViewModel       ↙      ↘
                  ↓       Favorites   Home Cache
                  └──────────┬──────────┘
                             ↓
                      Page-level UI State
                             ↓
                        Compose UI
```

- Remote 层将 GNews DTO 映射为内部 `Article` / `NewsPageResult`，页面不直接依赖网络响应结构。
- `HomeViewModel` 负责分类、首次加载、Refresh、Pagination、缓存恢复与请求竞争；`SearchViewModel` 负责输入流与 latest-wins 搜索语义。
- 收藏由单一 `FavoritesViewModel` 观察 Room 中的文章和 ID，再把统一收藏状态提供给 Home、Search、Detail 和 Favorites。
- `NewsApplication` 负责 Room Database 与本地 Repository 的应用级生命周期；远端 Repository 在 `MainActivity` 创建，页面 ViewModel 通过手动 Factory 注入。
- 当前规模下不引入 Hilt、多模块或机械式 UseCase 层，优先保持依赖边界和实际业务规则清晰。

## 关键设计

### 请求竞争与 latest-wins

Home 分类切换和 Search 连续输入都会产生“旧请求晚于新请求返回”的竞争。

Home 同时使用 `Job.cancel()`、单调递增的 `requestVersion` 和当前 `NewsCategory` 校验；Search 使用 `Job.cancel()`、`requestVersion` 与规范化 Query 校验。即使底层调用没有及时响应取消，失效结果仍不能覆盖当前页面状态。

Search 对 `trim()` 后的 Query 进行比较，空白输入不会请求网络；有效输入等待 `400 ms` 后再发起自动搜索，重复的规范化 Query 不产生无意义请求。

### Loading、Refresh 与 Pagination 状态分离

Home 不把所有网络请求压缩成一个 `isLoading`：

```text
首次加载
→ 当前没有可展示内容
→ Loading / Empty / Error / Content

Refresh
→ 保留已有内容
→ isRefreshing + refreshError

Pagination
→ 保留已有内容
→ Idle / Loading / Error / ManualContinue / EndReached
```

Refresh 失败只记录非阻断错误，原列表继续显示；分页失败只影响列表尾部，Retry 继续请求正确的下一页。

### 手写分页与动态数据去重

V1 使用手写 Pagination，以显式处理页码、并发请求、失败重试、去重和尾页判断。

- GNews 每页请求 `10` 条；
- 文章根据稳定的 `Article.id` 去重；
- `EndReached` 根据 Remote 原始返回条数判断，而不是去重后的追加数量；
- 同时遵守 GNews 前 `1000` 条的可访问边界；
- 如果一页原始数据仍然存在，但去重后没有新增文章，进入手动继续状态，避免自动触底形成连续请求循环。

第一页 Refresh 成功后重新建立分页基线；缓存恢复的第一页在完成一次网络刷新前不会直接从 Page 2 继续请求。

### Remote 数据映射与 Error Mapping

GNews DTO 的字段按外部数据处理，不假定所有内容永久完整：

- `id` 和 `title` 缺失时丢弃该 Article；
- URL 先校验 HTTP(S) 合法性，无效链接降级为 `null`；
- `publishedAt` 解析为 `Instant`，解析失败不会让整个列表崩溃；
- 原始列表非空但所有 Article 都无法映射时，视为 `INVALID_DATA`。

Repository 将底层异常映射为受控 `NewsError`：

```text
400  → INVALID_REQUEST
401  → AUTHENTICATION
403  → QUOTA_EXCEEDED
429  → RATE_LIMITED

SocketTimeoutException → TIMEOUT
IOException            → CONNECTION
JSON / Mapping Error   → INVALID_DATA
其他异常                → UNKNOWN
```

`CancellationException` 保持向上传播，不被误判为普通网络失败；UI 只消费业务错误，不直接显示底层 `Throwable.message` 或服务端错误正文。

### 收藏状态与跨页面一致性

收藏使用独立的 `favorite_articles` 表保存 Article 展示快照。

`FavoritesViewModel` 同时观察：

```text
收藏 Article 列表
收藏 Article ID 集合
pending 写入集合
```

Home、Search、Detail 和 Favorites 都消费同一份收藏 ID 状态，因此同一 Article 不会在不同页面出现互相矛盾的收藏标记。

收藏写入期间该 Article 进入 pending 状态，阻止同一条目重复操作；写入失败通过一次性事件触发 Snackbar，Room 中的持久化状态仍作为最终事实来源。

### 首页首屏缓存与 freshness

Room 使用两张独立表：

```text
home_first_page_articles
→ Category + position 对应的文章快照

home_first_page_cache_metadata
→ Category 对应的 fetchedAt
```

进入分类时：

```text
存在缓存
→ 立即展示缓存
→ 判断 fetchedAt

缓存 < 2 h
→ 普通返回不强制重复请求

缓存 ≥ 2 h
→ 保留缓存并刷新网络

用户主动 Refresh
→ 始终请求网络
```

成功的空第一页仍会保存 metadata，从而区分“服务端成功返回空列表”和“该分类从未缓存”。

缓存写入通过 `Mutex` 和请求版本校验串行保护；如果 Room 写缓存失败，已经成功展示的网络结果不会被反向改成 Error。

### 数据库演进

`NewsDatabase` 当前版本为 `2`。

- 数据库最初只保存收藏文章。
- `MIGRATION_1_2` 新增首页第一页文章表与缓存 metadata 表，不破坏已有收藏数据。
- Room schema 导出到 `app/schemas/`，迁移路径通过仪器测试覆盖。

Favorites 与 Home Cache 使用不同表和不同生命周期：缓存可以被替换，用户主动收藏的数据不会因缓存刷新或过期而被删除。

### 生命周期与详情快照

底部导航使用 Navigation Compose 的 `saveState / restoreState`；Home、Search 和 Favorites 的 ViewModel 创建在根 `NewsApp` 层级，Activity Recreation 后仍可保留当前业务状态。

详情路由只传递 `articleId`，打开文章时由 `ArticleSessionViewModel` 保存当前会话的 Article snapshot；旋转后可以继续恢复详情，而不把完整 Article 序列化进 Navigation Route。

当前只保证配置变更和 App Session 内的状态恢复，不把完整 Process Death 恢复作为 V1 范围。

## 项目结构

```text
app/src/main/java/io/github/alight77/news
├── data/
│   ├── local/          # Room Entity、DAO、Migration、Favorites 与 Home Cache
│   └── remote/         # Retrofit Service、DTO、Mapping、Network Client、Repository
├── domain/
│   ├── model/          # Article、Category、Page Result、业务错误
│   └── repository/     # Remote / Favorite / Cache 抽象
├── ui/
│   ├── article/        # 新闻详情与会话快照
│   ├── components/     # 图片、来源时间、收藏、错误等通用组件
│   ├── favorites/      # 收藏页与 FavoritesViewModel
│   ├── home/           # 首页、Home UI State、Refresh / Pagination
│   ├── search/         # 搜索页、Search UI State、debounce / latest-wins
│   └── theme/          # Material 3 主题
├── MainActivity.kt     # Remote Repository 装配与 Compose 入口
└── NewsApplication.kt # Room Database 与本地 Repository
```

## 构建与验证

### 环境要求

- Android Studio：支持 AGP `9.0.1` 的版本；
- JDK：`17` 或更高版本；
- Android SDK：`compileSdk` / `targetSdk` 为 API `36`，最低支持 API `26`；
- Gradle：使用项目自带的 Wrapper `9.1`，不需要单独安装全局 Gradle。

使用 Android Studio 打开项目并完成 Gradle Sync。

### GNews API Key

项目不会把真实 API Key 提交到 Git。请在项目根目录的 `local.properties` 中保留 Android SDK 配置，并增加：

```properties
GNEWS_API_KEY=your_gnews_api_key
```

Gradle 在构建时读取该值并生成 `BuildConfig.GNEWS_API_KEY`；如果 Key 为空，App 会进入受控的鉴权错误状态，而不会在源码中硬编码 Secret。该值会被编译进 APK，不能视为生产级 Secret；请只使用受限的开发 Key，避免将 `local.properties`、已构建 APK 或 Key 值上传到公开位置。

### 本地验证

```powershell
# Kotlin 编译检查
.\gradlew.bat :app:compileDebugKotlin

# JVM 单元测试：Repository、Mapping、ViewModel、缓存与请求竞争等
.\gradlew.bat :app:testDebugUnitTest

# AVD / 真机仪器测试：Room、Migration、Compose UI、生命周期与状态一致性
.\gradlew.bat :app:connectedDebugAndroidTest

# 静态检查
.\gradlew.bat :app:lintDebug
```

覆盖重点包括请求竞争、Search debounce、Refresh / Pagination、错误映射、缓存时序、Room Migration、跨页面收藏一致性、Activity Recreation、异常 UI 与布局回归。

## 有意保留的边界

为了保持项目范围聚焦、网络数据主线清晰，当前不引入以下内容：

- Hilt、复杂 MVI、多模块拆分或为简单操作机械增加 UseCase；
- Paging 3 / RemoteMediator；V1 保留手写 Home Pagination，用于完整展示分页状态和请求控制；
- Search Pagination、完整多页持久化缓存和完整 Offline First；
- 登录、账号系统、评论、推荐算法、Push、视频流或云端收藏同步；
- 抓取第三方新闻网页全文；详情只展示 API 可获得的内容预览，并跳转原文；
- Process Death 后完整分页列表 / 详情快照恢复；
- 发布级后台服务、CI/CD、监控或生产环境 Secret 托管。

这些可以作为后续演进方向；当前版本优先保证网络请求生命周期、Remote + Local 数据边界、失败路径和测试证据清晰。
