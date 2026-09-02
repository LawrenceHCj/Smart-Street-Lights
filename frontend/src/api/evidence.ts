import request from './request'

/**
 * 证据链审计（数据完整性审计）
 * 轻量状态 / 完整验证 / Evidence 历史分页，均由 Java 后端实现。
 */

export interface EvidenceStatus {
  deviceCode: string
  hasEvidence: boolean
  latestSeq: number
  headState: 'VALID' | 'INVALID' | 'NONE'
  metadataState: 'NORMAL' | 'INCONSISTENT'
  metadataIssue: string | null
  anchorState: string
  anchoredThroughSeq: number | null
  unanchoredCount: number | null
  anchorCoverageComplete: boolean
  verificationPerformed: boolean
}

export type OverallStatus =
  | 'VALID_ANCHORED'
  | 'VALID_PARTIALLY_ANCHORED'
  | 'VALID_UNANCHORED'
  | 'INVALID_CHAIN'
  | 'INVALID_MAC'
  | 'INVALID_SOURCE'
  | 'INVALID_ANCHOR'

export interface EvidenceVerify {
  deviceCode: string
  verifiedAt: number
  hasEvidence: boolean
  overallStatus: OverallStatus
  chainStatus: string
  macStatus: string
  sourceStatus: string
  anchorState: string
  checkedCount: number
  firstBrokenSeq: number | null
  breakType: string | null
  reason: string | null
  latestSeq: number
  anchoredThroughSeq: number | null
  unanchoredCount: number
  anchorCoverageComplete: boolean
}

export interface EvidenceEntry {
  seq: number
  deviceCode: string
  eventType: string
  eventTs: number
  sourceType: string
  sourceId: number
  canonicalPayload: string
  entryHash: string
  prevHash: string
  hashVersion: number
  payloadVersion: number
  macVersion: number
  keyId: string
  entryMac: string
  createdAt: string
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface EvidenceEntryQuery {
  page?: number
  size?: number
  fromSeq?: number
  toSeq?: number
  eventType?: string
  sourceType?: string
}

/** 轻量状态：GET /api/evidence/{deviceCode}/status */
export function getEvidenceStatus(deviceCode: string): Promise<EvidenceStatus> {
  return request.get(`/evidence/${deviceCode}/status`, { silent: true })
}

/** 完整验证：GET /api/evidence/{deviceCode}/verify（昂贵，仅手动触发） */
export function verifyEvidence(deviceCode: string): Promise<EvidenceVerify> {
  return request.get(`/evidence/${deviceCode}/verify`, { silent: true })
}

/** Evidence 历史分页：GET /api/evidence/{deviceCode}/entries */
export function listEvidenceEntries(deviceCode: string, query: EvidenceEntryQuery): Promise<PageResult<EvidenceEntry>> {
  return request.get(`/evidence/${deviceCode}/entries`, { params: query, silent: true })
}
