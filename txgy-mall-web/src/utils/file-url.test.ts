import { expect, it } from 'vitest'
import { normalizeFileUrl } from './file-url'

it('routes historical localhost file URLs through this installation without changing file identity', () => {
  expect(normalizeFileUrl('http://127.0.0.1:48080/admin-api/infra/file/4/get/a%2Bb.png'))
    .toBe('/app-api/infra/file/4/get/a%2Bb.png')
  expect(normalizeFileUrl('http://localhost:48080/app-api/infra/file/4/preview/a.png'))
    .toBe('/app-api/infra/file/4/preview/a.png')
  expect(normalizeFileUrl('https://cdn.example/a.png')).toBe('https://cdn.example/a.png')
  expect(normalizeFileUrl('http://localhost:48080/other')).toBe('http://localhost:48080/other')
})
