import { useState, useEffect } from 'react';
import { 
  FiFileText, 
  FiUploadCloud, 
  FiCheckCircle, 
  FiAlertTriangle, 
  FiXCircle, 
  FiUserCheck, 
  FiSearch, 
  FiEye, 
  FiAward,
  FiCalendar,
  FiMail,
  FiPhone,
  FiBriefcase,
  FiShield,
  FiX,
  FiPlus,
  FiCreditCard,
  FiBarChart2,
  FiClock,
  FiTruck,
  FiCheckSquare
} from 'react-icons/fi';
import toast from 'react-hot-toast';
import { aiService } from '../../services/aiService';

export default function CandidaturesPage() {
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [selectedCandidate, setSelectedCandidate] = useState(null);
  const [filterRec, setFilterRec] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    loadCandidates();
  }, []);

  const loadCandidates = async () => {
    try {
      setLoading(true);
      const data = await aiService.getCandidates();
      setCandidates(data);
    } catch (err) {
      console.error('Erreur chargement candidatures:', err);
      toast.error('Erreur lors du chargement des candidatures');
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      toast.error('Veuillez importer un fichier au format PDF.');
      return;
    }

    try {
      setUploading(true);
      const toastId = toast.loading('Analyse du CV par l’IA en cours...');
      const newCandidate = await aiService.uploadCv(file);
      toast.dismiss(toastId);
      toast.success(`CV de ${newCandidate.nom || 'Candidat'} analysé avec succès ! Score : ${newCandidate.scoreCompatibilite}%`);
      await loadCandidates();
      setSelectedCandidate(newCandidate);
      setNotes(newCandidate.notesSuperviseur || '');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Erreur lors de l’analyse du CV');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const handleDecision = async (statut) => {
    if (!selectedCandidate) return;
    try {
      const updated = await aiService.updateCandidateDecision(selectedCandidate.id, statut, notes);
      setSelectedCandidate(updated);
      await loadCandidates();
      
      if (statut === 'ACCEPTE') {
        toast.success(`Candidat accepté ! Un compte agent a été créé avec mot de passe (CIN).`);
      } else if (statut === 'EN_ENTRETIEN') {
        toast.success(`Candidat convoqué en entretien.`);
      } else {
        toast.error(`Candidat refusé.`);
      }
    } catch (err) {
      toast.error('Erreur lors de la mise à jour de la décision');
    }
  };

  // Stats
  const totalCount = candidates.length;
  const recommendedCount = candidates.filter(c => c.recommandation === 'RECOMMANDE').length;
  const toExamineCount = candidates.filter(c => c.recommandation === 'A_EXAMINER').length;
  const acceptedCount = candidates.filter(c => c.statut === 'ACCEPTE').length;

  const filtered = candidates.filter((c) => {
    const matchesRec = filterRec === 'ALL' || c.recommandation === filterRec || (filterRec === 'ACCEPTE' && c.statut === 'ACCEPTE');
    const name = `${c.nom || ''} ${c.prenom || ''} ${c.email || ''} ${c.cvFileName || ''} ${c.typePermis || ''}`.toLowerCase();
    const matchesSearch = name.includes(searchTerm.toLowerCase());
    return matchesRec && matchesSearch;
  });

  const getScoreBadge = (score) => {
    if (score >= 70) {
      return <span className="status-badge active" style={{ background: '#dcfce7', color: '#15803d', fontWeight: 'bold' }}>{score}% (Élevé)</span>;
    }
    if (score >= 40) {
      return <span className="status-badge" style={{ background: '#fef3c7', color: '#b45309', fontWeight: 'bold' }}>{score}% (Moyen)</span>;
    }
    return <span className="status-badge inactive" style={{ background: '#fee2e2', color: '#b91c1c', fontWeight: 'bold' }}>{score}% (Faible)</span>;
  };

  const getRecBadge = (rec) => {
    if (rec === 'RECOMMANDE') {
      return (
        <span className="status-badge active">
          <FiCheckCircle style={{ marginRight: '4px' }} /> Recommandé
        </span>
      );
    }
    if (rec === 'A_EXAMINER') {
      return (
        <span className="status-badge" style={{ background: '#fff7ed', color: '#c2410c', border: '1px solid #ffedd5' }}>
          <FiAlertTriangle style={{ marginRight: '4px' }} /> À examiner
        </span>
      );
    }
    return (
      <span className="status-badge inactive">
        <FiXCircle style={{ marginRight: '4px' }} /> Peu compatible
      </span>
    );
  };

  const getStatusBadge = (statut) => {
    if (statut === 'ACCEPTE') {
      return (
        <span className="status-badge active" style={{ background: '#10b981', color: '#ffffff' }}>
          <FiUserCheck style={{ marginRight: '4px' }} /> Accepté (Agent)
        </span>
      );
    }
    if (statut === 'REJETE') {
      return (
        <span className="status-badge inactive">
          <FiXCircle style={{ marginRight: '4px' }} /> Rejeté
        </span>
      );
    }
    if (statut === 'EN_ENTRETIEN') {
      return (
        <span className="status-badge" style={{ background: '#e0e7ff', color: '#4338ca' }}>
          <FiCalendar style={{ marginRight: '4px' }} /> En Entretien
        </span>
      );
    }
    return (
      <span className="status-badge" style={{ background: '#f1f5f9', color: '#64748b' }}>
        <FiClock style={{ marginRight: '4px' }} /> En Attente
      </span>
    );
  };

  const getInitials = (c) => {
    if (c.prenom && c.nom) return `${c.prenom[0]}${c.nom[0]}`.toUpperCase();
    if (c.nom) return c.nom.substring(0, 2).toUpperCase();
    return 'CV';
  };

  const getCriteriaSections = (c) => {
    const isCinValid = c.cin && /^[0-9]{8}$/.test(c.cin);
    const isPhoneValid = c.telephone && /^[2-9][0-9]{7}$/.test(c.telephone.replace(/[^0-9]/g, '').slice(-8));
    const hasPermis = c.typePermis && (c.typePermis.toUpperCase().includes('B') || c.typePermis.toUpperCase().includes('C'));
    const exp = c.anneesExperience || 0;
    const hasChauffeur = !!c.experienceChauffeur;
    const hasCollecte = !!c.experienceCollecte;
    const hasCertif = !!c.certifications || !!c.formations;

    return [
      {
        category: 'EXPERIENCE PROFESSIONNELLE',
        items: [
          {
            icon: FiBriefcase,
            title: "Durée d'expérience professionnelle (≥ 2 ans obligatoire)",
            pts: exp >= 5 ? 25 : exp >= 2 ? 20 : exp >= 1 ? 10 : 0,
            max: 25,
            desc: exp >= 2 ? `${exp} ans d'expérience (Conforme)` : exp > 0 ? `${exp} an (Insuffisant)` : 'Expérience non renseignée'
          },
          {
            icon: FiTruck,
            title: 'Expérience Chauffeur Poids Lourds & Collecte (BOM)',
            pts: (hasChauffeur ? 10 : 0) + (hasCollecte ? 10 : 0),
            max: 20,
            desc: hasChauffeur && hasCollecte ? 'Conduite poids lourds & collecte déchets validées' : hasChauffeur ? 'Conduite poids lourds validée' : hasCollecte ? 'Collecte déchets validée' : 'Non mentionnée'
          }
        ]
      },
      {
        category: 'FORMATIONS ET CERTIFICATIONS',
        items: [
          {
            icon: FiShield,
            title: 'Formations Sécurité Routière & Prévention',
            pts: hasCertif ? 15 : 0,
            max: 15,
            desc: hasCertif ? 'Attestations et formations de sécurité enregistrées' : 'Aucune formation sécurité détectée'
          },
          {
            icon: FiAward,
            title: 'Certifications Professionnelles (FIMO / FCOS)',
            pts: hasCertif ? 10 : 0,
            max: 10,
            desc: hasCertif ? 'Certifications professionnelles à jour' : 'Non mentionnées'
          }
        ]
      },
      {
        category: 'COMPETENCES ET DISPONIBILITE',
        items: [
          {
            icon: FiCreditCard,
            title: 'Permis B ou C Minimum (Obligatoire)',
            pts: hasPermis ? 20 : 0,
            max: 20,
            desc: hasPermis ? `Permis ${c.typePermis} (Valide)` : 'Aucun permis B ou C'
          },
          {
            icon: FiClock,
            title: 'Disponibilité & Aptitudes Métier',
            pts: (c.disponibilite ? 5 : 0) + (isPhoneValid ? 5 : 0),
            max: 10,
            desc: `Disponibilité : ${c.disponibilite || 'Immédiate'}`
          }
        ]
      }
    ];
  };

  const getFieldStatusTag = (val, isValid, label) => {
    if (!val) {
      return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', background: '#fff7ed', color: '#c2410c', padding: '2px 8px', borderRadius: '4px', fontSize: '0.78rem', fontWeight: 'bold' }}>
          🟠 MANQUANT
        </span>
      );
    }
    if (isValid === false) {
      return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', background: '#fee2e2', color: '#b91c1c', padding: '2px 8px', borderRadius: '4px', fontSize: '0.78rem', fontWeight: 'bold' }}>
          🔴 INVALIDE
        </span>
      );
    }
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', background: '#dcfce7', color: '#15803d', padding: '2px 8px', borderRadius: '4px', fontSize: '0.78rem', fontWeight: 'bold' }}>
        🟢 VALIDE
      </span>
    );
  };

  return (
    <div className="agent-page">
      {/* Header Standard comme les autres pages */}
      <div className="page-header flex-between">
        <div>
          <h1>Espace des candidatures</h1>
          <p>Analyse automatique des CVs, extraction de compétences et scoring de compatibilité.</p>
        </div>
        <label className="btn-primary" style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center' }}>
          <FiUploadCloud style={{ marginRight: '8px' }} />
          {uploading ? 'Analyse en cours...' : 'Importer un CV (PDF)'}
          <input type="file" accept=".pdf" className="hidden" onChange={handleFileUpload} disabled={uploading} style={{ display: 'none' }} />
        </label>
      </div>

      {/* Main Card */}
      <div className="card">
        {/* Table Toolbar */}
        <div className="table-toolbar flex-between" style={{ display: 'flex', justifyContent: 'space-between', gap: '16px', flexWrap: 'wrap', marginBottom: '20px' }}>
          <div className="search-box">
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Rechercher candidat par nom, email, permis..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <select 
            value={filterRec} 
            onChange={(e) => setFilterRec(e.target.value)}
            className="form-select filter-select"
            style={{ maxWidth: '240px' }}
          >
            <option value="ALL">Toutes les recommandations</option>
            <option value="RECOMMANDE">🟢 Recommandé</option>
            <option value="A_EXAMINER">🟠 À examiner</option>
            <option value="PEU_COMPATIBLE">🔴 Peu compatible</option>
            <option value="ACCEPTE">✅ Acceptés (Agents)</option>
          </select>
        </div>

        {/* Data Table Standard */}
        <div className="table-container">
          {loading ? (
            <div className="loading-state">Chargement des candidatures...</div>
          ) : filtered.length === 0 ? (
            <div className="empty-state">Aucune candidature trouvée. Importez un CV au format PDF.</div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Candidat</th>
                  <th>Permis</th>
                  <th>Expérience</th>
                  <th>Score IA</th>
                  <th>Recommandation</th>
                  <th>Statut</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <div className="user-cell">
                        <div className="avatar-sm" style={{ background: '#3b82f6', color: '#ffffff' }}>
                          {getInitials(c)}
                        </div>
                        <div>
                          <div style={{ fontWeight: '600' }}>
                            {c.prenom || c.nom ? `${c.prenom || ''} ${c.nom || ''}`.trim() : c.cvFileName}
                          </div>
                          <div style={{ fontSize: '0.8rem', color: '#64748b' }}>{c.email || 'Sans email'}</div>
                        </div>
                      </div>
                    </td>

                    <td>
                      <span className="status-badge" style={{ background: '#f1f5f9', color: '#0369a1', fontWeight: 'bold' }}>
                        {c.typePermis || 'Non renseigné'}
                      </span>
                    </td>

                    <td>{c.anneesExperience != null ? `${c.anneesExperience} ans` : 'N/C'}</td>
                    <td>{getScoreBadge(c.scoreCompatibilite || 0)}</td>
                    <td>{getRecBadge(c.recommandation)}</td>
                    <td>{getStatusBadge(c.statut)}</td>

                    <td className="actions-cell" style={{ textAlign: 'right' }}>
                      <button 
                        className="btn-secondary" 
                        onClick={() => { setSelectedCandidate(c); setNotes(c.notesSuperviseur || ''); }}
                        style={{ padding: '6px 12px', fontSize: '0.85rem' }}
                      >
                        <FiEye style={{ marginRight: '6px' }} /> Examiner
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Modal Détail Candidat Standard comme les autres modals du projet */}
      {selectedCandidate && (
        <div className="modal-overlay" onClick={() => setSelectedCandidate(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '650px' }}>
            
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <div className="avatar-sm" style={{ width: '40px', height: '40px', fontSize: '1rem', background: '#3b82f6', color: '#ffffff' }}>
                  {getInitials(selectedCandidate)}
                </div>
                <div>
                  <h2 style={{ margin: 0 }}>
                    {selectedCandidate.prenom || selectedCandidate.nom 
                      ? `${selectedCandidate.prenom || ''} ${selectedCandidate.nom || ''}` 
                      : 'Détail Candidat'}
                  </h2>
                  <small style={{ color: '#64748b' }}>Fichier CV : {selectedCandidate.cvFileName}</small>
                </div>
              </div>
              <button className="close-btn" onClick={() => setSelectedCandidate(null)}><FiX /></button>
            </div>

            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              
              {/* Score et Recommandation */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 'bold', textTransform: 'uppercase' }}>Score de Compatibilité IA</div>
                  <div style={{ marginTop: '4px' }}>{getScoreBadge(selectedCandidate.scoreCompatibilite)}</div>
                </div>
                <div>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 'bold', textTransform: 'uppercase' }}>Recommandation IA</div>
                  <div style={{ marginTop: '4px' }}>{getRecBadge(selectedCandidate.recommandation)}</div>
                </div>
              </div>

              {/* Critères de score par grandes catégories */}
              <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '14px' }}>
                <div style={{ fontWeight: 'bold', fontSize: '0.9rem', marginBottom: '12px', color: '#1e293b', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <FiBarChart2 style={{ color: '#2563eb' }} /> Décomposition des points attribués :
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  {getCriteriaSections(selectedCandidate).map((sec, sIdx) => (
                    <div key={sIdx} style={{ background: '#f8fafc', padding: '10px 12px', borderRadius: '6px', border: '1px solid #f1f5f9' }}>
                      <div style={{ fontSize: '0.78rem', fontWeight: 'bold', color: '#3b82f6', letterSpacing: '0.5px', marginBottom: '8px', textTransform: 'uppercase' }}>
                        {sec.category}
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        {sec.items.map((item, idx) => {
                          const IconComp = item.icon;
                          return (
                            <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '0.83rem', padding: '4px 0', borderBottom: idx < sec.items.length - 1 ? '1px solid #e2e8f0' : 'none' }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <IconComp style={{ color: '#64748b' }} />
                                <div>
                                  <div style={{ fontWeight: '600' }}>{item.title}</div>
                                  <div style={{ fontSize: '0.78rem', color: '#64748b' }}>{item.desc}</div>
                                </div>
                              </div>
                              <span style={{ fontWeight: 'bold', color: item.pts > 0 ? '#16a34a' : '#94a3b8', whiteSpace: 'nowrap' }}>
                                +{item.pts} / {item.max} pts
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Résumé IA */}
              {selectedCandidate.analyseIa && (
                <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', padding: '12px', borderRadius: '8px', color: '#1e3a8a', fontSize: '0.85rem' }}>
                  <strong style={{ display: 'block', marginBottom: '4px' }}>🤖 Synthèse de l'Analyse IA :</strong>
                  {selectedCandidate.analyseIa}
                </div>
              )}

              {/* Informations Personnelles avec statut des champs */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '0.85rem', background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>CIN Tunisienne :</strong>
                    {getFieldStatusTag(selectedCandidate.cin, selectedCandidate.cin && /^[0-9]{8}$/.test(selectedCandidate.cin))}
                  </div>
                  <span style={{ color: '#475569' }}>{selectedCandidate.cin || 'Non renseignée'}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>N° Permis :</strong>
                    {getFieldStatusTag(selectedCandidate.numeroPermis, !!selectedCandidate.numeroPermis)}
                  </div>
                  <span style={{ color: '#475569' }}>{selectedCandidate.numeroPermis || 'Non renseigné'}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>Type de Permis :</strong>
                    {getFieldStatusTag(selectedCandidate.typePermis, selectedCandidate.typePermis && (selectedCandidate.typePermis.includes('B') || selectedCandidate.typePermis.includes('C')))}
                  </div>
                  <span style={{ color: '#475569' }}>{selectedCandidate.typePermis || 'Non renseigné'}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>Expérience :</strong>
                    {getFieldStatusTag(selectedCandidate.anneesExperience != null ? `${selectedCandidate.anneesExperience} ans` : null, selectedCandidate.anneesExperience != null && selectedCandidate.anneesExperience >= 2)}
                  </div>
                  <span style={{ color: '#475569' }}>{selectedCandidate.anneesExperience != null ? `${selectedCandidate.anneesExperience} ans` : 'Non renseignée'}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>Téléphone :</strong>
                    {getFieldStatusTag(selectedCandidate.telephone, selectedCandidate.telephone && /^[2-9][0-9]{7}$/.test(selectedCandidate.telephone.replace(/[^0-9]/g, '').slice(-8)))}
                  </div>
                  <span style={{ color: '#475569' }}>{selectedCandidate.telephone || 'Non renseigné'}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>Email :</strong>
                    {getFieldStatusTag(selectedCandidate.email, selectedCandidate.email && selectedCandidate.email.includes('@'))}
                  </div>
                  <span style={{ color: '#475569' }}>{selectedCandidate.email || 'Non renseigné'}</span>
                </div>
              </div>

              {/* Zone Notes Superviseur */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label style={{ fontWeight: 'bold', fontSize: '0.85rem' }}>Remarques / Motif du superviseur :</label>
                <textarea
                  rows="2"
                  className="form-control"
                  style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.85rem' }}
                  placeholder="Saisissez vos observations..."
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                ></textarea>
              </div>

            </div>

            {/* Modal Footer Standard */}
            <div className="modal-footer" style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', padding: '14px 20px', borderTop: '1px solid #e2e8f0' }}>
              <button 
                type="button" 
                className="btn-secondary" 
                onClick={() => handleDecision('REJETE')}
                style={{ background: '#fee2e2', color: '#991b1b', border: '1px solid #fca5a5' }}
              >
                <FiXCircle style={{ marginRight: '6px' }} /> Rejeter
              </button>

              <button 
                type="button" 
                className="btn-secondary" 
                onClick={() => handleDecision('EN_ENTRETIEN')}
              >
                <FiCalendar style={{ marginRight: '6px' }} /> Entretien
              </button>

              <button 
                type="button" 
                className="btn-primary" 
                onClick={() => handleDecision('ACCEPTE')}
                style={{ background: '#10b981', borderColor: '#059669' }}
              >
                <FiUserCheck style={{ marginRight: '6px' }} /> Accepter le Candidat
              </button>
            </div>

          </div>
        </div>
      )}
    </div>
  );
}
