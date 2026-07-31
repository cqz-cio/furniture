<template>
  <div>
    <div class="text-center">
      <UserAvatar :img="userInfo?.avatar" />
      <p class="avatar-edit-hint">
        <Icon icon="ep:edit-pen" :size="13" />
        点击头像即可编辑
      </p>
    </div>
    <ul class="list-group list-group-striped">
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:user" />
        {{ t('profile.user.username') }}
        <div class="pull-right">{{ userInfo?.username }}</div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="icon-park-outline:peoples" />
        {{ t('profile.user.roles') }}
        <div v-if="userInfo?.roles" class="pull-right">
          {{ userInfo?.roles.map((role) => role.name).join(',') }}
        </div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:calendar" />
        {{ t('profile.user.createTime') }}
        <div class="pull-right">{{ formatDate(userInfo.createTime) }}</div>
      </li>
    </ul>
  </div>
</template>
<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import UserAvatar from './UserAvatar.vue'
import { useUserStore } from '@/store/modules/user'

import { getUserProfile, ProfileVO } from '@/api/system/user/profile'

defineOptions({ name: 'ProfileUser' })

const { t } = useI18n()
const userStore = useUserStore()
const userInfo = ref({} as ProfileVO)

const getUserInfo = async () => {
  const users = await getUserProfile()
  userInfo.value = users
}

// 监听 userStore 中头像的变化，同步更新本地 userInfo
watch(
  () => userStore.getUser.avatar,
  (newAvatar) => {
    if (newAvatar && userInfo.value) {
      userInfo.value.avatar = newAvatar
    }
  }
)

// 暴露刷新方法
defineExpose({
  refresh: getUserInfo
})

onMounted(async () => {
  await getUserInfo()
})
</script>

<style scoped>
.text-center {
  position: relative;
  min-height: 164px;
  padding: 8px 0 18px;
  text-align: center;
}

.avatar-edit-hint {
  display: flex;
  margin: 12px 0 0;
  color: var(--furniture-admin-muted);
  font-size: 12px;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.list-group-striped > .list-group-item {
  padding-right: 0;
  padding-left: 0;
  border-right: 0;
  border-left: 0;
  border-radius: 0;
}

.list-group {
  padding-left: 0;
  list-style: none;
}

.list-group-item {
  display: flex;
  min-height: 48px;
  padding: 12px 0;
  font-size: 13px;
  color: var(--furniture-admin-body);
  border-bottom: 1px solid var(--furniture-admin-border);
  align-items: center;
}

.pull-right {
  min-width: 0;
  margin-left: auto;
  overflow: hidden;
  color: var(--furniture-admin-ink);
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
