import { useEffect, useState } from 'react';
import api from '../services/api';
import { API_URL } from '../utils/constants';

/** Authenticated municipal evidence; the token never appears in an image URL. */
export default function ProtectedImage({ path, alt, className, onZoom }) {
  const [source, setSource] = useState(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!path) return undefined;
    const controller = new AbortController();
    let objectUrl;
    setSource(null);
    setError(false);
    const origin = new URL(API_URL, window.location.origin).origin;
    const normalized = /^https?:\/\//.test(path) || path.startsWith('/') ? path : `/uploads/photos/${path}`;
    const url = new URL(normalized, origin);
    if (url.origin !== origin || !url.pathname.startsWith('/uploads/')) {
      setError(true);
      return undefined;
    }
    api.get(url.href, { responseType: 'blob', signal: controller.signal })
      .then(({ data }) => {
        if (controller.signal.aborted) return;
        objectUrl = URL.createObjectURL(data);
        setSource(objectUrl);
      }).catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [path, attempt]);
  if (error) return <button type="button" className="btn-secondary" onClick={() => setAttempt(v => v + 1)}>Recharger la photo</button>;
  if (!source) return <span role="status">Chargement de la photo…</span>;
  const image = <img src={source} alt={alt} className={className} />;
  return onZoom ? <button type="button" className="evidence-zoom-button" onClick={() => onZoom(path)} aria-label={`Agrandir : ${alt}`}>{image}</button> : image;
}
