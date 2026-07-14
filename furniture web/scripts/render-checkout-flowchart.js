import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outputPng = path.resolve(__dirname, '../docs/checkout-closure-flowchart.png');
const tempHtml = path.resolve(__dirname, '../docs/checkout-closure-flowchart-render.html');

const nodes = [
  { id: 'U', label: '用户在 Checkout 页面', x: 60, y: 40, w: 220, h: 52, type: 'rect', color: '#E8F0FE' },
  { id: 'FE1', label: 'CheckoutPage.vue\n填写/选择收货信息', x: 350, y: 40, w: 240, h: 52, type: 'rect', color: '#E8F7EE' },
  { id: 'FE2', label: 'checkoutSession.js\n收货信息快照 + 本地必填校验', x: 60, y: 140, w: 300, h: 58, type: 'rect', color: '#FFF3CD' },
  { id: 'CKT', label: '地址是否已有有效核验快照？', x: 450, y: 140, w: 260, h: 70, type: 'diamond', color: '#FCE7F6' },
  { id: 'AV', label: 'app-api/member/address/verify', x: 780, y: 130, w: 260, h: 52, type: 'rect', color: '#EAF6FF' },
  { id: 'PAY', label: '进入支付前校验', x: 460, y: 250, w: 240, h: 52, type: 'rect', color: '#E8F0FE' },
  { id: 'AWR', label: '核验返回', x: 780, y: 220, w: 260, h: 70, type: 'diamond', color: '#FCE7F6' },
  { id: 'AR', label: '核验高可信', x: 1090, y: 120, w: 220, h: 52, type: 'rect', color: '#EAF6FF' },
  { id: 'AWL', label: '低置信/失败, 进入提示', x: 1090, y: 220, w: 220, h: 52, type: 'rect', color: '#FFF0E6' },
  { id: 'S1', label: 'AddressReviewPanel', x: 770, y: 330, w: 250, h: 52, type: 'rect', color: '#E8F0FE' },
  { id: 'UX', label: '用户确认', x: 520, y: 420, w: 200, h: 70, type: 'diamond', color: '#FCE7F6' },
  { id: 'ADDR1', label: '用原地址\na.selectedAddress=shippingForm', x: 120, y: 540, w: 260, h: 62, type: 'rect', color: '#F7F2FF' },
  { id: 'ADDR2', label: '用核验建议\na.selectedAddress=verifiedAddress', x: 430, y: 540, w: 280, h: 62, type: 'rect', color: '#F7F2FF' },
  { id: 'BLD', label: 'buildConfirmedShippingAddressInput', x: 760, y: 540, w: 300, h: 62, type: 'rect', color: '#E8F7EE' },
  { id: 'PAYG', label: 'app-api/trade/order/create', x: 760, y: 650, w: 300, h: 52, type: 'rect', color: '#E8F0FE' },
  { id: 'ORDER', label: '订单类型', x: 1080, y: 650, w: 220, h: 70, type: 'diamond', color: '#FCE7F6' },
  { id: 'ZERO', label: '0元订单直达订单页', x: 780, y: 770, w: 260, h: 58, type: 'rect', color: '#EAF6FF' },
  { id: 'SUBMIT', label: 'app-api/pay/order/submit', x: 1100, y: 770, w: 220, h: 52, type: 'rect', color: '#EAF6FF' },
  { id: 'PG', label: '第三方支付渠道', x: 1110, y: 880, w: 200, h: 52, type: 'rect', color: '#EAF6FF' },
  { id: 'RET', label: '前端支付返回处理', x: 1110, y: 980, w: 220, h: 60, type: 'rect', color: '#FFF3CD' },
  { id: 'ORD', label: '订单详情 / 状态=Paid', x: 820, y: 1080, w: 260, h: 56, type: 'rect', color: '#E8F7EE' },
  { id: 'ORG', label: '订单详情/列表拉取\napp-api/trade/order/get-detail/list', x: 60, y: 1080, w: 300, h: 70, type: 'rect', color: '#EAF6FF' },
  { id: 'ERR', label: '支付回调/异常', x: 1300, y: 1080, w: 200, h: 58, type: 'rect', color: '#FFF0E6' },
  { id: 'ORC', label: '后端支付回调验真', x: 1040, y: 1080, w: 220, h: 58, type: 'rect', color: '#FCE7F6' },
  { id: 'ORC2', label: '订单状态归档 + 幂等', x: 1030, y: 1185, w: 240, h: 58, type: 'rect', color: '#E8F0FE' },
];

const edges = [
  { from: 'U', to: 'FE1' },
  { from: 'FE1', to: 'FE2' },
  { from: 'FE2', to: 'CKT' },
  { from: 'CKT', to: 'AV', label: '否 / 已变更' },
  { from: 'CKT', to: 'PAY', label: '是' },
  { from: 'AV', to: 'AWR' },
  { from: 'AWR', to: 'AR', label: 'CONFIRMED' },
  { from: 'AWR', to: 'AWL', label: '其他' },
  { from: 'AR', to: 'S1' },
  { from: 'AWL', to: 'S1' },
  { from: 'S1', to: 'UX' },
  { from: 'UX', to: 'ADDR1', label: '用原地址' },
  { from: 'UX', to: 'ADDR2', label: '用核验建议' },
  { from: 'ADDR1', to: 'BLD' },
  { from: 'ADDR2', to: 'BLD' },
  { from: 'BLD', to: 'PAYG' },
  { from: 'PAYG', to: 'ORDER' },
  { from: 'ORDER', to: 'ZERO', label: 'amount=0' },
  { from: 'ORDER', to: 'SUBMIT', label: 'amount>0' },
  { from: 'SUBMIT', to: 'PG' },
  { from: 'PG', to: 'RET' },
  { from: 'RET', to: 'ORD' },
  { from: 'ZERO', to: 'ORD' },
  { from: 'ORG', to: 'ORD' },
  { from: 'ERR', to: 'ORC' },
  { from: 'ORC', to: 'ORC2' },
];

const nodeById = new Map(nodes.map((n) => [n.id, n]));
const width = 1560;
const height = 1280;

const escapeText = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const nodeAnchor = (n) => ({
  left: { x: n.x, y: n.y + n.h / 2 },
  right: { x: n.x + n.w, y: n.y + n.h / 2 },
  top: { x: n.x + n.w / 2, y: n.y },
  bottom: { x: n.x + n.w / 2, y: n.y + n.h },
});

const connect = (fromId, toId, label) => {
  const from = nodeById.get(fromId);
  const to = nodeById.get(toId);
  if (!from || !to) return '';
  const fromA = nodeAnchor(from);
  const toA = nodeAnchor(to);

  const start = { ...fromA.right };
  const end = { ...toA.left };
  const midX = (start.x + end.x) / 2;
  const path = `M ${start.x} ${start.y} L ${midX} ${start.y} L ${midX} ${end.y} L ${end.x} ${end.y}`;

  const labelOffset = label ? `\n<tspan x='${midX + 6}' y='${(start.y + end.y) / 2 - 8}' fill='#444' font-size='14'>${escapeText(label)}</tspan>` : '';
  const arrow = `<g><path d='${path}' stroke='#222' stroke-width='2' fill='none' /> <polygon points='${end.x-6},${end.y-4} ${end.x},${end.y} ${end.x-6},${end.y+4}' fill='#222'/></g>`;
  const text = label
    ? `<text x='${midX + 8}' y='${(start.y + end.y) / 2 - 12}' fill='#333' font-size='14'>${escapeText(label)}</text>`
    : '';
  return arrow + text;
};

const renderNode = (node) => {
  const isDiamond = node.type === 'diamond';
  const lines = escapeText(node.label).split('\n');
  const t = node.type === 'diamond'
    ? `<polygon points='${node.x + node.w / 2},${node.y} ${node.x + node.w},${node.y + node.h / 2} ${node.x + node.w / 2},${node.y + node.h} ${node.x},${node.y + node.h / 2}' fill='${node.color}' stroke='#2b2b2b' stroke-width='2' />`
    : `<rect x='${node.x}' y='${node.y}' width='${node.w}' height='${node.h}' fill='${node.color}' stroke='#2b2b2b' stroke-width='2' rx='8' />`;

  const lineHeight = 16;
  const startY = node.y + (node.h - lines.length * lineHeight) / 2 + 14;
  const text = lines
    .map((line, i) => `<tspan x='${node.x + node.w / 2}' y='${startY + i * lineHeight}' text-anchor='middle'>${escapeText(line)}</tspan>`)
    .join('');

  return `${t}<text font-size='14' fill='#111' font-family='Arial, "Microsoft YaHei", sans-serif'>${text}</text>`;
};

const svg = `<!doctype html>
<html>
<body>
<div id='canvas'>
  <svg width='${width}' height='${height}' xmlns='http://www.w3.org/2000/svg'>
    <rect width='${width}' height='${height}' fill='white' />
    ${nodes.map(renderNode).join('\n')}
    ${edges.map((e) => connect(e.from, e.to, e.label)).join('\n')}
  </svg>
</div>
</body>
</html>`;

fs.writeFileSync(tempHtml, svg, 'utf8');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width, height } });
  await page.goto(`file://${tempHtml.replace(/\\/g, '/')}`);
  await page.waitForTimeout(200);
  await page.screenshot({ path: outputPng, fullPage: true });
  await browser.close();
  fs.unlinkSync(tempHtml);
  console.log(`Saved: ${outputPng}`);
})();
