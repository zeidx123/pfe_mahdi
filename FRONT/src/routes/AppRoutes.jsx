import { useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { getPageMeta } from '../utils/pageMeta';
import { useAuth } from '../hooks/useAuth';
import MainLayout from '../layouts/MainLayout';
import LoginPage from '../pages/LoginPage';
import DashboardPage from '../pages/DashboardPage';

import AgentListPage from '../pages/agents/AgentListPage';

import CamionListPage from '../pages/camions/CamionListPage';
import PointCollectePage from '../pages/points/PointCollectePage';
import TourneePage from '../pages/tournees/TourneePage';
import TrackingPage from '../pages/tracking/TrackingPage';
import ReclamationPage from '../pages/reclamations/ReclamationPage';

import CandidaturesPage from '../pages/candidatures/CandidaturesPage';
import DocumentManagementPage from '../pages/ai/DocumentManagementPage';

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  
  if (loading) {
    return <div className="loading-screen">Chargement...</div>;
  }
  
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  
  return children;
};

const AppRoutes = () => {
  const { pathname } = useLocation();
  useEffect(() => {
    document.title = `${getPageMeta(pathname).title} · GestiCollecte`;
  }, [pathname]);
  return (
    <>
    <Toaster position="top-right" toastOptions={{ duration: 4500, className: 'app-toast' }} />
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      
      <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="agents" element={<AgentListPage />} />
        <Route path="camions" element={<CamionListPage />} />
        <Route path="points" element={<PointCollectePage />} />
        <Route path="tournees" element={<TourneePage />} />
        <Route path="tracking" element={<TrackingPage />} />
        <Route path="reclamations" element={<ReclamationPage />} />
        <Route path="candidatures" element={<CandidaturesPage />} />
        <Route path="optimisation" element={<Navigate to="/tournees" replace />} />
        <Route path="documents-ia" element={<DocumentManagementPage />} />
      </Route>
      
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
    </>
  );
};

export default AppRoutes;
