const naturalCollator = new Intl.Collator('zh-CN', {
  numeric: true,
  sensitivity: 'base',
})

export function compareNaturalText(left?: string | null, right?: string | null): number {
  return naturalCollator.compare((left ?? '').trim(), (right ?? '').trim())
}

export function compareDeviceCode<T extends { code?: string | null }>(left: T, right: T): number {
  const leftCode = left.code ?? ''
  const rightCode = right.code ?? ''
  const leftIsStandard = /^SL-/i.test(leftCode)
  const rightIsStandard = /^SL-/i.test(rightCode)

  if (leftIsStandard !== rightIsStandard) return leftIsStandard ? -1 : 1
  return compareNaturalText(leftCode, rightCode)
}

export function compareTimestampDesc(left?: string | number | null, right?: string | number | null): number {
  const leftTime = left == null ? Number.NEGATIVE_INFINITY : new Date(left).getTime()
  const rightTime = right == null ? Number.NEGATIVE_INFINITY : new Date(right).getTime()
  const safeLeft = Number.isNaN(leftTime) ? Number.NEGATIVE_INFINITY : leftTime
  const safeRight = Number.isNaN(rightTime) ? Number.NEGATIVE_INFINITY : rightTime
  return safeRight - safeLeft
}
