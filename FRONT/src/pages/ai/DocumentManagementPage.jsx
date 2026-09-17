import { useState, useEffect } from 'react';
import { aiService } from '../../services/aiService';
import { toast } from 'react-hot-toast';
import { 
  FiFileText, FiUpload, FiRefreshCw, FiTrash2, 
  FiCheckCircle, FiAlertCircle, FiClock, FiFile, FiInfo
} from 'react-icons/fi';

const DocumentManagementPage = () => {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [showErrorModal, setShowErrorModal] = useState(false);

  useEffect(() => {
    fetchDocuments();
    const interval = setInterval(fetchDocuments, 5000); // Rafraîchissement automatique des statuts
    return () => clearInterval(interval);
  }, []);

  const fetchDocuments = async () => {
    try {
      const data = await aiService.getDocuments();
      setDocuments(data || []);
    } catch (error) {
      console.error('Erreur chargement documents:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    // Validation extension
    const ext = file.name.split('.').pop().toLowerCase();
    if (!['pdf', 'docx', 'txt', 'md'].includes(ext)) {
      toast.error('Format non supporté. Veuillez choisir un fichier PDF, DOCX, TXT ou MD.');
      return;
    }

    try {
      setUploading(true);
      await aiService.uploadDocument(file);
      toast.success(`Document ${file.name} téléversé avec succès. Indexation en cours...`);
      fetchDocuments();
    } catch (error) {
      console.error('Erreur upload document:', error);
      toast.error("Échec du téléversement du document.");
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`Voulez-vous vraiment supprimer le document "${name}" ?`)) return;
    try {
      await aiService.deleteDocument(id);
      toast.success('Document et tranches vectorielles supprimés');
      fetchDocuments();
    } catch (error) {
      toast.error('Erreur lors de la suppression');
    }
  };

  const handleReindex = async (id, name) => {
    try {
      await aiService.reindexDocument(id);
      toast.success(`Réindexation lancée pour "${name}"`);
      fetchDocuments();
    } catch (error) {
      toast.error('Erreur lors de la réindexation');
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'INDEXED':
        return (
          <span className="badge badge-success" style={{ background: '#dcfce7', color: '#15803d', padding: '6px 12px', borderRadius: '20px', display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: '600' }}>
            <FiCheckCircle /> INDEXÉ
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="badge badge-warning" style={{ background: '#fef3c7', color: '#b45309', padding: '6px 12px', borderRadius: '20px', display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: '600' }}>
            <FiRefreshCw className="animate-spin" /> EN COURS
          </span>
        );
      case 'FAILED':
        return (
          <span className="badge badge-danger" style={{ background: '#fee2e2', color: '#b91c1c', padding: '6px 12px', borderRadius: '20px', display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: '600' }}>
            <FiAlertCircle /> ÉCHEC
          </span>
        );
      default:
        return (
          <span className="badge badge-secondary" style={{ background: '#f3f4f6', color: '#4b5563', padding: '6px 12px', borderRadius: '20px', display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: '600' }}>
            <FiClock /> TÉLÉVERSÉ
          </span>
        );
    }
  };

  return (
    <div className="page-container" style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', background: 'white', padding: '20px 24px', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: '700', color: '#111827', margin: 0, display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '28px' }}>📚</span> Base Documentaire
          </h1>
          <p style={{ color: '#6b7280', marginTop: '4px', marginBottom: 0, fontSize: '14px' }}>
            Gérez les documents de la municipalité (règlements, consignes, procédures) alimentant la mémoire de l'Assistant.
          </p>
        </div>

        <div>
          <label className="btn btn-primary" style={{ cursor: 'pointer', background: '#2563eb', color: 'white', padding: '10px 18px', borderRadius: '8px', fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '8px', boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)' }}>
            <FiUpload />
            {uploading ? 'Téléversement...' : '+ Ajouter un document'}
            <input 
              type="file" 
              accept=".pdf,.docx,.txt,.md" 
              onChange={handleFileUpload} 
              style={{ display: 'none' }} 
              disabled={uploading}
            />
          </label>
        </div>
      </div>

      {/* Cartes d'information rapide */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <div style={{ background: 'white', padding: '20px', borderRadius: '12px', borderLeft: '4px solid #2563eb', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ fontSize: '13px', color: '#6b7280', fontWeight: '600' }}>TOTAL DOCUMENTS</div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: '#111827', marginTop: '6px' }}>{documents.length}</div>
        </div>
        <div style={{ background: 'white', padding: '20px', borderRadius: '12px', borderLeft: '4px solid #16a34a', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ fontSize: '13px', color: '#6b7280', fontWeight: '600' }}>DOCUMENTS INDEXÉS</div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: '#16a34a', marginTop: '6px' }}>
            {documents.filter(d => d.status === 'INDEXED').length}
          </div>
        </div>
        <div style={{ background: 'white', padding: '20px', borderRadius: '12px', borderLeft: '4px solid #9333ea', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ fontSize: '13px', color: '#6b7280', fontWeight: '600' }}>TRANCHES (CHUNKS) VECTORIELLES</div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: '#9333ea', marginTop: '6px' }}>
            {documents.reduce((sum, d) => sum + (d.chunkCount || 0), 0)}
          </div>
        </div>
      </div>

      {/* Tableau des documents */}
      <div style={{ background: 'white', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)', overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: '#6b7280' }}>
            <FiRefreshCw className="animate-spin" style={{ fontSize: '24px', marginBottom: '8px' }} />
            <p>Chargement des documents de la base IA...</p>
          </div>
        ) : documents.length === 0 ? (
          <div style={{ padding: '60px 20px', textAlign: 'center', color: '#6b7280' }}>
            <FiFileText style={{ fontSize: '48px', color: '#9ca3af', marginBottom: '12px' }} />
            <h3 style={{ fontSize: '18px', color: '#374151', margin: '0 0 6px 0' }}>Aucun document indexé</h3>
            <p style={{ fontSize: '14px', margin: 0 }}>Ajoutez des fichiers PDF, DOCX ou TXT pour alimenter le RAG SmartWaste.</p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
              <thead>
                <tr style={{ background: '#f9fafb', borderBottom: '1px solid #e5e7eb', color: '#4b5563', textTransform: 'uppercase', fontSize: '12px', letterSpacing: '0.05em' }}>
                  <th style={{ padding: '16px 20px' }}>Nom du document</th>
                  <th style={{ padding: '16px 20px' }}>Type</th>
                  <th style={{ padding: '16px 20px' }}>Taille</th>
                  <th style={{ padding: '16px 20px' }}>Statut Indexation</th>
                  <th style={{ padding: '16px 20px' }}>Chunks</th>
                  <th style={{ padding: '16px 20px' }}>Ajouté par</th>
                  <th style={{ padding: '16px 20px' }}>Date</th>
                  <th style={{ padding: '16px 20px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.id} style={{ borderBottom: '1px solid #f3f4f6' }}>
                    <td style={{ padding: '16px 20px', fontWeight: '600', color: '#111827', display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <FiFile style={{ color: '#2563eb', fontSize: '18px' }} />
                      <div>
                        <div>{doc.originalName}</div>
                        {doc.errorMessage && (
                          <div style={{ fontSize: '12px', color: '#dc2626', marginTop: '2px', cursor: 'pointer' }} onClick={() => { setSelectedDoc(doc); setShowErrorModal(true); }}>
                            ⚠️ Voir l'erreur d'indexation
                          </div>
                        )}
                      </div>
                    </td>
                    <td style={{ padding: '16px 20px' }}>
                      <span style={{ background: '#eff6ff', color: '#1d4ed8', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: '700' }}>
                        {doc.fileType}
                      </span>
                    </td>
                    <td style={{ padding: '16px 20px', color: '#6b7280' }}>{formatFileSize(doc.fileSize)}</td>
                    <td style={{ padding: '16px 20px' }}>{getStatusBadge(doc.status)}</td>
                    <td style={{ padding: '16px 20px', fontWeight: '600', color: '#4b5563' }}>{doc.chunkCount || 0}</td>
                    <td style={{ padding: '16px 20px', color: '#6b7280' }}>{doc.uploadedBy || 'Admin'}</td>
                    <td style={{ padding: '16px 20px', color: '#6b7280', fontSize: '13px' }}>
                      {doc.createdAt ? new Date(doc.createdAt).toLocaleDateString('fr-FR') : '-'}
                    </td>
                    <td style={{ padding: '16px 20px', textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px' }}>
                        <button 
                          onClick={() => handleReindex(doc.id, doc.originalName)}
                          title="Réindexer le document"
                          style={{ padding: '8px', background: '#f3f4f6', border: 'none', borderRadius: '6px', cursor: 'pointer', color: '#4b5563' }}
                        >
                          <FiRefreshCw />
                        </button>
                        <button 
                          onClick={() => handleDelete(doc.id, doc.originalName)}
                          title="Supprimer"
                          style={{ padding: '8px', background: '#fee2e2', border: 'none', borderRadius: '6px', cursor: 'pointer', color: '#dc2626' }}
                        >
                          <FiTrash2 />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal d'affichage de l'erreur d'indexation */}
      {showErrorModal && selectedDoc && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyCenter: 'center', zIndex: 1000, padding: '20px' }}>
          <div style={{ background: 'white', borderRadius: '12px', maxWidth: '600px', width: '100%', padding: '24px', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>
            <h3 style={{ marginTop: 0, color: '#dc2626', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <FiAlertCircle /> Erreur d'indexation RAG
            </h3>
            <p style={{ fontWeight: '600', color: '#374151' }}>Fichier : {selectedDoc.originalName}</p>
            <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', borderRadius: '8px', padding: '16px', color: '#991b1b', fontFamily: 'monospace', fontSize: '13px', whiteSpace: 'pre-wrap', maxHeight: '200px', overflowY: 'auto' }}>
              {selectedDoc.errorMessage || 'Erreur inconnue lors du traitement du document.'}
            </div>
            <div style={{ marginTop: '20px', textAlign: 'right' }}>
              <button onClick={() => setShowErrorModal(false)} style={{ background: '#374151', color: 'white', padding: '8px 16px', border: 'none', borderRadius: '6px', cursor: 'pointer' }}>
                Fermer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DocumentManagementPage;
