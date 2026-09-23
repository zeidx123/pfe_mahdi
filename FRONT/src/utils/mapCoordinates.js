export const DEFAULT_MAP_CENTER = [36.722857, 10.283535];

const isCoordinate = (value, limit) => (
  (typeof value === 'number' || typeof value === 'string') &&
  String(value).trim() !== '' &&
  Number.isFinite(Number(value)) && Math.abs(Number(value)) <= limit
);

export const hasValidCoordinates = (latitude, longitude) => (
  isCoordinate(latitude, 90) && isCoordinate(longitude, 180)
);
