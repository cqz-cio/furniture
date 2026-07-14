# 家具电商数据看板发布与回滚手册

> 日期：2026-07-10
> 发布策略：默认关闭、数据库向前兼容、后端与安全边界先行、按租户和站点灰度、埋点与导出最后启用
> 默认启用租户：121
> 业务时区：Asia/Shanghai

## 1. 发布对象

| 对象 | 产物或配置 |
|---|---|
| MySQL | sql/mysql/statistics-commerce-dashboard.sql、分批成本回填和历史重算工具 |
| 交易服务 | yudao-module-trade-server，含成本快照与仅退款库存语义修复 |
| 统计服务 | yudao-module-statistics-server，含公共追踪、Trade 内网加购事件、聚合、查询、导出、清理 |
| 网关 | /app-api/statistics/behavior/track 精确路由、站点租户映射、Origin、限流、内部头清理 |
| 管理后台 | yudao-ui-admin-vue3 构建产物 |
| 家具前台 | furniture web 构建产物和分析同意配置 |
| 密钥管理 | HMAC 当前/上一版本引用、数据库和备份密钥引用 |
| 调度平台 | 今日昨日滚动、昨日定稿、近 7 日修复、事件物理清理任务 |

Statistics 服务、管理接口、任务接口和数据库不得直接暴露公网。公网只开放网关上的单一行为追踪路由和既有受认证管理路由。

## 2. 职责与审批

发布单必须明确：

- 发布负责人：控制开关、阶段推进和最终决策。
- 数据库执行人：只负责结构迁移、受控回填和对账。
- 网关负责人：配置站点映射、Origin、可信代理和限流。
- 安全/隐私验收人：核对密钥、日志脱敏、同意策略和删除流程。
- 业务验收人：确认币种、指标口径、角色矩阵和样本对账。
- 回滚执行人：与发布负责人不得是同一人的高风险操作包括数据库恢复、跨租户重算和隐私批量删除。

修改任务参数、启用利润导出、跨租户运维、销毁 HMAC 旧钥或恢复备份必须经过二次确认并留下审计。

## 3. 发布单与制品安全记录

发布单必须记录：

- Git 提交号、分支、构建流水线编号。
- 网关、交易服务、统计服务、管理后台、家具前台的旧版本和新版本。
- 每个镜像或静态产物的 SHA-256、签名验证结果和不可变镜像 digest。
- 数据库迁移文件 SHA-256；生产执行文件必须与评审文件一致。
- SBOM、依赖漏洞扫描和秘密扫描结果；高危未豁免或发现凭据时停止。
- 数据库实例、库名、迁移账号、备份位置、备份校验和、备份密钥引用。
- 计划开始、结束、灰度观察和允许回滚的窗口。
- 启用租户、站点 Host、精确 Origin、时区、币种、分析同意策略和 trafficDataAvailableFrom。
- 四类权限的授权角色和审批人。
- HMAC 当前、上一和待启用版本编号及 KMS 引用；发布单不得记录密钥值。

所有部署、迁移和备份凭据均为短时、最小权限凭据。禁止写入仓库、镜像、普通配置中心、命令行参数、聊天记录或发布日志。

## 4. 站点、租户、Origin 与网络边界

### 4.1 站点映射清单

上线前为每个站点填写并审批以下清单，禁止通配 Host、通配 Origin 或“默认租户”回退：

| siteId | Host | Allowed Origin | tenantId | enabled | timezone | currencyCode | consentRequired |
|---|---|---|---:|---:|---|---|---:|
| oakved-production | 生产实际 Host | 生产实际 HTTPS Origin | 121 | 初始 false | Asia/Shanghai | USD | 按地区评审值 |

网关必须：

1. 只把 POST /app-api/statistics/behavior/track 转发到 Statistics 服务。
2. 根据受控 Host 和 Origin 查找唯一站点映射。
3. 删除外部 tenant-id，再写入映射 tenantId；匿名客户端不能选择租户。
4. 有 Bearer Token 时校验令牌租户等于站点租户，不一致返回 403。
5. 删除外部 login-user、X-User-* 和其他内部身份头。
6. 在最外层可信代理删除客户端伪造的转发 IP 头，再用真实连接信息重建；应用只信任该代理链。
7. 不匹配站点、未启用站点、Origin: null、缺失浏览器 Origin 或非 HTTPS Origin 默认拒绝。受控内部烟测使用单独签名标记，不复用公网例外。

### 4.2 CORS、体积和限流

- Access-Control-Allow-Origin 只回显清单中的精确 Origin。
- 行为追踪不允许 credentials；允许方法只有 POST、OPTIONS，允许头只保留 Content-Type、Authorization、追踪所需的受控头。
- 请求体最大 8 KB。
- 专用限流键只使用服务端映射 tenantId + 可信客户端 IP，不包含 eventId 或请求体。
- 默认单 IP 120 次/分钟、单访客 120 次/分钟、单租户 6000 次/分钟，并配置全局并发和熔断。
- 被硬限流请求不写原始事件表，只记录不含请求体、UUID、完整 IP 或 User-Agent 的计数。

### 4.3 直连隔离

网络策略、安全组和服务发现必须保证：

- 公网无法访问 Statistics 服务端口、任务端口、数据库端口。
- 只有网关服务身份可以调用 app 追踪接口。
- 管理接口只经既有认证网关进入。
- 网关到服务使用 mTLS 或等价的服务身份认证。

验收必须包含公网直连失败和伪造 login-user 头失败，不能只依赖代码约定。

## 5. HMAC 密钥发布与轮换

### 5.1 启动要求

- 生产不提供默认 HMAC 密钥；当前租户缺少有效密钥时 Statistics 服务必须拒绝启动或保持追踪入口不可用。
- 密钥来自 KMS/Secrets Manager，应用配置只保存版本号和密钥引用。
- 每个租户使用独立派生密钥；数据库只保存 hash_key_version。
- 配置、Actuator、线程转储、错误日志和发布日志不得输出密钥或原始访客标识。

### 5.2 日界轮换

- HMAC 新版本只允许在 Asia/Shanghai 自然日 00:00:00 边界生效。
- 同一租户同一自然日只允许一个写入版本；聚合预检发现同日多个 hash_key_version 时，将该日标记为 PARTIAL 并停止发布，不能把两个版本直接当作两个访客集合。
- 新版本在前一日预加载并完成确定性测试，validFrom 精确记录为日界。
- 常规回滚不回退当天写入版本；应用回滚后继续使用当天已经生效的版本。
- 旧钥保留至使用该版本的原始事件全部超过保留期，并且相关会员/访客删除请求处理完成后才能销毁。
- 密钥疑似泄露时先关闭追踪入口。不得在同一自然日中途无声切换；若无法等到下一日，必须中止当日采集、记录覆盖缺口，并在下一日启用新版本。

### 5.3 验收

- 缺钥和错误版本启动失败。
- 同输入、同租户、同版本摘要稳定；不同租户或版本摘要不同。
- 日界前后分别写入旧、新版本，同一天不混版本。
- 旧版本仍能完成隐私删除检索。
- 日志、数据库、制品和配置导出中不存在密钥值。

## 6. 分阶段开关

以下开关默认全部关闭，并且服务端开关优先于编译期前端开关：

| 开关 | 初始值 | 作用 |
|---|---:|---|
| GATEWAY_BEHAVIOR_ROUTE_ENABLED | false | 按站点开放追踪路由 |
| STATISTICS_BEHAVIOR_INGEST_ENABLED | false | 接收原始事件 |
| STATISTICS_DASHBOARD_AGGREGATION_ENABLED | false | 运行聚合与重算 |
| STATISTICS_DASHBOARD_QUERY_ENABLED | false | 开放管理查询 |
| STATISTICS_DASHBOARD_EXPORT_ENABLED | false | 开放普通导出 |
| STATISTICS_DASHBOARD_PROFIT_ENABLED | false | 返回成本与利润字段 |
| STATISTICS_DASHBOARD_PROFIT_EXPORT_ENABLED | false | 开放利润导出 |
| VITE_BEHAVIOR_TRACKING_ENABLED | false | 家具前台发起追踪 |

另有受控配置：

- DASHBOARD_ENABLED_TENANTS 初始仅包含 121。
- DASHBOARD_EVENT_RETENTION_DAYS 固定审批值 180，清理任务参数不能覆盖。
- DASHBOARD_VISITOR_ID_TTL_DAYS 不得超过事件保留期，默认 180；前台到期轮换访客 UUID。
- DASHBOARD_TIMEZONE = Asia/Shanghai。
- DASHBOARD_CURRENCY_CODE 对租户 121 为 USD，上线前与支付配置对账。

关闭前端开关可能需要重新构建，因此必须同时保留可即时关闭的网关和服务端 kill switch。

## 7. 分析同意配置

### 7.1 需要同意的地区

- consentRequired = true 时，未同意前一期不创建 visitorId、sessionId 或其他持久/会话分析标识，也不发送任何行为事件。
- 一期不实现 cookieless、指纹或不可识别汇总上报作为替代路线。
- 用户同意后才初始化标识并开始上报；撤回后立即停止上报并清除本地标识。
- 不得把未同意用户记为业务 0。页面和数据说明必须披露：流量指标只覆盖已同意人群，可能存在选择偏差，不能代表全部访问者。
- trafficDataAvailableFrom 取实际开始合规采集的时间；同意策略变化必须记录，相关日期根据覆盖情况标记 PARTIAL。

### 7.2 不需要同意的地区

只有完成书面隐私评审并在发布单记录依据后，才能配置 consentRequired = false。无法确认时采用 true，且前台追踪保持关闭。

### 7.3 验收

- 未同意时 localStorage、sessionStorage 无分析标识，网络面板无追踪请求。
- 同意后只产生一套 visitor/session 标识并正常上报。
- 撤回后标识删除、请求停止。
- 页面显示覆盖范围和可能偏差，不出现 cookieless 数据。

## 8. 日志与审计门禁

行为追踪接口必须关闭通用请求体访问日志，或使用经验证的端点级删除策略。以下内容不得进入访问日志、错误日志、限流日志、链路追踪标签或告警：

- visitorId、sessionId、eventId 原文，以及购物车请求中的分析身份头。
- Authorization、Cookie、内部身份头。
- 请求体、完整 IP、完整 User-Agent、完整 Referrer。
- HMAC 密钥或其可恢复材料。

允许记录：traceId、映射 tenantId、站点编号、结果码、耗时、trafficQuality、受控 exclusionReason 和聚合计数。

导出另写专用审计：操作人、租户、权限、筛选摘要、行数、文件哈希、traceId、结果和过期时间，不记录导出内容。

发布门禁必须分别触发成功、参数错误、429 和 500，再检索应用日志、访问日志表和错误日志表；任一敏感原文出现即停止。

## 9. 自动化与安全测试门禁

### 9.1 构建测试

~~~powershell
Set-Location 'D:\code\furniture web'
npm test
npm run build

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn -pl yudao-module-mall/yudao-module-trade-server,yudao-module-mall/yudao-module-statistics-server -am -DskipITs test

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3'
pnpm check:dashboard
pnpm ts:check
pnpm build:prod
~~~

每条命令必须退出 0。不得使用跳过测试的构建产物。

### 9.2 必须通过的负向测试

- 伪造 tenant-id、跨租户 SPU/SKU、错配会员令牌租户均拒绝。
- 伪造 login-user 和转发 IP 头不能改变身份或限流键。
- 非清单 Origin、通配子域、Origin: null、缺失 Origin 和超大 body 均拒绝。
- 更换 eventId 不能绕过 IP/租户限流；服务端 5 秒复合去重生效。
- 两个租户同日数据在汇总、趋势、商品分页、导出和重算中完全隔离。
- 排序、keyword、日期和 ID 参数不能进入未绑定 SQL。
- 缺失成本时利润字段为 NULL；三类成本行数之和正确。
- 基础查询、利润查询、普通导出、利润导出四类权限分别拒绝。
- Excel 危险前缀写为文本而非公式；导出行数和频率上限生效。
- HMAC 日界轮换、同日单版本、缺钥失败和旧钥删除检索通过。
- 分析同意、撤回、物理删除和租户退场流程通过。

## 10. 数据库备份与基线

### 10.1 备份安全

- 创建一致性全库快照或等价可恢复备份。
- 备份使用独立密钥加密，密钥引用和备份对象分权保存。
- 记录对象版本、大小、SHA-256、创建时间、保留期限和允许恢复人员。
- 在隔离环境完成一次恢复演练并执行结构与抽样对账；仅验证文件非空不够。
- 数据库迁移账号只具有本迁移所需 DDL 权限；回填账号只具有明确表的 SELECT/UPDATE 权限。

### 10.2 发布前基线

~~~sql
SELECT NOW(3) AS baseline_time;

SELECT tenant_id, COUNT(*) AS order_count
FROM trade_order
WHERE deleted = b'0'
GROUP BY tenant_id;

SELECT tenant_id, COUNT(*) AS item_count,
       SUM(cost_price IS NULL) AS missing_cost_count
FROM trade_order_item
WHERE deleted = b'0'
GROUP BY tenant_id;

SELECT tenant_id, COUNT(*) AS product_stat_count
FROM product_statistics
WHERE deleted = b'0'
GROUP BY tenant_id;
~~~

若 cost_price 尚未创建，第一次基线只记录行数，结构迁移后再记录缺失成本数。基线还必须包含菜单、角色菜单关系、调度任务、网关站点配置版本和 HMAC 版本编号。

## 11. 正式发布顺序

### 步骤 0：冻结配置并确认全部开关关闭

- 核对启用租户只有 121。
- 前端、网关、接收、聚合、查询、导出、利润开关全部为 false。
- 锁定站点映射、Origin、时区、币种、同意策略和 HMAC 日界计划。

### 步骤 1：执行数据库结构迁移

使用密钥管理注入的短时凭据，不把密码放在命令行：

~~~powershell
mysql --default-character-set=utf8mb4 -h $env:DASHBOARD_DB_HOST -u $env:DASHBOARD_DB_USER -p $env:DASHBOARD_DB_NAME -e "source D:/code/yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql"
~~~

严格执行数据库迁移设计中的预检、迁移锁、字段/索引断言和重复执行门禁。生产只执行经副本连续运行两次验证过且哈希一致的脚本。

### 步骤 2：发布交易服务

发布成本快照、仅退款库存语义修复和事务提交后的内网购物车事件发布器。保持所有看板开关关闭，事件调用必须快速降级，验证：

1. 新订单 cost_price 非空、cost_estimated = 0。
2. 成本快照创建后不随 SKU 当前成本变化。
3. RETURN_AND_REFUND 才冲回库存和成本；仅退款不冲回。
4. 下单、支付、取消、部分退款、仅退款和退货退款无回归。
5. Statistics 接收关闭或不可用时，购物车写入仍成功且只增加失败监控。

### 步骤 3：发布 Statistics 服务暗版本

- 注入 HMAC KMS 引用、启用租户、时区、币种、保留期配置。
- 接收、聚合、查询和导出仍关闭。
- 服务必须在缺 HMAC 密钥时启动失败；配置正确后健康检查通过。
- 确认服务端口仅内网可达。

### 步骤 4：部署网关暗路由

- 配置精确路由、站点租户映射、Origin、内部头清理、可信代理、8 KB 上限和多层限流。
- GATEWAY_BEHAVIOR_ROUTE_ENABLED 仍为 false。
- 完成 4.1 至 4.3 的负向测试。

### 步骤 5：分批回填历史成本

按数据库迁移设计只对租户 121 初始化检查点，默认每批 10000：

- 每批提交后记录 lastId、扫描行数、更新行数、耗时、锁等待和复制延迟。
- 达到发布单中的 CPU、锁等待或复制延迟阈值立即暂停。
- 已有成本不覆盖，缺失 SKU 成本保持 NULL。
- 完成后核对精确、估算、缺失三类成本行数。

回填失败不启用利润开关；基础流量/销售是否继续发布由业务和安全验收人共同决定。

### 步骤 6：启用聚合并回填财务历史

- 先开启 STATISTICS_DASHBOARD_AGGREGATION_ENABLED。
- 手工对租户 121 重算最近 7 个完整自然日并核对固定数据集。
- 经审批按 startDate/endDate 分段回填更早财务数据，最大 366 天一批。
- 支付按 pay_time、退款和退货成本按 refund_time 归日。
- 流量历史不可回填；埋点启用日前状态为 UNAVAILABLE，PV/UV 为 NULL。
- 对账通过后启用今日昨日滚动、昨日定稿和近 7 日修复任务；事件清理任务在水位验证后最后启用。

### 步骤 7：启用网关与服务端接收小流量

1. 在 HMAC 日界计划允许的时间启用当前版本；不得在同日混用版本。
2. 先开启 STATISTICS_BEHAVIOR_INGEST_ENABLED。
3. 仅对受控烟测来源开启站点网关路由。
4. 验证租户绑定、HMAC 版本、质量分类、日志脱敏和限流。
5. 以受控账号完成真实加购，确认只生成一条 `SERVER_CART` 事件且公共接口拒绝 `ADD_TO_CART`。
6. 扩大到小比例真实站点流量，观察至少 30 分钟后再继续。

### 步骤 8：发布家具前台并灰度追踪

- 发布时 VITE_BEHAVIOR_TRACKING_ENABLED 仍为 false。
- 验证需要同意地区在未同意前不创建标识、不发事件且无 cookieless 上报。
- 小比例开启前台开关，验证首页、商品详情和结算公共事件；真实加购只能在 Trade 事务提交后产生 `SERVER_CART` 事件，浏览器不得另发公共加购事件。
- 临时关闭网关或服务端接收，浏览、加购、结算和下单必须正常。
- 观察通过后按站点逐步全量；发现异常可即时关闭网关或接收开关，无需等待重新构建。

### 步骤 9：发布管理后台与最小权限角色

- 开启 STATISTICS_DASHBOARD_QUERY_ENABLED，先只给管理员和验收账号。
- 运营角色只授予 statistics:dashboard:query。
- 财务/经营管理角色经审批授予 statistics:dashboard:profit-query。
- 普通导出使用 statistics:dashboard:export，利润导出使用 statistics:dashboard:profit-export；两者分别审批，导出开关最后开启。
- 无利润权限的接口响应本身不得包含成本、毛利或利润关注项。
- 使用四类测试账号重新登录并验证动态菜单和直接 API。

### 步骤 10：启用导出与利润

完成财务对账后按顺序开启：

1. STATISTICS_DASHBOARD_PROFIT_ENABLED。
2. STATISTICS_DASHBOARD_EXPORT_ENABLED。
3. STATISTICS_DASHBOARD_PROFIT_EXPORT_ENABLED。

验证单次 10000 行、单用户 10 分钟 3 次限制，公式注入防护、临时文件加密/过期删除和专用审计。

## 12. 发布后观察

小流量阶段至少观察 30 分钟，全量后至少再观察 60 分钟。每 5 分钟记录：

- 每租户、站点、事件类型的 accepted 和 excluded 数量及原因。
- 公共 5 秒去重 Redis 的可用性、SET NX 延迟和失败数；不可用时确认公共事件拒绝而非绕过去重写入。
- 429、4xx、5xx、P95、请求体拒绝数和全局熔断状态。
- 仅出现启用租户 121；出现其他租户立即关闭入口。
- hash_key_version 与当天计划一致；同租户同日不得出现第二版本。
- 聚合耗时、失败、锁冲突和各来源水位。
- trafficDataStatus、freshnessStatus、profitDataQuality、三类成本行数和 NULL 利润不变量。
- 数据库 CPU、连接池、锁等待、复制延迟和存储增长。
- 下单、支付、购物车、退款成功率和前台 JavaScript 错误率。
- 管理查询、普通导出、利润导出拒绝/成功审计。
- 日志脱敏抽查和秘密扫描。

告警条件包括：

- 站点正常有业务请求但连续 15 分钟 accepted 事件为 0。
- 接收到未启用租户、站点映射失败或令牌租户不一致。
- excluded/accepted 比例、随机 visitor churn 或单租户速率异常。
- 水位超过 10 分钟未推进触发延迟告警，超过 20 分钟升级为严重告警和暂停扩大灰度。
- 同租户同日出现多个 HMAC 版本。
- 公共 5 秒去重存储不可用或出现并发重复写入。
- INCOMPLETE 日仍返回非空利润。

## 13. 隐私删除与租户退场

上线前必须演练：

### 13.1 会员或访客删除

1. 校验租户和隐私请求身份。
2. 暂停该请求关联的新追踪或确认已撤回同意。
3. 按 tenant_id + user_id，或 tenant_id + hash_key_version + visitor_hash 分批物理删除原始事件。
4. 记录批次、数量和结果，不记录原始标识。
5. 重算受影响日期；日聚合不含个人标识，可按批准的业务留存继续保留。
6. 日志和备份按各自保留期到期，不能承诺即时修改不可变备份。

### 13.2 租户退场

1. 关闭前台开关、网关站点映射、服务端接收、聚合和导出。
2. 撤销角色和任务权限。
3. 物理删除原始事件和临时导出，按审批处理日聚合及检查点。
4. 等待所有隐私删除请求完成并确认原始事件保留期结束。
5. 最后销毁租户派生 HMAC 旧钥并记录 KMS 销毁证明。

数据库恢复不得无声恢复已完成隐私删除的数据。确需灾难恢复时，恢复后必须重放删除清单再开放服务。

## 14. 回滚触发条件

出现任一情况立即停止扩大灰度并执行相应层级回滚：

- 下单、支付、退款或库存主流程异常。
- 新订单成本快照缺失或改变订单创建结果。
- 跨租户写入、查询、聚合或导出。
- 非允许 Origin 可写入，或可伪造内部身份头。
- 敏感 UUID、令牌、完整 IP、User-Agent、请求体或密钥进入日志。
- HMAC 同日混版本、缺钥仍可启动或摘要无法用于删除。
- 未同意用户创建标识或发送事件，或出现未批准的 cookieless 上报。
- 聚合持续超过 4 分钟、数据库高负载或复制延迟越过阈值。
- 固定数据集或抽样财务对账不一致。
- INCOMPLETE 数据返回非空利润。
- 越权用户可查看利润或下载任一导出。
- 核心错误率持续 5 分钟高于发布前基线两倍。

## 15. 分层回滚

### 15.1 关闭前台追踪

关闭 VITE 开关对应的运行时配置；若只能编译期控制，则回滚静态产物。同时立即关闭服务端接收或网关路由，避免等待构建。确认交易主链路正常。

### 15.2 按站点关闭网关

将目标站点 GATEWAY_BEHAVIOR_ROUTE_ENABLED 设为 false。保留管理查询和数据库结构。验证外部追踪快速失败且不影响页面、加购和结算。

### 15.3 关闭接收、聚合、导出和看板

按影响范围关闭：

1. STATISTICS_BEHAVIOR_INGEST_ENABLED。
2. 四条数据看板任务。
3. 普通导出和利润导出。
4. 利润查询。
5. 基础查询并隐藏 /dashboard 菜单。

保留最后成功聚合和水位，禁止用全 0 替代。

~~~sql
UPDATE system_menu
SET visible = b'0', update_time = NOW()
WHERE path = '/dashboard' AND deleted = b'0';
~~~

### 15.4 回滚应用服务

1. 保持任务和入口关闭。
2. 回滚 Statistics 服务。
3. 若交易主流程受影响，再回滚交易服务。
4. 新增表和列保留，旧代码忽略。
5. 当天已经生效的 HMAC 版本不随应用回滚而降级；保留当前和旧版本密钥。
6. 同意策略不得因回滚而放宽。

### 15.5 处理错误采集数据

若仅特定站点、租户和时间窗被污染：

- 先备份并记录影响范围。
- 经数据所有者和隐私/安全审批后，按 tenant + 主键/时间范围物理删除或标记排除。
- 禁止无 tenant 条件删除。
- 重算受影响日期并核对水位、质量和三类成本行数。
- 流量缺口标为 PARTIAL/UNAVAILABLE，不伪造补数。

### 15.6 数据库恢复

只有结构或业务数据被错误破坏时才恢复：

1. 停止交易与统计相关写入。
2. 使用已验证、加密、哈希匹配的备份恢复到隔离环境。
3. 对照发布前基线、抽样订单和隐私删除清单核验。
4. 重放备份之后发生的支付、退款、成本快照和删除请求。
5. 完成一致性检查后再切换。

常规回滚禁止 DROP TABLE、DROP COLUMN、销毁密钥或恢复整库。

## 16. 回滚后验证

- 首页、商品详情、加购、结算、下单、支付、退款正常。
- 公网追踪路由和服务端接收符合目标关闭范围。
- 停止任务不再运行，水位不再被错误推进。
- 菜单、四类权限和导出入口符合回滚状态。
- 无跨租户数据、无日志敏感信息、无同日 HMAC 混版本。
- 未同意用户仍不创建标识、不发送事件。
- 数据库连接、CPU、锁等待、复制延迟和错误率恢复基线。
- 记录回滚原因、操作者、时间、租户/站点影响、删除或保留数据状态及后续重算计划。

## 17. 发布完成标准

以下条件全部满足才能关闭发布单：

- 所有构建、自动化、安全负向和隐私测试通过。
- 制品签名、哈希、SBOM、漏洞和秘密扫描通过。
- 数据库结构脚本在生产结构副本连续执行两次通过，生产结构断言通过。
- 历史成本检查点完成或有批准的剩余计划；三类成本行数可解释。
- 支付日、退款日、退货成本和币种抽样对账一致。
- 网关路由、站点租户映射、精确 Origin、可信代理和直连隔离验证通过。
- HMAC 日界版本、缺钥失败、旧钥保留和日志脱敏验证通过。
- 分析同意、撤回、无 cookieless、覆盖偏差披露和物理删除演练通过。
- 今日昨日滚动至少成功两次，分源水位正常推进。
- 运营、财务、普通导出和利润导出账号权限矩阵通过。
- 小流量与全量观察窗口均无回滚触发条件。
- 加密备份恢复演练有效，隐私删除清单可在恢复后重放。
- 发布记录由发布、数据库、安全/隐私和业务验收人共同确认。
