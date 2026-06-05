export const normalizeMemberDetailId = (value) => {
  const rawValue = Array.isArray(value) ? value[0] : value
  const id = Number(rawValue)

  return Number.isInteger(id) && id > 0 ? id : null
}

export const shouldLoadMemberDetail = (value) => normalizeMemberDetailId(value) !== null
