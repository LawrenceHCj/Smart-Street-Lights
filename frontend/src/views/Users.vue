<template>
  <div class="workspace-page users">
    <div class="page-intro">
      <div>
        <h1 class="page-title">用户与权限</h1>
        <p class="page-desc">管理平台账号与角色权限</p>
      </div>
      <div class="refresh">
        <el-button type="primary" :icon="Plus" :disabled="!ready" @click="openAdd">添加用户</el-button>
      </div>
    </div>

    <NotReadyBanner
      v-if="!ready"
      text="用户管理接口尚未由后端实现（约定 /api/users 系列），当前展示为空态；接口就绪后自动加载用户。"
    />

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">用户列表</div>
        <div class="panel-meta">{{ users.length }} 个账号</div>
      </div>
      <div class="table-wrap">
        <el-table :data="pagedUsers">
          <el-table-column label="用户名" min-width="160">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="user-avatar"><el-icon><User /></el-icon></span>
                <span class="user-name num">{{ row.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="160">
            <template #default="{ row }">
              <el-select
                :model-value="row.role"
                size="small"
                :disabled="!ready || !canManage(row)"
                @change="(v: UserRole) => onChangeRole(row, v)"
              >
                <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-pill" :class="row.status === 'ENABLED' ? 'online' : 'offline'">
                <span class="status-dot" :class="row.status === 'ENABLED' ? 'online' : 'offline'"></span>
                {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="140" align="right">
            <template #default="{ row }">
              <span class="created num">{{ time(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                text
                type="danger"
                :disabled="!ready || !canManage(row)"
                @click="onDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              {{ ready ? '暂无用户账号' : '用户服务暂不可用' }}
            </div>
          </template>
        </el-table>
        <div v-if="users.length > 10" class="pager" aria-label="用户列表分页">
          <div class="pager-summary">
            <span>共 <strong class="num">{{ users.length }}</strong> 个账号</span>
            <el-select
              :model-value="pageSize"
              size="small"
              class="page-size-select"
              aria-label="每页显示数量"
              @change="onSize"
            >
              <el-option v-for="size in pageSizes" :key="size" :label="`每页 ${size} 个`" :value="size" />
            </el-select>
          </div>
          <el-pagination
            v-if="users.length > pageSize"
            :current-page="page"
            :page-size="pageSize"
            :total="users.length"
            layout="prev, pager, next"
            background
            @current-change="onPage"
          />
        </div>
      </div>
    </div>

    <el-dialog v-model="addVisible" title="添加用户" width="min(420px, calc(100vw - 32px))">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="form.password" type="password" show-password placeholder="登录密码" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { User, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import { listUsers, createUser, updateUserRole, deleteUser } from '../api/user'
import type { UserVO, UserRole } from '../api/user'
import { probe } from '../api/helper'
import { getCurrentUsername } from '../utils/auth'
import NotReadyBanner from '../components/NotReadyBanner.vue'
import { compareNaturalText } from '../utils/sort'

const users = ref<UserVO[]>([])
const ready = ref(true)
const addVisible = ref(false)
const adding = ref(false)
const form = reactive({ username: '', password: '', role: 'operator' as UserRole })

// 客户端分页：用户列表分页展示
const page = ref(1)
const pageSize = ref(10)
const pageSizes = [10, 20, 50]
const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return [...users.value]
    .sort((left, right) => compareNaturalText(left.username, right.username))
    .slice(start, start + pageSize.value)
})
function onPage(p: number) {
  page.value = p
}
function onSize(s: number) {
  pageSize.value = s
  page.value = 1
}
// 删除/刷新后若页码越界则回退到最后一页
watch(users, (u) => {
  const maxPage = Math.max(1, Math.ceil(u.length / pageSize.value))
  if (page.value > maxPage) page.value = maxPage
})

const roleOptions: Array<{ value: UserRole; label: string }> = [
  { value: 'admin', label: '管理员' },
  { value: 'municipal', label: '市政人员' },
  { value: 'operator', label: '运维人员' },
]
const roleLabel = (r: UserRole) => roleOptions.find((o) => o.value === r)?.label ?? r
const currentUsername = getCurrentUsername()
const canManage = (user: UserVO) => user.username !== 'admin' && user.username !== currentUsername
const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback

const pad = (n: number) => String(n).padStart(2, '0')
function time(ts: number) {
  const d = new Date(ts)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function load() {
  const r = await probe(listUsers(), [])
  ready.value = r.ready
  if (r.ready) users.value = r.data
}

function openAdd() {
  form.username = ''
  form.password = ''
  form.role = 'operator'
  addVisible.value = true
}

async function submitAdd() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写用户名和初始密码')
    return
  }
  adding.value = true
  try {
    await createUser({ username: form.username.trim(), password: form.password, role: form.role })
    addVisible.value = false
    ElMessage.success(`用户 ${form.username.trim()} 已添加`)
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '添加用户失败，请重试'))
  } finally {
    adding.value = false
  }
}

async function onChangeRole(row: UserVO, role: UserRole) {
  const prev = row.role
  row.role = role
  try {
    await updateUserRole(row.id, role)
    ElMessage.success(`${row.username} 角色已调整为「${roleLabel(role)}」`)
  } catch (error) {
    row.role = prev // 失败回退
    ElMessage.error(errorMessage(error, '角色调整失败，请重试'))
  }
}

async function onDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(
      `删除用户 ${row.username} 后其将无法登录，确认删除？`,
      '删除用户',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  try {
    await deleteUser(row.id)
    users.value = users.value.filter((u) => u.id !== row.id)
    ElMessage.success(`用户 ${row.username} 已删除`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除用户失败，请重试'))
  }
}

onMounted(load)
</script>

<style scoped>
.users {
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
}

.table-wrap {
  padding: 6px 16px 16px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 58px;
  margin-top: 12px;
  padding: 10px 0 0;
  border-top: 1px solid var(--border-subtle);
}
.pager-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
}
.pager-summary strong { color: var(--text-primary); font-size: 13px; }
.page-size-select { width: 116px; }
.pager :deep(.el-pagination) { --el-pagination-bg-color: transparent; --el-pagination-button-color: var(--text-secondary); --el-pagination-hover-color: var(--accent-deep); }
.pager :deep(.el-pagination.is-background .btn-prev),
.pager :deep(.el-pagination.is-background .btn-next),
.pager :deep(.el-pagination.is-background .el-pager li) {
  min-width: 34px;
  height: 34px;
  border: 1px solid var(--border-subtle);
  border-radius: 2px;
  background: transparent;
}
.pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  border-color: var(--signal-strong);
  color: var(--ink);
  background: var(--signal);
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 3px;
  flex: none;
  background: var(--accent-dim);
  color: var(--accent-bright);
}
.user-avatar :deep(.el-icon) {
  font-size: 15px;
}
.user-name {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 550;
}
.created {
  color: var(--text-muted);
  font-size: 12.5px;
}
.table-empty {
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 12.5px;
}

@media (max-width: 560px) {
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
  .pager { align-items: stretch; flex-direction: column; }
  .pager-summary { justify-content: space-between; }
  .pager :deep(.el-pagination) { justify-content: center; }
}
/* Access roster */
.users { max-width: 1320px; }
.users .page-intro { min-height: 110px; padding: 0 4px 20px; border-bottom: 1px solid var(--border-strong); }
.users > .panel { border-width: 0 0 1px; background: transparent; }
.users .panel-head { padding-inline: 0; border-bottom-color: var(--border-strong); }
.users .table-wrap { padding: 12px 0 0 44px; background-image: linear-gradient(90deg, var(--signal) 0 4px, transparent 4px); }
.users .user-avatar { border-radius: 0; color: var(--ink); background: var(--signal); }
</style>
