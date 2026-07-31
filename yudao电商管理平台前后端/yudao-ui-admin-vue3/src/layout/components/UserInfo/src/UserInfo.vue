<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'

import avatarImg from '@/assets/imgs/avatar.gif'
import { isDevLinksVisible } from '@/config/furnitureLite'
import { useDesign } from '@/hooks/web/useDesign'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { useUserStore } from '@/store/modules/user'
import LockDialog from './components/LockDialog.vue'
import LockPage from './components/LockPage.vue'
import { useLockStore } from '@/store/modules/lock'

defineOptions({ name: 'UserInfo' })

const { t } = useI18n()

const { push, replace } = useRouter()

const userStore = useUserStore()

const tagsViewStore = useTagsViewStore()

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('user-info')

const avatar = computed(() => userStore.user.avatar || avatarImg)
const userName = computed(() =>
  userStore.user.nickname === '芋道源码' ? 'Oakved Console' : userStore.user.nickname || 'Admin'
)
const showDevLinks = computed(() => isDevLinksVisible())

// 锁定屏幕
const lockStore = useLockStore()
const getIsLock = computed(() => lockStore.getLockInfo?.isLock ?? false)
const dialogVisible = ref<boolean>(false)
const lockScreen = () => {
  dialogVisible.value = true
}

const loginOut = async () => {
  try {
    await ElMessageBox.confirm(t('common.loginOutMessage'), t('common.reminder'), {
      confirmButtonText: t('common.ok'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await userStore.loginOut()
    tagsViewStore.delAllViews()
    replace('/login?redirect=/index')
  } catch {}
}
const toProfile = async () => {
  push('/user/profile')
}
const editAvatar = async () => {
  push({ path: '/user/profile', query: { edit: 'avatar' } })
}
const toDocument = () => {
  window.open('https://doc.iocoder.cn/')
}
</script>

<template>
  <ElDropdown :class="prefixCls" trigger="click">
    <div class="erp-user-trigger">
      <div class="erp-user-avatar">
        <ElAvatar :src="avatar" :alt="`${userName}头像`" />
        <ElTooltip content="编辑头像" placement="bottom">
          <button
            aria-label="编辑头像"
            class="erp-user-avatar__edit"
            type="button"
            @click.stop="editAvatar"
          >
            <Icon icon="ep:edit-pen" :size="11" />
          </button>
        </ElTooltip>
      </div>
      <span class="erp-user-name <lg:hidden">
        {{ userName }}
      </span>
      <Icon class="<lg:hidden" icon="ep:arrow-down" :size="12" />
    </div>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem @click="toProfile">
          <Icon icon="ep:tools" />
          <div>{{ t('common.profile') }}</div>
        </ElDropdownItem>
        <ElDropdownItem @click="editAvatar">
          <Icon icon="ep:edit-pen" />
          <div>编辑头像</div>
        </ElDropdownItem>
        <ElDropdownItem v-if="showDevLinks">
          <Icon icon="ep:menu" />
          <div @click="toDocument">{{ t('common.document') }}</div>
        </ElDropdownItem>
        <ElDropdownItem divided>
          <Icon icon="ep:lock" />
          <div @click="lockScreen">{{ t('lock.lockScreen') }}</div>
        </ElDropdownItem>
        <ElDropdownItem divided @click="loginOut">
          <Icon icon="ep:switch-button" />
          <div>{{ t('common.loginOut') }}</div>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>

  <LockDialog v-if="dialogVisible" v-model="dialogVisible" />

  <teleport to="body">
    <transition name="fade-bottom" mode="out-in">
      <LockPage v-if="getIsLock" />
    </transition>
  </teleport>
</template>

<style scoped lang="scss">
.erp-user-trigger {
  display: flex;
  height: 44px;
  padding: 0 8px;
  color: var(--top-header-text-color);
  border-radius: 8px;
  cursor: pointer;
  outline: none;
  align-items: center;
  gap: 8px;
  transition: background-color 0.16s ease;
}

.erp-user-trigger:hover {
  background: var(--top-header-hover-color);
}

.erp-user-avatar {
  position: relative;
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;

  :deep(.el-avatar) {
    width: 36px;
    height: 36px;
    border: 2px solid #fff;
    box-shadow: 0 0 0 1px var(--furniture-admin-border);
  }
}

.erp-user-avatar__edit {
  position: absolute;
  right: -2px;
  bottom: -2px;
  display: grid;
  width: 18px;
  height: 18px;
  padding: 0;
  color: #fff;
  background: #1e2b3d;
  border: 2px solid #fff;
  border-radius: 50%;
  cursor: pointer;
  place-items: center;
}

.erp-user-avatar__edit:hover,
.erp-user-avatar__edit:focus-visible {
  background: var(--el-color-primary);
  outline: 2px solid rgb(23 107 219 / 22%);
  outline-offset: 1px;
}

.erp-user-name {
  max-width: 160px;
  overflow: hidden;
  color: var(--top-header-text-color);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fade-bottom-enter-active,
.fade-bottom-leave-active {
  transition:
    opacity 0.25s,
    transform 0.3s;
}

.fade-bottom-enter-from {
  opacity: 0;
  transform: translateY(-10%);
}

.fade-bottom-leave-to {
  opacity: 0;
  transform: translateY(10%);
}
</style>
