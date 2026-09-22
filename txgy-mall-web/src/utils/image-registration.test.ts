import { expect, it } from 'vitest'
import { createCaptchaSession, createImageRegistrationState, imageAuthPayload, normalizeLoginName, validateImageChallenge } from './image-registration.mjs'

const binding = { tenantId: '1', loginName: 'buyer_1', captchaSession: 'b'.repeat(32) }
const image = { tenantId: '1', challengeId: 'a'.repeat(32), imageDataUrl: 'data:image/png;base64,aGVsbG8=', expiresIn: 300, retryAfter: 2 }
const registration = { ...binding, password: 'Pass123', confirmPassword: 'Pass123', imageChallengeId: image.challengeId, imageCode: 'Ab2cd', memberIdentity: 'buyer', agree: true }

it('normalizes login names consistently without accepting phone numbers', () => {
  expect(normalizeLoginName(' Buyer_1 ')).toBe('buyer_1')
  expect(normalizeLoginName('A'.repeat(20))).toHaveLength(20)
  for (const invalid of ['short', 'a'.repeat(21), '19900000000', '_buyer1', 'buyer-1', '采购商123', null, 123456]) expect(() => normalizeLoginName(invalid)).toThrow()
})
it('uses cryptographic randomness for the form session and fails closed without it', () => {
  const random = { getRandomValues: (values: Uint8Array) => values.fill(17) } as unknown as Pick<Crypto, 'getRandomValues'>
  expect(createCaptchaSession(random)).toBe('11'.repeat(16))
  expect(() => createCaptchaSession({} as Crypto)).toThrow('安全连接')
})
it('image registration keeps contact mobile optional and never forwards it as verified mobile', () => {
  const payload = imageAuthPayload('register-image', { ...registration, loginName: ' Buyer_1 ', contactMobile: ' 19900000000 ', mobile: '18800000000', code: '123456', challengeId: image.challengeId, role: 'admin' })
  expect(payload).toMatchObject({ loginName: 'buyer_1', imageCode: 'AB2CD', contactMobile: '19900000000' })
  expect(payload).not.toHaveProperty('mobile'); expect(payload).not.toHaveProperty('code'); expect(payload).not.toHaveProperty('role')
  expect(imageAuthPayload('register-image', { ...registration, contactMobile: '', nickname: ' ' })).not.toHaveProperty('contactMobile')
  expect(imageAuthPayload('register-image', registration)).not.toHaveProperty('contactMobile')
})
it.each([{ confirmPassword: 'Other123' }, { password: '12345' }, { agree: false }, { memberIdentity: 'admin' }, { contactMobile: '123' },
  { imageChallengeId: '' }, { imageCode: '123456' }, { imageCode: '1234' }, { captchaSession: 'x' }, { nickname: 'x'.repeat(31) }])('rejects invalid image registration before transport: %j', change => {
  expect(() => imageAuthPayload('register-image', { ...registration, ...change })).toThrow()
})
it('password login permits both normalized login names and unchanged old phone identities', () => {
  expect(imageAuthPayload('account-login', { tenantId: '1', account: ' Buyer_1 ', password: '1234', mobile: '19900000000' })).toEqual({ tenantId: '1', account: 'buyer_1', password: '1234' })
  expect(imageAuthPayload('account-login', { tenantId: '1', account: '19900000000', password: '1234' }).account).toBe('19900000000')
  expect(() => imageAuthPayload('account-login', { tenantId: '1', account: 'buyer_1', password: '123' })).toThrow()
  for (const mobile of ['10000000000', '12000000000']) {
    expect(() => imageAuthPayload('account-login', { tenantId: '1', account: mobile, password: '1234' })).toThrow()
    expect(() => imageAuthPayload('register-image', { ...registration, contactMobile: mobile })).toThrow()
  }
})
it('image challenge requests cannot select another purpose or carry private values', () => {
  const result = imageAuthPayload('image-challenge', { ...binding, purpose: 'REGISTER', previousChallengeId: image.challengeId, password: 'secret', code: 'answer', headers: {} })
  expect(result).toEqual({ ...binding, purpose: 'REGISTER', previousChallengeId: image.challengeId })
  expect(() => imageAuthPayload('image-challenge', { ...binding, purpose: 'RESET_PASSWORD' })).toThrow()
  expect(() => imageAuthPayload('image-challenge', { ...binding, purpose: 'REGISTER', previousChallengeId: 'bad' })).toThrow()
})
it.each([{ tenantId: '2' }, { challengeId: 'bad' }, { imageDataUrl: 'https://external.example/captcha.png' },
  { imageDataUrl: 'data:image/svg+xml;base64,aGVsbG8=' }, { expiresIn: 0 }, { expiresIn: 601 }, { retryAfter: 301 }, { retryAfter: 0 }])('rejects foreign or malformed image response: %j', change => {
  expect(() => validateImageChallenge({ ...image, ...change }, '1')).toThrow()
})
it('returns only display metadata from image responses', () => {
  expect(validateImageChallenge({ ...image, answer: 'secret' }, '1')).toEqual(image)
})
it('binds the displayed image to tenant, normalized login name and form session', () => {
  const state = createImageRegistrationState()
  const ticket = state.begin(binding); expect(state.accept(ticket, image)).toBe(true)
  expect(state.payload({ ...binding, loginName: ' BUYER_1 ' }, 'ab2Cd')).toEqual({ ...binding, imageChallengeId: image.challengeId, imageCode: 'AB2CD' })
  expect(() => state.payload({ ...binding, tenantId: '2' }, 'ab2cd')).toThrow()
  expect(() => state.payload(binding, 'ab2cd')).toThrow()
})
it.each([{ loginName: 'buyer_2' }, { tenantId: '2' }, { captchaSession: 'c'.repeat(32) }])('does not reuse image after binding change: %j', change => {
  const state = createImageRegistrationState(); state.accept(state.begin(binding), image)
  expect(() => state.payload({ ...binding, ...change }, 'ab2cd')).toThrow()
})
it('invalidated late requests cannot replace a newer image', () => {
  const state = createImageRegistrationState(), old = state.begin(binding)
  state.invalidate(); const current = state.begin({ ...binding, loginName: 'buyer_2' })
  state.finish(old); expect(state.accept(old, image)).toBe(false)
  expect(state.accept(current, { ...image, challengeId: 'c'.repeat(32) })).toBe(true)
  expect(state.payload({ ...binding, loginName: 'buyer_2' }, 'ab2cd').imageChallengeId).toBe('c'.repeat(32))
})
it('refresh passes previous challenge for server invalidation and suppresses duplicate sends', () => {
  let now = 1000; const state = createImageRegistrationState(() => now)
  const first = state.begin(binding); expect(() => state.begin(binding)).toThrow()
  state.accept(first, image); expect(state.remaining()).toBe(2); expect(() => state.begin(binding)).toThrow()
  state.invalidate(); now += 2000
  const next = state.begin({ ...binding, tenantId: '2' })
  expect(next.previousChallengeId).toBe(image.challengeId)
  expect(() => state.payload(binding, 'ab2cd')).toThrow()
})
it('uses request start for expiry and cannot extend server validity with delayed responses', () => {
  let now = 1000; const state = createImageRegistrationState(() => now), ticket = state.begin(binding)
  now += 299000; state.accept(ticket, image); expect(state.expiresIn()).toBe(1)
  now += 1000; expect(() => state.payload(binding, 'ab2cd')).toThrow('重新获取')
  const expired = state.begin(binding); now += 300000
  expect(() => state.accept(expired, image)).toThrow('过期')
})
