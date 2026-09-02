<template>
  <div class="workspace-page evidence-page">
    <div class="page-intro">
      <div>
        <h1 class="page-title">证据链审计</h1>
        <p class="page-desc">通过 SHA-256 哈希链与 HMAC 校验检测历史数据是否被修改，并定位异常位置</p>
      </div>
      <div class="intro-actions">
        <el-select v-model="selectedDevice" placeholder="选择设备" filterable :loading="devicesLoading" @change="onDeviceChange">
          <el-option v-for="d in devices" :key="d.code" :value="d.code" :label="d.code">
            <span class="num">{{ d.code }}</span>
            <span class="device-status" :class="d.status === 'ONLINE' ? 'on' : 'off'">{{ d.status === 'ONLINE' ? '在线' : '离线' }}</span>
          </el-option>
        </el-select>
        <el-button size="small" :loading="statusLoading" :disabled="!selectedDevice" @click="loadStatus">刷新状态</el-button>
      </div>
    </div>

    <div class="audit-note" role="note">
      <el-icon aria-hidden="true"><InfoFilled /></el-icon>
      <div>
        <strong>轻量检查 ≠ 完整验证</strong>
        <span>轻量状态只校验链头 HMAC 与元数据一致性；「执行完整验证」才会扫描整条证据链并定位首次异常位置。</span>
      </div>
    </div>

    <!-- 区域 B：轻量完整性状态卡 -->
    <section v-if="selectedDevice" class="panel status-panel" aria-live="polite">
      <div class="panel-head">
        <div>
          <div class="panel-title">轻量完整性状态</div>
          <div class="panel-caption">设备 {{ selectedDevice }} 的链头与锚点元数据</div>
        </div>
        <el-tag v-if="status" :type="statusTagType" effect="light">{{ statusHeadline }}</el-tag>
      </div>

      <div v-if="statusError" class="error-state" role="alert">
        <span>{{ statusError }}</span>
        <el-button size="small" @click="loadStatus">重新加载</el-button>
      </div>

      <div v-else-if="status" class="status-grid">
        <article class="status-cell" :class="statusCellClass">
          <span class="cell-label">证据记录</span>
          <strong class="cell-value">{{ status.hasEvidence ? '存在' : '暂无' }}</strong>
          <span class="cell-foot">{{ status.hasEvidence ? `最新序号 ${status.latestSeq}` : '暂无证据记录' }}</span>
        </article>
        <article class="status-cell" :class="statusCellClass">
          <span class="cell-label">链头状态</span>
          <strong class="cell-value">{{ headStateText(status.headState) }}</strong>
          <span class="cell-foot">{{ status.verificationPerformed ? '已执行完整验证' : '尚未执行完整链验证' }}</span>
        </article>
        <article class="status-cell" :class="statusCellClass">
          <span class="cell-label">元数据一致性</span>
          <strong class="cell-value">{{ status.metadataState === 'NORMAL' ? '正常' : '异常' }}</strong>
          <span class="cell-foot">{{ issueText(status.metadataIssue) }}</span>
        </article>
        <article class="status-cell" :class="statusCellClass">
          <span class="cell-label">锚点覆盖</span>
          <strong class="cell-value">{{ anchorStateText(status.anchorState) }}</strong>
          <span class="cell-foot">{{ coverageText(status) }}</span>
        </article>
      </div>
      <div v-else v-loading="statusLoading" class="status-empty">正在读取轻量状态…</div>
    </section>

    <!-- 区域 D/E：完整验证 -->
    <section v-if="selectedDevice" class="panel verify-panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">完整证据链验证</div>
          <div class="panel-caption">扫描整条证据链，校验哈希链、Entry HMAC 与业务源一致性</div>
        </div>
        <el-button v-if="canVerify" type="primary" size="small" :loading="verifyLoading" @click="runVerify">
          {{ verifyLoading ? '正在验证历史证据链……' : '执行完整验证' }}
        </el-button>
        <el-tooltip v-else content="当前角色无完整验证权限" placement="top">
          <span><el-button size="small" disabled>执行完整验证</el-button></span>
        </el-tooltip>
      </div>

      <div v-if="verifyResult" class="verify-result" :class="overallClass(verifyResult.overallStatus)" aria-live="polite">
        <div class="verify-headline">
          <el-icon aria-hidden="true"><CircleCheck v-if="isValidOverall(verifyResult.overallStatus)" /><WarningFilled v-else /></el-icon>
          <strong>{{ overallText(verifyResult.overallStatus) }}</strong>
          <span class="freshness num">验证于 {{ formatTime(verifyResult.verifiedAt) }} · 检查 {{ verifyResult.checkedCount }} 条</span>
        </div>

        <div v-if="verifyResult.firstBrokenSeq != null" class="broken-locate">
          <span>首次异常位置：<b class="num">Seq #{{ verifyResult.firstBrokenSeq }}</b></span>
          <el-button size="small" type="danger" plain @click="locateAnomaly(verifyResult.firstBrokenSeq)">定位异常</el-button>
        </div>

        <div class="verify-dim-grid">
          <span><i>链</i>{{ verifyResult.chainStatus }}</span>
          <span><i>MAC</i>{{ verifyResult.macStatus }}</span>
          <span><i>源</i>{{ verifyResult.sourceStatus }}</span>
          <span><i>锚点</i>{{ verifyResult.anchorState }}</span>
          <span><i>覆盖</i>{{ verifyResult.anchoredThroughSeq ?? '—' }} / {{ verifyResult.latestSeq }}</span>
        </div>
        <div v-if="verifyResult.reason" class="verify-reason">{{ verifyResult.reason }}</div>
      </div>
      <div v-else class="status-empty muted">尚未执行完整验证，点击上方按钮开始。</div>
    </section>

    <!-- 区域 G：Evidence 历史详情表 -->
    <section v-if="selectedDevice && canVerify" class="panel entries-panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">证据历史条目</div>
          <div class="panel-caption">按证据链写入顺序（seq 升序）排序</div>
        </div>
        <div class="filter-row">
          <el-input v-model.number="fromSeq" size="small" placeholder="fromSeq" class="seq-input" clearable @keyup.enter="applyFilters" />
          <el-input v-model.number="toSeq" size="small" placeholder="toSeq" class="seq-input" clearable @keyup.enter="applyFilters" />
          <el-select v-model="eventType" size="small" placeholder="事件类型" clearable class="filter-select" @change="applyFilters">
            <el-option label="遥测" value="TELEMETRY" />
            <el-option label="命令" value="COMMAND" />
            <el-option label="回执" value="ACK" />
          </el-select>
          <el-select v-model="sourceType" size="small" placeholder="来源类型" clearable class="filter-select" @change="applyFilters">
            <el-option label="遥测点" value="LIGHT_POINT" />
            <el-option label="命令记录" value="DEVICE_COMMAND" />
          </el-select>
          <el-button size="small" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div v-if="entriesError" class="error-state" role="alert">
        <span>{{ entriesError }}</span>
        <el-button size="small" @click="loadEntries">重新加载</el-button>
      </div>

      <div v-else class="table-wrap">
        <el-table :data="entries.content" row-key="seq" v-loading="entriesLoading" empty-text="暂无证据条目">
          <el-table-column prop="seq" label="Seq" width="80">
            <template #default="{ row }"><span class="num">{{ row.seq }}</span></template>
          </el-table-column>
          <el-table-column label="事件类型" width="110">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.eventType }}</el-tag></template>
          </el-table-column>
          <el-table-column label="事件时间" width="150">
            <template #default="{ row }"><span class="num">{{ formatTime(row.eventTs) }}</span></template>
          </el-table-column>
          <el-table-column prop="sourceType" label="来源类型" width="130" />
          <el-table-column prop="sourceId" label="来源ID" width="90" align="right">
            <template #default="{ row }"><span class="num">{{ row.sourceId }}</span></template>
          </el-table-column>
          <el-table-column label="Hash" min-width="180">
            <template #default="{ row }">
              <span class="hash-cell"><code class="num">{{ shortHash(row.entryHash) }}</code><el-button link size="small" @click="copyText(row.entryHash)">复制</el-button></span>
            </template>
          </el-table-column>
          <el-table-column label="Entry MAC" min-width="180">
            <template #default="{ row }">
              <span class="hash-cell"><code class="num">{{ shortHash(row.entryMac) }}</code><el-button link size="small" @click="copyText(row.entryMac)">复制</el-button></span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="size"
          :total="entries.totalElements"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadEntries"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <!-- 详情 Drawer -->
    <el-drawer v-model="drawerVisible" :title="detailEntry ? `证据条目 Seq #${detailEntry.seq}` : '证据条目'" size="480px">
      <div v-if="detailEntry" class="detail-body">
        <dl class="detail-list">
          <div><dt>事件类型</dt><dd>{{ detailEntry.eventType }}</dd></div>
          <div><dt>事件时间</dt><dd class="num">{{ formatTime(detailEntry.eventTs) }}</dd></div>
          <div><dt>来源类型 / ID</dt><dd>{{ detailEntry.sourceType }} · <span class="num">{{ detailEntry.sourceId }}</span></dd></div>
          <div><dt>版本</dt><dd>hash v{{ detailEntry.hashVersion }} · payload v{{ detailEntry.payloadVersion }} · mac v{{ detailEntry.macVersion }}</dd></div>
          <div><dt>keyId</dt><dd class="num">{{ detailEntry.keyId }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ detailEntry.createdAt }}</dd></div>
          <div class="full"><dt>Entry Hash</dt><dd><code class="num full-hash">{{ detailEntry.entryHash }}</code><el-button link size="small" @click="copyText(detailEntry.entryHash)">复制</el-button></dd></div>
          <div class="full"><dt>Prev Hash</dt><dd><code class="num full-hash">{{ detailEntry.prevHash }}</code><el-button link size="small" @click="copyText(detailEntry.prevHash)">复制</el-button></dd></div>
          <div class="full"><dt>Entry MAC</dt><dd><code class="num full-hash">{{ detailEntry.entryMac }}</code><el-button link size="small" @click="copyText(detailEntry.entryMac)">复制</el-button></dd></div>
          <div class="full"><dt>canonicalPayload</dt><dd><pre class="payload-pre">{{ prettyPayload(detailEntry.canonicalPayload) }}</pre></dd></div>
        </dl>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CircleCheck, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { ApiBusinessError } from '../api/request'
import { getCurrentRole } from '../utils/auth'
import { listDevices, type DeviceVO } from '../api/device'
import {
  getEvidenceStatus,
  listEvidenceEntries,
  verifyEvidence,
  type EvidenceEntry,
  type EvidenceStatus,
  type EvidenceVerify,
  type OverallStatus,
  type PageResult,
} from '../api/evidence'

const role = getCurrentRole()
const canVerify = computed(() => role === 'admin' || role === 'municipal')

const devices = ref<DeviceVO[]>([])
const devicesLoading = ref(false)
const selectedDevice = ref('')

const status = ref<EvidenceStatus | null>(null)
const statusLoading = ref(false)
const statusError = ref('')

const verifyResult = ref<EvidenceVerify | null>(null)
const verifyLoading = ref(false)

const entries = ref<PageResult<EvidenceEntry>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 })
const entriesLoading = ref(false)
const entriesError = ref('')
const page = ref(0)
const size = ref(50)
const fromSeq = ref<number | undefined>(undefined)
const toSeq = ref<number | undefined>(undefined)
const eventType = ref<string | undefined>(undefined)
const sourceType = ref<string | undefined>(undefined)

const currentPage = computed({
  get: () => page.value + 1,
  set: (v: number) => { page.value = v - 1 },
})

const detailEntry = ref<EvidenceEntry | null>(null)
const drawerVisible = ref(false)

async function loadDevices() {
  devicesLoading.value = true
  try {
    devices.value = await listDevices()
  } catch {
    devices.value = []
  } finally {
    devicesLoading.value = false
  }
}

function onDeviceChange() {
  status.value = null
  statusError.value = ''
  verifyResult.value = null
  entries.value = { content: [], totalElements: 0, totalPages: 0, number: 0, size: size.value }
  resetFilters()
  loadStatus()
  if (canVerify.value) loadEntries()
}

async function loadStatus() {
  if (!selectedDevice.value) return
  statusLoading.value = true
  statusError.value = ''
  try {
    status.value = await getEvidenceStatus(selectedDevice.value)
  } catch (err) {
    status.value = null
    statusError.value = friendlyError(err)
  } finally {
    statusLoading.value = false
  }
}

async function runVerify() {
  if (!selectedDevice.value || verifyLoading.value) return
  verifyLoading.value = true
  verifyResult.value = null
  try {
    verifyResult.value = await verifyEvidence(selectedDevice.value)
  } catch (err) {
    verifyResult.value = null
    ElMessage.warning(friendlyError(err))
  } finally {
    verifyLoading.value = false
  }
}

async function loadEntries() {
  if (!selectedDevice.value) return
  entriesLoading.value = true
  entriesError.value = ''
  try {
    entries.value = await listEvidenceEntries(selectedDevice.value, {
      page: page.value,
      size: size.value,
      fromSeq: fromSeq.value,
      toSeq: toSeq.value,
      eventType: eventType.value,
      sourceType: sourceType.value,
    })
  } catch (err) {
    entries.value = { content: [], totalElements: 0, totalPages: 0, number: 0, size: size.value }
    entriesError.value = friendlyError(err)
  } finally {
    entriesLoading.value = false
  }
}

function applyFilters() {
  page.value = 0
  loadEntries()
}

function resetFilters() {
  fromSeq.value = undefined
  toSeq.value = undefined
  eventType.value = undefined
  sourceType.value = undefined
  page.value = 0
}

function onSizeChange() {
  page.value = 0
  loadEntries()
}

function locateAnomaly(seq: number) {
  resetFilters()
  fromSeq.value = Math.max(1, seq - 5)
  toSeq.value = seq + 5
  page.value = 0
  loadEntries()
}

function openDetail(row: EvidenceEntry) {
  detailEntry.value = row
  drawerVisible.value = true
}

function prettyPayload(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

function shortHash(value: string): string {
  if (!value) return '—'
  if (value.length <= 18) return value
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function issueText(issue: string | null): string {
  switch (issue) {
    case 'HEAD_MISSING': return '链头记录缺失'
    case 'HEAD_MAC_INVALID': return '链头完整性校验失败'
    case 'ANCHOR_AHEAD_OF_HEAD': return '锚点序号超过当前链头，数据状态异常'
    default: return issue || '—'
  }
}

function headStateText(state: string): string {
  switch (state) {
    case 'VALID': return '正常'
    case 'INVALID': return '异常'
    case 'NONE': return '无链头'
    default: return state
  }
}

function coverageText(s: EvidenceStatus): string {
  if (s.anchorCoverageComplete) return '已全部锚定'
  if (s.unanchoredCount != null && s.unanchoredCount > 0) return `未锚定 ${s.unanchoredCount} 条`
  return s.anchoredThroughSeq == null ? '暂无有效锚点' : '部分锚定'
}

type StatusLevel = 'gray' | 'red' | 'yellow' | 'green'

function isAnchorInvalid(anchorState: string): boolean {
  return anchorState === 'SIGNATURE_INVALID' || anchorState === 'CHAIN_MISMATCH' || anchorState === 'ENTRY_MISSING'
}

function getStatusLevel(s: EvidenceStatus): StatusLevel {
  if (!s.hasEvidence) return 'gray'
  if (s.metadataState === 'INCONSISTENT' || s.headState === 'INVALID' || isAnchorInvalid(s.anchorState)) return 'red'
  if (s.metadataState === 'NORMAL' && s.headState === 'VALID' && s.anchorState === 'VALID' && s.anchorCoverageComplete) return 'green'
  return 'yellow'
}

function getStatusText(s: EvidenceStatus): string {
  if (!s.hasEvidence) return '暂无证据记录'
  if (isAnchorInvalid(s.anchorState)) return '锚点完整性校验失败'
  if (s.metadataState === 'INCONSISTENT' || s.headState === 'INVALID') return '元数据完整性异常'
  if (s.metadataState === 'NORMAL' && s.headState === 'VALID' && s.anchorState === 'VALID' && s.anchorCoverageComplete) return '轻量检查正常'
  if (s.anchorState === 'NONE') return '当前暂无有效锚点覆盖'
  if (s.unanchoredCount != null && s.unanchoredCount > 0) return '存在尚未锚定的数据'
  return '部分锚定'
}

function anchorStateText(s: string): string {
  switch (s) {
    case 'VALID': return '有效'
    case 'NONE': return '暂无有效锚点'
    case 'DISABLED': return '锚点未启用'
    case 'SIGNATURE_INVALID':
    case 'CHAIN_MISMATCH':
    case 'ENTRY_MISSING': return '锚点完整性校验失败'
    default: return s
  }
}

function friendlyError(err: unknown): string {
  if (err instanceof ApiBusinessError) {
    if (err.code === 403) return '权限不足，当前角色无此操作权限'
    if (err.code === 400) return '请求参数错误'
    if (err.code === 500) return '证据链服务内部错误'
    return err.message || '证据链服务暂时无法访问'
  }
  if (axios.isAxiosError(err)) {
    if (err.code === 'ECONNABORTED') return '验证耗时较长，请稍后重试'
    return '证据链服务暂时无法访问'
  }
  return '证据链服务暂时无法访问'
}

const statusCellClass = computed(() => {
  if (!status.value) return ''
  const level = getStatusLevel(status.value)
  return { gray: 'cell-neutral', red: 'cell-bad', yellow: 'cell-warn', green: 'cell-good' }[level]
})

const statusTagType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  if (!status.value) return 'info'
  const level = getStatusLevel(status.value)
  const map: Record<StatusLevel, 'success' | 'warning' | 'danger' | 'info'> = {
    gray: 'info', red: 'danger', yellow: 'warning', green: 'success',
  }
  return map[level]
})

const statusHeadline = computed(() => (status.value ? getStatusText(status.value) : ''))

function isValidOverall(o: OverallStatus): boolean {
  return o === 'VALID_ANCHORED' || o === 'VALID_PARTIALLY_ANCHORED' || o === 'VALID_UNANCHORED'
}

function overallText(o: OverallStatus): string {
  switch (o) {
    case 'VALID_ANCHORED': return '完整性验证通过，全部证据已锚定'
    case 'VALID_PARTIALLY_ANCHORED': return '完整性验证通过，但存在未锚定尾部数据'
    case 'VALID_UNANCHORED': return '完整性验证通过，当前暂无有效锚点覆盖'
    case 'INVALID_CHAIN': return '哈希链完整性异常'
    case 'INVALID_MAC': return 'HMAC 完整性校验失败'
    case 'INVALID_SOURCE': return '证据与业务源记录不一致'
    case 'INVALID_ANCHOR': return '锚点校验失败'
    default: return o
  }
}

function overallClass(o: OverallStatus): string {
  if (o === 'VALID_ANCHORED') return 'overall-good'
  if (o.startsWith('VALID')) return 'overall-warn'
  return 'overall-bad'
}

onMounted(() => {
  loadDevices()
})
</script>

<style scoped>
.evidence-page { max-width: 1640px; }
.intro-actions { display: flex; align-items: center; gap: 10px; }
.intro-actions :deep(.el-select) { width: 260px; }
.device-status { margin-left: 10px; font-size: 11px; }
.device-status.on { color: var(--ok); }
.device-status.off { color: var(--text-muted); }
.audit-note { min-height: 58px; padding: 12px 16px; display: flex; align-items: center; gap: 12px; border: 1px solid rgba(40,123,93,.2); background: var(--accent-dim); color: var(--text-secondary); }
.audit-note > .el-icon { flex: none; color: var(--accent-deep); font-size: 18px; }
.audit-note > div { display: grid; gap: 2px; }
.audit-note strong { color: var(--accent-deep); font-size: 12px; }
.audit-note span { font-size: 12px; }
.status-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--border-subtle); background: var(--bg-surface); }
.status-cell { min-width: 0; min-height: 128px; padding: 18px 20px; display: flex; flex-direction: column; border-right: 1px solid var(--border-subtle); }
.status-cell:last-child { border-right: 0; }
.cell-label { color: var(--text-muted); font-size: 10px; letter-spacing: .1em; }
.cell-value { margin-top: 10px; color: var(--text-primary); font-size: 20px; letter-spacing: -.02em; }
.cell-foot { margin-top: auto; color: var(--text-muted); font-size: 11px; }
.status-cell.cell-good { box-shadow: inset 0 3px 0 var(--ok); }
.status-cell.cell-warn { box-shadow: inset 0 3px 0 #c98a1b; }
.status-cell.cell-bad { box-shadow: inset 0 3px 0 var(--danger); }
.status-cell.cell-neutral { box-shadow: inset 0 3px 0 var(--border-strong); }
.status-empty { min-height: 120px; padding: 24px; display: grid; place-items: center; color: var(--text-muted); }
.status-empty.muted { border: 1px dashed var(--border-subtle); }
.verify-result { margin-top: 14px; padding: 16px 18px; border: 1px solid var(--border-subtle); background: var(--bg-surface); }
.verify-result.overall-good { border-color: rgba(40,123,93,.3); background: var(--accent-dim); }
.verify-result.overall-warn { border-color: rgba(201,138,27,.35); background: rgba(201,138,27,.07); }
.verify-result.overall-bad { border-color: rgba(229,56,53,.3); background: var(--danger-dim); }
.verify-headline { display: flex; align-items: center; gap: 10px; }
.verify-headline .el-icon { font-size: 20px; color: var(--accent-deep); }
.overall-bad .verify-headline .el-icon { color: var(--danger); }
.verify-headline strong { font-size: 15px; }
.verify-headline .freshness { margin-left: auto; color: var(--text-muted); font-size: 11px; }
.broken-locate { margin-top: 12px; padding: 10px 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border: 1px solid rgba(229,56,53,.3); background: rgba(229,56,53,.06); color: var(--danger); }
.broken-locate b { font-size: 15px; }
.verify-dim-grid { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
.verify-dim-grid span { padding: 4px 10px; display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--border-subtle); border-radius: 3px; color: var(--text-secondary); font-size: 11px; }
.verify-dim-grid i { font-style: normal; color: var(--text-muted); font-size: 10px; }
.verify-reason { margin-top: 10px; color: var(--text-secondary); font-size: 12px; }
.filter-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.seq-input { width: 110px; }
.filter-select { width: 130px; }
.table-wrap { margin-top: 4px; overflow-x: auto; }
.hash-cell { display: inline-flex; align-items: center; gap: 6px; }
.hash-cell code { font-family: var(--font-data); font-size: 11px; }
.full-hash { display: inline-block; max-width: 100%; word-break: break-all; }
.pagination-row { margin-top: 14px; display: flex; justify-content: flex-end; }
.detail-body { padding: 4px 2px; }
.detail-list { display: grid; gap: 14px; }
.detail-list > div { display: grid; gap: 4px; }
.detail-list > div.full { grid-column: 1 / -1; }
.detail-list dt { color: var(--text-muted); font-size: 11px; }
.detail-list dd { color: var(--text-primary); font-size: 12px; word-break: break-all; }
.payload-pre { max-height: 260px; overflow: auto; padding: 12px; border: 1px solid var(--border-subtle); background: var(--bg-surface-2); color: var(--text-secondary); font: 11px/1.6 var(--font-data); white-space: pre-wrap; word-break: break-all; }
@media (max-width: 1080px) { .status-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .status-cell:nth-child(2) { border-right: 0; } .status-cell:nth-child(-n+2) { border-bottom: 1px solid var(--border-subtle); } }
@media (max-width: 700px) { .intro-actions { width: 100%; flex-direction: column; align-items: stretch; } .status-grid { grid-template-columns: 1fr; } .status-cell { min-height: 110px; border-right: 0; border-bottom: 1px solid var(--border-subtle); } .status-cell:last-child { border-bottom: 0; } }
</style>
