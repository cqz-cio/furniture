# RH Console Extraction Guide

> 用途：从你本机能正常打开的 RH 页面导出真实渲染后的布局数据。每个页面、每个状态、每个 viewport 都需要单独导出一份 JSON。

## 1. 使用方法

1. 打开目标 RH 页面。
2. 把浏览器窗口调到目标尺寸，例如桌面端或移动端。
3. 等页面图片和导航加载完成。
4. 按 `F12` 打开 DevTools。
5. 进入 `Console`。
6. 如果提示不能粘贴，先输入：

```text
allow pasting
```

7. 打开项目里的脚本文件：

```text
D:\furniture web\tools\rh-console-layout-export.js
```

8. 复制整个文件内容，粘贴到 Console，按 Enter。
9. 浏览器会下载一个 `rh-layout-...json` 文件。
10. 把下载的 JSON 文件发给 Codex。

## 2. 必采页面

按优先级采集：

| 优先级 | 页面 | URL | 桌面端 | 移动端 |
| --- | --- | --- | --- | --- |
| 1 | Sale 页面 | `https://rh.com/us/en/sale` | 已采一次，建议补 1440x900 | 需要 |
| 2 | 首页 | `https://rh.com/us/en` | 需要 | 需要 |
| 3 | Living 一级页 | 点击首页 `Living` | 需要 | 可后补 |
| 4 | 商品列表页 | 例如 All Living Sale | 需要 | 需要 |
| 5 | 商品详情页 | 任意 RH 商品详情页 | 需要 | 需要 |

## 3. 建议文件命名

下载后可以手动改名，方便管理：

```text
rh-sale-desktop-1440x900.json
rh-sale-mobile-390x844.json
rh-home-desktop-1440x900.json
rh-home-mobile-390x844.json
rh-living-desktop-1440x900.json
rh-plp-living-sale-desktop-1440x900.json
rh-pdp-sofa-desktop-1440x900.json
```

## 4. 每次导出前检查

- 页面不是空白。
- 图片已经出现。
- 顶部导航处于你想复刻的状态。
- 如果要复刻菜单展开，先展开菜单，再运行脚本。
- 如果要复刻筛选抽屉，先打开筛选，再运行脚本。

## 5. 重要原则

```text
一个页面 + 一个状态 + 一个 viewport = 一份 JSON
```

示例：

- Sale 桌面首屏是一份。
- Sale 移动端首屏是另一份。
- Sale 桌面菜单展开又是另一份。
- 商品列表筛选打开也是另一份。

不要用一份 JSON 试图覆盖所有页面。
