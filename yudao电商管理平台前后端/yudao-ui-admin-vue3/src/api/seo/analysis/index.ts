import request from '@/config/axios'

export type SeoAnalysisSourceType = 'ENTITY' | 'MANUAL' | 'DOCUMENT'
export type SeoAnalysisStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED'
export type SeoKeywordType = 'FOCUS' | 'RELATED'
export type SeoKeywordGrade = 'HIGH' | 'MEDIUM' | 'WEAK' | 'LOW'

export interface SeoContentSnapshotReqVO {
  seoTitle?: string
  h1?: string
  introduction?: string
  metaDescription?: string
  slug?: string
  body?: string
  headings?: string[]
  paragraphs?: string[]
  attributes?: Record<string, string>
  imageAlts?: string[]
}

export interface SeoAnalysisRunReqVO {
  siteId: number
  entityType: string
  entityId: number
  locale: string
  focusKeyphrase: string
  relatedKeyphrases: string[]
  sourceType: SeoAnalysisSourceType
  sourceId?: number
  idempotencyKey: string
  content?: SeoContentSnapshotReqVO
}

export interface SeoParsedDocumentRespVO {
  filename: string
  extension: 'docx' | 'pdf' | 'xlsx'
  contentType: string | null
  fileSize: number
  extractedCharacters: number
  truncated: boolean
  content: string
}

export interface SeoDocumentAnalysisRunReqVO {
  siteId: number
  entityType: string
  entityId: number
  locale: string
  focusKeyphrase: string
  relatedKeyphrases: string[]
  idempotencyKey: string
  file: File
}

export interface SeoKeywordRuleRespVO {
  id: number
  ruleCode: string
  dimension: string
  severity: string
  status: string
  score: number | null
  maxScore: number | null
  contentLocation: string | null
  evidence: Record<string, unknown> | null
  reason: string
  recommendation: string
  recoverableScore: number | null
  sort: number
}

export interface SeoKeywordAnalysisRespVO {
  id: number
  analysisId: number
  keywordType: SeoKeywordType
  keyword: string
  normalizedKeyword: string
  sort: number
  keyPositionPercent: number | null
  lexicalMatchPercent: number | null
  semanticPercent: number | null
  distributionPercent: number | null
  intentCoveragePercent: number | null
  relevancePercent: number | null
  confidencePercent: number
  grade: SeoKeywordGrade | null
  analysisStatus: SeoAnalysisStatus
  exactMatchCount: number
  variantMatchCount: number
  matchedLocations: string[]
  dictionaryVersion: string
  semanticModelVersion: string | null
  suggestionCount: number
  items: SeoKeywordRuleRespVO[] | null
}

export interface SeoAnalysisRespVO {
  id: number
  siteId: number
  sourceType: SeoAnalysisSourceType
  sourceId: number | null
  entityType: string
  entityId: number
  locale: string
  focusKeyphrase: string
  previousAnalysisId: number | null
  overallRelevancePercent: number | null
  confidencePercent: number | null
  totalScore: number | null
  engineVersion: string
  ruleProfileVersion: string
  dictionaryVersion: string
  semanticModelVersion: string | null
  analysisStatus: SeoAnalysisStatus
  failureCode: string | null
  failureMessage: string | null
  createTime: string
  updateTime: string
  keywords: SeoKeywordAnalysisRespVO[]
}

export interface SeoKeywordComparisonRespVO {
  keywordType: SeoKeywordType
  keyword: string
  normalizedKeyword: string
  previousPercent: number | null
  currentPercent: number | null
  deltaPercent: number | null
  changeType: 'ADDED' | 'REMOVED' | 'UNCHANGED' | 'IMPROVED' | 'REGRESSED'
  resolvedRuleCodes: string[]
  newRuleCodes: string[]
}

export interface SeoAnalysisCompareRespVO {
  previousAnalysisId: number
  currentAnalysisId: number
  keywords: SeoKeywordComparisonRespVO[]
}

export const createSeoIdempotencyKey = (prefix = 'seo-analysis') => {
  const randomPart = globalThis.crypto?.randomUUID?.() ||
    `${Date.now()}-${Math.random().toString(36).slice(2)}`
  return `${prefix}-${randomPart}`
}

export const runSeoAnalysis = async (data: SeoAnalysisRunReqVO) => {
  return request.post<number>({ url: '/seo/analysis/run', data })
}

export const parseSeoDocument = async (file: File) => {
  const data = new FormData()
  data.append('file', file)
  const response = await request.upload<{ data: SeoParsedDocumentRespVO }>({
    url: '/seo/analysis/document/parse',
    data
  })
  return response.data
}

export const runSeoDocumentAnalysis = async (requestData: SeoDocumentAnalysisRunReqVO) => {
  const data = new FormData()
  data.append('siteId', String(requestData.siteId))
  data.append('entityType', requestData.entityType)
  data.append('entityId', String(requestData.entityId))
  data.append('locale', requestData.locale)
  data.append('focusKeyphrase', requestData.focusKeyphrase)
  requestData.relatedKeyphrases.forEach((keyword) => data.append('relatedKeyphrases', keyword))
  data.append('idempotencyKey', requestData.idempotencyKey)
  data.append('file', requestData.file)
  const response = await request.upload<{ data: number }>({
    url: '/seo/analysis/document/run',
    data
  })
  return response.data
}

export const getSeoAnalysis = async (id: number) => {
  return request.get<SeoAnalysisRespVO>({ url: `/seo/analysis/${id}` })
}

export const getSeoAnalysisKeywords = async (id: number) => {
  return request.get<SeoKeywordAnalysisRespVO[]>({ url: `/seo/analysis/${id}/keywords` })
}

export const getSeoKeywordAnalysis = async (id: number, keywordAnalysisId: number) => {
  return request.get<SeoKeywordAnalysisRespVO>({
    url: `/seo/analysis/${id}/keywords/${keywordAnalysisId}`
  })
}

export const rerunSeoAnalysis = async (id: number, idempotencyKey: string) => {
  return request.post<number>({
    url: `/seo/analysis/${id}/rerun`,
    data: { idempotencyKey }
  })
}

export const compareSeoAnalysis = async (id: number, previousAnalysisId?: number) => {
  return request.get<SeoAnalysisCompareRespVO>({
    url: `/seo/analysis/${id}/compare`,
    params: previousAnalysisId ? { previousAnalysisId } : undefined
  })
}
