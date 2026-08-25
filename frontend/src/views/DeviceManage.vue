<template>
  <div class="dev-manage">
    <div class="page-intro">
      <div>
        <h2 class="page-title">设备管理</h2>
        <p class="page-desc">统一管理路灯设备：添加、查看、解绑</p>
      </div>
      <div class="refresh">
        <el-button type="primary" :icon="Plus" :disabled="!ready" @click="openAdd">添加设备</el-button>
      </div>
    </div>

    <NotReadyBanner
      v-if="!ready"
      text="设备增删接口尚未由后端实现（约定 POST/DELETE /api/devices），当前仅展示设备列表；添加 / 解绑操作不可用。"
    />

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">路灯设备台账</div>
        <div class="panel-meta">{{ devices.length }} 台设备</div>
      </div>
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
          <el-table-column prop="location" label="位置" min-width="180">
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
          <el-table-column label="最后在线" width="140" align="right">
            <template #default="{ row }">
              <span class="last num">{{ time(row.lastSeen) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right">
            <template #default="{ row }">
              <el-button
                size="small"
                text
                type="danger"
                :disabled="!ready"
                @click="onUnbind(row)"
              >
                解绑
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">暂无设备 —— 设备上线后自动出现在这里</div>
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="addVisible" title="添加路灯设备" width="420px" class="device-dialog">
      <el-form label-position="top" @submit.prevent="submitAdd">
        <el-form-item label="设备编号" required>
          <el-input v-model="form.code" placeholder="如 SL-020" :prefix-icon="ReadingLamp" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" placeholder="如 建设路 8 号" />
        </el-form-item>
        <div class="coordinate-grid" aria-describedby="coordinate-help">
          <el-form-item label="经度" required>
            <el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" :step="0.000001" controls-position="right" placeholder="如 106.298038" />
          </el-form-item>
          <el-form-item label="纬度" required>
            <el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" :step="0.000001" controls-position="right" placeholder="如 29.593390" />
          </el-form-item>
        </div>
        <p id="coordinate-help" class="coordinate-help">必填。请填写高德地图坐标拾取器给出的 GCJ-02 经度和纬度，设备将按此坐标显示在地图上。</p>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ReadingLamp, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDevices } from '../api/device'
import type { DeviceVO } from '../api/device'
import { addDevice, removeDevice } from '../api/deviceManage'
import { probe } from '../api/helper'
import NotReadyBanner from '../components/NotReadyBanner.vue'

const devices = ref<DeviceVO[]>([])
const ready = ref(true)
const addVisible = ref(false)
const adding = ref(false)
const form = reactive<{ code: string; location: string; longitude?: number; latitude?: number }>({ code: '', location: '' })

const pad = (n: number) => String(n).padStart(2, '0')
function time(ts: number | null) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  try {
    devices.value = await listDevices()
  } catch {
    // 拦截器已统一提示
  }
}

function openAdd() {
  form.code = ''
  form.location = ''
  form.longitude = undefined
  form.latitude = undefined
  addVisible.value = true
}

async function submitAdd() {
  if (!form.code.trim()) {
    ElMessage.warning('请输入设备编号')
    return
  }
  if (!Number.isFinite(form.longitude) || !Number.isFinite(form.latitude)) {
    ElMessage.warning('请输入设备安装点的经度和纬度')
    return
  }
  adding.value = true
  try {
    const r = await probe(addDevice({ code: form.code.trim(), location: form.location.trim(), longitude: form.longitude!, latitude: form.latitude! }), null)
    if (r.ready) {
      addVisible.value = false
      ElMessage.success(`设备 ${form.code.trim()} 已添加`)
      await load()
    } else {
      ready.value = false
    }
  } finally {
    adding.value = false
  }
}

async function onUnbind(row: DeviceVO) {
  try {
    await ElMessageBox.confirm(
      `解绑后设备 ${row.code} 将不再出现在管理平台，确认解绑？`,
      '解绑设备',
      { type: 'warning', confirmButtonText: '解绑', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  const r = await probe(removeDevice(row.code), null)
  if (r.ready) {
    devices.value = devices.value.filter((d) => d.code !== row.code)
    ElMessage.success(`设备 ${row.code} 已解绑`)
  } else {
    ready.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dev-manage {
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
  font-size: 20px;
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
}

.table-wrap {
  padding: 6px 16px 16px;
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
  background: var(--accent-dim);
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
.last {
  color: var(--text-muted);
  font-size: 12.5px;
}
.table-empty {
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 12.5px;
}
.coordinate-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.coordinate-grid :deep(.el-input-number) {
  width: 100%;
}
.coordinate-help {
  margin: -4px 0 8px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 560px) {
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
  .coordinate-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
