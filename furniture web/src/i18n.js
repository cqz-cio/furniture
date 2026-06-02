import { computed, ref } from "vue";

const STORAGE_KEY = "furniture-web-locale";

const messages = {
  en: {
    bag: "Shopping bag",
    cartTitle: "Shopping bag",
    emptyCart: "Your bag is empty.",
    checkout: "Checkout",
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
    bag: "购物袋",
    cartTitle: "购物袋",
    emptyCart: "购物袋还是空的。",
    checkout: "去结算",
    subtotal: "小计",
    quantity: "数量",
    remove: "移除",
    addToCart: "加入购物车",
    viewDetails: "查看详情",
    productsTitle: "家具商品系列",
    productsSubtitle: "优先读取 Yudao 商品数据；后端未启动时使用本地演示商品。",
    productDetail: "商品详情",
    loadingProducts: "商品加载中...",
    offlineCatalog: "演示商品",
    connectedCatalog: "Yudao 商品",
    stock: "库存",
    language: "语言",
    english: "English",
    chinese: "中文",
    close: "关闭",
  },
};

const getInitialLocale = () => {
  const saved = globalThis.localStorage?.getItem(STORAGE_KEY);
  if (saved && messages[saved]) return saved;
  return "en";
};

const locale = ref(getInitialLocale());

export const availableLocales = [
  { lang: "en", label: "English" },
  { lang: "zh-CN", label: "中文" },
];

export const currentLocale = computed(() => locale.value);

export const setLocale = (lang) => {
  if (!messages[lang]) return;
  locale.value = lang;
  globalThis.localStorage?.setItem(STORAGE_KEY, lang);
  if (globalThis.document) {
    globalThis.document.documentElement.lang = lang;
  }
};

export const t = (key) => messages[locale.value]?.[key] || messages.en[key] || key;

export const useI18n = () => ({
  availableLocales,
  currentLocale,
  setLocale,
  t,
});
