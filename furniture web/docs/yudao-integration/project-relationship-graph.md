# Furniture Web / Yudao Web / Yudao Cloud 项目关系图

## 总览

本项目采用“客户前台独立、运营后台独立、统一后端与数据库”的结构。

```mermaid
flowchart LR
  Customer["客户浏览器"] --> Furniture["furniture web<br/>家具商城前台"]
  Staff["运营/管理员浏览器"] --> AdminWeb["yudao-ui-admin-vue3<br/>Yudao 管理后台"]

  Furniture --> AppApi["/app-api<br/>会员端接口"]
  AdminWeb --> AdminApi["/admin-api<br/>管理端接口"]

  AppApi --> Server["yudao-server<br/>统一后端 :48080"]
  AdminApi --> Server

  Server --> MySQL["MySQL<br/>商品/订单/会员/权限数据"]
  Server --> Redis["Redis<br/>登录态/缓存"]
  Server --> Storage["文件/图片存储"]
```

## 三个项目的职责

```mermaid
flowchart TB
  subgraph Frontend["前端层"]
    Furniture["furniture web<br/>客户侧商城"]
    AdminWeb["yudao-ui-admin-vue3<br/>员工侧后台"]
  end

  subgraph Backend["后端层"]
    Server["yudao-cloud / yudao-server<br/>业务聚合服务"]
    Modules["Yudao 业务模块<br/>member / product / trade / pay / promotion / statistics"]
  end

  subgraph Data["数据层"]
    MySQL["MySQL"]
    Redis["Redis"]
    Files["文件/图片"]
  end

  Furniture -->|"商品、购物车、地址、结算、订单"| Server
  AdminWeb -->|"商品维护、订单管理、会员管理、权限配置"| Server
  Server --> Modules
  Modules --> MySQL
  Modules --> Redis
  Modules --> Files
```

## API 边界

```mermaid
flowchart LR
  Furniture["furniture web"] -->|"只能调用"| AppApi["/app-api"]
  AdminWeb["yudao-ui-admin-vue3"] -->|"只能调用"| AdminApi["/admin-api"]

  AppApi -->|"会员用户类型<br/>MEMBER token"| MemberAuth["客户/会员认证域"]
  AdminApi -->|"管理员用户类型<br/>ADMIN token"| AdminAuth["后台管理员认证域"]

  MemberAuth --> Server["yudao-server"]
  AdminAuth --> Server
```

## 家具商城核心业务流

```mermaid
sequenceDiagram
  participant Staff as 运营人员
  participant Admin as yudao-ui-admin-vue3
  participant Server as yudao-server
  participant DB as MySQL/Redis
  participant Customer as 客户
  participant Furniture as furniture web

  Staff->>Admin: 维护商品 SPU/SKU、价格、库存、上下架
  Admin->>Server: 调用 /admin-api
  Server->>DB: 写入商品、库存、配置数据

  Customer->>Furniture: 打开家具商城
  Furniture->>Server: 调用 /app-api/product/spu/page
  Server->>DB: 查询上架商品
  Server-->>Furniture: 返回商品列表

  Customer->>Furniture: 加购物车、结算、下单
  Furniture->>Server: 调用 /app-api/trade/cart/* 和 /app-api/trade/order/*
  Server->>DB: 校验会员、库存、价格、地址并创建订单
  Server-->>Furniture: 返回订单结果
```

## 本地联调关系

```mermaid
flowchart LR
  FurnitureDev["furniture web<br/>npm run dev"] -->|"http://127.0.0.1:48080/app-api"| Backend["yudao-server<br/>48080"]
  AdminDev["yudao-ui-admin-vue3<br/>pnpm dev"] -->|"http://localhost:48080/admin-api"| Backend
  Backend -->|"3306"| MySQL["yudao-mysql-local"]
  Backend -->|"6379"| Redis["yudao-redis-local"]
```

## 结论

- `furniture web` 是客户侧商城，负责品牌化购物体验。
- `yudao-ui-admin-vue3` 是员工侧后台，负责商品、订单、会员、权限等运营管理。
- `yudao-cloud / yudao-server` 是统一业务后端，是商品、库存、订单、会员和支付等数据的事实来源。
- 前台必须走 `/app-api`，后台必须走 `/admin-api`，两套 token 和用户类型不能混用。
