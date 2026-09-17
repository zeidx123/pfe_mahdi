import { Routes, Route, Navigate } from 'react-router-dom';
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
  return (
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
  );
};

export default AppRoutes;
