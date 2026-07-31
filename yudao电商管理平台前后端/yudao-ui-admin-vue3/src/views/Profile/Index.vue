<template>
  <div class="profile-page">
    <el-card class="profile-card profile-card--summary" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('profile.user.title') }}</span>
          <small>账号与身份信息</small>
        </div>
      </template>
      <ProfileUser ref="profileUserRef" />
    </el-card>
    <el-card class="profile-card profile-card--settings" shadow="never">
      <el-tabs v-model="activeName" class="profile-tabs" tab-position="top">
        <el-tab-pane :label="t('profile.info.basicInfo')" name="basicInfo">
          <BasicInfo @success="handleBasicInfoSuccess" />
        </el-tab-pane>
        <el-tab-pane :label="t('profile.info.resetPwd')" name="resetPwd">
          <ResetPwd />
        </el-tab-pane>
        <el-tab-pane :label="t('profile.info.userSocial')" name="userSocial">
          <UserSocial v-model:activeName="activeName" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
<script lang="ts" setup>
import { BasicInfo, ProfileUser, ResetPwd, UserSocial } from './components'

const { t } = useI18n()
defineOptions({ name: 'Profile' })
const activeName = ref('basicInfo')
const profileUserRef = ref()

// 处理基本信息更新成功
const handleBasicInfoSuccess = async () => {
  await profileUserRef.value?.refresh()
}
</script>
<style scoped>
.profile-page {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.profile-card {
  min-width: 0;
}

.profile-card--settings {
  min-height: 520px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.card-header span {
  color: var(--furniture-admin-ink);
  font-size: 15px;
  font-weight: 650;
}

.card-header small {
  color: var(--furniture-admin-muted);
  font-size: 12px;
  font-weight: 400;
}

.profile-tabs {
  min-height: 460px;
}

:deep(.profile-tabs > .el-tabs__content) {
  padding: 20px 4px 4px;
}

@media (width <= 960px) {
  .profile-page {
    grid-template-columns: 1fr;
  }
}
</style>
