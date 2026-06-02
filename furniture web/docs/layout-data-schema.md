# Layout Data Schema

> 目标：统一后续自动抽取输出的数据结构，方便从 RH 源站映射到 Vue3 组件。

## 1. File Layout

```text
data/
  rh-sale/
    outerhtml-extraction.json
    navigation-candidates.json
    image-spec-candidates.json
  pages/
    rh-home-1440.json
    rh-home-390.json
    rh-sale-1440.json
    rh-sale-390.json
    rh-plp-1440.json
    rh-pdp-1440.json
```

## 2. Page Record

```json
{
  "pageId": "RH-SALE",
  "url": "https://rh.com/us/en/sale",
  "viewport": {
    "width": 1440,
    "height": 900
  },
  "capturedAt": "2026-05-26T09:40:00",
  "title": "Sale | RH",
  "body": {
    "brand": "RH",
    "pagePath": "/us/en/sale",
    "userType": "ANONYMOUS"
  },
  "elements": [],
  "images": [],
  "interactions": []
}
```

## 3. Element Record

```json
{
  "id": "el-0001",
  "selector": "main#main",
  "tag": "main",
  "role": null,
  "text": "",
  "rect": {
    "x": 0,
    "y": 120,
    "width": 1440,
    "height": 1200
  },
  "styles": {
    "display": "block",
    "position": "relative",
    "margin": "0px",
    "padding": "0px",
    "fontFamily": "RHSans, Arial, sans-serif",
    "fontSize": "12px",
    "lineHeight": "18px",
    "color": "rgb(0, 0, 0)",
    "backgroundColor": "rgba(0, 0, 0, 0)"
  },
  "children": []
}
```

## 4. Image Record

```json
{
  "id": "img-0001",
  "selector": "section.hero img",
  "sourceType": "img",
  "url": "https://media.restorationhardware.com/...",
  "containerRect": {
    "x": 0,
    "y": 180,
    "width": 1440,
    "height": 640
  },
  "naturalSize": {
    "width": 2000,
    "height": 900
  },
  "renderedSize": {
    "width": 1440,
    "height": 640
  },
  "fit": "cover",
  "position": "center center",
  "recommended": {
    "oneX": "1440 x 640",
    "twoX": "2880 x 1280",
    "format": "WebP",
    "maxSize": "250KB - 600KB"
  }
}
```

## 5. Interaction Record

```json
{
  "id": "state-nav-living-open",
  "name": "Desktop Living navigation open",
  "trigger": "click Living",
  "viewport": "1440 x 900",
  "screenshot": "captures/source/rh-nav-living-1440.png",
  "notes": "Used for mega-menu reconstruction"
}
```

