function inspect(value) {
  if (typeof value === 'string') {
    return value
  }
  if (value === null || value === undefined) {
    return String(value)
  }
  try {
    return JSON.stringify(value)
  } catch {
    return Object.prototype.toString.call(value)
  }
}

module.exports = inspect
