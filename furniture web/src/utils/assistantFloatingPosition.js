export const getDefaultLauncherPosition = (viewport, size = 56, margin = 24) => ({
  left: Math.max(margin, viewport.width - size - margin),
  top: Math.max(margin, viewport.height - size - margin),
});

export const getDefaultPanelState = (viewport, options = {}) => {
  const margin = options.margin ?? 24;
  const width = Math.min(options.width ?? 460, viewport.width - margin * 2);
  const height = Math.min(options.height ?? 660, viewport.height - margin * 2);

  return {
    left: Math.max(margin, viewport.width - width - margin),
    top: Math.max(margin, viewport.height - height - margin),
    width,
    height,
  };
};

export const snapHorizontalPosition = (left, width, viewportWidth, margin = 24) => {
  const right = Math.max(margin, viewportWidth - width - margin);
  return left + width / 2 <= viewportWidth / 2 ? margin : right;
};
