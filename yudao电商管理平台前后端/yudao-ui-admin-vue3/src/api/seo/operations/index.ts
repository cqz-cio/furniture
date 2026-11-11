import request from '@/config/axios'

export interface OperationFile {
  id: number
  name: string
  size: number
  sha256: string
}
export interface DocumentFields {
  title: string
  kind: 'certificate' | 'guide' | 'catalog' | 'specification'
  language: string
  holder: string
  scope: string
  market: string
  owner: string
  issuedAt: string
  expiresAt: string
  assetId: number
}
export interface OperationDocument extends DocumentFields {
  id: number
  version: number
  published?: DocumentFields | null
}
export interface OperationReview {
  id: number
  version: number
  kind: 'catalog' | 'article' | 'document'
  targetId: string
  contentVersion: number
  title: string
  status: 'pending' | 'approved' | 'rejected' | 'published'
  note: string
  submittedAt?: string
  reviewedAt?: string
}
export interface OperationTask {
  id?: number
  version?: number
  title: string
  owner: string
  due: string
  status: 'open' | 'done'
}
export interface OperationCheck {
  id: number
  at: string
  issues: { kind: string; target: string; message: string }[]
}
export interface OperationState {
  documents: OperationDocument[]
  files: OperationFile[]
  reviews: OperationReview[]
  tasks: OperationTask[]
  checks: OperationCheck[]
}
export interface ContentBackup {
  format: string
  payload: Record<string, any>
  sha256: string
}
const base = '/seo/operations'
const post = <T = unknown>(path: string, data: object = {}) =>
  request.post<T>({ url: base + path, data: { siteId: 1, ...data } })
export const getOperations = () =>
  request.get<OperationState>({ url: base + '/state', params: { siteId: 1 } })
export const downloadDocumentFile = (id: number) =>
  request.download<Blob>({ url: base + '/file/download', params: { siteId: 1, id } })
export const deleteDocumentFile = (id: number) => post('/file/delete', { id })
export async function uploadDocumentFile(file: File) {
  const data = new FormData()
  data.append('siteId', '1')
  data.append('file', file)
  const result = await request.upload<{ data: OperationFile }>({
    url: base + '/file/upload',
    data,
    timeout: 60000
  })
  return result.data
}
export const saveDocument = (data: DocumentFields & { id?: number; version?: number }) =>
  request.put<OperationDocument>({ url: base + '/document', data: { siteId: 1, ...data } })
export const documentAction = (
  action: 'publish' | 'offline' | 'delete',
  data: { id: number; version: number }
) => post<OperationDocument>('/document/' + action, data)
export const submitReview = (kind: OperationReview['kind'], targetId: string, version: number) =>
  post<OperationReview>('/review/submit', { kind, targetId, version })
export const decideReview = (
  review: OperationReview,
  status: 'approved' | 'rejected',
  note: string
) =>
  post<OperationReview>('/review/decide', { id: review.id, version: review.version, status, note })
export const saveTask = (data: OperationTask) =>
  request.put<OperationTask>({ url: base + '/task', data: { siteId: 1, ...data } })
export const deleteTask = (data: OperationTask) =>
  post('/task/delete', { id: data.id, version: data.version })
export const runCheck = () => post<OperationCheck>('/check')
export const exportBackup = () =>
  request.get<ContentBackup>({ url: base + '/backup', params: { siteId: 1 } })
export const checkBackup = (backup: ContentBackup) =>
  post<Record<string, any>>('/backup/check', { backup })
export const restoreBackup = (
  backup: ContentBackup,
  kind: OperationReview['kind'],
  version: number,
  id?: number
) => post('/backup/restore', { backup, kind, version, id })
