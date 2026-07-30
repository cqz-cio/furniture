# Oakved 项目云服务器迁移方案

目标：把本地后台管理系统、家具前台网站、商品/用户/订单等数据库数据、项目里用到的图片资源，迁移到云服务器后保持页面和数据尽量一模一样。

## 1. 迁移范围

必须迁移的内容：

- 数据库：当前本地 MySQL 库 `ruoyi-vue-pro`，它包含后台菜单权限、商品、分类、SKU、会员、用户、订单、支付配置、收藏等数据。
- 家具前台：`D:\code\furniture web`，尤其是 `public/assets/generated-furniture` 和 `public/assets/brand`，这些是首页、商品页备用图、活动页图、品牌 logo。
- 后台管理端：`D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3`，包含后台页面、图标、logo、客服表情等静态资源。
- 后端服务：`D:\code\yudao电商管理平台前后端\yudao-cloud`，包含接口服务、验证码图片、后端资源。

当前图片结论：

- 商品主图、商品轮播图、分类图、品牌图目前主要保存在数据库字段里，但值大多是 `images.unsplash.com` 外链。
- 前台首页、活动页、商品页备用图是前台项目本地静态文件，不在数据库里。
- 当前 `infra_file` 表为空，所以目前没有文件中心上传文件目录必须单独迁移。
- 数据库里少量头像或 OAuth logo 指向 `test.yudao.iocoder.cn`，如果要完全长期稳定，后续建议迁到自己的 OSS/CDN。

## 2. 推荐部署结构

推荐先用一个域名，按路径区分：

```text
https://your-domain.com/            家具前台
https://your-domain.com/admin/      后台管理端
https://your-domain.com/app-api/    前台接口
https://your-domain.com/admin-api/  后台接口
```

云服务器建议准备：

```text
/opt/oakved/
  backend/
    yudao-server.jar
    .env
    logs/
  frontend/
    furniture/
    admin/
  sql/
    oakved-full.sql
  nginx/
    oakved.conf
```

## 3. 数据库迁移

本地导出完整库，不要只用项目里的 `sql/mysql/ruoyi-vue-pro.sql`，因为那个是初始化脚本，不一定包含你当前后台里的商品、用户、订单等实时数据。

推荐导出命令：

```bash
read -s -p "MySQL password: " MYSQL_PWD
export MYSQL_PWD
echo

mysqldump -h 127.0.0.1 -P 3306 -uroot \
  --single-transaction --routines --triggers --events \
  --default-character-set=utf8mb4 \
  ruoyi-vue-pro > oakved-full.sql

unset MYSQL_PWD
```

云服务器导入：

```bash
mysql -uroot -p -e "CREATE DATABASE oakved DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p oakved < oakved-full.sql
```

导入后重点检查：

```sql
SELECT COUNT(*) FROM product_spu;
SELECT COUNT(*) FROM product_sku;
SELECT COUNT(*) FROM product_category;
SELECT COUNT(*) FROM member_user;
SELECT COUNT(*) FROM system_users;
SELECT COUNT(*) FROM trade_order;
SELECT COUNT(*) FROM infra_file;
```

## 4. 后端配置

优先部署 `yudao-server` 这个统一后端服务，因为当前前台和后台都在访问 `48080` 端口，实际更像单体后端部署。`yudao-gateway`、Nacos、拆分微服务可以后续再做，不建议第一版迁云就把复杂度拉满。

关键配置文件：

- `D:\code\yudao电商管理平台前后端\yudao-cloud\yudao-server\src\main\resources\application-prod.yaml`
- `D:\code\furniture web\.env.backend-production.example`

云服务器后端运行环境变量建议：

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=48080
YUDAO_DB_URL=jdbc:mysql://127.0.0.1:3306/oakved?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true
YUDAO_DB_USERNAME=oakved_app
YUDAO_DB_PASSWORD=你的数据库密码
YUDAO_REDIS_HOST=127.0.0.1
YUDAO_REDIS_PORT=6379
YUDAO_REDIS_DATABASE=0
YUDAO_REDIS_PASSWORD=你的Redis密码
YUDAO_ADMIN_UI_URL=https://your-domain.com/admin
YUDAO_APP_UI_URL=https://your-domain.com
YUDAO_PAY_ORDER_NOTIFY_URL=https://your-domain.com/admin-api/pay/notify/order
YUDAO_PAY_REFUND_NOTIFY_URL=https://your-domain.com/admin-api/pay/notify/refund
YUDAO_PAY_TRANSFER_NOTIFY_URL=https://your-domain.com/admin-api/pay/notify/transfer
YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY=
YUDAO_SECURITY_MOCK_ENABLE=false
```

后端打包：

```bash
cd "D:\code\yudao电商管理平台前后端\yudao-cloud"
mvn -pl yudao-server -am -DskipTests package
```

云服务器启动：

```bash
java -jar /opt/oakved/backend/yudao-server.jar
```

生产建议后续改成 `systemd` 服务托管，避免 SSH 断开后进程退出。

## 5. 家具前台配置和打包

关键配置文件：

- `D:\code\furniture web\.env.production.example`
- `D:\code\furniture web\src\services\yudaoRequest.js`

创建 `D:\code\furniture web\.env.production`：

```env
VITE_YUDAO_APP_API_BASE=https://your-domain.com/app-api
VITE_YUDAO_APP_TENANT_ID=121
VITE_YUDAO_US_DEFAULT_AREA_ID=100200
VITE_YUDAO_PAY_CHANNEL_CODE=alipay_pc
VITE_ADDRESS_VERIFICATION_PATH=/member/address/verify
VITE_ADDRESS_VERIFICATION_STATUS_PATH=/member/address/verification-status
VITE_SHOW_AUTH_TOKEN_PANEL=false
```

打包：

```bash
cd "D:\code\furniture web"
npm install
npm run build
```

上传 `D:\code\furniture web\dist` 到：

```text
/opt/oakved/frontend/furniture/
```

注意：`public/assets/generated-furniture` 和 `public/assets/brand` 会被 Vite 自动带进 `dist`，所以不用单独复制，只要上传完整 `dist`。

## 6. 后台管理端配置和打包

关键配置文件：

- `D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3\.env.prod`

需要重点改：

```env
VITE_BASE_URL='https://your-domain.com'
VITE_API_URL=/admin-api
VITE_BASE_PATH=/admin/
VITE_MALL_H5_DOMAIN='https://your-domain.com'
VITE_FURNITURE_WEB_URL=https://your-domain.com
VITE_OUT_DIR=dist-prod
```

如果后台部署在域名根路径 `/`，`VITE_BASE_PATH=/`；如果部署在 `/admin/`，必须改成 `/admin/`，否则刷新页面或静态资源路径可能出错。

打包：

```bash
cd "D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3"
pnpm install
pnpm build:prod
```

上传 `dist-prod` 到：

```text
/opt/oakved/frontend/admin/
```

## 7. Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 50m;

    root /opt/oakved/frontend/furniture;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /admin/ {
        alias /opt/oakved/frontend/admin/;
        try_files $uri $uri/ /admin/index.html;
    }

    location /app-api/ {
        proxy_pass http://127.0.0.1:48080/app-api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /admin-api/ {
        proxy_pass http://127.0.0.1:48080/admin-api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

如果启用 HTTPS，后续用 Certbot 或云厂商证书把 `80` 升级为 `443`。

## 8. 图片长期稳定方案

第一阶段可以保留外链：

- 优点：迁移最快，数据库导入后马上能显示。
- 风险：`images.unsplash.com` 或 `test.yudao.iocoder.cn` 将来如果访问慢、被限制、失效，图片会挂。

第二阶段建议内化图片：

- 扫描数据库字段：`product_spu.pic_url`、`product_spu.slider_pic_urls`、`product_sku.pic_url`、`product_category.pic_url`、`product_category.big_pic_url`、`product_brand.pic_url`、`system_users.avatar`、`system_oauth2_client.logo`。
- 下载外链图片到自己的 OSS/CDN 或 `/opt/oakved/uploads/images/`。
- 批量更新数据库 URL 为自己的域名，例如 `https://your-domain.com/uploads/images/xxx.webp`。
- 如果使用本机目录，需要 Nginx 增加 `/uploads/` 静态资源映射。

## 9. 上线后验证

接口验证：

```bash
curl https://your-domain.com/app-api/product/spu/page?pageNo=1&pageSize=10
curl https://your-domain.com/admin-api/system/auth/get-permission-info
```

页面验证：

- 打开 `https://your-domain.com/`，确认首页图片、商品列表、商品详情页正常。
- 打开 `https://your-domain.com/admin/`，确认后台登录页、商城首页、商品管理正常。
- 后台商品中心检查商品图、分类图是否正常显示。
- 前台下单链路至少走到购物车和提交订单前。
- 浏览器控制台确认没有大量 `404` 静态资源错误。

数据库验证：

```sql
SELECT id, name, pic_url FROM product_spu LIMIT 5;
SELECT id, name, pic_url FROM product_category LIMIT 5;
SELECT id, avatar FROM system_users WHERE avatar IS NOT NULL AND avatar <> '';
SELECT COUNT(*) FROM infra_file;
```

## 10. 推荐执行顺序

1. 本地导出完整数据库 `oakved-full.sql`。
2. 云服务器安装 MySQL、Redis、JDK、Nginx。
3. 云服务器创建数据库并导入 `oakved-full.sql`。
4. 打包并上传 `yudao-server.jar`。
5. 配置后端生产环境变量，启动后端。
6. 打包家具前台，上传到 `/opt/oakved/frontend/furniture/`。
7. 打包后台管理端，上传到 `/opt/oakved/frontend/admin/`。
8. 配置 Nginx，验证 `/`、`/admin/`、`/app-api/`、`/admin-api/`。
9. 检查商品图、首页图、后台商品管理、用户登录。
10. 稳定后再做外链图片内化。

## 11. 最小可行方案

如果想最快上线，先做这四件事：

```text
1. 导出并导入完整 MySQL 数据库。
2. 后端用 prod 配置连云服务器 MySQL/Redis，跑在 48080。
3. 家具前台改 VITE_YUDAO_APP_API_BASE 后打包上传。
4. 后台管理端改 VITE_BASE_URL/VITE_BASE_PATH 后打包上传。
```

图片方面第一版不需要额外处理外链；但前台和后台的 `dist` 必须完整上传，因为里面包含本地静态图。
