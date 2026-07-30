# Oakved 云服务器部署工具包

这个目录用于把当前本地项目整理成一个可上传到云服务器的发布包。

它不会要求你现在就确定最终域名。你可以先用服务器 IP，例如 `http://1.2.3.4`，后面有域名和 HTTPS 后再重新打包。

## 一键生成发布包

在 Windows PowerShell 里执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
D:\code\oakved-deploy-kit\scripts\build-package.ps1 -PublicOrigin "http://你的服务器IP"
```

如果已经有域名：

```powershell
D:\code\oakved-deploy-kit\scripts\build-package.ps1 -PublicOrigin "https://你的域名"
```

生成结果默认在：

```text
D:\code\oakved-release\oakved-release-时间戳\
```

里面会包含：

```text
backend/
  yudao-server.jar
  backend.env
frontend/
  furniture/
  admin/
nginx/
  oakved.conf
server/
  import-db.sh
  start-backend.sh
  oakved-yudao.service
sql/
  放数据库 dump 的位置
```

## 数据库导出

如果本机有 `mysqldump`：

```powershell
D:\code\oakved-deploy-kit\scripts\export-db.ps1
```

如果提示找不到 `mysqldump`，安装 MySQL Client 或把 `mysqldump.exe` 加入 PATH 后再执行。

导出的 SQL 放到发布包的 `sql/oakved-full.sql`，上传到云服务器后执行：

```bash
bash /opt/oakved/server/import-db.sh /opt/oakved/sql/oakved-full.sql
```

## 上传到云服务器后

推荐上传到：

```text
/opt/oakved
```

然后：

```bash
cd /opt/oakved
bash server/import-db.sh sql/oakved-full.sql
bash server/start-backend.sh
sudo cp nginx/oakved.conf /etc/nginx/conf.d/oakved.conf
sudo nginx -t
sudo systemctl reload nginx
```

## 需要替换的内容

第一版只需要重点确认这些：

- `backend/backend.env` 里的数据库账号密码、Redis 密码。
- `nginx/oakved.conf` 里的 `server_name`，如果你只有 IP，可以先用 `_`。
- 如果使用 HTTPS，后续再把 Nginx 监听从 `80` 升级到 `443`。

## 图片说明

- 前台本地图片已经随 `frontend/furniture` 打包进去。
- 后台本地图标已经随 `frontend/admin` 打包进去。
- 商品图当前多为数据库里的外链 URL，数据库导入后会保持一致。
- 如果以后要完全自有化图片，再把数据库里的外链下载到 OSS/CDN，并批量替换 URL。
