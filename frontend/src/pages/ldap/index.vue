<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'


defineOptions({ name: 'LdapDirectory' })
interface DirectoryUser { dn: string; uid: string; commonName: string; surname: string; email?: string }
interface DirectoryGroup { dn: string; name: string; description?: string; members: string[] }
interface ServerStatus { running: boolean; ldapPort: number; ldapsPort: number; tlsEnabled: boolean; startTlsEnabled: boolean; baseDn: string; entries: number; persistenceHealthy: boolean }
interface BindAudit { id: number; bindDn: string; clientAddress?: string; success: boolean; resultCode: number; detail?: string; createdAt: string }
interface BindLock { bindDn: string; failedAttempts: number; lastFailedAt?: string; lockedUntil?: string; locked: boolean }

const loading = ref(false)
const users = ref<DirectoryUser[]>([])
const groups = ref<DirectoryGroup[]>([])
const tree = ref<any[]>([])
const status = ref<ServerStatus | null>(null)
const audits = ref<BindAudit[]>([])
const auditPage = ref(1)
const auditSize = ref(50)
const auditTotal = ref(0)
const auditQuery = reactive<{ bindDn: string; success: '' | boolean; timeRange: string[] }>({ bindDn: '', success: '', timeRange: [] })
const locks = ref<BindLock[]>([])
const activeTab = ref('users')
const userDialog = ref(false)
const groupDialog = ref(false)
const passwordDialog = ref(false)
const editingUid = ref('')
const passwordUid = ref('')
const newPassword = ref('')
const userForm = reactive({ uid: '', commonName: '', surname: '', email: '', password: '' })
const groupForm = reactive({ name: '', description: '' })
const userOptions = computed(() => users.value.map(user => ({ label: `${user.commonName} (${user.uid})`, value: user.uid })))

async function loadAll() {
  loading.value = true
  try {
    const [serverRes, userRes, groupRes, treeRes, lockRes] = await Promise.all([
      request.get('/api/v1/directory/server'), request.get('/api/v1/directory/users'),
      request.get('/api/v1/directory/groups'), request.get('/api/v1/directory/tree'),
      request.get('/api/v1/directory/security/locks'),
    ])
    status.value = serverRes.data.data
    users.value = userRes.data.data || []
    groups.value = groupRes.data.data || []
    tree.value = treeRes.data.data ? [treeRes.data.data] : []
    locks.value = lockRes.data.data || []
    await loadAudits()
  } finally { loading.value = false }
}

async function loadAudits() {
  const response = await request.get('/api/v1/directory/security/bind-audits', {
    params: {
      page: auditPage.value - 1,
      size: auditSize.value,
      bindDn: auditQuery.bindDn || undefined,
      success: auditQuery.success === '' ? undefined : auditQuery.success,
      from: auditQuery.timeRange?.[0] || undefined,
      to: auditQuery.timeRange?.[1] || undefined,
    },
  })
  const data = response.data.data
  audits.value = data?.records || []
  auditTotal.value = data?.total || 0
}

async function searchAudits() {
  auditPage.value = 1
  await loadAudits()
}

function openCreateUser() {
  editingUid.value = ''
  Object.assign(userForm, { uid: '', commonName: '', surname: '', email: '', password: '' })
  userDialog.value = true
}

function openEditUser(user: any) {
  editingUid.value = user.uid
  Object.assign(userForm, { uid: user.uid, commonName: user.commonName, surname: user.surname, email: user.email || '', password: '' })
  userDialog.value = true
}

async function saveUser() {
  if (!userForm.uid || !userForm.commonName || !userForm.surname) return ElMessage.warning('UID、姓名和姓氏必填')
  if (editingUid.value) {
    await request.put(`/api/v1/directory/users/${editingUid.value}`, userForm)
  } else {
    if (!userForm.password || userForm.password.length < 12) return ElMessage.warning('密码至少 12 位并满足复杂度要求')
    await request.post('/api/v1/directory/users', userForm)
  }
  userDialog.value = false
  ElMessage.success('目录用户已保存')
  await loadAll()
}

async function deleteUser(user: any) {
  await ElMessageBox.confirm(`删除目录用户 ${user.uid}？其用户组成员关系也会移除。`, '确认删除', { type: 'warning' })
  await request.delete(`/api/v1/directory/users/${user.uid}`)
  ElMessage.success('目录用户已删除')
  await loadAll()
}

function openPassword(user: any) {
  passwordUid.value = user.uid
  newPassword.value = ''
  passwordDialog.value = true
}

async function changePassword() {
  if (newPassword.value.length < 12) return ElMessage.warning('密码至少 12 位并满足复杂度要求')
  await request.put(`/api/v1/directory/users/${passwordUid.value}/password`, { password: newPassword.value })
  passwordDialog.value = false
  ElMessage.success('LDAP 密码已修改')
}

function openCreateGroup() {
  Object.assign(groupForm, { name: '', description: '' })
  groupDialog.value = true
}

async function saveGroup() {
  if (!groupForm.name) return ElMessage.warning('用户组名称必填')
  await request.post('/api/v1/directory/groups', groupForm)
  groupDialog.value = false
  ElMessage.success('用户组已创建')
  await loadAll()
}

async function updateMembers(group: any, values: string[]) {
  const currentMembers = group.members as string[]
  const before = new Set(currentMembers)
  const after = new Set(values)
  await Promise.all([
    ...values.filter(uid => !before.has(uid)).map(uid => request.put(`/api/v1/directory/groups/${group.name}/members/${uid}`)),
    ...currentMembers.filter(uid => !after.has(uid)).map(uid => request.delete(`/api/v1/directory/groups/${group.name}/members/${uid}`)),
  ])
  ElMessage.success('成员已更新')
  await loadAll()
}

async function deleteGroup(group: any) {
  await ElMessageBox.confirm(`删除用户组 ${group.name}？`, '确认删除', { type: 'warning' })
  await request.delete(`/api/v1/directory/groups/${group.name}`)
  ElMessage.success('用户组已删除')
  await loadAll()
}

async function unlockAccount(lock: any) {
  await request.delete('/api/v1/directory/security/locks', { params: { bindDn: lock.bindDn } })
  ElMessage.success('锁定状态已解除')
  await loadAll()
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" class="ldap-page">
    <section class="hero">
      <div>
        <div class="eyebrow">JADE DIRECTORY SERVICE</div>
        <h1>LDAP 目录控制台</h1>
        <p>Jade 进程直接提供 LDAP 协议、身份验证与目录查询，不依赖外部 OpenLDAP。</p>
      </div>
      <el-button type="primary" @click="loadAll">刷新状态</el-button>
    </section>

    <div class="status-grid">
      <el-card shadow="never"><span>服务状态</span><strong :class="status?.running ? 'ok' : 'bad'">{{ status?.running ? '运行中' : '已停止' }}</strong></el-card>
      <el-card shadow="never"><span>LDAP 端口</span><strong>{{ status?.ldapPort || '-' }}</strong></el-card>
      <el-card shadow="never"><span>LDAPS / TLS</span><strong :class="status?.tlsEnabled ? 'ok' : ''">{{ status?.tlsEnabled ? status.ldapsPort : '未启用' }}</strong></el-card>
      <el-card shadow="never"><span>StartTLS</span><strong :class="status?.startTlsEnabled ? 'ok' : ''">{{ status?.startTlsEnabled ? '已启用' : '未启用' }}</strong></el-card>
      <el-card shadow="never"><span>Base DN</span><strong class="mono">{{ status?.baseDn || '-' }}</strong></el-card>
      <el-card shadow="never"><span>目录条目</span><strong>{{ status?.entries ?? '-' }}</strong></el-card>
    </div>

    <el-card shadow="never" class="workspace">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="目录用户" name="users">
          <div class="toolbar"><el-button type="primary" @click="openCreateUser">新建用户</el-button></div>
          <el-table :data="users" stripe>
            <el-table-column prop="uid" label="UID" width="180" />
            <el-table-column prop="commonName" label="姓名" />
            <el-table-column prop="email" label="邮箱" />
            <el-table-column prop="dn" label="DN" min-width="300"><template #default="{ row }"><code>{{ row.dn }}</code></template></el-table-column>
            <el-table-column label="操作" width="230" fixed="right"><template #default="{ row }">
              <el-button text type="primary" @click="openEditUser(row)">编辑</el-button>
              <el-button text type="warning" @click="openPassword(row)">改密</el-button>
              <el-button text type="danger" @click="deleteUser(row)">删除</el-button>
            </template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="用户组" name="groups">
          <div class="toolbar"><el-button type="primary" @click="openCreateGroup">新建用户组</el-button></div>
          <el-table :data="groups" stripe>
            <el-table-column prop="name" label="组名" width="180" />
            <el-table-column prop="description" label="说明" />
            <el-table-column label="成员" min-width="320"><template #default="{ row }">
              <el-select :model-value="row.members" multiple filterable placeholder="选择目录用户" style="width: 100%" @change="updateMembers(row, $event)">
                <el-option v-for="option in userOptions" :key="option.value" v-bind="option" />
              </el-select>
            </template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button text type="danger" @click="deleteGroup(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="目录树" name="tree">
          <el-tree :data="tree" node-key="dn" default-expand-all :props="{ label: 'name', children: 'children' }">
            <template #default="{ data }"><span class="tree-node"><el-tag size="small" effect="plain">{{ data.type }}</el-tag><b>{{ data.name }}</b><code>{{ data.dn }}</code></span></template>
          </el-tree>
        </el-tab-pane>

        <el-tab-pane label="Bind 审计" name="audits">
          <div class="audit-filters">
            <el-input v-model="auditQuery.bindDn" clearable placeholder="按 Bind DN 筛选" @keyup.enter="searchAudits" />
            <el-select v-model="auditQuery.success" placeholder="全部结果" clearable>
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
            <el-date-picker v-model="auditQuery.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" />
            <el-button type="primary" @click="searchAudits">查询</el-button>
          </div>
          <el-table :data="audits" stripe>
            <el-table-column prop="createdAt" label="时间" width="190" />
            <el-table-column prop="bindDn" label="Bind DN" min-width="300"><template #default="{ row }"><code>{{ row.bindDn }}</code></template></el-table-column>
            <el-table-column prop="clientAddress" label="客户端" width="160" />
            <el-table-column label="结果" width="110"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
            <el-table-column prop="detail" label="详情" width="180" />
          </el-table>
          <el-pagination v-model:current-page="auditPage" v-model:page-size="auditSize" :total="auditTotal"
            :page-sizes="[20, 50, 100, 200]" layout="total, sizes, prev, pager, next" @change="loadAudits" />
        </el-tab-pane>

        <el-tab-pane label="账号锁定" name="locks">
          <el-table :data="locks" stripe>
            <el-table-column prop="bindDn" label="Bind DN" min-width="320"><template #default="{ row }"><code>{{ row.bindDn }}</code></template></el-table-column>
            <el-table-column prop="failedAttempts" label="失败次数" width="110" />
            <el-table-column prop="lockedUntil" label="锁定截止" width="220" />
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.locked ? 'danger' : 'warning'">{{ row.locked ? '已锁定' : '观察中' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="primary" @click="unlockAccount(row)">解除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>

  <el-dialog v-model="userDialog" :title="editingUid ? '编辑目录用户' : '新建目录用户'" width="520">
    <el-form :model="userForm" label-width="90">
      <el-form-item label="UID"><el-input v-model="userForm.uid" :disabled="!!editingUid" /></el-form-item>
      <el-form-item label="姓名"><el-input v-model="userForm.commonName" /></el-form-item>
      <el-form-item label="姓氏"><el-input v-model="userForm.surname" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
      <el-form-item v-if="!editingUid" label="初始密码"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer><el-button @click="userDialog = false">取消</el-button><el-button type="primary" @click="saveUser">保存</el-button></template>
  </el-dialog>

  <el-dialog v-model="groupDialog" title="新建用户组" width="480">
    <el-form :model="groupForm" label-width="90"><el-form-item label="组名"><el-input v-model="groupForm.name" /></el-form-item><el-form-item label="说明"><el-input v-model="groupForm.description" type="textarea" /></el-form-item></el-form>
    <template #footer><el-button @click="groupDialog = false">取消</el-button><el-button type="primary" @click="saveGroup">创建</el-button></template>
  </el-dialog>

  <el-dialog v-model="passwordDialog" title="修改 LDAP 密码" width="440">
    <p>目录用户：<b>{{ passwordUid }}</b></p><el-input v-model="newPassword" type="password" show-password placeholder="至少 12 位，含大小写、数字和特殊字符" />
    <template #footer><el-button @click="passwordDialog = false">取消</el-button><el-button type="primary" @click="changePassword">确认修改</el-button></template>
  </el-dialog>
</template>

<style scoped>

.ldap-page { max-width: 1480px; margin: 0 auto; }
.hero { display: flex; justify-content: space-between; align-items: center; padding: 30px 34px; margin-bottom: 16px; border-radius: 16px; color: white; background: radial-gradient(circle at 85% 20%, #36d39955, transparent 30%), linear-gradient(120deg, #071d18, #123f34); }
.hero h1 { margin: 5px 0 8px; font-size: 30px; }.hero p { margin: 0; color: #b8d8cf; }.eyebrow { color: #5ee6b2; font-size: 12px; letter-spacing: 2px; }
.status-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 14px; margin-bottom: 16px; }.status-grid :deep(.el-card__body) { display: flex; flex-direction: column; gap: 10px; }.status-grid span { color: var(--jade-text-secondary); font-size: 13px; }.status-grid strong { font-size: 20px; }.status-grid .mono { font-size: 15px; }.ok { color: var(--jade-primary); }.bad { color: #f87171; }
.workspace { border-radius: 12px; }.toolbar { display: flex; justify-content: flex-end; margin-bottom: 14px; }code { color: var(--jade-primary-light); font-size: 12px; }.tree-node { display: flex; gap: 10px; align-items: center; }.tree-node code { margin-left: 8px; color: var(--jade-text-secondary); }
.audit-filters { display: grid; grid-template-columns: minmax(240px, 1fr) 150px minmax(360px, 1.4fr) auto; gap: 12px; margin-bottom: 14px; }.audit-filters + .el-table { margin-bottom: 14px; }
@media (max-width: 900px) { .status-grid { grid-template-columns: repeat(2, 1fr); }.hero { align-items: flex-start; gap: 16px; } }
</style>
