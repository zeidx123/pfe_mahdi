import { useState, useEffect } from 'react';
import { Outlet, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { 
  FiHome, FiUsers, FiTruck, FiMapPin, 
  FiMap, FiAlertTriangle, FiFileText, 
  FiLogOut, FiMenu, FiBell, FiMessageSquare
} from 'react-icons/fi';
import { APP_NAME } from '../utils/constants';
import { alerteService } from '../services/alerteService';

const MainLayout = () => {
  const { user, logout } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [unreadAlerts, setUnreadAlerts] = useState(0);

  useEffect(() => {
    fetchUnreadAlerts();
    const interval = setInterval(fetchUnreadAlerts, 30000);
    return () => clearInterval(interval);
  }, []);

  const fetchUnreadAlerts = async () => {
    try {
      const count = await alerteService.getUnreadCount();
      setUnreadAlerts(count);
    } catch (e) {
      // fail silently
    }
  };

  const toggleSidebar = () => {
    setSidebarOpen(!sidebarOpen);
  };

  const menuItems = [
    { path: '/dashboard', icon: <FiHome />, label: 'Tableau de bord' },
    { path: '/agents', icon: <FiUsers />, label: 'Agents' },
    { path: '/camions', icon: <FiTruck />, label: 'Camions' },
    { path: '/points', icon: <FiMapPin />, label: 'Points de collecte' },
    { path: '/tournees', icon: <FiMap />, label: 'Tournées' },
    { path: '/tracking', icon: <FiMapPin />, label: 'Suivi GPS' },
    { path: '/reclamations', icon: <FiMessageSquare />, label: 'Réclamations' },
    { path: '/candidatures', icon: <span className="text-base">📄</span>, label: 'Espace des candidatures' },
    { path: '/documents-ia', icon: <span className="text-base">📚</span>, label: 'Base documentaire' },
  ];

  return (
    <div className="layout-container">
      <style>{`
        .notification-badge { position: absolute; top: 0; right: 0; background: #ef4444; color: white; font-size: 10px; font-weight: 700; width: 16px; height: 16px; border-radius: 50%; display: flex; align-items: center; justify-content: center; transform: translate(25%, -25%); border: 2px solid white; }
      `}</style>
      {/* Sidebar */}
      <aside className={`sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
        <div className="sidebar-header">
          <div className="logo-icon"><FiMap /></div>
          {sidebarOpen && <h2 className="logo-text">{APP_NAME}</h2>}
        </div>
        
        <div className="sidebar-menu">
          {menuItems.map((item) => (
            <NavLink 
              key={item.path} 
              to={item.path} 
              className={({isActive}) => `menu-item ${isActive ? 'active' : ''}`}
              title={!sidebarOpen ? item.label : ""}
            >
              <span className="menu-icon">{item.icon}</span>
              {sidebarOpen && <span className="menu-label">{item.label}</span>}
            </NavLink>
          ))}
        </div>
        
        <div className="sidebar-footer">
          <button className="logout-btn" onClick={logout} title={!sidebarOpen ? "Déconnexion" : ""}>
            <FiLogOut className="menu-icon" />
            {sidebarOpen && <span className="menu-label">Déconnexion</span>}
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="main-content">
        {/* Navbar */}
        <header className="navbar">
          <div className="navbar-left">
            <button className="toggle-btn" onClick={toggleSidebar}>
              <FiMenu />
            </button>
            <div className="breadcrumb">
              <span>Municipalité</span>
            </div>
          </div>
          
          <div className="navbar-right">
            <div className="user-profile">
              <div className="user-avatar">
                {user?.prenom?.charAt(0)}{user?.nom?.charAt(0)}
              </div>
              <div className="user-info">
                <span className="user-name">{user?.prenom} {user?.nom}</span>
                <span className="user-role">{user?.role}</span>
              </div>
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default MainLayout;
