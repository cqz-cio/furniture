<template>
  <el-form ref="formRef" :model="detailConfig" label-width="140px" :disabled="isDetail">
    <el-alert
      class="mb-16px"
      :title="
        isB2B
          ? '以下字段直接对应 B2B 家具网站商品详情页；留空时使用前台模板默认值。'
          : '以下配置控制家具网站商品详情页；留空时使用前台模板默认值。'
      "
      type="info"
      :closable="false"
    />

    <el-form-item label="Product type">
      <el-select v-model="detailConfig.productType" class="w-80!" @change="applyTemplate">
        <el-option label="Bed" value="bed" />
        <el-option label="Sofa" value="sofa" />
        <el-option label="Dining table" value="dining-table" />
        <el-option label="Chair" value="chair" />
        <el-option label="Lighting" value="lighting" />
      </el-select>
    </el-form-item>

    <el-form-item label="Collection">
      <el-input
        v-model="detailConfig.collection"
        class="w-80!"
        placeholder="CLOUD MODULAR COLLECTION"
      />
    </el-form-item>

    <el-form-item label="Hero note">
      <el-input
        v-model="detailConfig.heroNote"
        class="w-80!"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        placeholder="Shown in Sand Performance Linen..."
      />
    </el-form-item>

    <el-divider content-position="left">Fabric / Finish selector</el-divider>
    <el-form-item label="Selector label">
      <el-input v-model="detailConfig.fabricSelector.label" class="w-80!" />
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
import type { PropType } from 'vue'
import type { Spu } from '@/api/mall/product/spu'
import { propTypes } from '@/utils/propTypes'

defineOptions({ name: 'ProductFurnitureDetailForm' })

type Swatch = { label: string; swatch: string }
type OptionGroup = { key: string; label: string; helper: string; values: Array<string | Swatch> }
type AccordionSection = { title: string; rows: string[][] }
type DetailConfig = {
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

const templates: Record<string, DetailConfig> = {
  bed: {
    productType: 'bed',
    collection: 'LUXE BED COLLECTION',
    heroNote: 'Shown in Ivory Performance Linen with standard platform base.',
    fabricSelector: {
      stockedCount: 26,
      specialOrderCount: 191,
      label: 'SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS',
      swatches: [
        { label: 'Ivory Performance Linen', swatch: '#ebe5db' },
        { label: 'Warm Grey Basketweave', swatch: '#a29b91' }
      ]
    },
    highlights: ['Hand upholstered in premium performance fabric'],
    optionGroups: [
      {
        key: 'size',
        label: 'Size',
        helper: 'Choose the bed frame size.',
        values: ['Queen 1.5m', 'King 1.8m']
      },
      {
        key: 'fabric',
        label: 'Fabric',
        helper: 'Choose stocked or special order fabric.',
        values: ['Ivory Performance Linen']
      }
    ],
    accordions: [
      {
        title: 'DETAILS',
        rows: [['Design', 'Low, tailored upholstered bed with soft proportions']]
      },
      { title: 'DIMENSIONS', rows: [['King 1.8m', '198W x 214D x 112H cm']] },
      { title: 'MATERIALS', rows: [['Frame', 'Kiln-dried hardwood and engineered support']] },
      { title: 'CARE', rows: [['Fabric care', 'Vacuum with a soft brush attachment']] },
      { title: 'DELIVERY', rows: [['Lead time', 'Stocked options ship first']] }
    ],
    relatedLinks: [{ label: 'EXPLORE THE LUXE BED COLLECTION', href: '/products' }]
  },
  sofa: {
    productType: 'sofa',
    collection: 'CLOUD MODULAR COLLECTION',
    heroNote: 'Shown in Sand Performance Linen with classic depth and down-blend cushions.',
    fabricSelector: {
      stockedCount: 26,
      specialOrderCount: 191,
      label: 'SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS',
      swatches: [
        { label: 'Sand Performance Linen', swatch: '#c9b99d' },
        { label: 'Graphite Weave', swatch: '#2c2b29' }
      ]
    },
    highlights: ['Low, deep modular profile for relaxed living rooms'],
    optionGroups: [
      {
        key: 'configuration',
        label: 'Configuration',
        helper: 'Choose the seating layout.',
        values: ['Sofa', 'Sofa with chaise']
      },
      {
        key: 'fabric',
        label: 'Fabric',
        helper: 'Choose upholstery.',
        values: ['Sand Performance Linen']
      }
    ],
    accordions: [
      {
        title: 'DETAILS',
        rows: [['Design', 'Low modular frame with broad arms and loose back cushions']]
      },
      { title: 'DIMENSIONS', rows: [['Overall width', '220 / 260 / 300 cm']] },
      {
        title: 'MATERIALS',
        rows: [['Upholstery', 'Performance linen, velvet, leather or custom textile']]
      },
      { title: 'CARE', rows: [['Cushions', 'Rotate and fluff cushions to maintain shape']] },
      { title: 'DELIVERY', rows: [['Stocked fabric', 'Ready to ship in 3-7 days']] }
    ],
    relatedLinks: [{ label: 'EXPLORE THE CLOUD MODULAR COLLECTION', href: '/products' }]
  },
  'dining-table': {
    productType: 'dining-table',
    collection: 'MARBLE DINING COLLECTION',
    heroNote: 'Shown in White Carrara marble top with smoked oak pedestal base.',
    fabricSelector: {
      stockedCount: 8,
      specialOrderCount: 6,
      label: 'SELECT FROM 8 STONE TOPS AND 6 WOOD FINISHES',
      swatches: [
        { label: 'White Carrara Marble', swatch: '#eeeae2' },
        { label: 'Smoked Oak', swatch: '#5a3e2c' }
      ]
    },
    highlights: ['Statement dining table with stone or wood top options'],
    optionGroups: [
      {
        key: 'shape',
        label: 'Shape',
        helper: 'Select the dining room footprint.',
        values: ['Rectangular', 'Round']
      },
      {
        key: 'size',
        label: 'Size',
        helper: 'Controls seating capacity.',
        values: ['220 cm', '260 cm']
      }
    ],
    accordions: [
      { title: 'DETAILS', rows: [['Design', 'Sculptural pedestal dining table']] },
      { title: 'DIMENSIONS', rows: [['Seating capacity', '6 / 8 / 10 seats']] },
      { title: 'MATERIALS', rows: [['Stone', 'Marble, travertine or quartz-style finish']] },
      { title: 'CARE', rows: [['Stone care', 'Use coasters and wipe spills immediately']] },
      { title: 'DELIVERY', rows: [['Assembly', 'Base and top require on-site placement']] }
    ],
    relatedLinks: [{ label: 'EXPLORE THE MARBLE DINING COLLECTION', href: '/products' }]
  },
  chair: {
    productType: 'chair',
    collection: 'OUTDOOR LOUNGE COLLECTION',
    heroNote: 'Shown in Weathered Teak with Sand Perennials performance cushion.',
    fabricSelector: {
      stockedCount: 12,
      specialOrderCount: 48,
      label: 'SELECT FROM 12 STOCKED AND 48 SPECIAL ORDER OUTDOOR FABRICS',
      swatches: [
        { label: 'Sand Perennials', swatch: '#d7c7ae' },
        { label: 'Weathered Teak', swatch: '#9d8060' }
      ]
    },
    highlights: ['Outdoor lounge chair with weather-ready frame and cushions'],
    optionGroups: [
      {
        key: 'frame',
        label: 'Frame',
        helper: 'Choose frame finish.',
        values: ['Weathered Teak', 'Black Aluminum']
      },
      {
        key: 'fabric',
        label: 'Fabric',
        helper: 'Choose outdoor fabric.',
        values: ['Sand Perennials']
      }
    ],
    accordions: [
      { title: 'DETAILS', rows: [['Design', 'Relaxed outdoor lounge chair with angled back']] },
      { title: 'DIMENSIONS', rows: [['Seat height', '43 cm']] },
      { title: 'MATERIALS', rows: [['Frame', 'Weathered teak or powder-coated aluminum']] },
      { title: 'CARE', rows: [['Outdoor care', 'Cover or store cushions during heavy weather']] },
      { title: 'DELIVERY', rows: [['Delivery', 'Ships assembled or with minimal setup']] }
    ],
    relatedLinks: [{ label: 'EXPLORE THE OUTDOOR LOUNGE COLLECTION', href: '/products' }]
  },
  lighting: {
    productType: 'lighting',
    collection: 'ARCHITECTURAL LIGHTING COLLECTION',
    heroNote: 'Shown in Lacquered Brass with linen shade and warm dimmable bulb.',
    fabricSelector: {
      stockedCount: 6,
      specialOrderCount: 4,
      label: 'SELECT FROM 6 METAL FINISHES AND 4 SHADE OPTIONS',
      swatches: [
        { label: 'Lacquered Brass', swatch: '#b99a58' },
        { label: 'Matte Black', swatch: '#191919' }
      ]
    },
    highlights: ['Finish, shade, bulb and canopy are fixed lighting parameters'],
    optionGroups: [
      {
        key: 'size',
        label: 'Size',
        helper: 'Choose fixture size.',
        values: ['Small', 'Medium', 'Large']
      },
      {
        key: 'finish',
        label: 'Finish',
        helper: 'Choose metal finish.',
        values: ['Lacquered Brass', 'Matte Black']
      }
    ],
    accordions: [
      { title: 'DETAILS', rows: [['Design', 'Clean architectural lighting fixture']] },
      { title: 'DIMENSIONS', rows: [['Canopy', '13 cm diameter']] },
      { title: 'MATERIALS', rows: [['Body', 'Steel or brass with plated finish']] },
      { title: 'CARE', rows: [['Cleaning', 'Dust with a soft dry cloth']] },
      { title: 'DELIVERY', rows: [['Installation', 'Professional installation recommended']] }
    ],
    relatedLinks: [{ label: 'EXPLORE THE ARCHITECTURAL LIGHTING COLLECTION', href: '/products' }]
  }
}

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const detailConfig = reactive<DetailConfig>(clone(templates.bed))

const normalizeConfig = (config?: Partial<DetailConfig>): DetailConfig => {
  const type = config?.productType && templates[config.productType] ? config.productType : 'bed'
  return {
    ...clone(templates[type]),
    ...(config || {}),
    fabricSelector: {
      ...clone(templates[type].fabricSelector),
      ...(config?.fabricSelector || {})
    },
    highlights: config?.highlights || clone(templates[type].highlights),
    optionGroups: config?.optionGroups || clone(templates[type].optionGroups),
    accordions: config?.accordions || clone(templates[type].accordions),
    relatedLinks: config?.relatedLinks || clone(templates[type].relatedLinks)
  }
}

watch(
  () => (props.propFormData as any)?.detailConfig,
  (config) => {
    Object.assign(detailConfig, normalizeConfig(config as Partial<DetailConfig>))
  },
  { immediate: true }
)

const applyTemplate = () => {
  Object.assign(detailConfig, clone(templates[detailConfig.productType] || templates.bed))
}

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
    await unref(formRef)?.validate()
    ;(props.propFormData as any).detailConfig = clone(detailConfig)
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
</style>
