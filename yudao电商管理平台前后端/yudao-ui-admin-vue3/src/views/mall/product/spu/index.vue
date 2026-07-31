<!-- 商品中心 - 商品列表  -->
<template>
  <doc-alert title="【商品】商品 SPU 与 SKU" url="https://doc.iocoder.cn/mall/product-spu-sku/" />

  <ContentWrap v-if="profileLoading" v-loading="true" class="min-h-200px" />
  <ContentWrap v-else-if="profileError">
    <el-alert
      :closable="false"
      show-icon
      title="当前租户业务配置加载失败，请刷新页面后重试"
      type="error"
    />
  </ContentWrap>
  <template v-else-if="profileLoaded">
    <ContentWrap v-if="isB2B">
      <el-alert
        :closable="false"
        description="列表仅展示 B2B 询盘与网站运营需要的字段。未对 B2B 网站开放的价格保留在 ERP 内部，市场价、销量等 B2C 专用字段默认不显示。"
        show-icon
        title="当前为 B2B 商品视图"
        type="info"
      />
    </ContentWrap>

    <section class="product-status-bar" aria-label="商品状态筛选">
      <div class="product-status-tabs">
        <button
          v-for="item in tabsData"
          :key="item.type"
          :class="[
            'product-status-tab',
            {
              'is-active': Number(queryParams.tabType) === item.type,
              'is-warning': item.type === 3
            }
          ]"
          type="button"
          @click="handleTabSelect(item.type)"
        >
          <span>{{ item.name }}</span>
          <strong>{{ item.count }}</strong>
        </button>
      </div>
      <p>共 {{ total }} 个商品</p>
    </section>

    <!-- 搜索工作栏 -->
    <ContentWrap class="product-filter-panel" :auto-title="false">
      <el-form
        ref="queryFormRef"
        :inline="true"
        :model="queryParams"
        class="product-filter-form"
        label-width="68px"
      >
        <div class="product-filter-fields">
          <el-form-item label="商品名称" prop="name">
            <el-input
              v-model="queryParams.name"
              clearable
              placeholder="请输入商品名称"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="商品分类" prop="categoryId">
            <el-cascader
              v-model="queryParams.categoryId"
              :options="categoryList"
              :props="defaultProps"
              clearable
              filterable
              placeholder="请选择商品分类"
            />
          </el-form-item>
          <el-form-item label="创建时间" prop="createTime">
            <el-date-picker
              v-model="queryParams.createTime"
              :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
              end-placeholder="结束日期"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item class="product-filter-submit">
            <el-button type="primary" @click="handleQuery">
              <Icon class="mr-5px" icon="ep:search" />
              搜索
            </el-button>
            <el-button @click="resetQuery">
              <Icon class="mr-5px" icon="ep:refresh" />
              重置
            </el-button>
          </el-form-item>
        </div>
        <div class="product-filter-actions">
          <el-button
            v-hasPermi="['product:spu:create']"
            type="primary"
            @click="openForm(undefined)"
          >
            <Icon class="mr-5px" icon="ep:plus" />
            新增商品
          </el-button>
          <el-button
            v-hasPermi="['product:spu:export']"
            :loading="exportLoading"
            plain
            @click="handleExport"
          >
            <Icon class="mr-5px" icon="ep:download" />
            导出
          </el-button>
          <el-button v-hasPermi="['product:spu:update']" plain @click="handleErpSyncAll">
            ERP 全量同步
          </el-button>
        </div>
      </el-form>
    </ContentWrap>

    <!-- 列表 -->
    <ContentWrap class="product-table-panel" :auto-title="false">
      <el-table v-loading="loading" :data="list" row-key="id" show-overflow-tooltip>
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-form class="spu-table-expand" label-position="left">
              <el-row>
                <el-col :span="24">
                  <el-row>
                    <el-col :span="8">
                      <el-form-item label="商品分类:">
                        <span>{{ formatCategoryName(row.categoryId) }}</span>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item v-if="!isB2B" label="市场价:">
                        <span>{{ fenToYuan(row.marketPrice) }}</span>
                      </el-form-item>
                      <el-form-item v-else :label="b2bPriceIsWebsite ? '网站价格:' : '内部参考价:'">
                        <span>¥ {{ fenToYuan(row.price) }}</span>
                        <el-tag
                          class="ml-8px"
                          effect="plain"
                          size="small"
                          :type="b2bPriceIsWebsite ? 'success' : 'info'"
                        >
                          {{ b2bPriceIsWebsite ? '网站公开' : 'ERP 内部' }}
                        </el-tag>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item :label="isB2B ? '内部成本价:' : '成本价:'">
                        <span>{{ fenToYuan(row.costPrice) }}</span>
                        <el-tag v-if="isB2B" class="ml-8px" effect="plain" size="small" type="info">
                          ERP 内部
                        </el-tag>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-col>
              </el-row>
              <el-row v-if="!isB2B">
                <el-col :span="24">
                  <el-row>
                    <el-col :span="8">
                      <el-form-item label="浏览量:">
                        <span>{{ row.browseCount }}</span>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="虚拟销量:">
                        <span>{{ row.virtualSalesCount }}</span>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-col>
              </el-row>
            </el-form>
          </template>
        </el-table-column>
        <el-table-column label="商品信息" min-width="280">
          <template #default="{ row }">
            <div class="product-info-cell">
              <el-image
                fit="cover"
                :src="row.picUrl"
                class="product-info-cell__image"
                @click="imagePreview(row.picUrl)"
              />
              <div class="product-info-cell__copy">
                <el-tooltip effect="dark" :content="row.name" placement="top">
                  <strong>{{ row.name }}</strong>
                </el-tooltip>
                <small>
                  SPU #{{ row.id }} ·
                  {{ row.status < 0 ? '回收站' : row.status ? '已上架' : '已下架' }}
                </small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="showPriceColumn"
          align="center"
          label="价格"
          min-width="130"
          prop="price"
        >
          <template #default="{ row }"> ¥ {{ fenToYuan(row.price) }}</template>
        </el-table-column>
        <el-table-column
          v-if="inventoryEnabled"
          align="center"
          label="库存"
          min-width="90"
          prop="stock"
        />
        <el-table-column align="center" :label="isB2B ? '商品 SKU' : 'ERP 编码'" min-width="145">
          <template #default="{ row }">{{ erpBySpuId[row.id]?.erpProductCode || '-' }}</template>
        </el-table-column>
        <el-table-column align="center" label="ERP 状态" min-width="135">
          <template #default="{ row }">
            <el-tag :type="erpStatusType(erpBySpuId[row.id]?.syncStatus)" effect="light">
              <span class="erp-status-label">
                <i aria-hidden="true"></i>
                {{ erpStatusLabel(erpBySpuId[row.id]?.syncStatus) }}
              </span>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="center" label="最后同步" min-width="160">
          <template #default="{ row }">{{ erpBySpuId[row.id]?.lastSyncedAt || '-' }}</template>
        </el-table-column>
        <el-table-column align="center" label="排序" min-width="70" prop="sort" />
        <el-table-column align="center" fixed="right" label="操作" width="116">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)"> 详情 </el-button>
            <el-dropdown trigger="click">
              <el-button aria-label="更多商品操作" class="product-more-button" text>
                <Icon icon="ep:more-filled" :size="17" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openFrontendPreview(row.id)">
                    <Icon icon="ep:view" />
                    前台预览
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-hasPermi="['product:spu:update']"
                    @click="handleErpSync(row.id)"
                  >
                    <Icon icon="ep:refresh" />
                    同步 ERP
                  </el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['product:spu:update']" @click="openForm(row.id)">
                    <Icon icon="ep:edit-pen" />
                    修改商品
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status >= 0"
                    v-hasPermi="['product:spu:update']"
                    @click="toggleSaleStatus(row)"
                  >
                    <Icon :icon="row.status ? 'ep:video-pause' : 'ep:video-play'" />
                    {{ row.status ? '下架商品' : '上架商品' }}
                  </el-dropdown-item>
                  <template v-if="queryParams.tabType === 4">
                    <el-dropdown-item
                      v-hasPermi="['product:spu:update']"
                      @click="handleStatus02Change(row, ProductSpuStatusEnum.DISABLE.status)"
                    >
                      <Icon icon="ep:refresh-left" />
                      恢复商品
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-hasPermi="['product:spu:delete']"
                      divided
                      @click="handleDelete(row.id)"
                    >
                      <Icon icon="ep:delete" />
                      删除商品
                    </el-dropdown-item>
                  </template>
                  <el-dropdown-item
                    v-else
                    v-hasPermi="['product:spu:update']"
                    divided
                    @click="handleStatus02Change(row, ProductSpuStatusEnum.RECYCLE.status)"
                  >
                    <Icon icon="ep:delete" />
                    移至回收站
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页 -->
      <Pagination
        v-model:limit="queryParams.pageSize"
        v-model:page="queryParams.pageNo"
        :total="total"
        @pagination="getList"
      />
    </ContentWrap>
  </template>
</template>
<script lang="ts" setup>
import { createImageViewer } from '@/components/ImageViewer'
import { defaultProps, handleTree, treeToString } from '@/utils/tree'
import { ProductSpuStatusEnum } from '@/utils/constants'
import { fenToYuan } from '@/utils'
import download from '@/utils/download'
import * as ProductSpuApi from '@/api/mall/product/spu'
import * as ProductCategoryApi from '@/api/mall/product/category'
import { useTenantBusinessProfile } from '@/hooks/web/useTenantBusinessProfile'

defineOptions({ name: 'ProductSpu' })

const message = useMessage() // 消息弹窗
const route = useRoute() // 路由
const { t } = useI18n() // 国际化
const { push } = useRouter() // 路由跳转

const loading = ref(false) // 列表的加载中
const exportLoading = ref(false) // 导出的加载中
const total = ref(0) // 列表的总页数
const list = ref<ProductSpuApi.Spu[]>([]) // 列表的数据
const erpBySpuId = ref<Record<number, ProductSpuApi.ErpIntegration>>({})
const {
  profileLoading,
  profileLoaded,
  profileError,
  isB2B,
  inventoryEnabled,
  productFieldState,
  loadTenantBusinessProfile
} = useTenantBusinessProfile()
const b2bPriceIsWebsite = computed(() => isB2B.value && productFieldState('price') === 'WEBSITE')
const showPriceColumn = computed(() => !isB2B.value || b2bPriceIsWebsite.value)
const tabCounts = ref([0, 0, 0, 0, 0])
const visibleTabTypes = computed(() => (inventoryEnabled.value ? [0, 1, 2, 3, 4] : [0, 1, 4]))
const tabsData = computed(() =>
  visibleTabTypes.value.map((type) => ({
    type,
    name:
      type === 0
        ? inventoryEnabled.value
          ? '出售中'
          : '展示中'
        : type === 1
          ? inventoryEnabled.value
            ? '仓库中'
            : '未展示'
          : type === 2
            ? '已售罄'
            : type === 3
              ? '警戒库存'
              : '回收站',
    count: tabCounts.value[type]
  }))
)

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  tabType: 0,
  name: '',
  categoryId: undefined as any,
  createTime: undefined
}) // 查询参数
const queryFormRef = ref() // 搜索的表单Ref

/** 查询列表 */
const getList = async () => {
  if (!profileLoaded.value) return
  if (!inventoryEnabled.value && [2, 3].includes(Number(queryParams.value.tabType))) {
    queryParams.value.tabType = 0
  }
  loading.value = true
  try {
    const data = await ProductSpuApi.getSpuPage(queryParams.value)
    list.value = data.list
    total.value = data.total
    const integrations = await Promise.all(
      data.list.map(
        async (spu) => [spu.id, (await ProductSpuApi.getErpIntegration(spu.id!))[0]] as const
      )
    )
    erpBySpuId.value = Object.fromEntries(integrations.filter(([, value]) => value))
  } finally {
    loading.value = false
  }
}

/** 切换商品状态 */
const handleTabSelect = (tabType: number) => {
  queryParams.value.tabType = tabType
  queryParams.value.pageNo = 1
  getList()
}

const erpStatusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    SUCCESS: 'ERP 同步成功',
    PENDING: '等待同步',
    PROCESSING: '同步中',
    FAILED: '同步失败'
  }
  return status ? labels[status] || status : '未映射'
}

const erpStatusType = (status?: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING' || status === 'PROCESSING') return 'warning'
  return 'info'
}

const handleErpSync = async (spuId: number) => {
  await ProductSpuApi.syncErpIntegration(spuId)
  message.success('ERP 同步成功')
  await getList()
}

const handleErpSyncAll = async () => {
  await ProductSpuApi.syncAllErpIntegrations()
  message.success('ERP 全量同步成功')
  await getList()
}

/** 获得每个 Tab 的数量 */
const getTabsCount = async () => {
  const res = await ProductSpuApi.getTabsCount()
  for (let objName in res) {
    tabCounts.value[Number(objName)] = res[objName]
  }
}

/** 添加到仓库 / 回收站的状态 */
const handleStatus02Change = async (row: any, newStatus: number) => {
  try {
    // 二次确认
    const text = newStatus === ProductSpuStatusEnum.RECYCLE.status ? '加入到回收站' : '恢复到仓库'
    await message.confirm(`确认要"${row.name}"${text}吗？`)
    // 发起修改
    await ProductSpuApi.updateStatus({ id: row.id, status: newStatus })
    message.success(text + '成功')
    // 刷新 tabs 数据
    await getTabsCount()
    // 刷新列表
    await getList()
  } catch {}
}

/** 更新上架/下架状态 */
const handleStatusChange = async (row: any) => {
  try {
    // 二次确认
    const text = row.status ? '上架' : '下架'
    await message.confirm(`确认要${text}"${row.name}"吗？`)
    // 发起修改
    await ProductSpuApi.updateStatus({ id: row.id, status: row.status })
    message.success(text + '成功')
    // 刷新 tabs 数据
    await getTabsCount()
    // 刷新列表
    await getList()
  } catch {
    // 异常时，需要重置回之前的值
    row.status =
      row.status === ProductSpuStatusEnum.DISABLE.status
        ? ProductSpuStatusEnum.ENABLE.status
        : ProductSpuStatusEnum.DISABLE.status
  }
}

const toggleSaleStatus = async (row: any) => {
  row.status =
    row.status === ProductSpuStatusEnum.ENABLE.status
      ? ProductSpuStatusEnum.DISABLE.status
      : ProductSpuStatusEnum.ENABLE.status
  await handleStatusChange(row)
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await ProductSpuApi.deleteSpu(id)
    message.success(t('common.delSuccess'))
    // 刷新tabs数据
    await getTabsCount()
    // 刷新列表
    await getList()
  } catch {}
}

/** 商品图预览 */
const imagePreview = (imgUrl: string) => {
  createImageViewer({
    urlList: [imgUrl]
  })
}

/** 搜索按钮操作 */
const handleQuery = () => {
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 新增或修改 */
const openForm = (id?: number) => {
  // 修改
  if (typeof id === 'number') {
    push({ name: 'ProductSpuEdit', params: { id } })
    return
  }
  // 新增
  push({ name: 'ProductSpuAdd' })
}

/** 查看商品详情 */
const openDetail = (id: number) => {
  push({ name: 'ProductSpuDetail', params: { id } })
}

const getFurnitureWebBaseUrl = () =>
  String(import.meta.env.VITE_FURNITURE_WEB_URL || 'http://127.0.0.1:5173').replace(/\/+$/, '')

/** Preview the product detail page in the furniture web app. */
const openFrontendPreview = (id: number) => {
  window.open(`${getFurnitureWebBaseUrl()}/product/${id}`, '_blank')
}

/** 导出按钮操作 */
const handleExport = async () => {
  try {
    // 导出的二次确认
    await message.exportConfirm()
    // 发起导出
    exportLoading.value = true
    const data = await ProductSpuApi.exportSpu(queryParams.value)
    download.excel(data, '商品列表.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

/** 获取分类的节点的完整结构 */
const categoryList = ref() // 分类树
const formatCategoryName = (categoryId: number) => {
  return categoryList.value ? treeToString(categoryList.value, categoryId) : '-'
}

const pageInitialized = ref(false)

/** 激活时 */
onActivated(() => {
  if (pageInitialized.value && profileLoaded.value) {
    getList()
  }
})

/** 初始化 **/
onMounted(async () => {
  // 解析路由的 categoryId
  if (route.query.categoryId) {
    queryParams.value.categoryId = route.query.categoryId
  }
  try {
    await loadTenantBusinessProfile()
  } catch {
    return
  }
  if (!inventoryEnabled.value && [2, 3].includes(Number(queryParams.value.tabType))) {
    queryParams.value.tabType = 0
  }
  // 获得商品信息
  await getTabsCount()
  await getList()
  // 获得分类树
  const data = await ProductCategoryApi.getCategoryList({})
  categoryList.value = handleTree(data, 'id', 'parentId')
  pageInitialized.value = true
})
</script>
<style lang="scss" scoped>
.product-status-bar {
  display: flex;
  min-height: 42px;
  margin: -2px 0 14px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.product-status-tabs {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.product-status-tab {
  display: inline-flex;
  min-height: 36px;
  padding: 7px 14px;
  color: var(--furniture-admin-body);
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 8px;
  cursor: pointer;
  align-items: center;
  gap: 9px;
  transition:
    color 0.16s ease,
    border-color 0.16s ease,
    background-color 0.16s ease;
}

.product-status-tab span {
  font-size: 13px;
  font-weight: 550;
}

.product-status-tab strong {
  min-width: 18px;
  color: var(--furniture-admin-ink);
  font-size: 13px;
  font-weight: 680;
  text-align: center;
}

.product-status-tab:hover {
  border-color: #b9d4f7;
}

.product-status-tab.is-active {
  color: var(--furniture-admin-primary);
  background: #f0f6ff;
  border-color: #b9d4f7;
  box-shadow: 0 2px 7px rgb(23 107 219 / 8%);
}

.product-status-tab.is-active strong {
  color: var(--furniture-admin-primary);
}

.product-status-tab.is-warning strong {
  color: #d66c00;
}

.product-status-bar > p {
  flex: 0 0 auto;
  margin: 0;
  color: var(--furniture-admin-muted);
  font-size: 12px;
}

.product-filter-form {
  display: block !important;
}

.product-filter-fields {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr) minmax(320px, 1.4fr) auto;
  gap: 14px 18px;
  align-items: end;
}

.product-filter-fields :deep(.el-form-item) {
  display: grid;
  margin-bottom: 0;
}

.product-filter-fields :deep(.el-form-item__label) {
  width: auto !important;
  height: 24px;
  padding: 0;
  line-height: 20px;
  justify-content: flex-start;
}

.product-filter-fields :deep(.el-form-item__content) {
  width: 100%;
  margin-left: 0 !important;
}

.product-filter-fields :deep(.el-input),
.product-filter-fields :deep(.el-cascader),
.product-filter-fields :deep(.el-date-editor) {
  width: 100% !important;
}

.product-filter-submit {
  display: flex !important;
}

.product-filter-submit :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}

.product-filter-actions {
  display: flex;
  margin: 16px -17px -5px;
  padding: 13px 17px;
  border-top: 1px solid var(--furniture-admin-border);
  flex-wrap: wrap;
  gap: 8px;
}

.product-filter-actions :deep(.el-button) {
  margin-left: 0;
}

.product-info-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.product-info-cell__image {
  width: 58px;
  height: 42px;
  flex: 0 0 auto;
  cursor: zoom-in;
  object-fit: cover;
}

.product-info-cell__copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.product-info-cell__copy strong {
  overflow: hidden;
  color: var(--furniture-admin-ink);
  font-size: 13px;
  font-weight: 560;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-info-cell__copy small {
  color: var(--furniture-admin-muted);
  font-size: 11px;
}

.erp-status-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.erp-status-label i {
  width: 6px;
  height: 6px;
  background: currentcolor;
  border-radius: 50%;
}

.product-more-button {
  width: 30px;
  min-height: 30px !important;
  margin-left: 5px !important;
  padding: 0 !important;
  color: var(--furniture-admin-body) !important;
  border: 1px solid var(--furniture-admin-border) !important;
  border-radius: 6px !important;
}

.product-more-button:hover {
  color: var(--furniture-admin-primary) !important;
  background: #f4f8fe !important;
  border-color: #b9d4f7 !important;
}

.spu-table-expand {
  padding-left: 42px;

  :deep(.el-form-item__label) {
    width: 82px;
    font-weight: bold;
    color: #99a9bf;
  }
}

@media (width <= 1360px) {
  .product-filter-fields {
    grid-template-columns: repeat(2, minmax(240px, 1fr));
  }

  .product-filter-submit {
    align-self: end;
  }
}

@media (width <= 760px) {
  .product-status-bar {
    display: grid;
  }

  .product-status-tabs {
    flex-wrap: nowrap;
    padding-bottom: 4px;
    overflow-x: auto;
  }

  .product-status-tab {
    flex: 0 0 auto;
  }

  .product-filter-fields {
    grid-template-columns: 1fr;
  }

  .product-filter-submit :deep(.el-form-item__content) {
    justify-content: flex-start;
  }
}
</style>
