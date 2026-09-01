<template>
  <div class="workspace-page control">
    <div class="page-intro">
      <div>
        <h1 class="page-title">灯控管理</h1>
      </div>
      <div class="refresh">
        <span class="status-dot idle"></span>
        <span>开关与阈值操作实时生效</span>
      </div>
    </div>

    <!-- 市政人员只读提示（用 section 避免干扰 .control 网格里 div 的 nth-of-type 计数） -->
    <section v-if="isMunicipal" class="readonly-note" role="note">
      <el-icon class="readonly-icon"><Lock /></el-icon>
      <span>当前为市政人员只读权限：可查看设备状态与联动配置，无法开关灯或修改配置。</span>
    </section>

    <!-- F-05 / F-07 光照联动与阈值 -->
    <div class="panel config-panel">
      <div class="panel-head">
        <div class="panel-title">光照联动与阈值</div>
      </div>
      <NotReadyBanner
        v-if="!linkageReady"
        text="无法读取联动配置，请检查后端服务与 MQTT Broker 连接后重试。"
      />
      <div class="panel-body cfg">
        <div class="cfg-row cfg-toggle-row">
          <div class="cfg-main">
            <div class="cfg-title">自动联动开关</div>
          </div>
          <el-switch v-model="linkage.enabled" :disabled="!linkageReady || isMunicipal" aria-label="自动联动总开关" />
        </div>
        <div class="cfg-row cfg-field-row">
          <div class="cfg-main">
            <div class="cfg-title">光照阈值</div>
          </div>
          <div class="cfg-control">
            <el-input-number
              v-model="linkage.threshold"
              :min="0"
              :max="10000"
              :step="5"
              :disabled="!linkageReady || isMunicipal"
              aria-label="开灯光照阈值"
            />
            <span class="cfg-unit">Lux</span>
          </div>
        </div>
        <div class="cfg-row cfg-field-row">
          <div class="cfg-main">
            <div class="cfg-title">滞回值</div>
          </div>
          <div class="cfg-control">
            <el-input-number
              v-model="linkage.hysteresis"
              :min="1"
              :max="1000"
              :step="10"
              :disabled="!linkageReady || isMunicipal"
              aria-label="关灯滞回值"
            />
            <span class="cfg-unit">Lux</span>
          </div>
        </div>
        <div class="cfg-row schedule-section">
          <div class="cfg-main">
            <div class="cfg-title">分时亮度控制</div>
          </div>
          <div class="schedule-grid">
            <div v-for="period in linkage.brightnessPeriods" :key="period.name" class="schedule-row">
              <span class="schedule-name">{{ period.name }}</span>
              <el-time-select
                v-model="period.startTime"
                start="00:00"
                step="00:30"
                end="23:30"
                :clearable="false"
                :disabled="!linkageReady || isMunicipal"
                :aria-label="`${period.name}开始时间`"
              />
              <el-input-number
                v-model="period.brightnessPercent"
                :min="1"
                :max="100"
                :step="5"
                :disabled="!linkageReady || isMunicipal"
                :aria-label="`${period.name}亮度百分比`"
              />
              <span class="cfg-unit">%</span>
            </div>
          </div>
        </div>
        <div class="rule-preview" role="status" aria-live="polite">
          <div class="rule-preview-title">当前规则</div>
          <div class="rule-preview-flow">
            <span>低于 <strong>{{ linkage.threshold }} Lux</strong> 开灯</span>
            <span class="rule-separator" aria-hidden="true">/</span>
            <span>高于 <strong>{{ linkage.threshold + linkage.hysteresis }} Lux</strong> 关灯</span>
            <span class="rule-separator" aria-hidden="true">/</span>
            <span>{{ linkage.currentBrightnessPeriod || '当前时段' }} <strong>{{ linkage.currentBrightnessPercent ?? 100 }}%</strong></span>
          </div>
        </div>
        <div class="cfg-actions">
          <el-button type="primary" :loading="saving" :disabled="!linkageReady || isMunicipal" @click="saveLinkage">
            保存配置
          </el-button>
        </div>
      </div>
    </div>

    <!-- F-06 手动远程开关 -->
    <div class="panel device-panel">
      <div class="panel-head">
        <div class="panel-title">设备策略与远程控制</div>
        <div class="panel-meta">共 {{ filteredDevices.length }} 台 · 第 {{ currentPage }} / {{ totalPages }} 页</div>
      </div>
      <NotReadyBanner
        v-if="!switchReady"
        text="开关灯接口尚未由后端实现（约定 POST /api/devices/{deviceId}/switch），开关状态仅在本地会话内模拟。"
      />
      <div class="table-wrap">
        <div class="device-search">
          <div class="search-group">
            <el-input v-model="deviceQuery" placeholder="查找设备编号或位置" clearable :prefix-icon="Search" />
            <span class="search-hits">
              {{ deviceQuery.trim() ? `匹配 ${filteredDevices.length} 台` : `显示 ${pageStart}–${pageEnd} / ${filteredDevices.length} 台` }}
            </span>
          </div>
          <nav v-if="filteredDevices.length" class="device-pagination" aria-label="设备列表分页">
            <button
              type="button"
              class="page-button page-direction"
              :disabled="currentPage === 1"
              @click="currentPage--"
            >上一页</button>
            <button
              v-for="pageNumber in totalPages"
              :key="pageNumber"
              type="button"
              class="page-button page-number"
              :class="{ active: currentPage === pageNumber }"
              :aria-current="currentPage === pageNumber ? 'page' : undefined"
              @click="currentPage = pageNumber"
            >{{ pageNumber }}</button>
            <button
              type="button"
              class="page-button page-direction"
              :disabled="currentPage === totalPages"
              @click="currentPage++"
            >下一页</button>
          </nav>
        </div>
        <div v-if="selectedDevices.length" class="batch-bar" role="region" aria-label="批量操作" aria-live="polite">
          <span class="batch-count">已选择 {{ selectedDevices.length }} 台</span>
          <span class="batch-divider" aria-hidden="true"></span>
          <el-button size="small" :loading="batchBusy" :disabled="isMunicipal" @click="batchSetMode('AUTO')">设为自动</el-button>
          <el-button size="small" :loading="batchBusy" :disabled="isMunicipal" @click="batchSetMode('MANUAL')">设为手动</el-button>
          <el-button size="small" type="success" plain :loading="batchBusy" :disabled="isMunicipal" @click="batchSwitch(true)">批量开灯</el-button>
          <el-button size="small" type="danger" plain :loading="batchBusy" :disabled="isMunicipal" @click="batchSwitch(false)">批量关灯</el-button>
          <button type="button" class="clear-selection" @click="clearSelection">取消选择</button>
        </div>
        <el-table ref="deviceTable" :data="pagedDevices" row-key="code" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="46" :selectable="() => !isMunicipal" reserve-selection />
          <el-table-column label="设备" min-width="170">
            <template #default="{ row }">
              <div class="device-cell">
                <span class="device-avatar" :class="row.status === 'ONLINE' ? 'on' : 'off'">
                  <el-icon><ReadingLamp /></el-icon>
                </span>
                <span class="device-identity">
                  <span class="device-code num">{{ row.code }}</span>
                  <span class="device-state-text" :class="row.status === 'ONLINE' ? 'online' : 'offline'">
                    {{ row.status === 'ONLINE' ? '在线' : '离线' }}
                  </span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="location" label="位置" min-width="125" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="loc">{{ row.location }}</span>
            </template>
          </el-table-column>
          <el-table-column label="当前照度" width="108" align="right">
            <template #default="{ row }">
              <span class="lux num">{{ row.latestLux == null ? '—' : row.latestLux }}</span>
              <span v-if="row.latestLux != null" class="lux-unit">Lux</span>
            </template>
          </el-table-column>
          <el-table-column label="控制策略" width="152">
            <template #default="{ row }">
              <div class="strategy-cell">
                <el-select
                  :model-value="effectiveMode(row)"
                  size="small"
                  :disabled="isMunicipal || !linkage.enabled || modeBusy[row.code]"
                  :loading="modeBusy[row.code]"
                  :aria-label="`${row.code} 控制策略`"
                  @change="(mode: DeviceControlMode) => changeMode(row, mode)"
                >
                  <el-option label="自动策略" value="AUTO" />
                  <el-option label="手动控制" value="MANUAL" />
                </el-select>
                <span v-if="row.status !== 'ONLINE'" class="strategy-feedback locked">离线锁定</span>
                <span v-else-if="commandStates[row.code]" class="strategy-feedback" :class="cmdClass(commandStates[row.code]?.status)">
                  {{ cmdLabel(commandStates[row.code]?.status) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="路灯开关" width="94" align="right">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 'ONLINE' ? (switchState[row.code] ?? true) : false"
                :disabled="!switchReady || row.status !== 'ONLINE' || effectiveMode(row) === 'AUTO' || inFlight(row.code) || isMunicipal"
                :aria-label="`${row.code} 手动路灯开关`"
                inline-prompt
                active-text="开"
                inactive-text="关"
                @change="(val: boolean) => doSwitch(row, val)"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { Lock, ReadingLamp, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDevices, setDeviceControlMode, setBatchDeviceControlMode } from '../api/device'
import type { DeviceVO, DeviceControlMode } from '../api/device'
import { getLinkageConfig, saveLinkageConfig, switchLight, getCommandStatus } from '../api/control'
import type { LinkageConfig, ControlResult, CommandStatus } from '../api/control'
import { probe } from '../api/helper'
import { getCurrentRole } from '../utils/auth'
import NotReadyBanner from '../components/NotReadyBanner.vue'

// 角色标识：市政人员只读，禁止开关灯与修改联动配置
const isMunicipal = getCurrentRole() === 'municipal'

const devices = ref<DeviceVO[]>([])
// 设备查找栏：按设备编号或位置实时过滤
const deviceQuery = ref('')
function compareDeviceCode(a: DeviceVO, b: DeviceVO): number {
  const aReal = /^SL-/i.test(a.code || '')
  const bReal = /^SL-/i.test(b.code || '')
  if (aReal !== bReal) return aReal ? -1 : 1
  return (a.code || '').localeCompare(b.code || '', 'zh-CN', { numeric: true, sensitivity: 'base' })
}
const filteredDevices = computed(() => {
  const q = deviceQuery.value.trim().toLowerCase()
  const matches = q
    ? devices.value.filter(
        (d) => (d.code || '').toLowerCase().includes(q) || (d.location || '').toLowerCase().includes(q),
      )
    : devices.value
  return [...matches].sort(compareDeviceCode)
})
const currentPage = ref(1)
const pageSize = ref(10)
const totalPages = computed(() => Math.max(1, Math.ceil(filteredDevices.value.length / pageSize.value)))
const pageStart = computed(() => filteredDevices.value.length ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, filteredDevices.value.length))
const pagedDevices = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredDevices.value.slice(start, start + pageSize.value)
})

watch([deviceQuery, pageSize], () => {
  currentPage.value = 1
})
watch(totalPages, (pages) => {
  if (currentPage.value > pages) currentPage.value = pages
})
// 本地开关状态：离线设备固定为关；在线设备仅在执行成功后更新
const switchState = reactive<Record<string, boolean>>({})
function defaultLinkage(): LinkageConfig {
  return {
    enabled: false,
    threshold: 100,
    hysteresis: 50,
    brightnessPeriods: [
      { name: '深夜节能', startTime: '00:00', brightnessPercent: 50 },
      { name: '清晨照明', startTime: '05:00', brightnessPercent: 80 },
      { name: '日间待机', startTime: '07:00', brightnessPercent: 100 },
      { name: '傍晚照明', startTime: '18:00', brightnessPercent: 100 },
      { name: '夜间节能', startTime: '23:00', brightnessPercent: 70 },
    ],
    currentBrightnessPercent: 100,
    currentBrightnessPeriod: '日间待机',
  }
}

const linkage = ref<LinkageConfig>(defaultLinkage())
const linkageReady = ref(true)
const switchReady = ref(true)
const saving = ref(false)
const modeBusy = reactive<Record<string, boolean>>({})
const batchBusy = ref(false)
const selectedDevices = ref<DeviceVO[]>([])
const deviceTable = ref()

function effectiveMode(row: DeviceVO): DeviceControlMode {
  return linkage.value.enabled && row.controlMode !== 'MANUAL' ? 'AUTO' : 'MANUAL'
}

function onSelectionChange(rows: DeviceVO[]) {
  selectedDevices.value = rows
}

function clearSelection() {
  deviceTable.value?.clearSelection()
}

// 每台设备最近一次开关命令的阶段反馈
type CmdDisplay = 'SENDING' | CommandStatus
interface DeviceCommandState {
  status: CmdDisplay
  on: boolean
  message: string
}
const commandStates = reactive<Record<string, DeviceCommandState | undefined>>({})
const pollTimers = new Map<string, number>()
const guardTimers = new Map<string, number>()

const CMD_LABEL: Record<CmdDisplay, string> = {
  SENDING: '发送中',
  DISPATCHED: '已发送',
  ACKED: '设备已接收',
  SUCCESS: '执行成功',
  FAILED: '执行失败',
  TIMEOUT: '等待超时',
}
const CMD_CLASS: Record<CmdDisplay, string> = {
  SENDING: 'sending',
  DISPATCHED: 'dispatched',
  ACKED: 'acked',
  SUCCESS: 'success',
  FAILED: 'failed',
  TIMEOUT: 'timeout',
}

function cmdLabel(status?: CmdDisplay): string {
  return status ? CMD_LABEL[status] : '—'
}
function cmdClass(status?: CmdDisplay): string {
  return status ? CMD_CLASS[status] : 'idle'
}
/** 命令是否仍在流转（期间锁定开关防止重复下发） */
function inFlight(code: string): boolean {
  const s = commandStates[code]?.status
  return s === 'SENDING' || s === 'DISPATCHED' || s === 'ACKED'
}
function clearCommandTimers(code: string) {
  const it = pollTimers.get(code)
  if (it !== undefined) {
    window.clearInterval(it)
    pollTimers.delete(code)
  }
  const gt = guardTimers.get(code)
  if (gt !== undefined) {
    window.clearTimeout(gt)
    guardTimers.delete(code)
  }
}

async function loadDevices() {
  try {
    devices.value = await listDevices()
    for (const d of devices.value) {
      // 离线设备固定显示关；在线设备使用后端持久化的实际灯状态。
      switchState[d.code] = d.status === 'ONLINE' && d.lampStatus === 'ON'
    }
  } catch {
    // 拦截器已统一提示
  }
}

async function loadLinkage() {
  const r = await probe(getLinkageConfig(), defaultLinkage())
  linkageReady.value = r.ready
  linkage.value = r.data
}

async function saveLinkage() {
  if (isMunicipal) return
  saving.value = true
  try {
    const r = await probe(saveLinkageConfig({ ...linkage.value }), null)
    if (r.ready) {
      ElMessage.success('联动配置已保存')
      await Promise.all([loadDevices(), loadLinkage()])
    } else {
      linkageReady.value = false
    }
  } finally {
    saving.value = false
  }
}

async function changeMode(row: DeviceVO, mode: DeviceControlMode) {
  if (isMunicipal || !linkage.value.enabled) return
  modeBusy[row.code] = true
  try {
    const r = await probe(setDeviceControlMode(row.code, mode), null)
    if (r.ready && r.data) {
      row.controlMode = r.data.controlMode
      ElMessage.success(`${row.code} 已切换为${mode === 'AUTO' ? '自动策略' : '手动控制'}`)
    } else {
      ElMessage.error(`${row.code} 策略切换失败`)
    }
  } finally {
    modeBusy[row.code] = false
  }
}

async function batchSetMode(mode: DeviceControlMode) {
  if (!selectedDevices.value.length || isMunicipal) return
  if (mode === 'AUTO' && !linkage.value.enabled) {
    ElMessage.warning('请先开启左侧自动联动总开关')
    return
  }
  batchBusy.value = true
  const codes = selectedDevices.value.map(d => d.code)
  try {
    const r = await probe(setBatchDeviceControlMode(codes, mode), null)
    if (r.ready) {
      for (const device of devices.value) if (codes.includes(device.code)) device.controlMode = mode
      ElMessage.success(`已将 ${r.data ?? codes.length} 台设备设为${mode === 'AUTO' ? '自动策略' : '手动控制'}`)
      clearSelection()
    }
  } finally {
    batchBusy.value = false
  }
}

async function batchSwitch(on: boolean) {
  if (!selectedDevices.value.length || isMunicipal) return
  const online = selectedDevices.value.filter(d => d.status === 'ONLINE')
  if (!online.length) {
    ElMessage.warning('所选设备均不在线')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将 ${online.length} 台在线设备切换为手动控制并${on ? '开灯' : '关灯'}，是否继续？`,
      `批量${on ? '开灯' : '关灯'}`,
      { confirmButtonText: '确认执行', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  batchBusy.value = true
  const codes = online.map(d => d.code)
  try {
    await setBatchDeviceControlMode(codes, 'MANUAL')
    for (const device of online) device.controlMode = 'MANUAL'
    const results = await Promise.allSettled(online.map(device => switchLight(device.code, on)))
    let succeeded = 0
    results.forEach((result, index) => {
      const device = online[index]
      if (result.status === 'fulfilled') {
        commandStates[device.code] = { status: result.value.status, on, message: result.value.message ?? '' }
        if (result.value.status === 'SUCCESS') {
          switchState[device.code] = on
          succeeded++
        } else {
          applyCommandResult(device.code, result.value)
        }
      }
    })
    const failed = online.length - succeeded
    if (failed) ElMessage.warning(`批量操作完成：成功 ${succeeded} 台，待确认或失败 ${failed} 台`)
    else ElMessage.success(`已${on ? '开启' : '关闭'} ${succeeded} 台设备`)
    clearSelection()
  } finally {
    batchBusy.value = false
  }
}

async function doSwitch(row: DeviceVO, on: boolean) {
  if (isMunicipal) return
  const code = row.code
  clearCommandTimers(code)
  const prev = switchState[code] ?? row.status === 'ONLINE'
  commandStates[code] = { status: 'SENDING', on, message: '正在发送控制命令…' }
  const r = await probe(switchLight(code, on), null)
  if (!r.ready || !r.data?.commandId) {
    commandStates[code] = { status: 'FAILED', on, message: '命令发送失败，请检查后端服务' }
    switchState[code] = prev
    ElMessage.error(`${code} 命令发送失败`)
    return
  }
  applyCommandResult(code, r.data)
}

/** 应用一次命令状态快照：终态时停止轮询并提示，非终态继续轮询 */
function applyCommandResult(code: string, res: ControlResult) {
  const on = commandStates[code]?.on ?? res.action === 'ON'
  commandStates[code] = { status: res.status, on, message: res.message ?? '' }
  if (res.status === 'SUCCESS') {
    switchState[code] = on // 设备已确认执行，才翻转开关视觉
    clearCommandTimers(code)
    ElMessage.success(`${code} ${on ? '已开灯' : '已关灯'} · 执行成功`)
  } else if (res.status === 'FAILED') {
    clearCommandTimers(code)
    ElMessage.error(`${code} 执行失败`)
  } else if (res.status === 'TIMEOUT') {
    clearCommandTimers(code)
    ElMessage.warning(`${code} 等待设备回执超时`)
  } else {
    // DISPATCHED / ACKED：轮询直到终态
    startPolling(code, res.commandId)
  }
}

function startPolling(code: string, commandId: string) {
  clearCommandTimers(code)
  const interval = window.setInterval(async () => {
    const r = await probe(getCommandStatus(commandId), null)
    if (r.ready && r.data) applyCommandResult(code, r.data)
    // 查询失败静默重试下一轮
  }, 2000)
  pollTimers.set(code, interval)
  // 兜底：后端 60s 超时 + 最多 30s 延迟标记，前端最多等待 65s
  const guard = window.setTimeout(() => {
    const cur = commandStates[code]
    if (cur && (cur.status === 'DISPATCHED' || cur.status === 'ACKED')) {
      commandStates[code] = { ...cur, status: 'TIMEOUT', message: '等待设备回执超时' }
      clearCommandTimers(code)
      ElMessage.warning(`${code} 等待设备回执超时`)
    }
  }, 65000)
  guardTimers.set(code, guard)
}

onMounted(() => {
  loadDevices()
  loadLinkage()
})

onBeforeUnmount(() => {
  for (const it of pollTimers.values()) window.clearInterval(it)
  for (const gt of guardTimers.values()) window.clearTimeout(gt)
  pollTimers.clear()
  guardTimers.clear()
})
</script>

<style scoped>
.control {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 680;
  letter-spacing: -0.025em;
  color: var(--text-primary);
}
.refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-secondary);
}
/* 市政人员只读提示条（整页禁写） */
.readonly-note { grid-column: 1 / -1; flex: 1 0 100%; display: flex; align-items: center; gap: 8px; padding: 9px 16px; margin-bottom: 18px; font-size: 12.5px; color: var(--accent-bright); background: var(--accent-dim); border: 1px solid rgba(250,152,25,.24); border-radius: var(--radius-md); }
.readonly-icon { font-size: 14px; flex: none; }
.panel { position: relative; overflow: hidden; border-radius: var(--radius-lg); }
.panel-head { min-height: 56px; }

.cfg {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0 24px 22px;
}
.cfg-row {
  padding: 22px 0;
}
.cfg-row + .cfg-row {
  border-top: 1px solid var(--border-subtle);
}
.cfg-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.cfg-field-row {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
}
.cfg-main {
  min-width: 0;
}
.cfg-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.cfg-title::before { content: ''; display: inline-block; width: 4px; height: 12px; margin-right: 8px; border-radius: 0; background: var(--accent); vertical-align: -1px; }
.cfg-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.cfg-control :deep(.el-input-number) { width: 100%; }
.cfg-control :deep(.el-input__inner) { font-size: 17px; font-weight: 650; color: var(--text-primary); }
.cfg-unit {
  min-width: 28px;
  font-size: 12.5px;
  color: var(--text-muted);
}
.schedule-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.schedule-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: opacity .2s ease;
}
.schedule-row {
  display: grid;
  grid-template-columns: minmax(72px, 1fr) 112px 116px 20px;
  align-items: center;
  gap: 8px;
}
.schedule-name {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.schedule-row :deep(.el-select),
.schedule-row :deep(.el-input-number) { width: 100%; }
.schedule-row :deep(.el-input__inner) { font-variant-numeric: tabular-nums; }
.rule-preview {
  padding: 14px 16px;
  border: 1px solid rgba(53, 101, 83, .2);
  background: var(--accent-dim);
}
.rule-preview-title {
  margin-bottom: 6px;
  color: var(--accent-deep);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
}
.rule-preview-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.rule-preview-flow strong { color: var(--accent-deep); font-size: 13px; }
.rule-separator { color: var(--text-muted); }
.cfg-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

.table-wrap {
  padding: 8px 18px 18px;
  min-width: 0;
  overflow: hidden;
}
.table-wrap :deep(.el-table) { width: 100% !important; }
.table-wrap :deep(.el-scrollbar__bar.is-horizontal) { display: none; }
.device-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 0 12px;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  margin: 0 0 10px;
  padding: 6px 10px;
  border: 1px solid rgba(53, 101, 83, .24);
  background: var(--accent-dim);
}
.batch-count { color: var(--accent-deep); font-size: 12.5px; font-weight: 650; }
.batch-divider { width: 1px; height: 22px; margin: 0 2px; background: var(--border-subtle); }
.clear-selection {
  min-width: 72px;
  min-height: 32px;
  margin-left: auto;
  border: 0;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}
.clear-selection:hover, .clear-selection:focus-visible { color: var(--accent-deep); outline: 2px solid var(--accent); outline-offset: 2px; }
.search-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.search-group .el-input {
  width: 250px;
}
.search-hits {
  font-size: 12px;
  color: var(--text-muted);
}
.device-pagination {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}
.page-button {
  min-width: 32px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
  color: var(--text-secondary);
  background: var(--bg-surface);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.page-button:hover:not(:disabled),
.page-button:focus-visible {
  border-color: var(--accent);
  color: var(--accent-deep);
  outline: none;
}
.page-button.active {
  border-color: var(--accent-deep);
  color: #fff;
  background: var(--accent-deep);
}
.page-button:disabled {
  color: var(--text-muted);
  background: var(--bg-surface-2);
  cursor: not-allowed;
  opacity: .55;
}
.page-direction { min-width: 58px; }
.device-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.device-identity { display: flex; flex-direction: column; min-width: 0; gap: 2px; }
.device-state-text { font-size: 10.5px; line-height: 1.2; }
.device-state-text.online { color: var(--ok); }
.device-state-text.offline { color: var(--danger); }
.device-avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 3px;
  flex: none;
}
.device-avatar.on {
  background: var(--accent-dim);
  color: var(--accent-bright);
}
.device-avatar.off {
  background: var(--danger-dim);
  color: var(--danger);
}
.device-avatar :deep(.el-icon) {
  font-size: 15px;
}
.device-code {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 550;
  white-space: nowrap;
}
.loc {
  color: var(--text-secondary);
}
.lux {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--accent-bright);
}
.lux-unit {
  margin-left: 4px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.cmd-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 550;
  line-height: 1.5;
  background: var(--bg-surface-2);
  color: var(--text-secondary);
}
.cmd-pill .cmd-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.cmd-pill.sending { color: var(--text-muted); }
.cmd-pill.dispatched { color: var(--info); }
.cmd-pill.acked { color: var(--accent); }
.cmd-pill.success { color: var(--ok); }
.cmd-pill.failed { color: var(--danger); }
.cmd-pill.timeout { color: var(--warn); }
.cmd-pill.idle { color: var(--text-muted); }
.cmd-pill.locked { color: var(--text-muted); }
.strategy-cell { display: flex; flex-direction: column; align-items: stretch; gap: 4px; }
.strategy-feedback { min-height: 14px; padding-left: 2px; font-size: 10.5px; line-height: 1.2; color: var(--text-muted); }
.strategy-feedback.sending { color: var(--text-muted); }
.strategy-feedback.dispatched { color: var(--info); }
.strategy-feedback.acked { color: var(--accent); }
.strategy-feedback.success { color: var(--ok); }
.strategy-feedback.failed { color: var(--danger); }
.strategy-feedback.timeout { color: var(--warn); }
.strategy-feedback.locked { color: var(--text-muted); }

@media (max-width: 560px) {
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
  .cfg-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .device-search,
  .search-group {
    align-items: flex-start;
    flex-direction: column;
  }
  .search-group .el-input { width: 100%; }
  .batch-bar { align-items: stretch; flex-wrap: wrap; }
  .batch-divider { display: none; }
  .clear-selection { margin-left: 0; }
  .schedule-row { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 20px; }
  .schedule-name { grid-column: 1 / -1; white-space: normal; }
}
/* Dual-track strategy console */
.control { max-width: 1640px; display: grid; grid-template-columns: 520px minmax(0, 1fr); align-items: start; gap: 0; }
.control > .page-intro { grid-column: 1 / -1; margin-bottom: 18px; }
.control > .panel { min-height: 520px; border-radius: 0; }
.config-panel { border-color: var(--border-subtle); background: var(--bg-surface); }
.config-panel .panel-head { background: var(--bg-surface-2); }
.device-panel { min-width: 0; border-left: 0; }
@media (max-width: 1180px) { .control { display: flex; align-items: stretch; } .control > .panel { width: 100%; min-height: auto; } .device-panel { border-top: 0; border-left: 1px solid var(--border-subtle); } }
</style>
