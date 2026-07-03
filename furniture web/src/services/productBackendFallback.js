export const canUseProductDemoFallback = (env = import.meta.env) => !env?.PROD;

export const resolveProductBackendFailure = ({ env = import.meta.env, demoProducts = [] } = {}) => {
  if (canUseProductDemoFallback(env)) {
    return {
      source: "demo",
      products: demoProducts,
      error: false,
    };
  }

  return {
    source: "error",
    products: [],
    error: true,
  };
};
