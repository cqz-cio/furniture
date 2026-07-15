import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const home = await readFile(new URL('../src/views/mall/home/index.vue', import.meta.url), 'utf8')
const productSummary = await readFile(
  new URL('../src/views/mall/statistics/product/components/ProductSummary.vue', import.meta.url),
  'utf8'
)

assert.match(home, /:value="Number\(fenToYuan\(/)
assert.match(home, /:reference="Number\(fenToYuan\(/)
assert.doesNotMatch(home, /:(?:value|reference)="fenToYuan\(/)

assert.doesNotMatch(productSummary, /:value="fenToYuan\(/)
assert.match(productSummary, /:value="Number\(fenToYuan\(/)
assert.match(productSummary, /item\.orderPayPrice = Number\(fenToYuan\(item\.orderPayPrice\)\)/)
assert.match(
  productSummary,
  /item\.afterSaleRefundPrice = Number\(fenToYuan\(item\.afterSaleRefundPrice\)\)/
)
assert.doesNotMatch(productSummary, /axisLabel:\s*\{\s*textStyle:/)
assert.match(productSummary, /axisLabel:\s*\{\s*color: '#7F8B9C'/)

console.log('Mall warning contract passed.')
