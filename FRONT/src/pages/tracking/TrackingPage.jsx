import { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { FiTruck, FiMapPin, FiWifi, FiWifiOff } from 'react-icons/fi';
import api from '../../services/api';
import { websocketService } from '../../services/websocketService';

// Fix Leaflet icon issue
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

// Custom truck icon for map
const truckIcon = L.divIcon({
  className: '',
  html: `
    <div style="background-color: var(--primary); width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 6px rgba(0,0,0,0.3); border: 2px solid white;">
      <svg stroke="white" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="20" width="20" xmlns="http://www.w3.org/2000/svg"><rect x="1" y="3" width="15" height="13"></rect><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon><circle cx="5.5" cy="18.5" r="2.5"></circle><circle cx="18.5" cy="18.5" r="2.5"></circle></svg>
    </div>
    <div style="position: absolute; bottom: -8px; left: 50%; transform: translateX(-50%); width: 0; height: 0; border-left: 6px solid transparent; border-right: 6px solid transparent; border-top: 8px solid var(--primary);"></div>
  `,
  iconSize: [36, 44],
  iconAnchor: [18, 44],
  popupAnchor: [0, -44],
});

const MapCenterUpdater = ({ center }) => {
  const map = useMap();
  useEffect(() => {
    if (center) {
      map.flyTo(center, 13);
    }
  }, [center, map]);
  return null;
};

const TrackingPage = () => {
  const [positions, setPositions] = useState({});
  const [isConnected, setIsConnected] = useState(false);
  const [mapCenter] = useState([36.722857, 10.283535]); // Dépôt confirmé pour la démonstration
  const [now, setNow] = useState(Date.now());
  const [error, setError] = useState('');

  useEffect(() => {
    // 1. Fetch initial positions from REST API
    const fetchInitialPositions = async () => {
      try {
        const response = await api.get('/tracking/positions');
        const positionsMap = {};
        response.data.forEach(pos => {
          positionsMap[pos.camionId] = pos;
        });
        setPositions(positionsMap);
        setError('');
      } catch (error) {
        setError(error.response?.data?.message || "Positions indisponibles. Nouvelle tentative dans 15 secondes.");
      }
    };

    fetchInitialPositions();

    // 2. Connect to WebSocket for real-time updates
    const handleNewPosition = (position) => {

      setPositions(prev => ({
        ...prev,
        [position.camionId]: position
      }));
    };

    websocketService.connect(handleNewPosition);
    
    // Polling connection status (simple hack to show UI status)
    const interval = setInterval(() => {
      setIsConnected(websocketService.connected);
      setNow(Date.now());
    }, 1000);

    const refresh = setInterval(fetchInitialPositions, 15000);
    return () => {
      clearInterval(refresh);
      clearInterval(interval);
      websocketService.disconnect();
    };
  }, []);

  const activeTrucks = Object.values(positions);
  const isStale = (truck) => truck.stale || !truck.timestamp || now - new Date(truck.timestamp).getTime() > 60000;

  return (
    <div className="tracking-page">
      <div className="page-header-row">
        <div>
          <h1>Suivi GPS</h1>
          <p>Supervisez la flotte de camions sur le terrain en direct.</p>
        </div>
        <div className={`connection-status ${isConnected ? 'connected' : 'disconnected'}`}>
          {isConnected ? <><FiWifi /> En direct (Connecté)</> : <><FiWifiOff /> Déconnecté (Tentative...)</>}
        </div>
      </div>

      {error && <p role="alert">{error}</p>}
      <div className="map-layout">
        {/* LEFT: List of active trucks */}
        <div className="trucks-list-panel">
          <div className="panel-header">
            <h3>Flotte active</h3>
            <span className="badge">{activeTrucks.filter(t => !isStale(t)).length} signal(s) récent(s)</span>
          </div>
          
          <div className="trucks-list-scroll">
            {activeTrucks.length === 0 ? (
              <div className="empty-state">
                <FiTruck size={32} style={{opacity: 0.3, marginBottom: 10}} />
                <p>Aucun véhicule n'émet de signal GPS actuellement.</p>
              </div>
            ) : (
              activeTrucks.map(truck => (
                <div key={truck.camionId} className="truck-card">
                  <div className="truck-header">
                    <span className="truck-matricule">{truck.camionMatricule || truck.camionId}</span>
                    <span className={`status-badge ${truck.statut === 'EN_COLLECTE' ? 'badge-warning' : truck.statut === 'A_LARRET' ? 'badge-danger' : 'badge-success'}`}>
                      {isStale(truck) ? 'SIGNAL ANCIEN' : truck.statut || 'Position reçue'}
                    </span>
                  </div>
                  
                  {truck.tourneeReference && (
                    <div className="truck-tournee">
                      <FiMapPin size={12} /> Tournée : {truck.tourneeReference}
                    </div>
                  )}

                  <div className="truck-telemetry">
                    <div className="telemetry-item">
                      <span className="label">Vitesse</span>
                      <span className="value">{truck.vitesse != null ? `${truck.vitesse.toFixed(1)} km/h` : 'Non mesurée'}</span>
                    </div>

                  </div>
                  
                  <div className="truck-timestamp">
                    Dernier signal : {new Date(truck.timestamp).toLocaleTimeString()}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* RIGHT: Real-time Map */}
        <div className="map-panel">
          <MapContainer
            center={mapCenter}
            zoom={12}
            style={{ width: '100%', height: '100%' }}
          >
            <MapCenterUpdater center={mapCenter} />
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            
            {activeTrucks.map(truck => (
              Number.isFinite(truck.latitude) && Number.isFinite(truck.longitude) ? (
                <Marker
                  key={truck.camionId}
                  position={[truck.latitude, truck.longitude]}
                  icon={truckIcon}
                >
                  <Popup>
                    <div style={{ minWidth: 200, padding: 4 }}>
                      <strong style={{ fontSize: 16, display: 'block', borderBottom: '1px solid #e2e8f0', paddingBottom: 6, marginBottom: 6 }}>
                        {truck.camionMatricule || truck.camionId}
                      </strong>
                      <p style={{ margin: '4px 0', fontSize: 13 }}><strong>Statut:</strong> {isStale(truck) ? "Signal ancien / interrompu" : truck.statut}</p>
                      {truck.tourneeReference && <p style={{ margin: '4px 0', fontSize: 13 }}><strong>Tournée:</strong> {truck.tourneeReference}</p>}
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10, background: '#f8fafc', padding: '6px', borderRadius: 4 }}>
                        <div>
                          <span style={{ fontSize: 11, color: '#64748b', display: 'block' }}>Vitesse</span>
                          <strong style={{ fontSize: 13 }}>{truck.vitesse != null ? `${truck.vitesse.toFixed(1)} km/h` : 'Non mesurée'}</strong>
                        </div>

                      </div>
                      <p style={{ margin: '8px 0 0 0', fontSize: 11, color: '#94a3b8', textAlign: 'right' }}>
                        Mis à jour: {new Date(truck.timestamp).toLocaleTimeString()}
                      </p>
                    </div>
                  </Popup>
                </Marker>
              ) : null
            ))}
          </MapContainer>
        </div>
      </div>

      <style>{`
        .tracking-page { display: flex; flex-direction: column; height: 100%; }
        .page-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
        
        .connection-status { display: flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: 20px; font-size: 14px; font-weight: 600; }
        .connection-status.connected { background-color: #dcfce7; color: #166534; }
        .connection-status.disconnected { background-color: #fee2e2; color: #991b1b; }
        
        .map-layout { display: grid; grid-template-columns: 320px 1fr; gap: 1.5rem; flex: 1; min-height: 600px; }
        
        .trucks-list-panel { background: var(--bg-card); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; overflow: hidden; }
        .panel-header { padding: 1.25rem; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; }
        .panel-header h3 { font-size: 1.1rem; margin: 0; }
        .badge { background: var(--primary-light, #e0f2fe); color: var(--primary, #0369a1); font-size: 12px; padding: 4px 8px; border-radius: 12px; font-weight: 600; }
        
        .trucks-list-scroll { flex: 1; overflow-y: auto; padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
        .truck-card { border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1rem; background: #f8fafc; transition: all 0.2s; }
        .truck-card:hover { border-color: var(--primary); box-shadow: var(--shadow-md); }
        
        .truck-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
        .truck-matricule { font-weight: 700; font-size: 15px; }
        
        .status-badge { font-size: 11px; padding: 2px 8px; border-radius: 12px; font-weight: 600; }
        .badge-success { background: #dcfce7; color: #166534; }
        .badge-warning { background: #fef9c3; color: #854d0e; }
        .badge-danger { background: #fee2e2; color: #991b1b; }
        
        .truck-tournee { font-size: 12px; color: var(--text-muted); margin-bottom: 12px; display: flex; align-items: center; gap: 4px; }
        
        .truck-telemetry { display: flex; gap: 12px; margin-bottom: 12px; }
        .telemetry-item { flex: 1; background: white; border: 1px solid var(--border-color); padding: 8px; border-radius: 6px; }
        .telemetry-item .label { display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 2px; }
        .telemetry-item .value { display: flex; align-items: center; font-size: 13px; font-weight: 600; }
        
        .truck-timestamp { font-size: 11px; color: var(--text-muted); text-align: right; }
        
        .map-panel { border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-sm); }
        .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 3rem 1rem; text-align: center; color: var(--text-muted); height: 100%; }
      `}</style>
    </div>
  );
};

export default TrackingPage;
