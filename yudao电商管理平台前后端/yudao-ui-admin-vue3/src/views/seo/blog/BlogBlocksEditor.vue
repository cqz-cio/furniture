<script setup lang="ts">
import type { WebsiteBlogBlock } from '@/api/seo/blog'
const props = defineProps<{ modelValue: WebsiteBlogBlock[] }>()
const emit = defineEmits<{ 'update:modelValue': [blocks: WebsiteBlogBlock[]] }>()
function change(index: number, key: string, value: unknown) {
  const blocks = JSON.parse(JSON.stringify(props.modelValue))
  Object.assign(blocks[index], { [key]: value })
  emit('update:modelValue', blocks)
}
function remove(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index)
  )
}
function move(index: number, offset: number) {
  const blocks = JSON.parse(JSON.stringify(props.modelValue)),
    next = index + offset
  if (next < 0 || next >= blocks.length) return
  ;[blocks[index], blocks[next]] = [blocks[next], blocks[index]]
  emit('update:modelValue', blocks)
}
function add(type: string) {
  const block: WebsiteBlogBlock =
    type === 'heading'
      ? { type: 'heading', text: '', level: 2 }
      : type === 'list'
        ? { type: 'list', items: [''], ordered: false }
        : type === 'image'
          ? { type: 'image', src: '', alt: '' }
          : { type: 'paragraph', text: '' }
  emit('update:modelValue', [...props.modelValue, block])
}
</script>
<template>
  <p>完整正文块：{{ modelValue.length }} 个。保留原有段落、标题、列表和图片，修改后保存为草稿。</p>
  <el-collapse>
    <el-collapse-item
      v-for="(block, index) in modelValue"
      :key="index"
      :name="index"
      :title="`${index + 1}. ${{ paragraph: '段落', heading: '标题', list: '列表', image: '图片' }[block.type]} · ${'text' in block ? block.text.slice(0, 60) : block.type === 'image' ? block.alt : block.items[0]?.slice(0, 60) || ''}`"
    >
      <template v-if="block.type === 'paragraph' || block.type === 'heading'"
        ><el-input
          :model-value="block.text"
          type="textarea"
          :rows="4"
          :maxlength="16000"
          @update:model-value="(value) => change(index, 'text', value)" /><el-input-number
          v-if="block.type === 'heading'"
          :model-value="block.level"
          :min="2"
          :max="6"
          class="mt-8px"
          aria-label="标题级别"
          @update:model-value="(value) => change(index, 'level', value)"
      /></template>
      <template v-else-if="block.type === 'list'"
        ><el-input
          :model-value="block.items.join('\n')"
          type="textarea"
          :rows="5"
          placeholder="每行一个列表项"
          @update:model-value="(value) => change(index, 'items', value.split('\n'))"
        /><el-checkbox
          :model-value="block.ordered || false"
          @update:model-value="(value) => change(index, 'ordered', value)"
          >编号列表</el-checkbox
        ></template
      >
      <template v-else-if="block.type === 'image'"
        ><el-input
          :model-value="block.src"
          placeholder="/assets/… 图片路径"
          @update:model-value="(value) => change(index, 'src', value)" /><el-input
          :model-value="block.alt"
          placeholder="图片说明"
          maxlength="240"
          class="mt-8px"
          @update:model-value="(value) => change(index, 'alt', value)"
      /></template>
      <div class="mt-12px"
        ><el-button size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button
        ><el-button size="small" :disabled="index === modelValue.length - 1" @click="move(index, 1)"
          >下移</el-button
        ><el-button size="small" type="danger" plain @click="remove(index)"
          >删除内容块</el-button
        ></div
      >
    </el-collapse-item>
  </el-collapse>
  <el-dropdown class="mt-12px" @command="add"
    ><el-button>添加正文块</el-button
    ><template #dropdown
      ><el-dropdown-menu
        ><el-dropdown-item command="paragraph">段落</el-dropdown-item
        ><el-dropdown-item command="heading">标题</el-dropdown-item
        ><el-dropdown-item command="list">列表</el-dropdown-item
        ><el-dropdown-item command="image">图片</el-dropdown-item></el-dropdown-menu
      ></template
    ></el-dropdown
  >
</template>
