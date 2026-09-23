import { useState, useEffect, useRef } from 'react';
import { aiService } from '../../services/aiService';
import api from '../../services/api';
import { toast } from 'react-hot-toast';
import { 
  FiFileText, FiUpload, FiRefreshCw, FiTrash2, 
  FiCheckCircle, FiAlertCircle, FiClock, FiFile, FiDownload, FiX, FiShield
} from 'react-icons/fi';
import './DocumentManagementPage.css';

const DocumentManagementPage = () => {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [showErrorModal, setShowErrorModal] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [busy, setBusy] = useState(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchDocuments();
    const interval = setInterval(fetchDocuments, 5000); // Rafraîchissement automatique des statuts
    return () => clearInterval(interval);
  }, []);

  const fetchDocuments = async () => {
    try {
      const data = await aiService.getDocuments();
      setDocuments(data || []);
      setLoadError('');
    } catch (error) {
      setLoadError(error.response?.data?.message || 'Documents indisponibles. Nouvelle tentative automatique.');
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
    } catch {
      toast.error('Erreur lors de la suppression');
    }
  };

  const handleReindex = async (id, name) => {
    try {
      await aiService.reindexDocument(id);
      toast.success(`Réindexation lancée pour "${name}"`);
      fetchDocuments();
    } catch {
      toast.error('Erreur lors de la réindexation');
    }
  };

  const toggleApproval = async (doc) => {
    setBusy(doc.id);
    try {
      await api.patch(`/ai/documents/${doc.id}/approval`, { approved: !doc.approved });
      await fetchDocuments();
      toast.success(doc.approved ? 'Document retiré des sources autorisées' : 'Document approuvé pour le chauffeur');
    } catch (error) { toast.error(error.response?.data?.message || "Impossible de modifier l'approbation"); }
    finally { setBusy(null); }
  };

  const downloadSource = async (doc) => {
    try {
      const response = await api.get(`/ai/documents/${doc.id}/source`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const link = document.createElement('a'); link.href = url; link.download = doc.originalName;
      document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { toast.error(error.response?.data?.message || 'Source indisponible'); }
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
          <span className="status-badge docs-status-success">
            <FiCheckCircle aria-hidden="true" /> Indexé
          </span>
        );
      case 'TEXT_READY':
        return <span className="status-badge docs-status-warning"><FiFileText aria-hidden="true" /> Extraits disponibles</span>;
      case 'PROCESSING':
        return (
          <span className="status-badge docs-status-warning">
            <span className="docs-spinner"><FiRefreshCw aria-hidden="true" /></span> En cours
          </span>
        );
      case 'FAILED':
        return (
          <span className="status-badge docs-status-danger">
            <FiAlertCircle aria-hidden="true" /> Échec
          </span>
        );
      default:
        return (
          <span className="status-badge docs-status-neutral">
            <FiClock aria-hidden="true" /> Téléversé
          </span>
        );
    }
  };

  return (
    <div className="document-page">
      <div className="page-header flex-between">
        <div>
          <h1>Base documentaire</h1>
          <p>Les consignes et procédures approuvées pour l’assistant des chauffeurs.</p>
        </div>
        <div className="docs-upload-action">
          <button type="button" className="btn-primary" disabled={uploading} onClick={() => fileInputRef.current?.click()}>
            <FiUpload aria-hidden="true" />
            {uploading ? 'Téléversement…' : 'Ajouter un document'}
          </button>
          <input ref={fileInputRef} type="file" accept=".pdf,.docx,.txt,.md" onChange={handleFileUpload} hidden disabled={uploading} aria-label="Document à téléverser" />
          <span>PDF, DOCX, TXT ou Markdown</span>
        </div>
      </div>

      <div className="docs-summary-grid" aria-label="État de la base documentaire">
        <div className="card docs-summary-card">
          <span className="docs-summary-icon"><FiFileText aria-hidden="true" /></span>
          <div><span className="docs-summary-label">Documents</span><strong>{loading || loadError ? '—' : documents.length}</strong></div>
        </div>
        <div className="card docs-summary-card">
          <span className="docs-summary-icon docs-summary-icon-green"><FiShield aria-hidden="true" /></span>
          <div><span className="docs-summary-label">Sources approuvées</span><strong>{loading || loadError ? '—' : documents.filter(d => d.approved).length}</strong></div>
        </div>
        <div className="card docs-summary-card">
          <span className="docs-summary-icon docs-summary-icon-green"><FiCheckCircle aria-hidden="true" /></span>
          <div><span className="docs-summary-label">Index sémantique prêt</span><strong>{loading || loadError ? '—' : documents.filter(d => d.semanticIndexReady).length}</strong></div>
        </div>
        <div className="card docs-summary-card">
          <span className="docs-summary-icon"><FiFile aria-hidden="true" /></span>
          <div><span className="docs-summary-label">Passages extraits</span><strong>{loading || loadError ? '—' : documents.reduce((sum, d) => sum + (d.chunkCount || 0), 0)}</strong></div>
        </div>
      </div>

      <div className="docs-guidance">
        <FiShield aria-hidden="true" />
        <p>Vérifiez chaque document avant de l’approuver. Si la recherche sémantique est indisponible, les textes exploitables et approuvés restent accessibles sous forme d’extraits.</p>
      </div>

      {loadError && <div className="docs-load-error" role="alert"><FiAlertCircle aria-hidden="true" /><p>{loadError}</p><button type="button" className="btn-secondary" onClick={fetchDocuments}>Réessayer</button></div>}
      <section className="card docs-library" aria-labelledby="docs-library-title">
        <div className="docs-library-header">
          <div><h2 id="docs-library-title">Documents de la municipalité</h2><p>Approbation et disponibilité des sources</p></div>
          <button type="button" className="btn-secondary docs-refresh-button" onClick={fetchDocuments} disabled={loading}><FiRefreshCw aria-hidden="true" /> Actualiser</button>
        </div>
        {loading ? (
          <div className="empty-state docs-empty-state" role="status">
            <span className="docs-empty-icon docs-spinner"><FiRefreshCw aria-hidden="true" /></span>
            <h3>Chargement des documents…</h3>
            <p>Récupération des sources et de leur état de traitement.</p>
          </div>
        ) : documents.length === 0 ? (
          <div className="empty-state docs-empty-state">
            <span className="docs-empty-icon"><FiFileText aria-hidden="true" /></span>
            <h3>{loadError ? 'Documents indisponibles' : 'Votre base documentaire est prête à accueillir ses sources'}</h3>
            <p>{loadError ? 'Le chargement a échoué. Réessayez pour consulter les documents.' : 'Ajoutez une consigne, un règlement ou une procédure, puis approuvez son utilisation par l’assistant.'}</p>
            {!loadError && <button type="button" className="btn-secondary" onClick={() => fileInputRef.current?.click()} disabled={uploading}><FiUpload aria-hidden="true" /> Ajouter le premier document</button>}
          </div>
        ) : (
          <div className="docs-table-scroll">
            <table className="data-table docs-table">
              <thead>
                <tr>
                  <th scope="col">Document</th>
                  <th scope="col">Disponibilité</th>
                  <th scope="col">Approbation</th>
                  <th scope="col">Ajout</th>
                  <th scope="col" className="docs-actions-heading">Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.id}>
                    <td>
                      <div className="docs-file-cell">
                        <span className="docs-file-icon"><FiFileText aria-hidden="true" /></span>
                        <div>
                          <strong className="docs-file-name">{doc.originalName}</strong>
                          <div className="docs-file-meta"><span>{doc.fileType || 'Document'}</span><span>{formatFileSize(doc.fileSize)}</span></div>
                          {doc.errorMessage && <button type="button" className="docs-error-link" onClick={() => { setSelectedDoc(doc); setShowErrorModal(true); }}><FiAlertCircle aria-hidden="true" /> Voir l’erreur de traitement</button>}
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="docs-cell-stack">
                        {getStatusBadge(doc.status)}
                        <span className="docs-cell-detail">{doc.chunkCount || 0} passage{doc.chunkCount !== 1 ? 's' : ''} extrait{doc.chunkCount !== 1 ? 's' : ''}</span>
                        <span className="docs-cell-detail">{doc.textReady ? 'Texte exploitable' : 'Texte indisponible'}</span>
                        <span className="docs-cell-detail">{doc.semanticIndexReady ? 'Recherche sémantique prête' : 'Recherche sémantique indisponible'}</span>
                      </div>
                    </td>
                    <td>
                      <div className="docs-cell-stack">
                        <span className={`status-badge ${doc.approved ? 'docs-status-success' : 'docs-status-neutral'}`}>
                          {doc.approved ? <FiCheckCircle aria-hidden="true" /> : <FiClock aria-hidden="true" />}{doc.approved ? 'Approuvé' : 'À examiner'}
                        </span>
                        <button type="button" className="btn-secondary docs-approval-button" disabled={busy === doc.id || (!doc.approved && !doc.textReady)} onClick={() => toggleApproval(doc)} title={!doc.approved && !doc.textReady ? 'Le texte doit être exploitable avant l’approbation' : undefined}>
                          {busy === doc.id ? 'Mise à jour…' : doc.approved ? 'Retirer l’approbation' : 'Approuver'}
                        </button>
                      </div>
                    </td>
                    <td>
                      <div className="docs-cell-stack">
                        <span>{doc.createdAt ? new Date(doc.createdAt).toLocaleDateString('fr-FR') : 'Date non renseignée'}</span>
                        <span className="docs-cell-detail">{doc.uploadedBy || 'Auteur non renseigné'}</span>
                      </div>
                    </td>
                    <td>
                      <div className="docs-row-actions">
                        <button type="button" className="btn-secondary docs-icon-button" onClick={() => downloadSource(doc)} title="Télécharger la source" aria-label={`Télécharger la source : ${doc.originalName}`}><FiDownload aria-hidden="true" /></button>
                        <button type="button" className="btn-secondary docs-icon-button" onClick={() => handleReindex(doc.id, doc.originalName)} title="Réindexer le document" aria-label={`Réindexer : ${doc.originalName}`}><FiRefreshCw aria-hidden="true" /></button>
                        <button type="button" className="btn-secondary docs-icon-button docs-delete-button" onClick={() => handleDelete(doc.id, doc.originalName)} title="Supprimer le document" aria-label={`Supprimer : ${doc.originalName}`}><FiTrash2 aria-hidden="true" /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {showErrorModal && selectedDoc && (
        <div className="modal-overlay document-error-modal" onKeyDown={(event) => { if (event.key === 'Escape') setShowErrorModal(false); }}>
          <div className="modal-content" role="dialog" aria-modal="true" aria-labelledby="document-error-title" aria-describedby="document-error-details">
            <div className="modal-header">
              <h2 id="document-error-title"><FiAlertCircle aria-hidden="true" /> Erreur de traitement</h2>
              <button type="button" className="close-btn" aria-label="Fermer l’erreur de traitement" onClick={() => setShowErrorModal(false)}><FiX aria-hidden="true" /></button>
            </div>
            <div className="modal-body">
              <p className="docs-error-filename">{selectedDoc.originalName}</p>
              <div id="document-error-details" className="docs-error-details">{selectedDoc.errorMessage || 'Erreur inconnue lors du traitement du document.'}</div>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn-secondary" autoFocus onClick={() => setShowErrorModal(false)}>Fermer</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DocumentManagementPage;
