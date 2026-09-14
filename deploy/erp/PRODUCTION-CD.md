# 生产 CD

GitHub Actions → **ERP CD - production**，始终选择 `main`。CI 自动构建一次镜像并上传 GHCR；生产 CD 使用已上传的摘要，不重新构建。选择的完整 Release ID 必须已通过 **ERP CD - test**。

## 首次接管现有生产服务

1. `preflight`：核对现有 Docker 服务、旧 Java 服务、V048 数据库、Nginx、磁盘和旁路目录服务。不会停止服务、迁移数据库或改路由。
2. `prepare`：从 GHCR 下载生产镜像，备份现有数据库，在独立数据库中演练还原、迁移及再次执行的幂等性。校验产品、SKU、账号、附件数量和附件字节数，并执行租户 121/162 的数据检查。现有生产继续运行。
3. 查看结果 `status=prepared`、`restore_passed=true`、`migration_passed=true`。准备有效期 24 小时，必须使用相同 Release ID，环境变化需重新 prepare。
4. 确定维护窗口后，选择 `cutover` 并勾选 `confirm_cutover`。这是首次真正切换线上流量的操作。
5. 切换成功后在 Settings → Environments → production → Variables 将 `ERP_CD_ENABLED` 设置为 `true`，以后使用 `deploy`。

首次接管已核对的目标是 `ubuntu@43.153.40.182:22`，部署根目录 `/opt/oakved-deploy/production`，后台入口 `https://api.vanzhome.com/admin/`。当前支持源数据库 V048 升级至通过测试的 V049 或 V050；新增迁移需先审查接管配置。

切换时先对 ERP 路由启用维护响应，停止原来的 `furniture-erp-production` 容器和 `oakved-yudao.service`，确认旧数据库没有其他连接，再重新备份最新数据并生成新的生产数据库。原数据库不执行迁移。新服务使用 `48082/18081` 端口和 `oakved-erp-production` 项目，验证健康状态、版本回执及租户接口后才开放流量。TLS、图片缓存、其他站点配置和目录服务保持原样。

## 中断和恢复

- 首次切换失败或中断：使用相同 Release ID 选择 `recover` 并勾选确认，依据服务器记录处理。不要直接重跑 deploy。
- 尚未放开流量：停止新服务，恢复旧 Docker 容器的启动策略、旧 Java 服务和原来的路由。
- 已经可能放开流量：只恢复新版本服务，禁止自动退回旧数据库，避免丢失新订单或修改。
- `prepare` 不会登记成功上线记录；生产发布成功记录只在实际切换和健康检查完成后创建。
- 备份和阶段记录位于 `/opt/oakved-deploy/production/bootstrap/`，包含运行凭据的文件只保存在服务器的私有目录。失败尝试和备份保留供排查，不作为“旧镜像”删除。

## 日常发布

测试 CD 成功 → 生产 `preflight` → 手动 `deploy`。两者使用同一个完整 Release ID。跨数据库版本发布还需配置相应的 `migration_reviews`，不会因为打开 CD 开关而跳过迁移审核。`rollback` 只接受已经登记且与当前数据库兼容的历史版本。

GitHub production 环境使用变量 `ERP_SSH_HOST`、`ERP_SSH_USER`、`ERP_SSH_PORT`、`ERP_DEPLOY_ROOT`、`ERP_CD_ENABLED`，密钥使用 Secrets `ERP_SSH_PRIVATE_KEY`、`ERP_SSH_KNOWN_HOSTS`。不要把登录密码或私钥放进仓库。GitHub 执行时无需每次人工授权。

本机维护连接应绑定物理网卡 IPv4，例如 `ssh -b 192.168.110.145 -i "$HOME/.ssh/vanz_github_actions" ubuntu@43.153.40.182`。runner 也支持 `ERP_SSH_BIND_ADDRESS`；GitHub 云端 runner 不设置这个本机地址。
