import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { normalizeBlogSections, normalizeBlogPublishedAt } from '../src/utils/websiteBlogPayload.ts'
import { prepareCatalogContent } from '../src/utils/websiteCatalogPayload.ts'

test('saving imported articles preserves all 994 blocks and strips response-only numbering', () => {
  const { articles } = JSON.parse(
    readFileSync(
      new URL('../../../../website/trendzgreen-homepage/src/legacy-articles.json', import.meta.url),
      'utf8'
    )
  )
  let count = 0
  for (const article of articles) {
    const source = [
      { id: 'body', number: '01', title: article.title, paragraphs: [], blocks: article.blocks }
    ]
    const before = JSON.stringify(source)
    const result = normalizeBlogSections(source)
    assert.deepEqual(result[0].blocks, article.blocks)
    assert.equal('number' in result[0], false)
    assert.equal(JSON.stringify(source), before)
    result[0].blocks[0].text = 'edited copy'
    assert.equal(JSON.stringify(source), before)
    count += article.blocks.length
  }
  assert.equal(count, 994)
})

test('legacy paragraph editor retains its contract and removes empty sections', () => {
  const result = normalizeBlogSections([
    { title: ' Title ', paragraphs: [' hello ', ' ', 'world'] },
    { title: ' ', paragraphs: [] }
  ])
  assert.deepEqual(result, [{ id: 'section-1', title: 'Title', paragraphs: ['hello', 'world'] }])
})

test('structured images, list ordering and table provenance survive edits', () => {
  const blocks = [
    { type: 'image', src: '/assets/articles/test.webp', alt: 'Image' },
    { type: 'list', ordered: true, table: true, items: ['one', 'two'] },
    { type: 'heading', level: 3, text: 'Heading' }
  ]
  const result = normalizeBlogSections([{ title: 'Body', paragraphs: [], blocks }])
  assert.deepEqual(result[0].blocks, blocks)
})

test('clearing optional measurements preserves omitted data and source metadata', () => {
  const input = {
    schemaVersion: 1,
    products: [
      {
        slug: 'sample',
        sourceBlocks: [{ type: 'paragraph', text: 'source' }],
        specs: [
          { code: 'A', description: 'Reference', rimMm: null, capacityOz: 12 },
          { code: 'B', description: 'No data' }
        ]
      }
    ]
  }
  const result = prepareCatalogContent(input)
  assert.equal('rimMm' in result.products[0].specs[0], false)
  assert.equal(result.products[0].specs[0].capacityOz, 12)
  assert.deepEqual(result.products[0].sourceBlocks, input.products[0].sourceBlocks)
  assert.equal(input.products[0].specs[0].rimMm, null)
})

test('publication dates use ERP epoch milliseconds without falling back to 1970', () => {
  const expected = Date.parse('2026-07-06T07:08:37Z')
  assert.equal(normalizeBlogPublishedAt('2026-07-06T07:08:37Z'), expected)
  assert.equal(normalizeBlogPublishedAt(expected), expected)
  assert.equal(normalizeBlogPublishedAt(String(expected)), expected)
  assert.equal(normalizeBlogPublishedAt(''), undefined)
  assert.equal(normalizeBlogPublishedAt(null), undefined)
  assert.throws(() => normalizeBlogPublishedAt('not-a-date'))
})
