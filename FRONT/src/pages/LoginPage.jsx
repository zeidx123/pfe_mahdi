import { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import toast from 'react-hot-toast';
import { APP_NAME } from '../utils/constants';
import { FiMap } from 'react-icons/fi';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isLoading) return;
    
    if (!email || !password) {
      toast.error('Veuillez remplir tous les champs');
      return;
    }
    
    try {
      setIsLoading(true);
      await login(email, password);
      toast.success('Connexion réussie !');
      navigate('/dashboard');
    } catch (error) {
      const status = error.response?.status;
      const message = !error.response
        ? 'Serveur injoignable. Vérifiez la connexion et l’adresse du serveur, puis réessayez.'
        : [401, 403].includes(status)
          ? 'Identifiants incorrects ou compte désactivé.'
          : error.response?.data?.message || 'Connexion indisponible. Réessayez dans un instant.';
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <div className="login-logo">
            <FiMap />
          </div>
          <h1>{APP_NAME}</h1>
          <p>Espace superviseur</p>
        </div>
        <div className="login-intro"><h2>Heureux de vous retrouver.</h2><p>Connectez-vous pour organiser vos collectes et suivre vos équipes.</p></div>
        
        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="email">Email professionnel</label>
            <input
              type="email"
              autoComplete="username"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Ex: superviseur@municipalite.ma"
              disabled={isLoading}
            />
          </div>
          
          <div className="form-group">
            <label htmlFor="password">Mot de passe</label>
            <input
              type="password"
              autoComplete="current-password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              disabled={isLoading}
            />
          </div>
          
          <button type="submit" className="btn-primary btn-block" disabled={isLoading}>
            {isLoading ? 'Connexion en cours...' : 'Se connecter'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default LoginPage;
