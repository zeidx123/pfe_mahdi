export const DEFAULT_MAP_CENTER = [36.722857, 10.283535];

const isCoordinate = (value, limit) => (
  (typeof value === 'number' || typeof value === 'string') &&
  String(value).trim() !== '' &&
  Number.isFinite(Number(value)) && Math.abs(Number(value)) <= limit
);

export const hasValidCoordinates = (latitude, longitude) => (
  isCoordinate(latitude, 90) && isCoordinate(longitude, 180)
);

export const getUserLocation = () => {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !navigator?.geolocation) {
      reject(new Error("La géolocalisation n'est pas supportée par votre navigateur"));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve([position.coords.latitude, position.coords.longitude]);
      },
      (error) => {
        reject(error);
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
    );
  });
};
