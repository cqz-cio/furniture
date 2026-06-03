import { computed, ref } from "vue";

const STORAGE_KEY = "furniture-web-locale";
const DEFAULT_LOCALE = "en";

export const availableLocales = [
  { lang: "en", label: "English", shortLabel: "EN" },
  { lang: "zh-CN", label: "中文", shortLabel: "中文" },
  { lang: "fr", label: "Français", shortLabel: "FR" },
];

const messages = {
  en: {
    common: {
      close: "Close",
      search: "Search",
      language: "Language",
      checkout: "Checkout",
      loading: "Loading...",
      working: "Working...",
    },
    header: {
      menuOpen: "Open menu",
      menuClose: "Close menu",
      account: "Account",
      bag: "Shopping bag",
      regionSelector: "Region and language selector",
      selectedCountry: "Selected country",
      mobileRegion: "United States ($) / English",
      signInTitle: "Sign In",
      signInIntro: "Access your RH account, saved items, and order history.",
      createAccount: "Create an Account",
      forgotPassword: "Forgot Password?",
      secureLink: "Secure Sign In",
      tradeSignIn: "Trade Sign In",
    },
    home: {
      heroEyebrow: "Welcome to the World of RH",
      heroSubtitle: "Discover furnishings, lighting, textiles, and objects of enduring design.",
      gridAria: "RH home feature collections",
    },
    cart: {
      title: "Shopping bag",
      empty: "Your bag is empty.",
      emptyHelp: "Add pieces from the gallery to begin.",
      subtotal: "Subtotal",
      quantity: "Quantity",
      remove: "Remove",
      itemCount: "{count} ITEMS",
      remoteBag: "Live Yudao bag",
      localBag: "Local preview bag",
      deliveryNote: "Delivery and tax are calculated at checkout.",
    },
    auth: {
      aria: "Yudao account access",
      connected: "Connected",
      notConnected: "Not connected",
      accountLabel: "Yudao account",
      help: "Save a Yudao App token to sync your bag, checkout, and orders.",
      accessToken: "Access token",
      updateToken: "Update token",
      saveToken: "Save token",
      clear: "Clear",
    },
    checkout: {
      eyebrow: "Checkout",
      statusTitle: "Checkout Status",
      deliveryTitle: "Delivery Address",
      shipTo: "Ship to",
      itemsTitle: "Order Items",
      itemsCount: "{count} pieces selected",
      itemKickerYudao: "Yudao item",
      itemKickerPreview: "Preview item",
      emptyNote: "Your bag is empty. Add pieces before checkout.",
      summaryTitle: "Order Summary",
      pieces: "Pieces",
      merchandise: "Merchandise",
      delivery: "Delivery",
      estimatedTotal: "Estimated total",
      settlementIncluded: "Yudao settlement is included.",
      settlementPending: "Final delivery and settlement appear after account checkout.",
      noAddress: "No Yudao address is available for this user.",
      mode: {
        yudao: {
          title: "Review Your Order",
          message: "Confirm your delivery address and send the order to Yudao.",
          cta: "Create Yudao Order",
          status: "Live checkout is ready for Yudao order creation.",
        },
        "token-required": {
          title: "Connect Your Account",
          message: "Save a Yudao App token in the account panel before creating a remote order.",
          cta: "Add Token To Continue",
          status: "A Yudao token is required before remote checkout.",
        },
        "local-preview": {
          title: "Review Your Selections",
          message: "Demo products can be reviewed locally. Add Yudao products to create a live order.",
          cta: "Preview Only",
          status: "This bag contains local preview items.",
        },
        empty: {
          title: "Your Bag Is Empty",
          message: "Browse the gallery and add pieces before beginning checkout.",
          cta: "Return To Gallery",
          status: "Add items to begin checkout.",
        },
      },
    },
    orders: {
      eyebrow: "Orders",
      title: "Order History",
      connectedCount: "{count} orders connected to your Yudao account.",
      intro: "Review live Yudao orders created from the furniture storefront.",
      loading: "Loading orders...",
      tokenRequired: "Add a Yudao App token to view orders.",
      selectedOrder: "Selected Order",
      orderLabel: "Order",
      view: "View",
      empty: "No remote orders are available yet. Create a Yudao order from checkout to see it here.",
      status: "Status {status}",
    },
    bag: "Shopping bag",
    cartTitle: "Shopping bag",
    emptyCart: "Your bag is empty.",
    subtotal: "Subtotal",
    quantity: "Quantity",
    remove: "Remove",
    addToCart: "Add to Cart",
    viewDetails: "View Details",
    productsTitle: "Furniture Collection",
    productsSubtitle: "Live product data from Yudao, with local demo products when the backend is offline.",
    productDetail: "Product Detail",
    loadingProducts: "Loading products...",
    offlineCatalog: "Demo catalog",
    connectedCatalog: "Yudao catalog",
    stock: "Stock",
    language: "Language",
    english: "English",
    chinese: "中文",
    close: "Close",
  },
  "zh-CN": {
    common: {
      close: "关闭",
      search: "搜索",
      language: "语言",
      checkout: "结账",
      loading: "正在加载...",
      working: "处理中...",
    },
    header: {
      menuOpen: "打开菜单",
      menuClose: "关闭菜单",
      account: "账户",
      bag: "购物袋",
      regionSelector: "地区和语言选择器",
      selectedCountry: "已选国家",
      mobileRegion: "美国 ($) / 中文",
      signInTitle: "登录",
      signInIntro: "访问你的 RH 账户、收藏商品和订单记录。",
      createAccount: "创建账户",
      forgotPassword: "忘记密码？",
      secureLink: "安全登录",
      tradeSignIn: "设计师账户登录",
    },
    home: {
      heroEyebrow: "欢迎来到 RH 的世界",
      heroSubtitle: "探索经久设计的家具、灯具、织物与家居饰品。",
      gridAria: "RH 首页精选系列",
    },
    cart: {
      title: "购物袋",
      empty: "你的购物袋是空的。",
      emptyHelp: "从图库中添加商品即可开始。",
      subtotal: "小计",
      quantity: "数量",
      remove: "移除",
      itemCount: "{count} 件商品",
      remoteBag: "Yudao 实时购物袋",
      localBag: "本地预览购物袋",
      deliveryNote: "配送费和税费将在结账时计算。",
    },
    auth: {
      aria: "Yudao 账户访问",
      connected: "已连接",
      notConnected: "未连接",
      accountLabel: "Yudao 账户",
      help: "保存 Yudao App token 以同步购物袋、结账和订单。",
      accessToken: "访问令牌",
      updateToken: "更新令牌",
      saveToken: "保存令牌",
      clear: "清除",
    },
    checkout: {
      eyebrow: "结账",
      statusTitle: "结账状态",
      deliveryTitle: "配送地址",
      shipTo: "配送至",
      itemsTitle: "订单商品",
      itemsCount: "已选择 {count} 件",
      itemKickerYudao: "Yudao 商品",
      itemKickerPreview: "预览商品",
      emptyNote: "你的购物袋是空的。请先添加商品再结账。",
      summaryTitle: "订单摘要",
      pieces: "件数",
      merchandise: "商品金额",
      delivery: "配送",
      estimatedTotal: "预计总计",
      settlementIncluded: "已包含 Yudao 结算信息。",
      settlementPending: "最终配送和结算信息会在账户结账后显示。",
      noAddress: "该用户没有可用的 Yudao 地址。",
      mode: {
        yudao: {
          title: "核对订单",
          message: "确认配送地址，并将订单发送至 Yudao。",
          cta: "创建 Yudao 订单",
          status: "实时结账已准备好创建 Yudao 订单。",
        },
        "token-required": {
          title: "连接账户",
          message: "请先在账户面板保存 Yudao App token，再创建远程订单。",
          cta: "添加令牌以继续",
          status: "远程结账前需要 Yudao 令牌。",
        },
        "local-preview": {
          title: "核对所选商品",
          message: "演示商品可在本地预览。添加 Yudao 商品即可创建实时订单。",
          cta: "仅预览",
          status: "此购物袋包含本地预览商品。",
        },
        empty: {
          title: "购物袋为空",
          message: "浏览图库并添加商品后再开始结账。",
          cta: "返回图库",
          status: "添加商品后即可开始结账。",
        },
      },
    },
    orders: {
      eyebrow: "订单",
      title: "订单历史",
      connectedCount: "{count} 个订单已连接到你的 Yudao 账户。",
      intro: "查看从家具 storefront 创建的实时 Yudao 订单。",
      loading: "正在加载订单...",
      tokenRequired: "添加 Yudao App token 以查看订单。",
      selectedOrder: "已选订单",
      orderLabel: "订单",
      view: "查看",
      empty: "暂无远程订单。请从结账页创建 Yudao 订单后在此查看。",
      status: "状态 {status}",
    },
    bag: "购物袋",
    cartTitle: "购物袋",
    emptyCart: "你的购物袋是空的。",
    subtotal: "小计",
    quantity: "数量",
    remove: "移除",
    addToCart: "加入购物袋",
    viewDetails: "查看详情",
    productsTitle: "家具系列",
    productsSubtitle: "优先读取 Yudao 商品数据；后端离线时显示本地演示商品。",
    productDetail: "商品详情",
    loadingProducts: "正在加载商品...",
    offlineCatalog: "演示目录",
    connectedCatalog: "Yudao 目录",
    stock: "库存",
    language: "语言",
    english: "English",
    chinese: "中文",
    close: "关闭",
  },
  fr: {
    common: {
      close: "Fermer",
      search: "Rechercher",
      language: "Langue",
      checkout: "Paiement",
      loading: "Chargement...",
      working: "Traitement...",
    },
    header: {
      menuOpen: "Ouvrir le menu",
      menuClose: "Fermer le menu",
      account: "Compte",
      bag: "Panier",
      regionSelector: "Sélecteur de région et de langue",
      selectedCountry: "Pays sélectionné",
      mobileRegion: "États-Unis ($) / Français",
      signInTitle: "Connexion",
      signInIntro: "Accédez à votre compte RH, à vos favoris et à votre historique de commandes.",
      createAccount: "Créer un compte",
      forgotPassword: "Mot de passe oublié ?",
      secureLink: "Connexion sécurisée",
      tradeSignIn: "Connexion Trade",
    },
    home: {
      heroEyebrow: "Bienvenue dans l'univers RH",
      heroSubtitle: "Découvrez du mobilier, des luminaires, des textiles et des objets au design durable.",
      gridAria: "Collections en vedette RH",
    },
    cart: {
      title: "Panier",
      empty: "Votre panier est vide.",
      emptyHelp: "Ajoutez des pièces depuis la galerie pour commencer.",
      subtotal: "Sous-total",
      quantity: "Quantité",
      remove: "Retirer",
      itemCount: "{count} ARTICLES",
      remoteBag: "Panier Yudao en direct",
      localBag: "Panier de prévisualisation local",
      deliveryNote: "La livraison et les taxes sont calculées au paiement.",
    },
    auth: {
      aria: "Accès au compte Yudao",
      connected: "Connecté",
      notConnected: "Non connecté",
      accountLabel: "Compte Yudao",
      help: "Enregistrez un token Yudao App pour synchroniser le panier, le paiement et les commandes.",
      accessToken: "Token d'accès",
      updateToken: "Mettre à jour le token",
      saveToken: "Enregistrer le token",
      clear: "Effacer",
    },
    checkout: {
      eyebrow: "Paiement",
      statusTitle: "État du paiement",
      deliveryTitle: "Adresse de livraison",
      shipTo: "Livrer à",
      itemsTitle: "Articles de la commande",
      itemsCount: "{count} pièces sélectionnées",
      itemKickerYudao: "Article Yudao",
      itemKickerPreview: "Article de prévisualisation",
      emptyNote: "Votre panier est vide. Ajoutez des pièces avant le paiement.",
      summaryTitle: "Résumé de commande",
      pieces: "Pièces",
      merchandise: "Marchandises",
      delivery: "Livraison",
      estimatedTotal: "Total estimé",
      settlementIncluded: "Le règlement Yudao est inclus.",
      settlementPending: "La livraison et le règlement définitifs apparaîtront après le paiement du compte.",
      noAddress: "Aucune adresse Yudao n'est disponible pour cet utilisateur.",
      mode: {
        yudao: {
          title: "Vérifier votre commande",
          message: "Confirmez votre adresse de livraison et envoyez la commande à Yudao.",
          cta: "Créer la commande Yudao",
          status: "Le paiement en direct est prêt pour créer une commande Yudao.",
        },
        "token-required": {
          title: "Connecter votre compte",
          message: "Enregistrez un token Yudao App dans le panneau du compte avant de créer une commande distante.",
          cta: "Ajouter un token pour continuer",
          status: "Un token Yudao est requis avant le paiement distant.",
        },
        "local-preview": {
          title: "Vérifier vos sélections",
          message: "Les produits de démonstration peuvent être vérifiés localement. Ajoutez des produits Yudao pour créer une commande en direct.",
          cta: "Prévisualisation seulement",
          status: "Ce panier contient des articles de prévisualisation locale.",
        },
        empty: {
          title: "Votre panier est vide",
          message: "Parcourez la galerie et ajoutez des pièces avant de commencer le paiement.",
          cta: "Retour à la galerie",
          status: "Ajoutez des articles pour commencer le paiement.",
        },
      },
    },
    orders: {
      eyebrow: "Commandes",
      title: "Historique des commandes",
      connectedCount: "{count} commandes connectées à votre compte Yudao.",
      intro: "Consultez les commandes Yudao en direct créées depuis la boutique de mobilier.",
      loading: "Chargement des commandes...",
      tokenRequired: "Ajoutez un token Yudao App pour consulter les commandes.",
      selectedOrder: "Commande sélectionnée",
      orderLabel: "Commande",
      view: "Voir",
      empty: "Aucune commande distante n'est encore disponible. Créez une commande Yudao depuis le paiement pour la voir ici.",
      status: "Statut {status}",
    },
    bag: "Panier",
    cartTitle: "Panier",
    emptyCart: "Votre panier est vide.",
    subtotal: "Sous-total",
    quantity: "Quantité",
    remove: "Retirer",
    addToCart: "Ajouter au panier",
    viewDetails: "Voir les détails",
    productsTitle: "Collection de mobilier",
    productsSubtitle: "Données produit Yudao en direct, avec produits de démonstration locaux lorsque le backend est hors ligne.",
    productDetail: "Détail du produit",
    loadingProducts: "Chargement des produits...",
    offlineCatalog: "Catalogue de démonstration",
    connectedCatalog: "Catalogue Yudao",
    stock: "Stock",
    language: "Langue",
    english: "English",
    chinese: "中文",
    close: "Fermer",
  },
};

const safeStorage = () => {
  try {
    return globalThis.localStorage || null;
  } catch {
    return null;
  }
};

const normalizeLocale = (lang) => (messages[lang] ? lang : DEFAULT_LOCALE);

const getInitialLocale = () => {
  const storage = safeStorage();
  if (!storage) return DEFAULT_LOCALE;

  try {
    return normalizeLocale(storage.getItem(STORAGE_KEY));
  } catch {
    return DEFAULT_LOCALE;
  }
};

const updateDocumentLanguage = (lang) => {
  try {
    if (globalThis.document?.documentElement) {
      globalThis.document.documentElement.lang = lang;
    }
  } catch {
    // Some non-browser test environments expose partial document objects.
  }
};

const persistLocale = (lang) => {
  const storage = safeStorage();
  if (!storage) return;

  try {
    storage.setItem(STORAGE_KEY, lang);
  } catch {
    // Locale changes should still work if browser storage is disabled.
  }
};

const locale = ref(getInitialLocale());
updateDocumentLanguage(locale.value);

export const currentLocale = computed(() => locale.value);

export const getMessage = (key, lang) => {
  const parts = key.split(".");
  let message = messages[normalizeLocale(lang)];

  for (const part of parts) {
    if (!message || typeof message !== "object" || !(part in message)) {
      return undefined;
    }
    message = message[part];
  }

  return typeof message === "string" ? message : undefined;
};

const interpolate = (template, params) =>
  template.replace(/\{([A-Za-z0-9_]+)\}/g, (match, name) =>
    Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : match
  );

export const setLocale = (lang) => {
  const nextLocale = normalizeLocale(lang);
  locale.value = nextLocale;
  persistLocale(nextLocale);
  updateDocumentLanguage(nextLocale);
};

export const t = (key, params = {}) => {
  const template = getMessage(key, locale.value) ?? getMessage(key, DEFAULT_LOCALE);
  return template ? interpolate(template, params) : key;
};

export const useI18n = () => ({
  availableLocales,
  currentLocale,
  setLocale,
  t,
});
