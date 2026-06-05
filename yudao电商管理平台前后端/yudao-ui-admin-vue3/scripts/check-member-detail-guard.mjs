import assert from 'node:assert/strict'

const { normalizeMemberDetailId, shouldLoadMemberDetail } = await import(
  '../src/views/member/user/detail/memberDetailGuard.mjs'
)

assert.equal(normalizeMemberDetailId('12'), 12)
assert.equal(normalizeMemberDetailId(['12']), 12)
assert.equal(normalizeMemberDetailId(undefined), null)
assert.equal(normalizeMemberDetailId(''), null)
assert.equal(normalizeMemberDetailId('abc'), null)
assert.equal(normalizeMemberDetailId('0'), null)

assert.equal(shouldLoadMemberDetail('12'), true)
assert.equal(shouldLoadMemberDetail(undefined), false)
assert.equal(shouldLoadMemberDetail('abc'), false)
