# 家具电商数据看板发布与回滚手册

> 日期：2026-07-10
> 发布策略：数据库向前兼容、后端先于前端、埋点最后启用

## 1. 发布对象

| 对象 | 产物 |
|---|---|
| MySQL | `sql/mysql/statistics-commerce-dashboard.sql` |
| 交易服务 | `yudao-module-trade-server` |
| 统计服务 | `yudao-module-statistics-server` |
| 管理后台 | `yudao-ui-admin-vue3` 构建产物 |
| 家具前台 | `furniture web` 构建产物 |

## 2. 发布前记录

发布单必须记录：

- Git提交号。
- 数据库实例、库名和备份位置。
- 交易服务、统计服务、管理后台、家具前台旧版本号和新版本号。
- 发布负责人、数据库执行人、验收人。
- 计划开始、结束和允许回滚的时间窗口。
- 租户编号和需要授权的角色。
- 生产环境已设置非默认 `STATISTICS_BEHAVIOR_HASH_SALT`，并与旧版本配置安全保存。

## 3. 发布前门禁

在源码根目录执行：

```powershell
Set-Location 'D:\code\furniture web'
npm test
npm run build
```

```powershell
Set-Location 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn -pl yudao-module-mall/yudao-module-trade-server,yudao-module-mall/yudao-module-statistics-server -am -DskipITs test
```

```powershell
Set-Location 'D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3'
pnpm check:dashboard
pnpm ts:check
pnpm build:prod
```

每条命令必须退出码0。任何失败都中止发布，不使用跳过测试的构建产物。

## 4. 数据库备份与基线

先记录基线：

```sql
SELECT NOW() AS baseline_time;
SELECT COUNT(*) FROM trade_order;
SELECT COUNT(*) FROM trade_order_item;
SELECT COUNT(*) FROM product_spu;
SELECT COUNT(*) FROM product_sku;
SELECT COUNT(*) FROM product_statistics;
```

备份至少包含：

- `trade_order_item`
- `product_statistics`
- `system_menu`
- `system_role_menu`
- `infra_job`

新建全库快照或一致性备份后，验证备份文件存在、大小非0，并记录恢复命令。未验证备份不得继续。

## 5. 正式发布顺序

### 步骤1：执行数据库迁移

```powershell
mysql --default-character-set=utf8mb4 -h $env:DASHBOARD_DB_HOST -u $env:DASHBOARD_DB_USER -p $env:DASHBOARD_DB_NAME -e "source D:/code/yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql"
```

数据库凭据通过安全终端交互或密钥管理注入，禁止写入仓库和发布日志。

执行迁移验证：

```sql
SHOW TABLES LIKE 'statistics_behavior_event';
SHOW TABLES LIKE 'statistics_traffic_daily';
SHOW COLUMNS FROM trade_order_item LIKE 'cost_price';
SHOW COLUMNS FROM product_statistics LIKE 'paid_order_count';
```

### 步骤2：发布交易服务

发布含成本快照的新交易服务。验证：

1. 健康检查正常。
2. 创建一笔测试订单或在预发布环境创建订单。
3. 查询订单明细的 `cost_price` 非空、`cost_estimated=0`。
4. 下单、支付、取消和退款原有流程无回归。

统计服务上线前先发布交易服务，避免新订单继续产生无成本快照明细。

### 步骤3：发布统计服务

验证：

- 服务健康检查正常。
- Swagger/OpenAPI中出现行为上报与四个看板查询接口。
- 匿名上报一条测试事件返回成功。
- 数据库出现对应事件且只保存哈希访客标识。
- 手工执行 `dashboardStatisticsJob` 参数 `0`，当日聚合产生一行。

### 步骤4：配置任务

在定时任务管理中确认：

- `数据看板-当日聚合` 启用，5分钟一次。
- `数据看板-昨日定稿` 启用，每日00:10。
- `数据看板-事件清理` 启用，每日02:30。
- 原 `Mall 商品统计 Job` 停止，避免重复写商品统计表。

观察一次当日聚合成功日志，确认耗时小于4分钟。

### 步骤5：发布管理后台

验证：

- `/dashboard` 页面可以直接刷新，不返回404。
- furniture-lite模式没有过滤该菜单。
- 有查询权限可查看数据。
- 仅有查询权限时不能导出。
- 卡片、趋势、漏斗和商品表均能加载。
- 旧商品统计、订单、商品管理页面仍可访问。

### 步骤6：分配角色权限

在角色管理中按业务范围分配：

- 数据看板菜单。
- `statistics:dashboard:query`。
- `statistics:dashboard:export`，仅授予允许下载经营数据的角色。

使用一个业务账号重新登录验证动态菜单刷新。

### 步骤7：发布家具前台

前台最后发布，避免埋点早于接收接口上线。

验证：

1. 打开首页，HOME_VIEW增加1。
2. 打开有效商品，PRODUCT_DETAIL_VIEW包含正确SPU。
3. 登录并加购成功，ADD_TO_CART包含正确SKU和数量。
4. 点击结算，CHECKOUT_START增加1。
5. 临时阻断埋点接口，浏览、加购和结算仍正常。

## 6. 发布后30分钟观察

每5分钟记录一次：

```sql
SELECT event_type, COUNT(*)
FROM statistics_behavior_event
WHERE create_time >= NOW() - INTERVAL 30 MINUTE
GROUP BY event_type;

SELECT tenant_id, day, home_pv, product_detail_pv, paid_order_count,
       gross_profit, update_time
FROM statistics_traffic_daily
WHERE day = CURRENT_DATE;
```

同时观察：

- 埋点接口错误率和P95。
- 统计服务CPU、内存、数据库连接池。
- 聚合任务耗时和失败次数。
- 管理接口P95。
- 家具前台JavaScript错误率。
- 下单、支付和购物车成功率是否发生异常下降。

## 7. 回滚触发条件

出现任一情况立即停止继续发布并评估回滚：

- 下单或支付主流程异常。
- 新订单成本快照导致订单创建失败。
- 埋点接口影响前台加载或交互。
- 聚合任务持续超过4分钟或造成数据库高负载。
- 指标与固定验收数据集明显不一致。
- 越权用户可以查看或导出经营数据。
- 发布后核心错误率持续5分钟高于发布前基线两倍。

## 8. 分层回滚

### 8.1 仅关闭前台埋点

适用于埋点流量异常但业务功能正常：

1. 回滚家具前台到旧静态产物，或关闭 `VITE_BEHAVIOR_TRACKING_ENABLED=false` 后重新发布。
2. 保留统计服务和数据库结构。
3. 确认首页、商品、加购、结算恢复正常。

### 8.2 关闭聚合和看板

适用于聚合高负载或指标错误：

1. 停止三条数据看板任务。
2. 将 `/dashboard` 菜单设为不可见。
3. 回滚管理后台到旧产物。
4. 保留原始事件，修复后可以重新聚合。

```sql
UPDATE system_menu
SET visible=b'0', update_time=NOW()
WHERE path='/dashboard' AND deleted=b'0';
```

### 8.3 回滚后端服务

适用于交易或统计服务回归：

1. 先停止数据看板任务。
2. 回滚统计服务。
3. 若订单流程受影响，再回滚交易服务。
4. 新增列和新表保留，旧代码会忽略它们。
5. 运行下单、支付、退款和健康检查。

### 8.4 数据库恢复

只有数据被错误更新、删除或结构迁移损坏时才恢复数据库：

1. 停止交易和统计写入。
2. 使用已验证备份恢复受影响表。
3. 对照发布前行数和抽样订单核验。
4. 恢复服务前完成一致性检查。

默认回滚不执行 `DROP TABLE` 或 `DROP COLUMN`，避免丢失上线期间采集的事件和成本快照。

## 9. 回滚后验证

- 首页、商品详情、加购、结算和下单正常。
- 订单支付和退款正常。
- 管理后台旧导航正常。
- 停止的任务不再运行。
- 数据库连接、CPU和错误率回归基线。
- 记录回滚原因、时间、影响范围和遗留数据状态。

## 10. 发布完成标准

- 30分钟观察无触发回滚条件。
- 当日聚合至少成功执行两次。
- 管理看板与数据库抽样数据一致。
- 权限账号验证通过。
- 业务主流程验证通过。
- 发布单记录完整并由验收人确认。
