<template>
  <div class="not-ready" role="status" aria-live="polite">
    <el-icon class="icon" aria-hidden="true"><WarningFilled /></el-icon>
    <div class="body">
      <div class="title">后端服务未就绪</div>
      <div class="desc">{{ text }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'

withDefaults(defineProps<{ text?: string }>(), {
  text: '该功能对应的后端接口尚未实现，当前操作不可用；接口就绪后自动恢复。',
})
</script>

<style scoped>
.not-ready {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin: 16px 20px 0;
  padding: 13px 16px;
  border: 1px solid rgba(246, 164, 77, 0.3);
  background: var(--warn-dim);
  border-radius: var(--radius-md);
}
.icon {
  font-size: 18px;
  color: var(--warn);
  margin-top: 2px;
}
.title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--warn);
}
.desc {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.6;
}
</style>
