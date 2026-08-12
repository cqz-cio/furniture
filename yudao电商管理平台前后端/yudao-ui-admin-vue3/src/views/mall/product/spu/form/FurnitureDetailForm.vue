<template>
  <el-form
    ref="formRef"
    :model="detailConfig"
    :rules="detailRules"
    label-width="140px"
    :disabled="isDetail"
  >
    <el-alert
      class="mb-16px"
      :title="
        isSimplifiedSeating
          ? '座椅类精简模式只保留老网页有依据的公开参数和详情折叠内容；户外模板字段不会保存。'
          : isB2B
            ? '以下字段直接对应 B2B 家具网站商品详情页；Product type 自动跟随“基础设置 → 商品分类”，不会自动带入示例内容。公开参数必须填写，其他可选内容可保持为空。'
            : '以下配置控制家具网站商品详情页；未填写的可选内容保持为空。'
      "
      type="info"
      :closable="false"
    />

    <el-divider content-position="left">Product information</el-divider>
    <el-alert
      class="mb-16px"
      :title="
        isSimplifiedSeating
          ? '座椅类商品只保留老网页已有的 Item No.、Material、Size、Color、Service、Sample、Packing；不填写 Finish。'
          : 'Item No. 是客户可见的型号，不等同于 ERP 自动 SKU。Color 表示颜色，Finish 仅表示涂装、漆面等表面处理；Size 统一使用 cm。'
      "
      type="success"
      :closable="false"
    />
    <el-form-item label="Item No." prop="itemNo">
      <el-input v-model="detailConfig.itemNo" class="w-100!" maxlength="64" placeholder="VZC0099" />
    </el-form-item>
    <el-form-item label="Material" prop="material">
      <el-input
        v-model="detailConfig.material"
        class="w-100!"
        maxlength="255"
        placeholder="Solid oak and oak veneer"
      />
    </el-form-item>
    <el-form-item label="Size" prop="dimension">
      <div class="product-dimension-grid">
        <el-select v-model="detailConfig.dimension.shape" aria-label="Dimension shape">
          <el-option label="Rectangular" value="rectangular" />
          <el-option label="Round" value="round" />
        </el-select>
        <template v-if="detailConfig.dimension.shape === 'round'">
          <div class="product-number-field">
            <span>Diameter</span>
            <el-input-number
              v-model="detailConfig.dimension.diameter"
              :min="0.1"
              :max="1000"
              :precision="1"
              :step="1"
              controls-position="right"
            />
            <span>cm</span>
          </div>
        </template>
        <template v-else>
          <div class="product-number-field">
            <span>W</span>
            <el-input-number
              v-model="detailConfig.dimension.width"
              :min="0.1"
              :max="1000"
              :precision="1"
              :step="1"
              controls-position="right"
            />
            <span>cm</span>
          </div>
          <div class="product-number-field">
            <span>D</span>
            <el-input-number
              v-model="detailConfig.dimension.depth"
              :min="0.1"
              :max="1000"
              :precision="1"
              :step="1"
              controls-position="right"
            />
            <span>cm</span>
          </div>
        </template>
        <div class="product-number-field">
          <span>H</span>
          <el-input-number
            v-model="detailConfig.dimension.height"
            :min="0.1"
            :max="1000"
            :precision="1"
            :step="1"
            controls-position="right"
          />
          <span>cm</span>
        </div>
      </div>
    </el-form-item>
    <el-form-item label="Color" prop="color">
      <el-input
        v-model="detailConfig.color"
        class="w-100!"
        maxlength="255"
        placeholder="As shown or according to the customer's request"
      />
    </el-form-item>
    <el-form-item v-if="!isSimplifiedSeating" label="Finish" prop="finish">
      <el-input
        v-model="detailConfig.finish"
        class="w-100!"
        maxlength="255"
        placeholder="Natural oak, clear matte lacquer"
      />
    </el-form-item>
    <el-form-item label="Service" prop="service">
      <el-input
        v-model="detailConfig.service"
        class="w-100!"
        maxlength="255"
        placeholder="OEM & ODM"
      />
    </el-form-item>
    <el-form-item label="Sample" prop="sample">
      <el-select
        v-model="detailConfig.sample"
        allow-create
        class="w-100!"
        filterable
        placeholder="Available"
      >
        <el-option label="Available" value="Available" />
        <el-option label="Made to order" value="Made to order" />
        <el-option label="Unavailable" value="Unavailable" />
      </el-select>
    </el-form-item>
    <el-form-item label="Packing" prop="packing">
      <div class="product-packing-grid">
        <el-select
          v-model="detailConfig.packing.method"
          allow-create
          filterable
          placeholder="Packing method"
        >
          <el-option label="Carton packing" value="Carton packing" />
          <el-option label="Knock-down carton" value="Knock-down carton" />
          <el-option label="Fully assembled carton" value="Fully assembled carton" />
          <el-option label="Protective export carton" value="Protective export carton" />
          <el-option label="Mail-order carton" value="Mail-order carton" />
        </el-select>
        <el-input-number
          v-model="detailConfig.packing.itemQuantity"
          :min="1"
          :max="999"
          :precision="0"
          controls-position="right"
          aria-label="Item quantity"
        />
        <el-select v-model="detailConfig.packing.itemUnit" aria-label="Item unit">
          <el-option label="pc" value="pc" />
          <el-option label="set" value="set" />
        </el-select>
        <span>/</span>
        <el-input-number
          v-model="detailConfig.packing.cartonQuantity"
          :min="1"
          :max="999"
          :precision="0"
          controls-position="right"
          aria-label="Carton quantity"
        />
        <span>carton(s)</span>
      </div>
    </el-form-item>

    <el-form-item label="Product type">
      <div class="w-80!">
        <el-input
          :model-value="selectedCategoryName"
          class="w-100!"
          placeholder="请先在基础设置中选择商品分类"
          readonly
        />
        <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
          自动跟随“基础设置 → 商品分类”；如需调整，请回到基础设置修改。不会自动填充或修改其他内容。
        </div>
      </div>
    </el-form-item>

    <el-alert
      v-if="isSimplifiedSeating"
      class="mb-16px"
      title="座椅类精简字段"
      description="Chair、Dining Chair、Bar Stool 等座椅商品的 Item No.、Material、Size、Color、Service、Sample、Packing 会进入 PRODUCT INFORMATION；Feature、Application、Design Style 只放在 DETAILS。若原规格仅写 2 Pcs/Ctn，Packing method 可留空。"
      type="success"
      :closable="false"
      show-icon
    />

    <template v-if="!isSimplifiedSeating">
      <el-form-item label="Collection">
        <el-input
          v-model="detailConfig.collection"
          class="w-80!"
          placeholder="Optional collection name"
        />
      </el-form-item>

      <el-form-item label="Hero note">
        <el-input
          v-model="detailConfig.heroNote"
          class="w-80!"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="Optional product note shown below the gallery"
        />
      </el-form-item>

      <el-divider content-position="left">Fabric / Finish selector</el-divider>
      <el-form-item label="Selector label">
        <el-input
          v-model="detailConfig.fabricSelector.label"
          class="w-80!"
          placeholder="Optional selector heading"
        />
      </el-form-item>
      <el-form-item label="Counts">
        <div class="flex gap-12px">
          <el-input-number v-model="detailConfig.fabricSelector.stockedCount" :min="0" />
          <el-input-number v-model="detailConfig.fabricSelector.specialOrderCount" :min="0" />
        </div>
      </el-form-item>
      <el-form-item label="Swatches">
        <div class="admin-detail-list">
          <div
            v-for="(swatch, index) in detailConfig.fabricSelector.swatches"
            :key="index"
            class="admin-detail-row"
          >
            <el-input v-model="swatch.label" placeholder="Label" />
            <el-color-picker v-model="swatch.swatch" />
            <el-button @click="removeSwatch(index)">Remove</el-button>
          </div>
          <el-button @click="addSwatch">Add swatch</el-button>
        </div>
      </el-form-item>

      <el-divider content-position="left">Highlights</el-divider>
      <el-form-item label="Highlights">
        <div class="admin-detail-list">
          <div v-for="(_, index) in detailConfig.highlights" :key="index" class="admin-detail-row">
            <el-input v-model="detailConfig.highlights[index]" placeholder="Product highlight" />
            <el-button @click="removeHighlight(index)">Remove</el-button>
          </div>
          <el-button @click="addHighlight">Add highlight</el-button>
        </div>
      </el-form-item>

      <el-divider content-position="left">Option groups</el-divider>
      <div
        v-for="(group, groupIndex) in detailConfig.optionGroups"
        :key="groupIndex"
        class="admin-detail-panel"
      >
        <div class="admin-detail-row">
          <el-input v-model="group.key" placeholder="key" />
          <el-input v-model="group.label" placeholder="Label" />
          <el-button @click="removeOptionGroup(groupIndex)">Remove group</el-button>
        </div>
        <el-input v-model="group.helper" class="mt-8px" placeholder="Helper text" />
        <el-input
          class="mt-8px"
          type="textarea"
          :model-value="optionValuesText(group)"
          placeholder="One option per line"
          @update:model-value="(value) => updateOptionValues(group, value)"
        />
      </div>
      <el-form-item label=" ">
        <el-button @click="addOptionGroup">Add option group</el-button>
      </el-form-item>
    </template>

    <el-divider content-position="left">Accordion sections</el-divider>
    <div
      v-for="(section, sectionIndex) in detailConfig.accordions"
      :key="sectionIndex"
      class="admin-detail-panel"
    >
      <div class="admin-detail-row">
        <el-input v-model="section.title" placeholder="DIMENSIONS" />
        <el-button @click="removeAccordion(sectionIndex)">Remove section</el-button>
      </div>
      <div v-for="(row, rowIndex) in section.rows" :key="rowIndex" class="admin-detail-row mt-8px">
        <el-input v-model="row[0]" placeholder="Label" />
        <el-input v-model="row[1]" placeholder="Value" />
        <el-button @click="removeAccordionRow(section, rowIndex)">Remove</el-button>
      </div>
      <el-button class="mt-8px" @click="addAccordionRow(section)">Add row</el-button>
    </div>
    <el-form-item label=" ">
      <el-button @click="addAccordion">Add accordion section</el-button>
    </el-form-item>

    <template v-if="!isSimplifiedSeating">
      <el-divider content-position="left">Related links</el-divider>
      <div
        v-for="(link, linkIndex) in detailConfig.relatedLinks"
        :key="linkIndex"
        class="admin-detail-panel"
      >
        <div class="admin-detail-row">
          <el-input v-model="link.label" placeholder="Link label" />
          <el-input v-model="link.href" placeholder="/collection or https://..." />
          <el-button @click="removeRelatedLink(linkIndex)">Remove</el-button>
        </div>
      </div>
      <el-form-item label=" ">
        <el-button @click="addRelatedLink">Add related link</el-button>
      </el-form-item>
    </template>

    <el-alert
      v-if="isB2B"
      :closable="false"
      class="mb-16px"
      description="关联商品由当前商品分类自动匹配，无需在此重复维护。"
      show-icon
      title="Related products"
      type="success"
    />
  </el-form>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, unref, watch } from 'vue'
import type { PropType } from 'vue'
import * as ProductCategoryApi from '@/api/mall/product/category'
import type { CategoryVO } from '@/api/mall/product/category'
import type { Spu } from '@/api/mall/product/spu'
import { useMessage } from '@/hooks/web/useMessage'
import { propTypes } from '@/utils/propTypes'

defineOptions({ name: 'ProductFurnitureDetailForm' })

type Swatch = { label: string; swatch: string }
type OptionGroup = { key: string; label: string; helper: string; values: Array<string | Swatch> }
type AccordionSection = { title: string; rows: string[][] }
type Dimension = {
  shape: 'rectangular' | 'round'
  width: number | null
  depth: number | null
  diameter: number | null
  height: number | null
  unit: 'cm'
}
type Packing = {
  method: string
  itemQuantity: number
  itemUnit: 'pc' | 'set'
  cartonQuantity: number
}
type DetailConfig = {
  itemNo: string
  material: string
  color: string
  finish: string
  dimension: Dimension
  service: string
  sample: string
  packing: Packing
  productType: string
  collection: string
  heroNote: string
  fabricSelector: {
    stockedCount: number
    specialOrderCount: number
    label: string
    swatches: Swatch[]
  }
  highlights: string[]
  optionGroups: OptionGroup[]
  accordions: AccordionSection[]
  relatedLinks: { label: string; href: string }[]
}

const props = defineProps({
  propFormData: {
    type: Object as PropType<Spu>,
    default: () => {}
  },
  isDetail: propTypes.bool.def(false),
  businessMode: propTypes.string.def('B2C')
})

const isB2B = computed(() => props.businessMode === 'B2B')
const emit = defineEmits(['update:activeName'])
const formRef = ref()
const message = useMessage()

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))

const createEmptyConfig = (): DetailConfig => ({
  itemNo: '',
  material: '',
  color: '',
  finish: '',
  dimension: {
    shape: 'rectangular',
    width: null,
    depth: null,
    diameter: null,
    height: null,
    unit: 'cm'
  },
  service: '',
  sample: '',
  packing: {
    method: '',
    itemQuantity: 1,
    itemUnit: 'pc',
    cartonQuantity: 1
  },
  productType: '',
  collection: '',
  heroNote: '',
  fabricSelector: {
    stockedCount: 0,
    specialOrderCount: 0,
    label: '',
    swatches: []
  },
  highlights: [],
  optionGroups: [],
  accordions: [],
  relatedLinks: []
})

const detailConfig = reactive<DetailConfig>(createEmptyConfig())
const categoryList = ref<CategoryVO[]>([])

const categoryProductTypeAliases: Record<string, string> = {
  sofas: 'sofa',
  'single-sofas': 'single-sofa',
  'lounge-chairs': 'lounge-chair',
  chairs: 'chair',
  'dining-chairs': 'dining-chair',
  'bar-stools': 'bar-stool',
  'bar-counter-stools': 'bar-stool',
  'bar-and-counter-stools': 'bar-stool',
  ottomans: 'ottoman',
  beds: 'bed',
  benches: 'bed-bench',
  nightstands: 'nightstand',
  dressers: 'dresser',
  wardrobes: 'wardrobe',
  vanities: 'vanity',
  desks: 'desk',
  'dining-tables': 'dining-table',
  'round-tables': 'round-table',
  'coffee-tables': 'coffee-table',
  'side-tables': 'side-table',
  'media-consoles': 'media-console',
  sideboards: 'sideboard',
  lighting: 'lighting',
  rugs: 'rug'
}

const normalizeCategorySlug = (name: string) =>
  name
    .trim()
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')

const categoryToProductType = (name: string) => {
  const slug = normalizeCategorySlug(name)
  return categoryProductTypeAliases[slug] || slug
}

const selectedCategory = computed(() => {
  const categoryId = Number(props.propFormData?.categoryId)
  return categoryList.value.find((category) => Number(category.id) === categoryId)
})
const selectedCategoryName = computed(() => selectedCategory.value?.name || '')
const selectedProductType = computed(
  () =>
    (selectedCategory.value ? categoryToProductType(selectedCategory.value.name) : '') ||
    detailConfig.productType
)
const simplifiedSeatingTypes = new Set(['chair', 'dining-chair', 'bar-stool', 'lounge-chair'])
const isSimplifiedSeating = computed(() => simplifiedSeatingTypes.has(selectedProductType.value))

const refreshCategoryList = async () => {
  categoryList.value = await ProductCategoryApi.getCategoryList({})
}

onMounted(refreshCategoryList)

const positiveNumberOrNull = (value: unknown): number | null => {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : null
}

const positiveInteger = (value: unknown, fallback = 1): number => {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : fallback
}

const normalizeConfig = (config?: Partial<DetailConfig>): DetailConfig => {
  const empty = createEmptyConfig()
  const dimension = config?.dimension || empty.dimension
  const rawPacking = config?.packing as Packing | string | undefined
  const packing =
    typeof rawPacking === 'string'
      ? { ...empty.packing, method: rawPacking }
      : rawPacking || empty.packing
  return {
    ...empty,
    ...(config || {}),
    itemNo: typeof config?.itemNo === 'string' ? config.itemNo : '',
    material: typeof config?.material === 'string' ? config.material : '',
    color: typeof config?.color === 'string' ? config.color : '',
    finish: typeof config?.finish === 'string' ? config.finish : '',
    dimension: {
      shape: dimension.shape === 'round' ? 'round' : 'rectangular',
      width: positiveNumberOrNull(dimension.width),
      depth: positiveNumberOrNull(dimension.depth),
      diameter: positiveNumberOrNull(dimension.diameter),
      height: positiveNumberOrNull(dimension.height),
      unit: 'cm'
    },
    service: typeof config?.service === 'string' ? config.service : '',
    sample: typeof config?.sample === 'string' ? config.sample : '',
    packing: {
      method: typeof packing.method === 'string' ? packing.method : '',
      itemQuantity: positiveInteger(packing.itemQuantity),
      itemUnit: packing.itemUnit === 'set' ? 'set' : 'pc',
      cartonQuantity: positiveInteger(packing.cartonQuantity)
    },
    fabricSelector: {
      ...empty.fabricSelector,
      ...(config?.fabricSelector || {})
    },
    highlights: config?.highlights || [],
    optionGroups: config?.optionGroups || [],
    accordions: config?.accordions || [],
    relatedLinks: config?.relatedLinks || []
  }
}

watch(
  () => (props.propFormData as any)?.detailConfig,
  (config) => {
    Object.assign(detailConfig, normalizeConfig(config as Partial<DetailConfig>))
  },
  { immediate: true }
)

const validateText =
  (label: string) => (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (typeof value === 'string' && value.trim()) {
      callback()
      return
    }
    callback(new Error(`${label} is required`))
  }

const validateDimension = (_rule: unknown, value: Dimension, callback: (error?: Error) => void) => {
  const commonValid = value?.unit === 'cm' && positiveNumberOrNull(value?.height) !== null
  const footprintValid =
    value?.shape === 'round'
      ? positiveNumberOrNull(value?.diameter) !== null
      : positiveNumberOrNull(value?.width) !== null && positiveNumberOrNull(value?.depth) !== null
  callback(commonValid && footprintValid ? undefined : new Error('Enter complete dimensions in cm'))
}

const validatePacking = (_rule: unknown, value: Packing, callback: (error?: Error) => void) => {
  const valid =
    (isSimplifiedSeating.value || Boolean(value?.method?.trim())) &&
    positiveInteger(value?.itemQuantity, 0) > 0 &&
    ['pc', 'set'].includes(value?.itemUnit) &&
    positiveInteger(value?.cartonQuantity, 0) > 0
  callback(valid ? undefined : new Error('Enter the packing method, item quantity and cartons'))
}

const detailRules = computed(() =>
  isB2B.value
    ? {
        ...(isSimplifiedSeating.value
          ? {
              itemNo: [{ validator: validateText('Item No.'), trigger: 'blur' }],
              color: [{ validator: validateText('Color'), trigger: 'blur' }],
              service: [{ validator: validateText('Service'), trigger: 'blur' }],
              sample: [{ validator: validateText('Sample'), trigger: 'change' }]
            }
          : {
              finish: [{ validator: validateText('Finish'), trigger: 'blur' }]
            }),
        material: [{ validator: validateText('Material'), trigger: 'blur' }],
        dimension: [{ validator: validateDimension, trigger: 'change' }],
        packing: [{ validator: validatePacking, trigger: 'change' }]
      }
    : {}
)

const optionValuesText = (group: OptionGroup) =>
  group.values.map((value) => (typeof value === 'string' ? value : value.label)).join('\n')
const updateOptionValues = (group: OptionGroup, value: string) => {
  group.values = value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

const addSwatch = () =>
  detailConfig.fabricSelector.swatches.push({ label: 'New finish', swatch: '#d8d1c4' })
const removeSwatch = (index: number) => detailConfig.fabricSelector.swatches.splice(index, 1)
const addHighlight = () => detailConfig.highlights.push('New product highlight')
const removeHighlight = (index: number) => detailConfig.highlights.splice(index, 1)
const addOptionGroup = () =>
  detailConfig.optionGroups.push({
    key: 'custom',
    label: 'Custom option',
    helper: '',
    values: ['Option']
  })
const removeOptionGroup = (index: number) => detailConfig.optionGroups.splice(index, 1)
const addAccordion = () =>
  detailConfig.accordions.push({ title: 'DETAILS', rows: [['Label', 'Value']] })
const removeAccordion = (index: number) => detailConfig.accordions.splice(index, 1)
const addAccordionRow = (section: AccordionSection) => section.rows.push(['Label', 'Value'])
const removeAccordionRow = (section: AccordionSection, index: number) =>
  section.rows.splice(index, 1)
const addRelatedLink = () =>
  detailConfig.relatedLinks.push({ label: 'Explore the collection', href: '/products' })
const removeRelatedLink = (index: number) => detailConfig.relatedLinks.splice(index, 1)

const validate = async () => {
  try {
    if (categoryList.value.length === 0) {
      await refreshCategoryList()
    }
    await unref(formRef)?.validate()
    const normalized = normalizeConfig(detailConfig)
    normalized.productType = selectedProductType.value
    if (!normalized.productType) {
      throw new Error('Product category is required')
    }
    normalized.itemNo = normalized.itemNo.trim()
    normalized.material = normalized.material.trim()
    normalized.color = normalized.color.trim()
    normalized.finish = normalized.finish.trim()
    normalized.service = normalized.service.trim()
    normalized.sample = normalized.sample.trim()
    normalized.packing.method = normalized.packing.method.trim()
    if (simplifiedSeatingTypes.has(normalized.productType)) {
      normalized.finish = ''
      normalized.collection = ''
      normalized.heroNote = ''
      normalized.fabricSelector = {
        stockedCount: 0,
        specialOrderCount: 0,
        label: '',
        swatches: []
      }
      normalized.highlights = []
      normalized.optionGroups = []
      normalized.relatedLinks = []
    }
    ;(props.propFormData as any).detailConfig = clone(normalized)
  } catch (e) {
    message.error('Furniture detail configuration is incomplete')
    emit('update:activeName', 'furnitureDetail')
    throw e
  }
}

defineExpose({ validate })
</script>

<style scoped>
.admin-detail-list {
  display: grid;
  gap: 8px;
  width: min(760px, 100%);
}

.admin-detail-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.admin-detail-panel {
  display: grid;
  gap: 8px;
  margin: 0 0 16px 140px;
  width: min(760px, calc(100% - 140px));
  border: 1px solid var(--el-border-color);
  padding: 12px;
}

.product-dimension-grid,
.product-packing-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  width: min(980px, 100%);
}

.product-dimension-grid > .el-select,
.product-packing-grid > .el-select:first-child {
  width: 220px;
}

.product-packing-grid > .el-select:not(:first-child) {
  width: 90px;
}

.product-number-field {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
</style>
