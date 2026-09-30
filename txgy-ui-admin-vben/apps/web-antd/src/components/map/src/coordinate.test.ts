import { describe, expect, it } from 'vitest';

import { bd09ToGcj02, gcj02ToBd09 } from './coordinate';

describe('coordinate conversion', () => {
  it('round trips BD09 coordinates', () => {
    const point = { longitude: 109.328721, latitude: 36.864545 };
    const gcj02 = bd09ToGcj02(point.longitude, point.latitude);
    const roundTrip = gcj02ToBd09(gcj02.longitude, gcj02.latitude);
    expect(roundTrip.longitude).toBeCloseTo(point.longitude, 6);
    expect(roundTrip.latitude).toBeCloseTo(point.latitude, 6);
  });

  it('converts BD09 coordinates to a nearby GCJ02 point', () => {
    const point = bd09ToGcj02(109.328721, 36.864545);
    expect(point.longitude).toBeLessThan(109.328721);
    expect(point.latitude).toBeLessThan(36.864545);
    expect(point.longitude).toBeGreaterThan(109.31);
    expect(point.latitude).toBeGreaterThan(36.84);
  });
});
