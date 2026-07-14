const inspectCustom =
  typeof Symbol !== 'undefined' ? Symbol.for('nodejs.util.inspect.custom') : 'inspect.custom'

function inspect(value: unknown) {
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

inspect.custom = inspectCustom

export { inspect }
export default { inspect }
