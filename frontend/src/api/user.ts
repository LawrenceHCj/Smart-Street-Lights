import request from './request'

/**
 * 用户与权限（功能清单 F-11）
 * 接口已由 Java 后端实现；调用失败时由页面降级处理。
 */

export type UserRole = 'admin' | 'municipal' | 'operator'

export interface UserVO {
  id: number
  username: string
  role: UserRole
  status: 'ENABLED' | 'DISABLED'
  createdAt: number
}

export interface AddUserPayload {
  username: string
  password: string
  role: UserRole
}

/** 用户列表 F-11：GET /api/users */
export function listUsers(): Promise<UserVO[]> {
  return request.get('/users', { silent: true })
}

/** 新增用户 F-11：POST /api/users */
export function createUser(payload: AddUserPayload): Promise<null> {
  return request.post('/users', payload, { silent: true })
}

/** 修改角色 F-11：PUT /api/users/{id}/role */
export function updateUserRole(id: number, role: UserRole): Promise<null> {
  return request.put(`/users/${id}/role`, { role }, { silent: true })
}

/** 删除用户 F-11：DELETE /api/users/{id} */
export function deleteUser(id: number): Promise<null> {
  return request.delete(`/users/${id}`, { silent: true })
}
