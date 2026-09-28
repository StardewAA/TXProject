const X_PI = (Math.PI * 3000) / 180;

export function bd09ToGcj02(longitude: number, latitude: number) {
  const x = longitude - 0.0065;
  const y = latitude - 0.006;
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
  return { longitude: z * Math.cos(theta), latitude: z * Math.sin(theta) };
}

export function gcj02ToBd09(longitude: number, latitude: number) {
  const z = Math.sqrt(longitude * longitude + latitude * latitude)
    + 0.00002 * Math.sin(latitude * X_PI);
  const theta = Math.atan2(latitude, longitude)
    + 0.000003 * Math.cos(longitude * X_PI);
  return {
    longitude: z * Math.cos(theta) + 0.0065,
    latitude: z * Math.sin(theta) + 0.006,
  };
}
