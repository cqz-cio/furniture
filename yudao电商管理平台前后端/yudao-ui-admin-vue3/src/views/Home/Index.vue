<template>
  <ErpPageLoading v-if="profileLoading" title="首页" />
  <el-result
    v-else-if="profileError"
    icon="error"
    title="租户业务配置加载失败"
    sub-title="请刷新页面后重试"
  />
  <B2BHome v-else-if="isB2B" />
  <section v-else class="furniture-admin-page">
    <div class="furniture-admin-page-head">
      <div>
        <p class="furniture-admin-kicker">Oakved Console</p>
        <h1>家具运营控制台</h1>
        <p> 聚焦商品、订单、售后、库存与支付异常，把日常处理入口放在第一屏，减少通用后台噪音。 </p>
      </div>
      <el-space wrap>
        <el-tag effect="plain" type="success">今日履约正常</el-tag>
        <el-button type="primary" @click="go('/mall/trade/order')">处理订单</el-button>
        <el-button @click="go('/mall/product/spu')">维护商品</el-button>
      </el-space>
    </div>

    <div class="furniture-admin-metrics">
      <button
        v-for="item in metrics"
        :key="item.label"
        class="furniture-admin-metric"
        type="button"
        @click="go(item.path)"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </button>
    </div>

    <div class="furniture-admin-work-grid">
      <el-card class="furniture-admin-panel" shadow="never">
        <template #header>
          <div class="furniture-admin-panel-title">
            <span>运营待办</span>
            <el-tag type="warning" effect="plain">需要今日处理</el-tag>
          </div>
        </template>
        <div class="furniture-admin-status-list">
          <button
            v-for="item in queues"
            :key="item.title"
            class="furniture-admin-status-row"
            type="button"
            @click="go(item.path)"
          >
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
            <el-tag :type="item.type" effect="light">{{ item.count }}</el-tag>
          </button>
        </div>
      </el-card>

      <el-card class="furniture-admin-panel" shadow="never">
        <template #header>
          <div class="furniture-admin-panel-title">
            <span>常用工作台</span>
            <span>快速进入高频模块</span>
          </div>
        </template>
        <div class="furniture-admin-action-grid">
          <button
            v-for="item in actions"
            :key="item.title"
            class="furniture-admin-action-card"
            type="button"
            @click="go(item.path)"
          >
            <Icon :icon="item.icon" :size="22" />
            <span>{{ item.title }}</span>
            <small>{{ item.description }}</small>
          </button>
        </div>
      </el-card>
    </div>

    <el-card class="furniture-admin-table-panel" shadow="never">
      <template #header>
        <div class="furniture-admin-panel-title">
          <span>重点商品状态</span>
          <el-button text type="primary" @click="go('/mall/product/spu')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="priorityProducts">
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="furniture-admin-product-cell">
              <img :src="row.image" :alt="row.name" />
              <span>
                <strong>{{ row.name }}</strong>
                <small>{{ row.category }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="120" />
        <el-table-column prop="orders" label="近 7 日订单" width="140" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="row.type" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="140" />
      </el-table>
    </el-card>
  </section>
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import ErpPageLoading from '@/layout/components/ErpPageLoading.vue'
import { useTenantBusinessProfile } from '@/hooks/web/useTenantBusinessProfile'
import B2BHome from './B2BHome.vue'

defineOptions({ name: 'Index' })

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const router = useRouter()
const go = (path: string) => router.push(path)
const { isB2B, profileLoading, profileError, loadTenantBusinessProfile } =
  useTenantBusinessProfile()

onMounted(() => {
  void loadTenantBusinessProfile().catch(() => undefined)
})

const metrics = [
  { label: '今日订单', value: '128', hint: '较昨日 +12%', path: '/mall/trade/order' },
  { label: '待发货', value: '34', hint: '优先核对大件物流', path: '/mall/trade/order' },
  { label: '售后待处理', value: '7', hint: '含 2 单高优先级', path: '/mall/trade/after-sale' },
  {
    label: '低库存',
    value: '18',
    hint: '软装与灯具补货',
    path: '/mall/product/spu?tabType=3'
  }
]

const queues: Array<{
  title: string
  description: string
  count: string
  type: TagType
  path: string
}> = [
  {
    title: '订单履约队列',
    description: '待配货、待出库、待同步物流单号',
    count: '34',
    type: 'warning',
    path: '/mall/trade/order'
  },
  {
    title: '支付异常',
    description: '支付成功未回调、退款待确认',
    count: '5',
    type: 'danger',
    path: '/pay/order'
  },
  {
    title: '地址核验',
    description: '大件家具配送地址、楼层与预约时间核对',
    count: '11',
    type: 'primary',
    path: '/mall/trade/order'
  },
  {
    title: '售后审核',
    description: '退换货、补发配件、安装反馈',
    count: '7',
    type: 'info',
    path: '/mall/trade/after-sale'
  }
]

const actions = [
  {
    title: '商品管理',
    description: '上架、价格、图片与前台预览',
    icon: 'ep:goods',
    path: '/mall/product/spu'
  },
  {
    title: '订单中心',
    description: '支付、发货、售后联动处理',
    icon: 'ep:tickets',
    path: '/mall/trade/order'
  },
  {
    title: '会员用户',
    description: '标签、等级与复购维护',
    icon: 'ep:user',
    path: '/member/user'
  },
  {
    title: '支付订单',
    description: '回调异常与退款记录',
    icon: 'ep:wallet',
    path: '/pay/order'
  },
  {
    title: '文件素材',
    description: '商品图、空间图、证书资料',
    icon: 'ep:folder-opened',
    path: '/infra/file'
  },
  {
    title: '售后管理',
    description: '退换货、补发与安装反馈',
    icon: 'ep:service',
    path: '/mall/trade/after-sale'
  }
]

const priorityProducts: Array<{
  name: string
  category: string
  image: string
  stock: string
  orders: number
  status: string
  type: TagType
  owner: string
}> = [
  {
    name: '艾维琳模块沙发',
    category: '客厅 / 沙发',
    image: '/assets/generated-furniture/product-sofa-cover.webp',
    stock: '24 件',
    orders: 19,
    status: '热销补货',
    type: 'warning',
    owner: '商品运营'
  },
  {
    name: '大理石穹顶台灯',
    category: '灯具 / 台灯',
    image: '/assets/generated-furniture/product-pendant-cover.webp',
    stock: '63 件',
    orders: 13,
    status: '正常',
    type: 'success',
    owner: '软装组'
  },
  {
    name: '胡桃木收纳柜',
    category: '餐厅 / 储物',
    image: '/assets/generated-furniture/product-table-cover.webp',
    stock: '8 件',
    orders: 11,
    status: '低库存',
    type: 'danger',
    owner: '仓配组'
  }
]
</script>
