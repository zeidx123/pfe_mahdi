import { useEffect } from 'react';
import { useMap } from 'react-leaflet';

const MapResizeObserver = () => {
  const map = useMap();

  useEffect(() => {
    if (typeof ResizeObserver === 'undefined') return;
    let frame;
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => map.invalidateSize({ pan: false }));
    });
    observer.observe(map.getContainer());
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [map]);

  return null;
};

export default MapResizeObserver;
