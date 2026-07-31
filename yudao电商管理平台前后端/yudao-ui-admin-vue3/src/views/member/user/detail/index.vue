<template>
  <div v-loading="loading" class="member-detail-page">
    <ErpPageState
      v-if="loadState === 'error'"
      description="未能读取这条会员记录。记录可能已删除、当前账号无查看权限，或会员服务暂时不可用。"
      eyebrow="会员中心"
      primary-text="重试"
      secondary-text="返回会员列表"
      title="会员详情加载失败"
      type="error"
      @primary="loadMemberPage"
      @secondary="backToList"
    />
    <el-row v-else-if="loadState === 'ready'" :gutter="10">
      <!-- 左上角：基本信息 -->
      <el-col :span="14" class="detail-info-item">
        <UserBasicInfo :user="user">
          <template #header>
            <div class="card-header">
              <CardTitle title="基本信息" />
              <el-button
                v-hasPermi="['member:user:update']"
                size="small"
                text
                type="primary"
                @click="openForm('update')"
              >
                编辑
              </el-button>
            </div>
          </template>
        </UserBasicInfo>
      </el-col>
      <!-- 右上角：账户信息 -->
      <el-col :span="10" class="detail-info-item">
        <el-card class="h-full" shadow="never">
          <template #header>
            <CardTitle title="账户信息" />
          </template>
          <div v-loading="walletLoading">
            <div v-if="walletError" class="member-wallet-error">
              <el-alert
                :closable="false"
                description="会员基本信息已加载，但余额与充值汇总暂时无法读取。为避免误判，页面不会显示零值。"
                show-icon
                title="账户信息暂不可用"
                type="warning"
              />
              <el-button class="mt-12px" size="small" @click="getUserWallet">
                重新加载账户信息
              </el-button>
            </div>
            <UserAccountInfo v-else :user="user" :wallet="wallet" />
          </div>
        </el-card>
      </el-col>
      <!-- 下边：账户明细 -->
      <!-- TODO 芋艿：【订单管理】【售后管理】【收藏记录】-->
      <el-card header="账户明细" shadow="never" style="width: 100%; margin-top: 20px">
        <template #header>
          <CardTitle title="账户明细" />
        </template>
        <el-tabs>
          <el-tab-pane label="积分">
            <UserPointList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="签到" lazy>
            <UserSignList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="成长值" lazy>
            <UserExperienceRecordList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="余额" lazy>
            <UserBalanceList v-if="wallet.id" :wallet-id="wallet.id" />
          </el-tab-pane>
          <el-tab-pane label="收货地址" lazy>
            <UserAddressList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="订单管理" lazy>
            <UserOrderList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="售后管理" lazy>
            <UserAfterSaleList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="收藏记录" lazy>
            <UserFavoriteList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="优惠劵" lazy>
            <UserCouponList :user-id="memberDetailId" />
          </el-tab-pane>
          <el-tab-pane label="推广用户" lazy>
            <UserBrokerageList :bind-user-id="memberDetailId" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </el-row>
  </div>

  <!-- 表单弹窗：添加/修改 -->
  <UserForm ref="formRef" @success="loadMemberPage" />
</template>
<script lang="ts" setup>
import * as WalletApi from '@/api/pay/wallet/balance'
import * as UserApi from '@/api/member/user'
import { useTagsViewStore } from '@/store/modules/tagsView'
import UserForm from '@/views/member/user/UserForm.vue'
import UserAccountInfo from './UserAccountInfo.vue'
import UserAddressList from './UserAddressList.vue'
import UserBasicInfo from './UserBasicInfo.vue'
import UserBrokerageList from './UserBrokerageList.vue'
import UserCouponList from './UserCouponList.vue'
import UserExperienceRecordList from './UserExperienceRecordList.vue'
import UserOrderList from './UserOrderList.vue'
import UserPointList from './UserPointList.vue'
import UserSignList from './UserSignList.vue'
import UserFavoriteList from './UserFavoriteList.vue'
import UserAfterSaleList from './UserAftersaleList.vue'
import UserBalanceList from './UserBalanceList.vue'
import { CardTitle } from '@/components/Card/index'
import { ElMessage } from 'element-plus'
import { normalizeMemberDetailId } from './memberDetailGuard.mjs'
import { ErpPageState } from '@/components/ErpPageState'

defineOptions({ name: 'MemberDetail' })

const loading = ref(true) // 加载中
const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const user = ref<UserApi.UserVO>({} as UserApi.UserVO)

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string) => {
  if (loadState.value === 'ready') {
    formRef.value.open(type, memberId)
  }
}

/** 获得用户 */
const getUserData = async (id: number) => {
  loading.value = true
  loadState.value = 'loading'
  try {
    const result = await UserApi.getUser(id, { hideErrorMessage: true })
    if (!result?.id) {
      throw new Error('会员记录不存在')
    }
    user.value = result
    loadState.value = 'ready'
    return true
  } catch {
    user.value = {} as UserApi.UserVO
    loadState.value = 'error'
    return false
  } finally {
    loading.value = false
  }
}

/** 初始化 */
const { currentRoute, push } = useRouter() // 路由
const { delView } = useTagsViewStore() // 视图操作
const route = useRoute()
const memberId = normalizeMemberDetailId(route.params.id)
const memberDetailId = computed(() => memberId || 0)
/* 用户钱包相关信息 */
const WALLET_INIT_DATA = {
  balance: 0,
  totalExpense: 0,
  totalRecharge: 0
} as WalletApi.WalletVO // 钱包初始化数据
const wallet = ref<WalletApi.WalletVO>(WALLET_INIT_DATA) // 钱包信息
const walletLoading = ref(false)
const walletError = ref(false)

/** 查询用户钱包信息 */
const getUserWallet = async () => {
  if (!memberId) {
    wallet.value = WALLET_INIT_DATA
    return
  }
  const params = { userId: memberId }
  walletLoading.value = true
  walletError.value = false
  try {
    const result = await WalletApi.getWallet(params)
    if (!result) {
      throw new Error('会员账户不存在')
    }
    wallet.value = result
  } catch {
    wallet.value = WALLET_INIT_DATA
    walletError.value = true
  } finally {
    walletLoading.value = false
  }
}

const backToList = () => {
  delView(unref(currentRoute))
  push('/member/user')
}

const loadMemberPage = async () => {
  if (!memberId) {
    ElMessage.warning('参数错误，会员编号不能为空！')
    loadState.value = 'error'
    loading.value = false
    return
  }
  if (await getUserData(memberId)) {
    await getUserWallet()
  }
}

onMounted(loadMemberPage)
</script>
<style lang="css" scoped>
.member-detail-page {
  min-height: 260px;
}

.detail-info-item:first-child {
  padding-left: 0 !important;
}

/* first-child 不生效有没有大佬给看下q.q */
.detail-info-item:nth-child(2) {
  padding-right: 0 !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.member-wallet-error {
  min-height: 150px;
}
</style>
