import { useState, useEffect } from 'react';
import { Outlet, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { FiHome, FiUsers, FiTruck, FiMapPin, FiMap, FiLogOut, FiMenu, FiMessageSquare, FiFileText, FiBookOpen, FiNavigation, FiChevronRight } from 'react-icons/fi';
import { APP_NAME } from '../utils/constants';
import { getPageMeta } from '../utils/pageMeta';

const navigation = [
  { label: 'Pilotage', items: [
    { path: '/dashboard', icon: FiHome, label: 'Tableau de bord' },
    { path: '/tournees', icon: FiMap, label: 'Tournées' },
    { path: '/tracking', icon: FiNavigation, label: 'Suivi GPS' },
    { path: '/reclamations', icon: FiMessageSquare, label: 'Réclamations' },
  ] },
  { label: 'Ressources', items: [
    { path: '/agents', icon: FiUsers, label: 'Chauffeurs' },
    { path: '/camions', icon: FiTruck, label: 'Camions' },
    { path: '/points', icon: FiMapPin, label: 'Points de collecte' },
  ] },
  { label: 'Accompagnement', items: [
    { path: '/candidatures', icon: FiFileText, label: 'Candidatures' },
    { path: '/documents-ia', icon: FiBookOpen, label: 'Base documentaire' },
  ] },
];

const MainLayout = () => {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(() => window.innerWidth >= 1100);
  const page = getPageMeta(pathname);

  useEffect(() => {
    const narrow = window.matchMedia('(max-width: 1099px)');
    const handleChange = (event) => setSidebarOpen(!event.matches);
    narrow.addEventListener('change', handleChange);
    return () => narrow.removeEventListener('change', handleChange);
  }, []);

  return (
    <div className="layout-container">
      <a className="skip-link" href="#page-content">Aller au contenu</a>
      {sidebarOpen && <button className="sidebar-backdrop" aria-label="Fermer le menu" onClick={() => setSidebarOpen(false)} />}
      <aside id="app-sidebar" className={`sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
        <div className="sidebar-header">
          <div className="logo-icon" aria-hidden="true"><FiMap /></div>
          <div className="brand-copy"><h2 className="logo-text">{APP_NAME}</h2><span>Gestion des collectes</span></div>
        </div>
        <nav className="sidebar-menu" aria-label="Navigation principale">
          {navigation.map((group) => <div className="nav-group" key={group.label}>
            <p className="nav-group-label">{group.label}</p>
            {group.items.map(({ path, icon: Icon, label }) => <NavLink
              key={path} to={path} aria-label={label} title={sidebarOpen ? undefined : label}
              onClick={() => { if (window.innerWidth < 640) setSidebarOpen(false); }}
              className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}
            >
              <span className="menu-icon" aria-hidden="true"><Icon /></span>
              <span className="menu-label">{label}</span>
            </NavLink>)}
          </div>)}
        </nav>
        <div className="sidebar-footer">
          <div className="workspace-caption"><span className="workspace-dot" />Espace superviseur</div>
          <button className="logout-btn" onClick={logout} title="Déconnexion" aria-label="Déconnexion">
            <FiLogOut className="menu-icon" aria-hidden="true" /><span className="menu-label">Déconnexion</span>
          </button>
        </div>
      </aside>
      <div className="main-content">
        <header className="navbar">
          <div className="navbar-left">
            <button className="toggle-btn" aria-label={sidebarOpen ? 'Réduire le menu' : 'Ouvrir le menu'} aria-expanded={sidebarOpen} aria-controls="app-sidebar" onClick={() => setSidebarOpen((open) => !open)}><FiMenu /></button>
            <div className="breadcrumb"><span>{page.section}</span><FiChevronRight aria-hidden="true" /><strong>{page.title}</strong></div>
          </div>
          <div className="user-profile">
            <div className="user-avatar" aria-hidden="true">{user?.prenom?.charAt(0)}{user?.nom?.charAt(0)}</div>
            <div className="user-info"><span className="user-name">{user?.prenom} {user?.nom}</span><span className="user-role">Superviseur</span></div>
          </div>
        </header>
        <main id="page-content" className="page-content" tabIndex={-1}><Outlet /></main>
      </div>
    </div>
  );
};

export default MainLayout;
