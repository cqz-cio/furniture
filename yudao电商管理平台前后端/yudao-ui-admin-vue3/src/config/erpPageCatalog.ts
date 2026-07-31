import type { ComputedRef, InjectionKey } from 'vue'

export type ErpPageKind =
  | 'overview'
  | 'list'
  | 'hierarchy'
  | 'analytics'
  | 'settings'
  | 'form'
  | 'detail'
  | 'workspace'

export interface ErpPageContext {
  moduleKey: string
  moduleLabel: string
  kind: ErpPageKind
  kindLabel: string
  description: string
  recordLabel: string
  immersive?: boolean
}

type PageDefinition = Omit<ErpPageContext, 'kindLabel' | 'moduleKey'> & {
  moduleKey?: string
}

const kindLabels: Record<ErpPageKind, string> = {
  overview: '运营概览',
  list: '数据列表',
  hierarchy: '结构管理',
  analytics: '分析看板',
  settings: '配置中心',
  form: '业务表单',
  detail: '详情视图',
  workspace: '智能工作台'
}

const moduleDefaults: Record<string, Pick<ErpPageContext, 'moduleKey' | 'moduleLabel'>> = {
  dashboard: { moduleKey: 'dashboard', moduleLabel: '运营总览' },
  seo: { moduleKey: 'seo', moduleLabel: 'SEO 管理' },
  crm: { moduleKey: 'crm', moduleLabel: '询盘中心' },
  mall: { moduleKey: 'mall', moduleLabel: '商城系统' },
  member: { moduleKey: 'member', moduleLabel: '会员中心' },
  pay: { moduleKey: 'pay', moduleLabel: '支付中心' },
  ai: { moduleKey: 'ai', moduleLabel: 'AI 工作台' },
  infra: { moduleKey: 'infra', moduleLabel: '文件服务' },
  system: { moduleKey: 'system', moduleLabel: '系统管理' },
  user: { moduleKey: 'user', moduleLabel: '个人中心' }
}

const pages: Record<string, PageDefinition> = {
  '/dashboard': {
    moduleLabel: '运营总览',
    kind: 'overview',
    description: '集中查看商品、交易、库存与询盘的关键经营信号。',
    recordLabel: '经营指标'
  },
  '/index': {
    moduleLabel: '运营总览',
    kind: 'overview',
    description: '集中查看待办事项、异常提醒与近期业务进展。',
    recordLabel: '运营事项'
  },
  '/seo/metadata': {
    moduleLabel: 'SEO 管理',
    kind: 'list',
    description: '统一维护商品、分类、文章和页面的搜索元数据与发布状态。',
    recordLabel: 'SEO 内容'
  },
  '/seo/site-config': {
    moduleLabel: 'SEO 管理',
    kind: 'settings',
    description: '配置站点默认搜索规则、标题规范和社交分享基础信息。',
    recordLabel: '站点配置'
  },
  '/seo/analysis': {
    moduleLabel: 'SEO 管理',
    kind: 'analytics',
    description: '检查关键词覆盖、内容质量和搜索优化建议。',
    recordLabel: '分析结果'
  },
  '/crm/clue': {
    moduleLabel: '询盘中心',
    kind: 'list',
    description: '从首次提交到客户转化，持续跟进每一条网站询盘。',
    recordLabel: '询盘'
  },
  '/crm/customer': {
    moduleLabel: '询盘中心',
    kind: 'list',
    description: '沉淀客户主体、联系信息、成交历史与持续跟进记录。',
    recordLabel: '客户档案'
  },
  '/crm/contact': {
    moduleLabel: '询盘中心',
    kind: 'list',
    description: '维护客户联系人、职责信息和关联业务关系。',
    recordLabel: '联系人'
  },
  '/mall/home': {
    moduleLabel: '商城系统',
    kind: 'overview',
    description: '汇总商城经营数据、待处理订单和商品运营提醒。',
    recordLabel: '商城指标'
  },
  '/mall/product/spu': {
    moduleLabel: '商品中心',
    kind: 'list',
    description: '维护商品资料、销售状态、库存与 ERP 同步结果。',
    recordLabel: '商品'
  },
  '/mall/product/category': {
    moduleLabel: '商品中心',
    kind: 'hierarchy',
    description: '管理前后台一致的商品分类层级与展示顺序。',
    recordLabel: '商品分类'
  },
  '/mall/product/brand': {
    moduleLabel: '商品中心',
    kind: 'list',
    description: '维护品牌档案、品牌标识和启用状态。',
    recordLabel: '品牌'
  },
  '/mall/product/property': {
    moduleLabel: '商品中心',
    kind: 'hierarchy',
    description: '维护商品规格、属性值与 SKU 组合基础数据。',
    recordLabel: '商品属性'
  },
  '/mall/product/comment': {
    moduleLabel: '商品中心',
    kind: 'list',
    description: '审核商品评价、回复用户反馈并管理展示状态。',
    recordLabel: '商品评价'
  },
  '/mall/statistics/product': {
    moduleLabel: '数据看板',
    kind: 'analytics',
    description: '分析商品浏览、转化、销量和销售额表现。',
    recordLabel: '商品指标'
  },
  '/mall/trade/order': {
    moduleLabel: '订单中心',
    kind: 'list',
    description: '查询并处理支付、备货、发货、核销和订单异常。',
    recordLabel: '订单'
  },
  '/mall/trade/after-sale': {
    moduleLabel: '订单中心',
    kind: 'list',
    description: '集中审核退款退货申请并跟踪售后处理进度。',
    recordLabel: '售后单'
  },
  '/mall/trade/delivery/express': {
    moduleLabel: '订单中心',
    kind: 'list',
    description: '维护可用快递公司、编码和发货服务状态。',
    recordLabel: '快递公司'
  },
  '/mall/trade/delivery/express/express-template': {
    moduleLabel: '订单中心',
    kind: 'list',
    description: '配置不同区域、计费方式与包邮规则。',
    recordLabel: '运费模板'
  },
  '/mall/trade/delivery/express-template': {
    moduleLabel: '订单中心',
    kind: 'list',
    description: '配置不同区域、计费方式与包邮规则。',
    recordLabel: '运费模板'
  },
  '/mall/trade/delivery/pick-up-store': {
    moduleLabel: '订单中心',
    kind: 'list',
    description: '维护自提门店、营业信息和可核销范围。',
    recordLabel: '自提门店'
  },
  '/member/user': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '查询会员档案并维护等级、标签、积分和账户状态。',
    recordLabel: '会员'
  },
  '/member/membership': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '配置会员权益、服务范围与生效规则。',
    recordLabel: '会员权益'
  },
  '/member/gift-registry': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '登记礼品需求、收件信息和后续处理状态。',
    recordLabel: '礼品登记'
  },
  '/member/trade-application': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '审核会员提交的交易申请并跟踪处理结果。',
    recordLabel: '交易申请'
  },
  '/member/level': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '维护会员等级、升级门槛和等级权益。',
    recordLabel: '会员等级'
  },
  '/member/tag': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '建立可复用的会员标签并支持精细化运营。',
    recordLabel: '会员标签'
  },
  '/member/group': {
    moduleLabel: '会员中心',
    kind: 'list',
    description: '按业务规则整理会员分组与运营人群。',
    recordLabel: '会员分组'
  },
  '/pay/app': {
    moduleLabel: '支付中心',
    kind: 'list',
    description: '维护业务应用与支付渠道的接入配置。',
    recordLabel: '支付应用'
  },
  '/pay/order': {
    moduleLabel: '支付中心',
    kind: 'list',
    description: '核对业务订单、支付渠道、交易金额与支付状态。',
    recordLabel: '支付订单'
  },
  '/pay/refund': {
    moduleLabel: '支付中心',
    kind: 'list',
    description: '查询退款请求、渠道进度与最终退款结果。',
    recordLabel: '退款订单'
  },
  '/infra/file': {
    moduleLabel: '文件服务',
    kind: 'list',
    description: '查看系统文件、访问地址、大小和存储状态。',
    recordLabel: '文件'
  },
  '/infra/file/file-config': {
    moduleLabel: '文件服务',
    kind: 'list',
    description: '维护本地、对象存储和云端文件服务配置。',
    recordLabel: '存储配置'
  },
  '/infra/file-config': {
    moduleLabel: '文件服务',
    kind: 'list',
    description: '维护本地、对象存储和云端文件服务配置。',
    recordLabel: '存储配置'
  },
  '/system/user': {
    moduleLabel: '系统管理',
    kind: 'hierarchy',
    description: '按部门管理后台账号、角色范围和登录状态。',
    recordLabel: '系统用户'
  },
  '/system/role': {
    moduleLabel: '系统管理',
    kind: 'list',
    description: '维护岗位角色、数据范围与功能权限。',
    recordLabel: '角色'
  },
  '/system/menu': {
    moduleLabel: '系统管理',
    kind: 'hierarchy',
    description: '管理导航结构、页面权限和操作按钮授权。',
    recordLabel: '菜单权限'
  },
  '/system/tenant/list': {
    moduleLabel: '系统管理',
    kind: 'list',
    description: '维护租户信息、可用套餐和账号有效期。',
    recordLabel: '租户'
  },
  '/system/messages/mail/mail-account': {
    moduleLabel: '系统管理',
    kind: 'list',
    description: '配置业务通知使用的发件邮箱和连接参数。',
    recordLabel: '邮箱账号'
  },
  '/system/messages/mail/mail-template': {
    moduleLabel: '系统管理',
    kind: 'list',
    description: '维护业务邮件模板、参数和启用状态。',
    recordLabel: '邮件模板'
  },
  '/system/messages/mail/mail-log': {
    moduleLabel: '系统管理',
    kind: 'list',
    description: '追踪邮件发送记录、接收人和失败原因。',
    recordLabel: '邮件记录'
  },
  '/ai/knowledge': {
    moduleLabel: 'AI 工作台',
    kind: 'list',
    description: '管理面向业务助手的知识库、文档与召回配置。',
    recordLabel: '知识库'
  },
  '/ai/workflow': {
    moduleLabel: 'AI 工作台',
    kind: 'list',
    description: '管理可复用的 AI 流程、版本与发布状态。',
    recordLabel: 'AI 工作流'
  },
  '/ai/console/model': {
    moduleLabel: 'AI 工作台',
    kind: 'settings',
    description: '配置可用模型、能力范围和调用参数。',
    recordLabel: '模型'
  },
  '/ai/console/image': {
    moduleLabel: 'AI 工作台',
    kind: 'list',
    description: '查看绘图任务、生成结果和使用记录。',
    recordLabel: '绘图任务'
  },
  '/ai/console/music': {
    moduleLabel: 'AI 工作台',
    kind: 'list',
    description: '查看音乐生成任务、结果和处理状态。',
    recordLabel: '音乐任务'
  },
  '/ai/console/write': {
    moduleLabel: 'AI 工作台',
    kind: 'list',
    description: '查看 AI 写作任务、内容和生成状态。',
    recordLabel: '写作任务'
  },
  '/ai/console/mind-map': {
    moduleLabel: 'AI 工作台',
    kind: 'list',
    description: '查看思维导图任务、结果和使用记录。',
    recordLabel: '导图任务'
  },
  '/ai/chat': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '在上下文连续的工作区中完成业务问答与资料协作。',
    recordLabel: '对话',
    immersive: true
  },
  '/ai/chat/index': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '在上下文连续的工作区中完成业务问答与资料协作。',
    recordLabel: '对话',
    immersive: true
  },
  '/ai/image': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '生成和管理业务所需的图像内容。',
    recordLabel: '绘图',
    immersive: true
  },
  '/ai/image/index': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '生成和管理业务所需的图像内容。',
    recordLabel: '绘图',
    immersive: true
  },
  '/ai/music': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '生成和管理业务音频与音乐内容。',
    recordLabel: '音乐',
    immersive: true
  },
  '/ai/write': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '在结构化写作流程中完成业务内容生产。',
    recordLabel: '写作',
    immersive: true
  },
  '/ai/mind-map': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '从主题快速生成并整理可编辑思维导图。',
    recordLabel: '思维导图',
    immersive: true
  },
  '/ai/mindmap': {
    moduleLabel: 'AI 工作台',
    kind: 'workspace',
    description: '从主题快速生成并整理可编辑思维导图。',
    recordLabel: '思维导图',
    immersive: true
  },
  '/user/profile': {
    moduleLabel: '个人中心',
    kind: 'settings',
    description: '维护账号资料、安全设置和可编辑头像。',
    recordLabel: '个人资料'
  }
}

const prefixPages: Array<[string, PageDefinition]> = [
  [
    '/mall/product/spu/add',
    {
      moduleLabel: '商品中心',
      kind: 'form',
      description: '按步骤补齐商品基础信息、规格库存和销售设置。',
      recordLabel: '商品'
    }
  ],
  [
    '/mall/product/spu/edit/',
    {
      moduleLabel: '商品中心',
      kind: 'form',
      description: '维护商品基础信息、规格库存和销售设置。',
      recordLabel: '商品'
    }
  ],
  [
    '/mall/product/spu/detail/',
    {
      moduleLabel: '商品中心',
      kind: 'detail',
      description: '查看商品资料、SKU、库存和同步状态。',
      recordLabel: '商品'
    }
  ],
  [
    '/mall/product/property/value/',
    {
      moduleLabel: '商品中心',
      kind: 'hierarchy',
      description: '维护当前商品属性下可使用的属性值。',
      recordLabel: '属性值'
    }
  ],
  [
    '/mall/trade/order/detail/',
    {
      moduleLabel: '订单中心',
      kind: 'detail',
      description: '核对订单、支付、商品、收货和履约全链路信息。',
      recordLabel: '订单'
    }
  ],
  [
    '/mall/trade/after-sale/detail/',
    {
      moduleLabel: '订单中心',
      kind: 'detail',
      description: '查看售后原因、退款金额、处理记录和关联订单。',
      recordLabel: '售后单'
    }
  ],
  [
    '/member/user/detail/',
    {
      moduleLabel: '会员中心',
      kind: 'detail',
      description: '查看会员资料、账户资产、交易和行为记录。',
      recordLabel: '会员'
    }
  ],
  [
    '/crm/clue/detail/',
    {
      moduleLabel: '询盘中心',
      kind: 'detail',
      description: '查看询盘原文、客户信息、邮件投递和处理轨迹。',
      recordLabel: '询盘'
    }
  ],
  [
    '/crm/customer/detail/',
    {
      moduleLabel: '询盘中心',
      kind: 'detail',
      description: '查看客户档案、联系人、询盘与业务跟进记录。',
      recordLabel: '客户档案'
    }
  ],
  [
    '/crm/contact/detail/',
    {
      moduleLabel: '询盘中心',
      kind: 'detail',
      description: '查看联系人资料和关联客户业务。',
      recordLabel: '联系人'
    }
  ],
  [
    '/ai/knowledge/document/create',
    {
      moduleLabel: 'AI 工作台',
      kind: 'form',
      description: '上传或录入文档并设置知识库切分参数。',
      recordLabel: '知识库文档'
    }
  ],
  [
    '/ai/knowledge/document/update',
    {
      moduleLabel: 'AI 工作台',
      kind: 'form',
      description: '维护文档内容和知识库切分参数。',
      recordLabel: '知识库文档'
    }
  ],
  [
    '/ai/knowledge/document',
    {
      moduleLabel: 'AI 工作台',
      kind: 'list',
      description: '维护知识库文档、切分状态和索引结果。',
      recordLabel: '知识库文档'
    }
  ],
  [
    '/ai/knowledge/segment',
    {
      moduleLabel: 'AI 工作台',
      kind: 'list',
      description: '查看并校正知识库文档的内容分段。',
      recordLabel: '知识分段'
    }
  ],
  [
    '/ai/knowledge/retrieval',
    {
      moduleLabel: 'AI 工作台',
      kind: 'analytics',
      description: '测试知识库召回效果并检查命中文档片段。',
      recordLabel: '召回结果'
    }
  ],
  [
    '/ai/console/workflow/',
    {
      moduleLabel: 'AI 工作台',
      kind: 'workspace',
      description: '编排模型、工具和数据节点，形成可复用业务流程。',
      recordLabel: 'AI 工作流',
      immersive: true
    }
  ],
  [
    '/pay/cashier',
    {
      moduleLabel: '支付中心',
      kind: 'workspace',
      description: '选择支付方式并完成订单收款验证。',
      recordLabel: '收银台'
    }
  ]
]

const normalizePath = (path: string) => {
  const cleanPath = path.split('?')[0].replace(/\/+/g, '/')
  if (cleanPath === '/') return cleanPath
  return cleanPath.replace(/\/$/, '')
}

const resolveModule = (path: string) => {
  const segment = path.split('/').filter(Boolean)[0] || 'dashboard'
  return (
    moduleDefaults[segment] || {
      moduleKey: segment || 'workspace',
      moduleLabel: '业务工作台'
    }
  )
}

const inferKind = (path: string): ErpPageKind => {
  if (/\/(detail)(\/|$)/.test(path)) return 'detail'
  if (/\/(add|create|edit|update)(\/|$)/.test(path)) return 'form'
  return 'list'
}

export const resolveErpPageContext = (
  currentPath: string,
  pageTitle = '业务页面'
): ErpPageContext => {
  const path = normalizePath(currentPath)
  const module = resolveModule(path)
  const definition = pages[path] || prefixPages.find(([prefix]) => path.startsWith(prefix))?.[1]
  const kind = definition?.kind || inferKind(path)

  return {
    moduleKey: definition?.moduleKey || module.moduleKey,
    moduleLabel: definition?.moduleLabel || module.moduleLabel,
    kind,
    kindLabel: kindLabels[kind],
    description: definition?.description || `查看并处理${pageTitle}相关的业务数据与操作。`,
    recordLabel:
      definition?.recordLabel || pageTitle.replace(/管理|列表|详情|配置/g, '') || '业务数据',
    immersive: definition?.immersive
  }
}

export const ERP_PAGE_CONTEXT_KEY: InjectionKey<ComputedRef<ErpPageContext>> =
  Symbol('ERP_PAGE_CONTEXT')
