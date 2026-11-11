import type { WebsiteBlogSection } from '../api/seo/blog/index'

/** Full ERP's LocalDateTime deserializer accepts epoch milliseconds. */
export function normalizeBlogPublishedAt(value?: string | number | null): number | undefined {
  if (value === undefined || value === null || value === '') return undefined
  const milliseconds =
    typeof value === 'number' || /^\d+$/.test(value) ? Number(value) : Date.parse(value)
  if (!Number.isFinite(milliseconds)) throw new Error('文章发布时间无效，请重新选择日期。')
  return milliseconds
}

/** Keep imported structured blocks intact and omit response-only section numbering. */
export function normalizeBlogSections(sections: WebsiteBlogSection[]): WebsiteBlogSection[] {
  return sections
    .map((section, index) => ({
      id: section.id || `section-${index + 1}`,
      title: section.title.trim(),
      paragraphs: (section.paragraphs || []).map((p) => p.trim()).filter(Boolean),
      ...(section.blocks?.length ? { blocks: JSON.parse(JSON.stringify(section.blocks)) } : {})
    }))
    .filter((section) => section.title || section.paragraphs.length || section.blocks?.length)
}
