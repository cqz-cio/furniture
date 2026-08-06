<template>
  <div class="website-navigation-page">
    <section class="navigation-toolbar" aria-label="官网导航操作栏">
      <div class="navigation-toolbar__identity">
        <div class="navigation-toolbar__icon">
          <Icon icon="ep:guide" />
        </div>
        <div>
          <div class="navigation-toolbar__title">
            <strong>{{ navigationBrandLabel }}</strong>
            <el-tag size="small" type="info" effect="plain">English</el-tag>
          </div>
          <p>{{ navigationDescription }}</p>
        </div>
      </div>

      <div class="navigation-toolbar__actions">
        <div class="navigation-version">
          <span>草稿 v{{ draft?.version || '-' }}</span>
          <span class="navigation-version__divider"></span>
          <span>{{ publishedStatusLabel }}</span>
        </div>
        <el-button :disabled="busy" @click="openHistory">
          <Icon icon="ep:clock" class="mr-5px" />
          发布记录
        </el-button>
        <el-button :loading="refreshing" :disabled="busy" @click="refreshCategories">
          <Icon icon="ep:refresh" class="mr-5px" />
          {{ isOakvedNavigation ? '刷新可选分类' : '同步商品分类' }}
        </el-button>
        <el-button
          :loading="saving"
          :disabled="busy || !dirty"
          v-hasPermi="['seo:navigation:update']"
          @click="persistDraft(true)"
        >
          <Icon icon="ep:document-checked" class="mr-5px" />
          保存草稿
        </el-button>
        <el-button
          type="primary"
          plain
          :loading="previewLoading"
          :disabled="busy || !siteConfigured"
          v-hasPermi="['seo:navigation:preview']"
          @click="refreshInlinePreview"
        >
          <Icon icon="ep:view" class="mr-5px" />
          保存并预览
        </el-button>
        <el-button
          type="primary"
          :loading="publishing"
          :disabled="busy || changeSummary.length === 0"
          v-hasPermi="['seo:navigation:publish']"
          @click="publishDraft"
        >
          <Icon icon="ep:promotion" class="mr-5px" />
          发布到官网
        </el-button>
      </div>
    </section>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>
        <el-button type="primary" link @click="loadDraft">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="navigation-workspace">
      <main class="navigation-editor">
        <ContentWrap
          title="本次发布变化"
          message="发布前先确认访客会看到哪些变化。"
          surface="panel"
          :auto-title="false"
        >
          <div v-if="changeSummary.length" class="navigation-change-list">
            <div v-for="item in changeSummary" :key="item">
              <Icon icon="ep:circle-check" />
              <span>{{ item }}</span>
            </div>
          </div>
          <el-empty v-else :image-size="54" description="当前草稿与线上版本一致，无需重复发布" />
        </ContentWrap>
        <ContentWrap
          title="一级导航"
          :message="
            isOakvedNavigation
              ? '顺序与家具官网顶部一致；名称和显示状态可调整，真实地址由系统安全生成。'
              : '拖动调整官网顶部顺序；页面地址已固定，业务人员不用填写链接。'
          "
          surface="form"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <el-tag type="info" effect="plain">{{ primarySectionTag }}</el-tag>
            </div>
          </template>

          <p class="section-description">
            {{
              isOakvedNavigation
                ? '拖动调整 NEW、SHOP BY COLLECTIONS、BEDROOM 等顶部顺序；不需要填写原始 URL。'
                : '拖动调整官网顶部顺序；页面地址已固定，业务人员不用填写链接。'
            }}
          </p>

          <draggable
            v-model="primaryItems"
            item-key="itemKey"
            handle=".navigation-row__handle"
            ghost-class="navigation-row--ghost"
            :animation="180"
            class="navigation-list"
            @end="onPrimarySortEnd"
          >
            <template #item="{ element, index }">
              <article
                class="navigation-row"
                :class="{
                  'navigation-row--hidden': !element.visible,
                  'navigation-row--styled': element.styleVariant === 'SALE'
                }"
              >
                <button type="button" class="navigation-row__handle" aria-label="拖动调整顺序">
                  <Icon icon="ep:rank" />
                </button>
                <span class="navigation-row__order">{{ index + 1 }}</span>
                <div class="navigation-row__body">
                  <el-input
                    v-model="element.label"
                    maxlength="64"
                    :disabled="busy"
                    @input="markDirty"
                  />
                  <small>
                    <Icon icon="ep:link" />
                    {{ itemTargetLabel(element) }}
                  </small>
                </div>
                <el-tag
                  v-if="element.styleVariant === 'SALE'"
                  size="small"
                  type="danger"
                  effect="plain"
                >
                  SALE 强调
                </el-tag>
                <div class="navigation-row__visibility">
                  <span>{{ element.visible ? '官网显示' : '已隐藏' }}</span>
                  <el-switch v-model="element.visible" :disabled="busy" @change="markDirty" />
                </div>
              </article>
            </template>
          </draggable>
        </ContentWrap>

        <ContentWrap
          v-if="!isOakvedNavigation"
          title="Products 二级目录"
          message="名称可直接修改；确认保存后会同步更新商品中心和已发布官网。"
          surface="form"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <el-tag type="success" effect="plain">名称双向同步</el-tag>
            </div>
          </template>

          <p class="section-description">
            商品分类页和这里都可以改名；保存时确认一次，两边会立即使用同一个名称。
          </p>

          <div class="category-picker">
            <el-select
              v-model="categoryToAdd"
              filterable
              clearable
              :disabled="busy || availableCategoryOptions.length === 0"
              placeholder="选择一个商品分类"
              class="category-picker__select"
            >
              <el-option
                v-for="option in availableCategoryOptions"
                :key="option.id"
                :label="`${option.name} · ${option.publishedProductCount} 个在线商品`"
                :value="option.id"
              />
            </el-select>
            <el-button type="primary" plain :disabled="busy || !categoryToAdd" @click="addCategory">
              <Icon icon="ep:plus" class="mr-5px" />
              加入二级目录
            </el-button>
          </div>

          <div v-if="categoryItems.length === 0" class="category-empty">
            <Icon icon="ep:folder-opened" />
            <div>
              <strong>Products 暂无二级目录</strong>
              <p>从上方选择商品分类即可加入，不需要手工填写链接。</p>
            </div>
          </div>

          <draggable
            v-else
            v-model="categoryItems"
            item-key="itemKey"
            handle=".navigation-row__handle"
            ghost-class="navigation-row--ghost"
            :animation="180"
            class="navigation-list"
            @end="onCategorySortEnd"
          >
            <template #item="{ element, index }">
              <article
                class="navigation-row navigation-row--category"
                :class="{
                  'navigation-row--hidden': !element.visible,
                  'navigation-row--unavailable': !element.available
                }"
              >
                <button type="button" class="navigation-row__handle" aria-label="拖动调整顺序">
                  <Icon icon="ep:rank" />
                </button>
                <span class="navigation-row__order">{{ index + 1 }}</span>
                <div class="navigation-row__body">
                  <el-input
                    v-model="element.label"
                    maxlength="64"
                    show-word-limit
                    :disabled="busy || !element.available"
                    @input="markCategoryNameDirty(element)"
                  />
                  <small v-if="element.available">
                    <Icon icon="ep:goods" />
                    分类 ID {{ element.categoryId }} ·
                    {{ element.publishedProductCount || 0 }} 个在线商品 · 保存后同步商品中心
                  </small>
                  <small v-else class="is-error">
                    <Icon icon="ep:warning-filled" />
                    分类已停用或删除，保存后将从官网隐藏
                  </small>
                </div>
                <el-tag v-if="element.available" size="small" type="info" effect="plain">
                  双向同步
                </el-tag>
                <div class="navigation-row__visibility">
                  <span>{{ element.visible ? '官网显示' : '已隐藏' }}</span>
                  <el-switch
                    v-model="element.visible"
                    :disabled="busy || !element.available"
                    @change="markDirty"
                  />
                </div>
                <el-button
                  type="danger"
                  link
                  :disabled="busy"
                  aria-label="移出二级目录"
                  @click="removeCategory(index)"
                >
                  <Icon icon="ep:delete" />
                </el-button>
              </article>
            </template>
          </draggable>

          <div class="category-sync-note">
            <Icon icon="ep:info-filled" />
            <span> 新建商品分类不会自动出现在官网；在这里勾选并发布后才会上线，避免误展示。 </span>
          </div>
        </ContentWrap>

        <ContentWrap
          v-if="isOakvedNavigation"
          title="下拉导航（二、三级）"
          message="每个一级导航下最多配置两层；桌面端按左侧二级、右侧三级展示。"
          surface="form"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <el-tag type="success" effect="plain">最多三级</el-tag>
            </div>
          </template>

          <p class="section-description">
            二级和三级导航只需选择“固定页面、商品筛选或商品分类”；系统会生成真实官网地址。
          </p>

          <el-collapse v-model="oakvedExpandedKeys" class="oakved-navigation-tree">
            <el-collapse-item
              v-for="primary in primaryItems"
              :key="primary.itemKey"
              :name="primary.itemKey"
            >
              <template #title>
                <div class="oakved-tree-heading">
                  <div>
                    <strong>{{ primary.label }}</strong>
                    <small>{{ descendantCount(primary) }} 个下拉导航项</small>
                  </div>
                  <el-tag v-if="!primary.visible" size="small" type="info" effect="plain">
                    一级已隐藏
                  </el-tag>
                </div>
              </template>

              <div class="oakved-tree-panel">
                <div class="oakved-tree-panel__toolbar">
                  <span>二级导航</span>
                  <el-button
                    type="primary"
                    plain
                    size="small"
                    :disabled="busy"
                    @click.stop="addDropdownItem(primary)"
                  >
                    <Icon icon="ep:plus" class="mr-4px" />
                    新增二级导航
                  </el-button>
                </div>

                <div v-if="!(primary.children || []).length" class="oakved-tree-empty">
                  <Icon icon="ep:folder-opened" />
                  <span>当前一级导航没有下拉内容；官网只显示顶部链接。</span>
                </div>

                <draggable
                  v-else
                  v-model="primary.children"
                  item-key="itemKey"
                  handle=".oakved-node__handle"
                  ghost-class="navigation-row--ghost"
                  :animation="180"
                  class="oakved-node-list"
                  @end="onOakvedSortEnd"
                >
                  <template #item="{ element, index }">
                    <article
                      class="oakved-node"
                      :class="{ 'navigation-row--hidden': !element.visible }"
                    >
                      <div class="oakved-node__main">
                        <button
                          type="button"
                          class="oakved-node__handle"
                          aria-label="拖动调整二级导航顺序"
                        >
                          <Icon icon="ep:rank" />
                        </button>
                        <span class="navigation-row__order">{{ index + 1 }}</span>
                        <div class="oakved-node__name">
                          <el-input
                            v-model="element.label"
                            maxlength="64"
                            :disabled="busy || element.itemType === 'CATEGORY'"
                            @input="markDirty"
                          />
                          <small>{{ itemTargetLabel(element) }}</small>
                        </div>
                        <el-select
                          v-model="element.itemType"
                          class="oakved-node__type"
                          :disabled="busy"
                          aria-label="二级导航类型"
                          @change="onDropdownItemTypeChange(element)"
                        >
                          <el-option
                            v-for="option in oakvedItemTypeOptions"
                            :key="option.value"
                            :label="option.label"
                            :value="option.value"
                          />
                        </el-select>
                        <el-select
                          v-if="['ROUTE', 'FILTER'].includes(element.itemType)"
                          v-model="element.targetKey"
                          class="oakved-node__target"
                          filterable
                          :disabled="busy"
                          placeholder="选择跳转目标"
                          @change="markDirty"
                        >
                          <el-option
                            v-for="option in targetOptionsFor(element.itemType)"
                            :key="option.targetKey"
                            :label="`${option.label} · ${option.href}`"
                            :value="option.targetKey"
                          />
                        </el-select>
                        <el-select
                          v-else-if="element.itemType === 'CATEGORY'"
                          v-model="element.categoryId"
                          class="oakved-node__target"
                          filterable
                          :disabled="busy"
                          placeholder="选择商品分类"
                          @change="onDropdownCategoryChange(element)"
                        >
                          <el-option
                            v-for="option in categoryOptions"
                            :key="option.id"
                            :label="`${option.name} · ${option.publishedProductCount} 个在线商品`"
                            :value="option.id"
                          />
                        </el-select>
                        <div v-else class="oakved-node__directory-label">无需跳转目标</div>
                        <el-select
                          v-model="element.openMode"
                          class="oakved-node__open"
                          :disabled="busy || element.itemType === 'DIRECTORY'"
                          aria-label="打开方式"
                          @change="markDirty"
                        >
                          <el-option label="当前窗口" value="_self" />
                          <el-option label="新窗口" value="_blank" />
                        </el-select>
                        <div class="oakved-node__visibility">
                          <el-switch
                            v-model="element.visible"
                            :disabled="busy"
                            @change="markDirty"
                          />
                        </div>
                        <div class="oakved-node__actions">
                          <el-button
                            type="primary"
                            link
                            :disabled="busy"
                            @click="addDropdownItem(element)"
                          >
                            添加三级
                          </el-button>
                          <el-button
                            type="danger"
                            link
                            :disabled="busy"
                            @click="removeDropdownItem(primary.children || [], index)"
                          >
                            删除
                          </el-button>
                        </div>
                      </div>

                      <draggable
                        v-if="(element.children || []).length"
                        v-model="element.children"
                        item-key="itemKey"
                        handle=".oakved-node__handle"
                        ghost-class="navigation-row--ghost"
                        :animation="180"
                        class="oakved-node-list oakved-node-list--third"
                        @end="onOakvedSortEnd"
                      >
                        <template #item="{ element: child, index: childIndex }">
                          <article
                            class="oakved-node oakved-node--third"
                            :class="{ 'navigation-row--hidden': !child.visible }"
                          >
                            <div class="oakved-node__main">
                              <button
                                type="button"
                                class="oakved-node__handle"
                                aria-label="拖动调整三级导航顺序"
                              >
                                <Icon icon="ep:rank" />
                              </button>
                              <span class="navigation-row__order">{{ childIndex + 1 }}</span>
                              <div class="oakved-node__name">
                                <el-input
                                  v-model="child.label"
                                  maxlength="64"
                                  :disabled="busy || child.itemType === 'CATEGORY'"
                                  @input="markDirty"
                                />
                                <small>{{ itemTargetLabel(child) }}</small>
                              </div>
                              <el-select
                                v-model="child.itemType"
                                class="oakved-node__type"
                                :disabled="busy"
                                aria-label="三级导航类型"
                                @change="onDropdownItemTypeChange(child)"
                              >
                                <el-option
                                  v-for="option in oakvedItemTypeOptions"
                                  :key="option.value"
                                  :label="option.label"
                                  :value="option.value"
                                />
                              </el-select>
                              <el-select
                                v-if="['ROUTE', 'FILTER'].includes(child.itemType)"
                                v-model="child.targetKey"
                                class="oakved-node__target"
                                filterable
                                :disabled="busy"
                                placeholder="选择跳转目标"
                                @change="markDirty"
                              >
                                <el-option
                                  v-for="option in targetOptionsFor(child.itemType)"
                                  :key="option.targetKey"
                                  :label="`${option.label} · ${option.href}`"
                                  :value="option.targetKey"
                                />
                              </el-select>
                              <el-select
                                v-else-if="child.itemType === 'CATEGORY'"
                                v-model="child.categoryId"
                                class="oakved-node__target"
                                filterable
                                :disabled="busy"
                                placeholder="选择商品分类"
                                @change="onDropdownCategoryChange(child)"
                              >
                                <el-option
                                  v-for="option in categoryOptions"
                                  :key="option.id"
                                  :label="`${option.name} · ${option.publishedProductCount} 个在线商品`"
                                  :value="option.id"
                                />
                              </el-select>
                              <div v-else class="oakved-node__directory-label">无需跳转目标</div>
                              <el-select
                                v-model="child.openMode"
                                class="oakved-node__open"
                                :disabled="busy || child.itemType === 'DIRECTORY'"
                                aria-label="打开方式"
                                @change="markDirty"
                              >
                                <el-option label="当前窗口" value="_self" />
                                <el-option label="新窗口" value="_blank" />
                              </el-select>
                              <div class="oakved-node__visibility">
                                <el-switch
                                  v-model="child.visible"
                                  :disabled="busy"
                                  @change="markDirty"
                                />
                              </div>
                              <div class="oakved-node__actions">
                                <el-button
                                  type="danger"
                                  link
                                  :disabled="busy"
                                  @click="removeDropdownItem(element.children || [], childIndex)"
                                >
                                  删除
                                </el-button>
                              </div>
                            </div>
                          </article>
                        </template>
                      </draggable>
                    </article>
                  </template>
                </draggable>
              </div>
            </el-collapse-item>
          </el-collapse>

          <div class="category-sync-note">
            <Icon icon="ep:info-filled" />
            <span>隐藏一级导航会连同其二、三级内容一起隐藏；删除二级导航也会删除其三级内容。</span>
          </div>
        </ContentWrap>
      </main>

      <aside class="navigation-preview" aria-label="官网真实预览">
        <ContentWrap
          title="官网真实预览"
          message="预览使用真实官网组件和商品数据，不会修改线上版本。"
          surface="panel"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <div class="preview-actions">
                <el-tag v-if="inlinePreviewUrl" type="success" effect="plain">未发布草稿</el-tag>
                <el-button
                  v-if="inlinePreviewUrl"
                  size="small"
                  :loading="widePreviewLoading"
                  :disabled="busy"
                  @click="openWidePreview"
                >
                  <Icon icon="ep:full-screen" class="mr-4px" />
                  宽屏查看
                </el-button>
              </div>
            </div>
          </template>

          <p class="section-description"> 预览使用真实官网组件和商品数据，不会修改线上版本。 </p>

          <el-alert
            v-if="!siteConfigLoading && !siteConfigured"
            title="首次预览前，请先确认官网地址"
            type="warning"
            show-icon
            :closable="false"
            class="mb-16px"
          >
            <template #default>
              <span>系统需要知道在哪个官网打开预览；只需设置一次，不会发布导航。</span>
              <el-button type="primary" link @click="goToSiteConfig">去确认官网地址</el-button>
            </template>
          </el-alert>

          <div v-if="inlinePreviewUrl" class="preview-browser">
            <div class="preview-browser__bar">
              <Icon icon="ep:monitor" />
              <span>{{ inlinePreviewDisplayUrl }}</span>
              <el-button
                type="primary"
                link
                :loading="previewLoading"
                @click="refreshInlinePreview"
              >
                刷新预览
              </el-button>
            </div>
            <iframe
              :key="inlinePreviewUrl"
              :src="inlinePreviewUrl"
              :title="`${navigationBrandLabel}未发布导航预览`"
              class="preview-browser__frame"
            ></iframe>
          </div>

          <div v-else class="preview-placeholder">
            <div class="preview-placeholder__icon">
              <Icon icon="ep:view" />
            </div>
            <strong>先看看改动在真实官网里的样子</strong>
            <p>点击“保存并预览”，系统会自动保存草稿并打开真实官网组件。</p>
            <el-button
              type="primary"
              :loading="previewLoading"
              :disabled="busy || !siteConfigured"
              v-hasPermi="['seo:navigation:preview']"
              @click="refreshInlinePreview"
            >
              生成真实预览
            </el-button>
          </div>

          <div class="preview-security-note">
            <Icon icon="ep:lock" />
            <div>
              <strong>预览链接为一次性临时凭证</strong>
              <p>10 分钟内有效，只能兑换一次；刷新时系统会生成新的凭证。</p>
            </div>
          </div>
        </ContentWrap>
      </aside>
    </div>

    <el-dialog
      v-model="widePreviewVisible"
      :title="`${navigationBrandLabel} · 未发布导航宽屏预览`"
      width="96%"
      top="2vh"
      destroy-on-close
      class="wide-preview-dialog"
    >
      <iframe
        v-if="widePreviewUrl"
        :key="widePreviewUrl"
        :src="widePreviewUrl"
        :title="`${navigationBrandLabel}未发布导航宽屏预览`"
        class="wide-preview-frame"
      ></iframe>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="官网导航发布记录" size="520px">
      <el-alert
        class="mb-14px"
        :closable="false"
        show-icon
        type="info"
        title="恢复操作只会覆盖当前草稿；预览确认并再次发布后，官网才会变化。"
      />
      <el-empty v-if="!historyLoading && history.length === 0" description="尚无发布记录" />
      <el-table v-else v-loading="historyLoading" :data="history" stripe>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.revisionNo }}</template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="165">
          <template #default="{ row }">{{ formatHistoryTime(row.publishedTime) }}</template>
        </el-table-column>
        <el-table-column label="发布人" width="100">
          <template #default="{ row }">{{
            row.publishedBy ? `用户 #${row.publishedBy}` : '-'
          }}</template>
        </el-table-column>
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">
              {{ row.status === 'PUBLISHED' ? '当前线上' : '历史版本' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="96">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status === 'PUBLISHED' || busy"
              @click="restoreHistory(row)"
            >
              恢复为草稿
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import {
  createWebsiteNavigationPreviewTicket,
  getWebsiteNavigationHistory,
  getWebsiteNavigationDraft,
  publishWebsiteNavigation,
  saveWebsiteNavigationDraft,
  restoreWebsiteNavigationDraft,
  type WebsiteNavigationCategoryOptionRespVO,
  type WebsiteNavigationDraftRespVO,
  type WebsiteNavigationItemRespVO,
  type WebsiteNavigationItemSaveReqVO,
  type WebsiteNavigationRevisionRespVO,
  type WebsiteNavigationTargetOptionRespVO,
  type WebsiteNavigationItemType
} from '@/api/seo/navigation'
import { getSeoSiteConfig } from '@/api/seo/siteConfig'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'SeoNavigation' })

const SITE_ID = 1
const LOCALE = 'en'

const pageRoutes: Record<string, string> = {
  HOME: '首页 /',
  PRODUCTS: '产品中心 /products',
  ABOUT_US: '品牌介绍 /about-us',
  WORKSHOP: '工坊 /workshop',
  BLOG: '博客 /blog',
  CONTACT: '联系我们 /contact'
}

const message = useMessage()
const router = useRouter()
const draft = ref<WebsiteNavigationDraftRespVO>()
const primaryItems = ref<WebsiteNavigationItemRespVO[]>([])
const categoryItems = ref<WebsiteNavigationItemRespVO[]>([])
const categoryOptions = ref<WebsiteNavigationCategoryOptionRespVO[]>([])
const originalCategoryNames = ref<Map<number, string>>(new Map())
const editedCategoryIds = ref<Set<number>>(new Set())
const targetOptions = ref<WebsiteNavigationTargetOptionRespVO[]>([])
const oakvedExpandedKeys = ref<string[]>([])
const categoryToAdd = ref<number>()
const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const publishing = ref(false)
const previewLoading = ref(false)
const widePreviewLoading = ref(false)
const dirty = ref(false)
const loadError = ref('')
const inlinePreviewUrl = ref('')
const widePreviewUrl = ref('')
const widePreviewVisible = ref(false)
const historyVisible = ref(false)
const historyLoading = ref(false)
const history = ref<WebsiteNavigationRevisionRespVO[]>([])
const siteConfigLoading = ref(false)
const siteConfigured = ref(false)

const isOakvedNavigation = computed(() => draft.value?.navigationTemplate === 'OAKVED_B2C')
const navigationBrandLabel = computed(() =>
  isOakvedNavigation.value ? 'Oakved 官网导航' : 'VANZ 官网导航'
)
const navigationDescription = computed(() =>
  isOakvedNavigation.value
    ? '管理家具官网顶部导航及二、三级下拉目录；链接从安全目标中选择。'
    : '两个入口都能改分类名称；确认保存后，商品中心和官网导航保持一致。'
)
const primarySectionTag = computed(
  () => `${primaryItems.value.length} 项固定${isOakvedNavigation.value ? '导航' : '页面'}`
)
const allDraftItems = computed(() =>
  isOakvedNavigation.value
    ? flattenTree(primaryItems.value)
    : [...primaryItems.value, ...categoryItems.value]
)

const busy = computed(
  () =>
    loading.value ||
    saving.value ||
    refreshing.value ||
    publishing.value ||
    previewLoading.value ||
    widePreviewLoading.value ||
    siteConfigLoading.value
)

const publishedStatusLabel = computed(() =>
  draft.value?.publishedRevisionNo ? `线上 v${draft.value.publishedRevisionNo}` : '尚未发布'
)

const changeSummary = computed(() => {
  const publishedItems = draft.value?.publishedItems || []
  const draftItems = allDraftItems.value
  if (!publishedItems.length) {
    const visibleCount = draftItems.filter((item) => item.visible).length
    return visibleCount ? [`首次发布 ${visibleCount} 个可见导航项`] : []
  }
  const messages: string[] = []
  const publishedMap = new Map(publishedItems.map((item) => [item.itemKey, item]))
  const draftMap = new Map(draftItems.map((item) => [item.itemKey, item]))
  const addedItems = draftItems.filter(
    (item) => item.parentItemKey && !publishedMap.has(item.itemKey)
  )
  const removedItems = publishedItems.filter(
    (item) => item.parentItemKey && !draftMap.has(item.itemKey)
  )
  if (addedItems.length) {
    messages.push(
      `${isOakvedNavigation.value ? '新增下拉导航' : '新增二级目录'}：${addedItems
        .map((item) => item.label)
        .join('、')}`
    )
  }
  if (removedItems.length) {
    messages.push(
      `${isOakvedNavigation.value ? '移除下拉导航' : '移除二级目录'}：${removedItems
        .map((item) => item.label)
        .join('、')}`
    )
  }

  const renamed = draftItems.filter((item) => {
    const published = publishedMap.get(item.itemKey)
    return published && published.label !== item.label
  })
  if (renamed.length) messages.push(`修改名称：${renamed.map((item) => item.label).join('、')}`)

  const visibilityChanged = draftItems.filter((item) => {
    const published = publishedMap.get(item.itemKey)
    return published && published.visible !== item.visible
  })
  if (visibilityChanged.length) {
    messages.push(
      `调整显示状态：${visibilityChanged
        .map((item) => `${item.label}（${item.visible ? '显示' : '隐藏'}）`)
        .join('、')}`
    )
  }

  const orderSignature = (
    items: WebsiteNavigationItemRespVO[],
    existingKeys: Map<string, WebsiteNavigationItemRespVO>
  ) => {
    const groups = new Map<string, WebsiteNavigationItemRespVO[]>()
    items
      .filter((item) => existingKeys.has(item.itemKey))
      .forEach((item) => {
        const parentKey = item.parentItemKey || ''
        groups.set(parentKey, [...(groups.get(parentKey) || []), item])
      })
    return [...groups.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .flatMap(([parentKey, siblings]) =>
        siblings
          .sort((left, right) => left.sort - right.sort)
          .map((item) => `${parentKey}:${item.itemKey}`)
      )
      .join('|')
  }
  const orderChanged =
    orderSignature(publishedItems, draftMap) !== orderSignature(draftItems, publishedMap)
  if (orderChanged) messages.push('调整导航顺序')
  return messages
})

const selectedCategoryIds = computed(
  () => new Set(categoryItems.value.map((item) => item.categoryId).filter(Boolean))
)

const availableCategoryOptions = computed(() =>
  categoryOptions.value.filter((option) => !selectedCategoryIds.value.has(option.id))
)

const pendingCategoryRenames = computed(() =>
  categoryItems.value.filter(
    (item) =>
      item.available &&
      item.categoryId &&
      editedCategoryIds.value.has(item.categoryId) &&
      item.label.trim() !== (originalCategoryNames.value.get(item.categoryId) || '').trim()
  )
)

const inlinePreviewDisplayUrl = computed(() => safePreviewDisplayUrl(inlinePreviewUrl.value))

const oakvedItemTypeOptions: Array<{ label: string; value: WebsiteNavigationItemType }> = [
  { label: '仅作为目录', value: 'DIRECTORY' },
  { label: '固定页面', value: 'ROUTE' },
  { label: '商品筛选', value: 'FILTER' },
  { label: '商品分类', value: 'CATEGORY' }
]

const buildTree = (items: WebsiteNavigationItemRespVO[]) => {
  const nodeMap = new Map<string, WebsiteNavigationItemRespVO>()
  items.forEach((item) => nodeMap.set(item.itemKey, { ...item, children: [] }))
  const roots: WebsiteNavigationItemRespVO[] = []
  ;[...nodeMap.values()]
    .sort((left, right) => left.sort - right.sort)
    .forEach((item) => {
      const parent = item.parentItemKey ? nodeMap.get(item.parentItemKey) : undefined
      if (parent) {
        parent.children = [...(parent.children || []), item]
      } else {
        roots.push(item)
      }
    })
  return roots
}

const flattenTree = (
  items: WebsiteNavigationItemRespVO[],
  parentItemKey = ''
): WebsiteNavigationItemRespVO[] =>
  items.flatMap((item) => [
    { ...item, parentItemKey },
    ...flattenTree(item.children || [], item.itemKey)
  ])

const targetOptionsFor = (itemType: WebsiteNavigationItemType) =>
  targetOptions.value.filter((option) => option.itemType === itemType)

const itemTargetLabel = (item: WebsiteNavigationItemRespVO) => {
  if (item.itemType === 'PAGE') return pageRouteLabel(item.pageKey)
  if (item.itemType === 'CATEGORY') {
    return item.categoryId ? `商品分类 /products/category/${item.categoryId}` : '请选择商品分类'
  }
  if (item.itemType === 'DIRECTORY') return '仅展开下级导航，不直接跳转'
  const target = targetOptions.value.find((option) => option.targetKey === item.targetKey)
  return target ? `${target.label} ${target.href}` : '请选择安全跳转目标'
}

const descendantCount = (item: WebsiteNavigationItemRespVO): number =>
  (item.children || []).reduce((count, child) => count + 1 + descendantCount(child), 0)

const createCustomItemKey = () => {
  const randomPart = globalThis.crypto?.randomUUID?.().replaceAll('-', '').slice(0, 16)
  return `CUSTOM_${String(randomPart || Date.now()).toUpperCase()}`
}

const addDropdownItem = (parent: WebsiteNavigationItemRespVO) => {
  const children = parent.children || []
  parent.children = [
    ...children,
    {
      itemKey: createCustomItemKey(),
      parentItemKey: parent.itemKey,
      itemType: 'DIRECTORY',
      label: children.length ? 'New navigation item' : 'New menu',
      sort: (children.length + 1) * 10,
      visible: true,
      openMode: '_self',
      styleVariant: 'DEFAULT',
      available: true,
      children: []
    }
  ]
  markDirty()
}

const removeDropdownItem = (siblings: WebsiteNavigationItemRespVO[], index: number) => {
  siblings.splice(index, 1)
  normalizeSort()
  markDirty()
}

const onDropdownItemTypeChange = (item: WebsiteNavigationItemRespVO) => {
  item.targetKey = undefined
  item.categoryId = undefined
  item.available = true
  item.publishedProductCount = undefined
  markDirty()
}

const onDropdownCategoryChange = (item: WebsiteNavigationItemRespVO) => {
  const category = categoryOptions.value.find((option) => option.id === item.categoryId)
  if (category) {
    item.label = category.name
    item.available = true
    item.publishedProductCount = category.publishedProductCount
  }
  markDirty()
}

const loadSiteConfig = async () => {
  siteConfigLoading.value = true
  try {
    const config = await getSeoSiteConfig(SITE_ID)
    siteConfigured.value = Boolean(config?.siteUrl)
  } catch {
    siteConfigured.value = false
  } finally {
    siteConfigLoading.value = false
  }
}

const goToSiteConfig = () =>
  router.push({ path: '/seo/site-config', query: { returnTo: '/seo/navigation' } })

const loadDraft = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const response = await getWebsiteNavigationDraft(SITE_ID, LOCALE)
    draft.value = response
    let normalizedUnavailableCategory = false
    const normalizedItems = response.items.map((item) => {
      if (!item.available && item.visible) {
        normalizedUnavailableCategory = true
        return { ...item, visible: false, children: [] }
      }
      return { ...item, children: [] }
    })
    if (response.navigationTemplate === 'OAKVED_B2C') {
      primaryItems.value = buildTree(normalizedItems)
      oakvedExpandedKeys.value = primaryItems.value
        .filter((item) => (item.children || []).length > 0)
        .map((item) => item.itemKey)
      categoryItems.value = []
    } else {
      primaryItems.value = normalizedItems
        .filter((item) => item.itemType === 'PAGE')
        .map((item) => ({ ...item }))
      categoryItems.value = normalizedItems
        .filter((item) => item.itemType === 'CATEGORY')
        .map((item) => ({ ...item }))
    }
    categoryOptions.value = response.categoryOptions.map((option) => ({ ...option }))
    originalCategoryNames.value = new Map(
      categoryItems.value
        .filter((item) => item.categoryId)
        .map((item) => [item.categoryId as number, item.label])
    )
    editedCategoryIds.value = new Set()
    targetOptions.value = response.targetOptions.map((option) => ({ ...option }))
    dirty.value = normalizedUnavailableCategory
  } catch {
    loadError.value = '官网导航加载失败，请确认 SEO 服务和商品中心已启动'
  } finally {
    loading.value = false
  }
}

const markDirty = () => {
  dirty.value = true
  inlinePreviewUrl.value = ''
}

const markCategoryNameDirty = (item: WebsiteNavigationItemRespVO) => {
  if (!item.categoryId) return
  const nextEditedIds = new Set(editedCategoryIds.value)
  const originalName = originalCategoryNames.value.get(item.categoryId) || ''
  if (item.label.trim() === originalName.trim()) {
    nextEditedIds.delete(item.categoryId)
  } else {
    nextEditedIds.add(item.categoryId)
  }
  editedCategoryIds.value = nextEditedIds
  markDirty()
}

const normalizeSort = () => {
  if (isOakvedNavigation.value) {
    const normalizeBranch = (items: WebsiteNavigationItemRespVO[], parentItemKey = '') => {
      items.forEach((item, index) => {
        item.parentItemKey = parentItemKey
        item.sort = (index + 1) * 10
        normalizeBranch(item.children || [], item.itemKey)
      })
    }
    normalizeBranch(primaryItems.value)
    return
  }
  primaryItems.value.forEach((item, index) => {
    item.parentItemKey = ''
    item.sort = (index + 1) * 10
  })
  categoryItems.value.forEach((item, index) => {
    item.parentItemKey = 'PAGE_PRODUCTS'
    item.sort = (index + 1) * 10
  })
}

const onPrimarySortEnd = () => {
  normalizeSort()
  markDirty()
}

const onCategorySortEnd = () => {
  normalizeSort()
  markDirty()
}

const onOakvedSortEnd = () => {
  normalizeSort()
  markDirty()
}

const addCategory = () => {
  if (!categoryToAdd.value) return
  const option = categoryOptions.value.find((item) => item.id === categoryToAdd.value)
  if (!option) return
  categoryItems.value.push({
    itemKey: `CATEGORY_${option.id}`,
    parentItemKey: 'PAGE_PRODUCTS',
    itemType: 'CATEGORY',
    categoryId: option.id,
    label: option.name,
    sort: (categoryItems.value.length + 1) * 10,
    visible: true,
    openMode: '_self',
    styleVariant: 'DEFAULT',
    available: true,
    publishedProductCount: option.publishedProductCount
  })
  originalCategoryNames.value = new Map(originalCategoryNames.value).set(option.id, option.name)
  categoryToAdd.value = undefined
  markDirty()
}

const removeCategory = (index: number) => {
  const categoryId = categoryItems.value[index]?.categoryId
  categoryItems.value.splice(index, 1)
  if (categoryId) {
    const nextEditedIds = new Set(editedCategoryIds.value)
    nextEditedIds.delete(categoryId)
    editedCategoryIds.value = nextEditedIds
  }
  normalizeSort()
  markDirty()
}

const pageRouteLabel = (pageKey?: string) => (pageKey ? pageRoutes[pageKey] : '')

const validateLocalItems = () => {
  const blankItem = allDraftItems.value.find((item) => !item.label.trim())
  if (blankItem) {
    message.warning('导航名称不能为空')
    return false
  }
  if (isOakvedNavigation.value) {
    const invalidTarget = allDraftItems.value.find(
      (item) => ['ROUTE', 'FILTER'].includes(item.itemType) && !item.targetKey
    )
    if (invalidTarget) {
      message.warning(`请为“${invalidTarget.label}”选择跳转目标`)
      return false
    }
    const invalidCategory = allDraftItems.value.find(
      (item) =>
        item.itemType === 'CATEGORY' && (!item.categoryId || (item.visible && !item.available))
    )
    if (invalidCategory) {
      message.warning(`请为“${invalidCategory.label}”选择可用商品分类`)
      return false
    }
  }
  return true
}

const toSaveItem = (item: WebsiteNavigationItemRespVO): WebsiteNavigationItemSaveReqVO => ({
  itemKey: item.itemKey,
  parentItemKey: item.parentItemKey || '',
  itemType: item.itemType,
  pageKey: item.pageKey,
  targetKey: item.targetKey,
  categoryId: item.categoryId,
  label: item.label.trim(),
  syncCategoryName:
    !isOakvedNavigation.value &&
    item.itemType === 'CATEGORY' &&
    Boolean(item.categoryId && editedCategoryIds.value.has(item.categoryId)),
  sort: item.sort,
  visible: item.visible,
  openMode: item.openMode || '_self',
  styleVariant: item.styleVariant || 'DEFAULT'
})

const persistDraft = async (notify = true) => {
  if (!draft.value || !validateLocalItems()) return false
  const categoryRenames = [...pendingCategoryRenames.value]
  if (categoryRenames.length) {
    await message.confirm(
      `确认同步修改以下分类名称吗？\n${categoryRenames
        .map(
          (item) =>
            `• ${originalCategoryNames.value.get(item.categoryId as number) || '-'} → ${item.label.trim()}`
        )
        .join('\n')}\n确认后会同时更新商品中心和已发布官网的 Products 二级导航，无需再次发布。`,
      '同步分类名称'
    )
  }
  normalizeSort()
  saving.value = true
  try {
    await saveWebsiteNavigationDraft({
      revisionId: draft.value.revisionId,
      siteId: draft.value.siteId,
      locale: draft.value.locale,
      version: draft.value.version,
      items: allDraftItems.value.map(toSaveItem)
    })
    inlinePreviewUrl.value = ''
    widePreviewUrl.value = ''
    await loadDraft()
    if (notify) {
      message.success(
        categoryRenames.length
          ? `已同步 ${categoryRenames.length} 个商品分类名称，并保存导航草稿`
          : '导航草稿已保存，线上官网未受影响'
      )
    }
    return true
  } finally {
    saving.value = false
  }
}

const ensureDraftSaved = async () => {
  if (!dirty.value) return true
  return persistDraft(false)
}

const refreshCategories = async () => {
  refreshing.value = true
  try {
    if (!(await ensureDraftSaved())) return
    await loadDraft()
    message.success('已同步商品中心的最新分类和商品数量')
  } finally {
    refreshing.value = false
  }
}

const requestPreviewUrl = async () => {
  if (!draft.value) return ''
  const ticket = await createWebsiteNavigationPreviewTicket(
    draft.value.revisionId,
    draft.value.version
  )
  return ticket.previewUrl
}

const refreshInlinePreview = async () => {
  previewLoading.value = true
  try {
    if (!(await ensureDraftSaved())) return
    inlinePreviewUrl.value = await requestPreviewUrl()
  } finally {
    previewLoading.value = false
  }
}

const openWidePreview = async () => {
  widePreviewLoading.value = true
  try {
    if (!(await ensureDraftSaved())) return
    widePreviewUrl.value = await requestPreviewUrl()
    widePreviewVisible.value = true
  } finally {
    widePreviewLoading.value = false
  }
}

const publishDraft = async () => {
  if (!(await ensureDraftSaved()) || !draft.value) return
  if (!changeSummary.value.length) {
    message.info('当前草稿与线上版本一致，无需重复发布')
    return
  }
  await message.confirm(
    `确认发布以下变化吗？\n${changeSummary.value.map((item) => `• ${item}`).join('\n')}\n发布后访客将立即看到新导航。`
  )
  publishing.value = true
  try {
    await publishWebsiteNavigation(draft.value.revisionId, draft.value.version)
    inlinePreviewUrl.value = ''
    widePreviewUrl.value = ''
    widePreviewVisible.value = false
    message.success('官网导航发布成功')
    await loadDraft()
  } finally {
    publishing.value = false
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    history.value = await getWebsiteNavigationHistory(SITE_ID, LOCALE)
  } finally {
    historyLoading.value = false
  }
}

const openHistory = async () => {
  historyVisible.value = true
  await loadHistory()
}

const restoreHistory = async (revision: WebsiteNavigationRevisionRespVO) => {
  if (!draft.value) return
  await message.confirm(
    `确认把历史版本 v${revision.revisionNo} 恢复为当前草稿吗？当前未发布草稿会被覆盖，官网暂时不会变化。`
  )
  await restoreWebsiteNavigationDraft(
    draft.value.revisionId,
    draft.value.version,
    revision.revisionId
  )
  historyVisible.value = false
  inlinePreviewUrl.value = ''
  widePreviewUrl.value = ''
  await loadDraft()
  message.success(`已恢复 v${revision.revisionNo} 为草稿，请预览确认后发布`)
}

const formatHistoryTime = (value?: string) =>
  value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'

const safePreviewDisplayUrl = (value: string) => {
  if (!value) return ''
  try {
    const url = new URL(value)
    return `${url.origin}${url.pathname}`
  } catch {
    return '官网预览'
  }
}

onMounted(() => {
  loadDraft()
  loadSiteConfig()
})
onActivated(() => {
  if (draft.value) loadSiteConfig()
})
</script>

<style scoped lang="scss">
.website-navigation-page {
  display: grid;
  gap: 14px;
}

.navigation-toolbar {
  display: flex;
  min-height: 76px;
  padding: 14px 18px;
  background: var(--furniture-admin-surface, #fff);
  border: 1px solid var(--furniture-admin-border);
  border-radius: var(--furniture-admin-radius, 7px);
  box-shadow: var(--furniture-admin-shadow, 0 1px 3px rgb(15 36 58 / 5%));
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.navigation-toolbar__identity {
  display: flex;
  min-width: 320px;
  align-items: center;
  gap: 12px;
}

.navigation-toolbar__icon {
  display: grid;
  width: 40px;
  height: 40px;
  font-size: 19px;
  color: var(--furniture-admin-primary);
  background: var(--furniture-admin-primary-soft, #edf5ff);
  border-radius: 7px;
  place-items: center;
}

.navigation-toolbar__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.navigation-toolbar__title strong {
  font-size: 15px;
  color: var(--furniture-admin-ink);
}

.navigation-toolbar__identity p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--furniture-admin-muted);
}

.navigation-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.navigation-version {
  display: inline-flex;
  min-height: 32px;
  padding: 0 11px;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border: 1px solid var(--furniture-admin-border);
  border-radius: 5px;
  align-items: center;
  gap: 8px;
}

.navigation-version__divider {
  width: 1px;
  height: 12px;
  background: var(--furniture-admin-border);
}

.navigation-workspace {
  display: grid;
  grid-template-columns: minmax(430px, 0.82fr) minmax(680px, 1.58fr);
  align-items: start;
  gap: 14px;
}

.navigation-editor {
  display: grid;
  gap: 14px;
}

.navigation-change-list {
  display: grid;
  gap: 8px;
}

.navigation-change-list > div {
  display: flex;
  padding: 9px 11px;
  color: var(--furniture-admin-body);
  background: var(--furniture-admin-panel-soft);
  border-radius: 5px;
  align-items: flex-start;
  gap: 8px;
}

.navigation-change-list > div > :first-child {
  margin-top: 2px;
  color: var(--furniture-admin-primary);
  flex: 0 0 auto;
}

.navigation-preview {
  position: sticky;
  top: 14px;
}

.section-header-actions {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: flex-end;
}

.section-description {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--furniture-admin-muted);
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.navigation-list {
  display: grid;
  gap: 8px;
}

.navigation-row {
  display: grid;
  grid-template-columns: 28px 28px minmax(0, 1fr) auto;
  min-height: 66px;
  padding: 9px 11px;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
  align-items: center;
  gap: 9px;
}

.navigation-row:hover {
  border-color: color-mix(
    in srgb,
    var(--furniture-admin-primary) 35%,
    var(--furniture-admin-border)
  );
  box-shadow: 0 4px 14px rgb(15 36 58 / 6%);
}

.navigation-row--category {
  grid-template-columns: 28px 28px minmax(0, 1fr) 28px;
  grid-template-rows: auto auto;
}

.navigation-row--category > .navigation-row__body {
  width: 100%;
  grid-column: 3;
  grid-row: 1;
}

.navigation-row--category > .el-tag {
  grid-column: 3;
  grid-row: 2;
  justify-self: start;
}

.navigation-row--category > .navigation-row__visibility {
  grid-column: 3;
  grid-row: 2;
  justify-self: end;
}

.navigation-row--category > .el-button {
  grid-column: 4;
  grid-row: 1 / span 2;
  align-self: center;
}

.navigation-row--styled {
  grid-template-columns: 28px 28px minmax(0, 1fr) auto auto;
}

.navigation-row--hidden {
  background: var(--furniture-admin-panel-soft);
}

.navigation-row--unavailable {
  border-color: color-mix(in srgb, var(--furniture-admin-error) 34%, var(--furniture-admin-border));
}

.navigation-row--ghost {
  background: var(--furniture-admin-primary-soft, #edf5ff);
  border-color: var(--furniture-admin-primary);
  opacity: 0.72;
}

.navigation-row__handle {
  display: grid;
  width: 28px;
  height: 32px;
  padding: 0;
  font-size: 16px;
  color: var(--furniture-admin-muted);
  cursor: grab;
  background: transparent;
  border: 0;
  border-radius: 4px;
  place-items: center;
}

.navigation-row__handle:hover,
.navigation-row__handle:focus-visible {
  color: var(--furniture-admin-primary);
  background: var(--furniture-admin-primary-soft, #edf5ff);
  outline: none;
}

.navigation-row__handle:active {
  cursor: grabbing;
}

.navigation-row__order {
  display: grid;
  width: 24px;
  height: 24px;
  font-size: 11px;
  font-weight: 650;
  color: var(--furniture-admin-body);
  background: var(--furniture-admin-panel-soft);
  border-radius: 4px;
  place-items: center;
}

.navigation-row__body {
  min-width: 0;
}

.navigation-row__body strong {
  display: block;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--furniture-admin-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navigation-row__body small {
  display: flex;
  margin-top: 5px;
  overflow: hidden;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
  align-items: center;
  gap: 4px;
}

.navigation-row__body small.is-error {
  color: var(--furniture-admin-error);
}

.navigation-row__visibility {
  display: flex;
  min-width: 88px;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
}

.navigation-row__visibility span {
  font-size: 11px;
  color: var(--furniture-admin-muted);
}

.category-picker {
  display: flex;
  padding: 12px;
  margin-bottom: 12px;
  background: var(--furniture-admin-panel-soft);
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
  gap: 8px;
}

.category-picker__select {
  flex: 1;
}

.category-empty {
  display: flex;
  min-height: 94px;
  padding: 18px;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border: 1px dashed var(--furniture-admin-border);
  border-radius: 6px;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.category-empty > :first-child {
  font-size: 25px;
}

.category-empty strong {
  font-size: 13px;
  color: var(--furniture-admin-body);
}

.category-empty p {
  margin: 4px 0 0;
  font-size: 11px;
}

.category-sync-note,
.preview-security-note {
  display: flex;
  padding: 11px 12px;
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border-radius: 5px;
  align-items: flex-start;
  gap: 8px;
}

.category-sync-note > :first-child,
.preview-security-note > :first-child {
  margin-top: 2px;
  color: var(--furniture-admin-primary);
  flex: 0 0 auto;
}

.oakved-navigation-tree {
  border-top: 1px solid var(--furniture-admin-border);
}

:deep(.oakved-navigation-tree .el-collapse-item__header) {
  height: auto;
  min-height: 58px;
  padding: 8px 12px;
  background: #fff;
  border-right: 1px solid var(--furniture-admin-border);
  border-left: 1px solid var(--furniture-admin-border);
}

:deep(.oakved-navigation-tree .el-collapse-item__wrap) {
  background: var(--furniture-admin-panel-soft);
  border-right: 1px solid var(--furniture-admin-border);
  border-left: 1px solid var(--furniture-admin-border);
}

:deep(.oakved-navigation-tree .el-collapse-item__content) {
  padding: 0;
}

.oakved-tree-heading {
  display: flex;
  width: 100%;
  padding-right: 12px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.oakved-tree-heading > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.oakved-tree-heading strong {
  overflow: hidden;
  font-size: 13px;
  color: var(--furniture-admin-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oakved-tree-heading small {
  font-size: 11px;
  font-weight: 400;
  color: var(--furniture-admin-muted);
}

.oakved-tree-panel {
  padding: 12px;
}

.oakved-tree-panel__toolbar {
  display: flex;
  margin-bottom: 10px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.oakved-tree-panel__toolbar > span {
  font-size: 11px;
  font-weight: 650;
  color: var(--furniture-admin-body);
}

.oakved-tree-empty {
  display: flex;
  min-height: 68px;
  padding: 14px;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  background: #fff;
  border: 1px dashed var(--furniture-admin-border);
  border-radius: 6px;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.oakved-node-list {
  display: grid;
  gap: 8px;
}

.oakved-node {
  padding: 10px;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
}

.oakved-node__main {
  display: grid;
  grid-template-columns: 28px 28px minmax(0, 1fr) 108px;
  align-items: center;
  gap: 8px;
}

.oakved-node__handle {
  display: grid;
  grid-column: 1;
  grid-row: 1;
  width: 28px;
  height: 32px;
  padding: 0;
  font-size: 16px;
  color: var(--furniture-admin-muted);
  cursor: grab;
  background: transparent;
  border: 0;
  border-radius: 4px;
  place-items: center;
}

.oakved-node__handle:hover,
.oakved-node__handle:focus-visible {
  color: var(--furniture-admin-primary);
  background: var(--furniture-admin-primary-soft, #edf5ff);
  outline: none;
}

.oakved-node__main > .navigation-row__order {
  grid-column: 2;
  grid-row: 1;
}

.oakved-node__name {
  min-width: 0;
  grid-column: 3;
  grid-row: 1;
}

.oakved-node__name small {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  font-size: 10px;
  color: var(--furniture-admin-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oakved-node__visibility {
  display: flex;
  grid-column: 4;
  grid-row: 1;
  justify-content: flex-end;
}

.oakved-node__type {
  grid-column: 3;
  grid-row: 2;
}

.oakved-node__open {
  grid-column: 4;
  grid-row: 2;
}

.oakved-node__target,
.oakved-node__directory-label {
  grid-column: 3 / -1;
  grid-row: 3;
}

.oakved-node__directory-label {
  display: flex;
  min-height: 32px;
  padding: 0 10px;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border: 1px dashed var(--furniture-admin-border);
  border-radius: 4px;
  align-items: center;
}

.oakved-node__actions {
  display: flex;
  grid-column: 3 / -1;
  grid-row: 4;
  justify-content: flex-end;
  gap: 4px;
}

.oakved-node-list--third {
  padding: 10px 0 0 28px;
  margin-top: 10px;
  border-top: 1px solid var(--furniture-admin-border);
}

.oakved-node--third {
  background: var(--furniture-admin-panel-soft);
  border-left: 3px solid
    color-mix(in srgb, var(--furniture-admin-primary) 36%, var(--furniture-admin-border));
}

.preview-browser {
  overflow: hidden;
  background: #111;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 7px;
}

.preview-browser__bar {
  display: flex;
  min-height: 40px;
  padding: 0 12px;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  background: #f7f9fc;
  border-bottom: 1px solid var(--furniture-admin-border);
  align-items: center;
  gap: 7px;
}

.preview-browser__bar span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.preview-browser__frame {
  display: block;
  width: 100%;
  height: min(66vh, 720px);
  background: #fff;
  border: 0;
}

.preview-placeholder {
  display: flex;
  min-height: 470px;
  padding: 34px;
  text-align: center;
  background: linear-gradient(150deg, #f8fafc, #eef3f8);
  border: 1px dashed var(--furniture-admin-border);
  border-radius: 7px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}

.preview-placeholder__icon {
  display: grid;
  width: 54px;
  height: 54px;
  margin-bottom: 14px;
  font-size: 24px;
  color: var(--furniture-admin-primary);
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 50%;
  box-shadow: 0 8px 22px rgb(15 36 58 / 8%);
  place-items: center;
}

.preview-placeholder strong {
  font-size: 15px;
  color: var(--furniture-admin-ink);
}

.preview-placeholder p {
  max-width: 360px;
  margin: 8px 0 18px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--furniture-admin-muted);
}

.preview-security-note {
  align-items: flex-start;
}

.preview-security-note strong {
  font-size: 11px;
  font-weight: 600;
  color: var(--furniture-admin-body);
}

.preview-security-note p {
  margin: 2px 0 0;
}

.wide-preview-frame {
  display: block;
  width: 100%;
  height: calc(94vh - 112px);
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
}

:deep(.wide-preview-dialog .el-dialog__body) {
  padding: 0 18px 18px;
}

@media (width <= 1380px) {
  .navigation-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .navigation-toolbar__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .navigation-workspace {
    grid-template-columns: minmax(410px, 0.9fr) minmax(570px, 1.35fr);
  }
}

@media (width <= 1120px) {
  .navigation-workspace {
    grid-template-columns: 1fr;
  }

  .navigation-preview {
    position: static;
  }
}

@media (width <= 720px) {
  .navigation-toolbar__identity {
    min-width: 0;
  }

  .navigation-toolbar__actions > * {
    flex: 1 1 auto;
  }

  .navigation-version {
    flex-basis: 100%;
  }

  .category-picker {
    flex-direction: column;
  }

  .navigation-row,
  .navigation-row--styled {
    grid-template-columns: 24px 24px minmax(0, 1fr) auto;
  }

  .navigation-row--category {
    grid-template-columns: 24px 24px minmax(0, 1fr) 28px;
  }

  .navigation-row--category > .el-tag {
    grid-column: 3;
    justify-self: start;
  }

  .navigation-row--category > .navigation-row__visibility {
    grid-column: 3;
    justify-self: end;
  }

  .navigation-row--styled > .navigation-row__visibility {
    grid-column: 3 / -1;
    justify-self: start;
  }

  .oakved-node__main {
    grid-template-columns: 24px 24px minmax(0, 1fr) 96px;
  }

  .oakved-node-list--third {
    padding-left: 14px;
  }
}
</style>
