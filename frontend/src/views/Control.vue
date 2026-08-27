<template>
  <div class="workspace-page control">
    <div class="page-intro">
      <div>
        <h1 class="page-title">灯控管理</h1>
        <p class="page-desc">手动远程开关路灯，配置光照联动自动开关阈值</p>
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
    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">光照联动与阈值</div>
        <div class="panel-meta">联动 F-05 · 阈值 F-07</div>
      </div>
      <NotReadyBanner
        v-if="!linkageReady"
        text="无法读取联动配置，请检查后端服务与 MQTT Broker 连接后重试。"
      />
      <div class="panel-body cfg">
        <div class="cfg-row">
          <div class="cfg-main">
            <div class="cfg-title">自动联动开关</div>
            <div class="cfg-desc">开启后，低于开灯阈值自动点亮；达到关灯阈值才关闭，避免临界照度反复开关。</div>
          </div>
          <el-switch v-model="linkage.enabled" :disabled="!linkageReady || isMunicipal" />
        </div>
        <div class="cfg-row">
          <div class="cfg-main">
            <div class="cfg-title">光照阈值</div>
            <div class="cfg-desc">环境光照低于该值时触发开灯。室内常用 100–400 Lux</div>
          </div>
          <el-input-number
            v-model="linkage.threshold"
            :min="0"
            :max="10000"
            :step="5"
            :disabled="!linkageReady || isMunicipal"
          />
          <span class="cfg-unit">Lux</span>
        </div>
        <div class="cfg-row">
          <div class="cfg-main">
            <div class="cfg-title">滞回值</div>
            <div class="cfg-desc">灯亮后，照度还需高于开灯阈值加此值才会关灯。</div>
          </div>
          <el-input-number
            v-model="linkage.hysteresis"
            :min="1"
            :max="1000"
            :step="10"
            :disabled="!linkageReady || isMunicipal"
          />
          <span class="cfg-unit">Lux</span>
        </div>
        <div class="cfg-actions">
          <el-button type="primary" :loading="saving" :disabled="!linkageReady || isMunicipal" @click="saveLinkage">
            保存配置
          </el-button>
        </div>
      </div>
    </div>

    <!-- F-06 手动远程开关 -->
    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">手动远程开关灯</div>
        <div class="panel-meta">{{ devices.length }} 台设备</div>
      </div>
      <NotReadyBanner
        v-if="!switchReady"
        text="开关灯接口尚未由后端实现（约定 POST /api/devices/{deviceId}/switch），开关状态仅在本地会话内模拟。"
      />
      <div class="table-wrap">
        <div class="device-search">
          <el-input v-model="deviceQuery" placeholder="查找设备编号或位置" clearable :prefix-icon="Search" style="width: 250px" />
          <span v-if="deviceQuery.trim()" class="search-hits">匹配 {{ filteredDevices.length }} 台</span>
        </div>
        <el-table :data="filteredDevices">
          <el-table-column label="设备" min-width="200">
            <template #default="{ row }">
              <div class="device-cell">
                <span class="device-avatar" :class="row.status === 'ONLINE' ? 'on' : 'off'">
                  <el-icon><ReadingLamp /></el-icon>
                </span>
                <span class="device-code num">{{ row.code }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="location" label="位置" min-width="160">
            <template #default="{ row }">
              <span class="loc">{{ row.location }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-pill" :class="row.status === 'ONLINE' ? 'online' : 'offline'">
                <span class="status-dot" :class="row.status === 'ONLINE' ? 'online' : 'offline'"></span>
                {{ row.status === 'ONLINE' ? '在线' : '离线' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="当前照度" width="120" align="right">
            <template #default="{ row }">
              <span class="lux num">{{ row.latestLux == null ? '—' : row.latestLux }}</span>
              <span v-if="row.latestLux != null" class="lux-unit">Lux</span>
            </template>
          </el-table-column>
          <el-table-column label="命令状态" width="140">
            <template #default="{ row }">
              <span v-if="row.status !== 'ONLINE'" class="cmd-pill locked">离线锁定</span>
              <span v-else-if="!commandStates[row.code]" class="cmd-pill idle">—</span>
              <span v-else class="cmd-pill" :class="cmdClass(commandStates[row.code]?.status)">
                <span class="cmd-dot"></span>{{ cmdLabel(commandStates[row.code]?.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="路灯开关" width="130" align="right" fixed="right">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 'ONLINE' ? (switchState[row.code] ?? true) : false"
                :disabled="!switchReady || row.status !== 'ONLINE' || inFlight(row.code) || isMunicipal"
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
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { Lock, ReadingLamp, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listDevices } from '../api/device'
import type { DeviceVO } from '../api/device'
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
const filteredDevices = computed(() => {
  const q = deviceQuery.value.trim().toLowerCase()
  if (!q) return devices.value
  return devices.value.filter(
    (d) => (d.code || '').toLowerCase().includes(q) || (d.location || '').toLowerCase().includes(q),
  )
})
// 本地开关状态：离线设备固定为关；在线设备仅在执行成功后更新
const switchState = reactive<Record<string, boolean>>({})
const linkage = ref<LinkageConfig>({ enabled: false, threshold: 100, hysteresis: 50 })
const linkageReady = ref(true)
const switchReady = ref(true)
const saving = ref(false)

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
      // 离线设备默认关；在线设备默认开
      switchState[d.code] = d.status === 'ONLINE'
    }
  } catch {
    // 拦截器已统一提示
  }
}

async function loadLinkage() {
  const r = await probe(getLinkageConfig(), { enabled: false, threshold: 100, hysteresis: 50 })
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
    } else {
      linkageReady.value = false
    }
  } finally {
    saving.value = false
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
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-muted);
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
  padding-block: 6px;
}
.cfg-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 0;
}
.cfg-row + .cfg-row {
  border-top: 1px solid var(--border-subtle);
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
.cfg-desc {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--text-muted);
}
.cfg-unit {
  margin-left: 8px;
  font-size: 12.5px;
  color: var(--text-muted);
}
.cfg-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.table-wrap {
  padding: 8px 18px 18px;
}
.device-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0 12px;
}
.device-search .el-input {
  width: 250px;
}
.search-hits {
  font-size: 12px;
  color: var(--text-muted);
}
.device-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
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
}
.device-cell::after { content: '可控制'; margin-left: 2px; padding: 1px 5px; border: 1px solid rgba(250,152,25,.32); border-radius: 2px; color: var(--accent-bright); font-size: 9px; letter-spacing: .04em; }
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
}
/* Dual-track strategy console */
.control { max-width: 1640px; display: grid; grid-template-columns: minmax(360px, .7fr) minmax(0, 1.3fr); align-items: start; gap: 0; }
.control > .page-intro { grid-column: 1 / -1; margin-bottom: 18px; }
.control > .panel { min-height: 520px; border-radius: 0; }
.control > .panel:nth-of-type(1) { color: #e7f4ef; border-color: var(--ink); background: var(--ink); }
.control > .panel:nth-of-type(1) .panel-head { color: #fff; border-color: rgba(255,255,255,.12); background: var(--ink-soft); }
.control > .panel:nth-of-type(1) .panel-title { color: #fff; }
.control > .panel:nth-of-type(1) .panel-meta, .control > .panel:nth-of-type(1) .cfg-desc { color: #91aaa1; }
.control > .panel:nth-of-type(2) { border-left: 0; }
.control .cfg-row { min-height: 118px; padding: 24px; border-color: rgba(255,255,255,.11); }
.control .cfg-title { color: #000; }
.control .cfg-actions { padding: 20px 24px; }
@media (max-width: 980px) { .control { display: flex; align-items: stretch; } .control > .panel { width: 100%; min-height: auto; } .control > .panel:nth-of-type(2) { border-top: 0; border-left: 1px solid var(--border-subtle); } }
</style>
