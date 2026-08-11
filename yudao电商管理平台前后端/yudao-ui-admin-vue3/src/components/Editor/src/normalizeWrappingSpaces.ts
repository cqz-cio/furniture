const NON_BREAKING_SPACE_PATTERN = /\u00a0|&nbsp;|&#0*160;|&#x0*a0;/gi

/**
 * 将旧网页文案中的不换行空格转换为普通空格。
 *
 * 只改变空格的换行语义，不会移除段落、换行标签或其它富文本格式。
 */
export const normalizeWrappingSpaces = (html: string): string =>
  html.replace(NON_BREAKING_SPACE_PATTERN, ' ')
