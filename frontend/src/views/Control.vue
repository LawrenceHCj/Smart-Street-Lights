<template>
  <div class="control">
    <div class="page-intro">
      <div>
        <h2 class="page-title">设备控制</h2>
        <p class="page-desc">手动远程开关路灯，配置光照联动自动开关阈值</p>
      </div>
      <div class="refresh">
        <span class="status-dot idle"></span>
        <span>开关与阈值操作实时生效</span>
      </div>
    </div>

    <!-- F-05 / F-07 光照联动与阈值 -->
    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">光照联动与阈值</div>
        <div class="panel-meta">联动 F-05 · 阈值 F-07</div>
      </div>
      <NotReadyBanner
        v-if="!linkageReady"
        text="联动配置与阈值接口尚未由后端实现（约定 POST/GET /api/config/linkage），当前为本地模拟，保存不会持久化。"
      />
      <div class="panel-body cfg">
        <div class="cfg-row">
          <div class="cfg-main">
            <div class="cfg-title">自动联动开关</div>
            <div class="cfg-desc">开启后按阈值自动控制路灯：光照低于阈值自动开灯，高于阈值自动关灯</div>
          </div>
          <el-switch v-model="linkage.enabled" :disabled="!linkageReady" />
        </div>
        <div class="cfg-row">
          <div class="cfg-main">
            <div class="cfg-title">光照阈值</div>
            <div class="cfg-desc">环境光照低于该值时触发开灯，建议值 20–60 Lux</div>
          </div>
          <el-input-number
            v-model="linkage.threshold"
            :min="0"
            :max="500"
            :step="5"
            :disabled="!linkageReady"
          />
          <span class="cfg-unit">Lux</span>
        </div>
        <div class="cfg-actions">
          <el-button type="primary" :loading="saving" :disabled="!linkageReady" @click="saveLinkage">
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
        <el-table :data="devices">
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
          <el-table-column label="路灯开关" width="130" align="right">
            <template #default="{ row }">
              <el-switch
                :model-value="switchState[row.code] ?? true"
                :disabled="!switchReady || switching"
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
import { ref, reactive, onMounted } from 'vue'
import { ReadingLamp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listDevices } from '../api/device'
import type { DeviceVO } from '../api/device'
import { getLinkageConfig, saveLinkageConfig, switchLight } from '../api/control'
import type { LinkageConfig } from '../api/control'
import { probe } from '../api/helper'
import NotReadyBanner from '../components/NotReadyBanner.vue'

const devices = ref<DeviceVO[]>([])
// 本地开关状态：后端无开关态接口，先按设备编号维护会话内状态，默认开
const switchState = reactive<Record<string, boolean>>({})
const linkage = ref<LinkageConfig>({ enabled: false, threshold: 30 })
const linkageReady = ref(true)
const switchReady = ref(true)
const saving = ref(false)
const switching = ref(false)

async function loadDevices() {
  try {
    devices.value = await listDevices()
    for (const d of devices.value) {
      if (switchState[d.code] === undefined) switchState[d.code] = true
    }
  } catch {
    // 拦截器已统一提示
  }
}

async function loadLinkage() {
  const r = await probe(getLinkageConfig(), { enabled: false, threshold: 30 })
  linkageReady.value = r.ready
  linkage.value = r.data
}

async function saveLinkage() {
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
  switching.value = true
  try {
    const r = await probe(switchLight(row.code, on), null)
    if (r.ready) {
      switchState[row.code] = on // 受控开关：更新本地状态跟随视觉
      ElMessage.success(`${row.code} 已${on ? '开灯' : '关灯'}`)
    } else {
      switchReady.value = false
      switchState[row.code] = !on // 失败回退，恢复原状态
    }
  } finally {
    switching.value = false
  }
}

onMounted(() => {
  loadDevices()
  loadLinkage()
})
</script>

<style scoped>
.control::before { content: ''; position: fixed; z-index: -1; width: 560px; height: 560px; right: -230px; bottom: -180px; border: 1px solid rgba(241,185,78,.1); border-radius: 50%; box-shadow: 0 0 0 80px rgba(241,185,78,.018), 0 0 0 160px rgba(241,185,78,.012); pointer-events: none; }
.control {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.page-title {
  margin: 0;
  font-size: 25px;
  font-weight: 650;
  letter-spacing: 0.01em;
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
.panel { position: relative; overflow: hidden; border-radius: 8px; }
.panel::before { content: ''; position: absolute; inset: 0 auto auto 0; width: 3px; height: 100%; background: linear-gradient(var(--accent), transparent 45%); opacity: .85; }
.panel-head { min-height: 64px; background: linear-gradient(90deg, rgba(97, 73, 25, .18), transparent 45%); }

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
.cfg-title::before { content: ''; display: inline-block; width: 7px; height: 7px; margin-right: 8px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 10px var(--accent-glow); vertical-align: 1px; }
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
  border-radius: 9px;
  flex: none;
}
.device-avatar.on {
  background: linear-gradient(135deg, rgba(241,185,78,.25), var(--accent-dim));
  color: var(--accent-bright);
}
.device-avatar.off {
  background: rgba(226, 98, 90, 0.12);
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
.device-cell::after { content: '可控制'; margin-left: 2px; padding: 1px 5px; border: 1px solid rgba(241,185,78,.28); border-radius: 3px; color: var(--accent-bright); font-size: 9px; letter-spacing: .04em; }
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
</style>
