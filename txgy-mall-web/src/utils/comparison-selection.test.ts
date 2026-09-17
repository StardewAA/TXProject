import { describe, expect, it } from 'vitest'
import { combineComparisonIds, comparisonIds, comparisonQuantity, comparisonStorageKey, difference, readAnonymousComparison } from './comparison-selection'

describe('SKU comparison selections', () => {
  it('preserves SKU selection order and large string identifiers', () => expect(comparisonIds([2, '9007199254740993'])).toEqual(['2', '9007199254740993']))
  it.each([[1, 1], [0], [-1], ['01'], ['x'], [1.5], [Number.MAX_SAFE_INTEGER + 1], [1, 2, 3, 4, 5], [''], [{}], null])('rejects invalid or over-limit ids %j', value => expect(() => comparisonIds(value)).toThrow())
  it('never silently removes an existing item when login merge exceeds four', () => expect(() => combineComparisonIds(['1', '2', '3'], ['4', '5'])).toThrow())
  it('merges explicitly by SKU id, not by product name or price', () => expect(combineComparisonIds(['1', '2'], ['2', '3'])).toEqual(['1', '2', '3']))
  it('isolates anonymous storage by tenant and recovers from invalid storage', () => {
    expect(comparisonStorageKey('1')).not.toEqual(comparisonStorageKey('3000'))
    expect(readAnonymousComparison({ getItem: () => '{bad' }, '1')).toEqual([])
    expect(readAnonymousComparison({ getItem: () => '["1","2"]' }, '1')).toEqual(['1', '2'])
    expect(readAnonymousComparison({ getItem: () => { throw new Error('storage denied') } }, '1')).toEqual([])
  })
  it.each([0, -1, 1.1, '1', NaN, Infinity, 1000])('rejects invalid sale-unit quantity %s', value => expect(() => comparisonQuantity(value)).toThrow())
  it('accepts positive integer sale-unit quantities only', () => expect(comparisonQuantity(999)).toBe(999))
  it('treats absent values as unknown and does not equate unknown with zero', () => {
    expect(difference([null, undefined])).toBe(false)
    expect(difference([null, 0])).toBe(true)
    expect(difference(['20mm', '30mm'])).toBe(true)
  })
})
