import { useEffect, useState } from 'react';
import { FiUploadCloud, FiSearch, FiEye, FiX, FiDownload, FiCheckCircle, FiRefreshCw } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { candidateService } from '../../services/candidateService';
import './candidatures.css';

const FIELDS = [
  ['typePermis', 'Catégories de permis', 'text', 'C, CE'],
  ['anneesExperience', 'Années de conduite', 'number', '1.5'],
  ['formationSecurite', 'Formation sécurité pertinente', 'boolean'],
  ['experienceCollecteConfirmee', 'Expérience de collecte', 'boolean'],
  ['nom', 'Nom'], ['prenom', 'Prénom'], ['cin', 'CIN'], ['email', 'Email'], ['telephone', 'Téléphone'],
  ['numeroPermis', 'Numéro de permis'], ['dateExpirationPermis', 'Expiration du permis', 'date'],
  ['adresse', 'Adresse'], ['dateNaissance', 'Date de naissance', 'date'], ['age', 'Âge (ans)', 'number', '25'],
  ['experienceChauffeur', 'Expérience de conduite — détail'], ['experienceCollecte', 'Collecte — détail'],
  ['formations', 'Formations'], ['certifications', 'Certifications'],
];
const FIELD_STATES = { MISSING: 'Manquant', UNCERTAIN: 'À vérifier', INVALID: 'Invalide', VERIFIED: 'Vérifié' };
const REQUIRED = { MET: 'Satisfaits', UNMET: 'Non satisfaits', NEEDS_REVIEW: 'À vérifier', NOT_REQUIRED: 'Facultatif' };
const DECISIONS = { EN_ATTENTE: 'En attente', EN_ENTRETIEN: 'Entretien', ACCEPTE: 'Accepté', REJETE: 'Rejeté' };
const RECOMMANDATIONS = {
  RECOMMANDE: { label: 'Recommandé', variant: 'badge-success' },
  A_EXAMINER: { label: 'À examiner', variant: 'badge-warning' },
  PEU_COMPATIBLE: { label: 'Peu compatible', variant: 'badge-danger' },
};
const RECOMMANDATION_RANK = { RECOMMANDE: 0, A_EXAMINER: 1, PEU_COMPATIBLE: 2 };
const EXTRACTION_STATES = {
  EXTRACTED: 'Informations extraites · à vérifier',
  PROVIDER_UNAVAILABLE: 'Extraction IA indisponible',
  INVALID_RESPONSE: 'Réponse IA inexploitable',
  MANUAL_REVIEW_REQUIRED: 'Saisie manuelle requise',
  LEGACY_REVIEW_REQUIRED: 'Ancien dossier à vérifier',
};
const EXTRACTION_FAILURES = {
  PROVIDER_HTTP_ERROR: 'Le fournisseur IA a refusé ou interrompu la demande.',
  PROVIDER_TIMEOUT: 'Le fournisseur IA n’a pas répondu dans le délai prévu.',
  NOT_CONFIGURED: 'Le service IA n’est pas configuré. Vérifiez sa configuration avant de relancer.',
  INVALID_MODEL_CONFIGURATION: 'Le modèle configuré n’est pas disponible. Vérifiez la configuration du service IA.',
  EMPTY_RESPONSE: 'Le fournisseur IA a renvoyé une réponse vide.',
  INVALID_PROVIDER_RESPONSE: 'La réponse du fournisseur IA n’a pas pu être interprétée.',
  INVALID_EXTRACTION_RESPONSE: 'La réponse IA ne contient pas une extraction de CV exploitable.',
  PROVIDER_UNAVAILABLE: 'Le fournisseur IA est momentanément indisponible.',
};
const WEIGHTS = [
  ['licenceWeight', 'licenceRequired', 'Permis accepté'],
  ['experienceWeight', 'experienceRequired', 'Expérience de conduite'],
  ['safetyWeight', 'safetyRequired', 'Formation sécurité'],
  ['collectionWeight', 'collectionRequired', 'Expérience de collecte'],
];
const errorMessage = (error) => error.response?.data?.message || error.message || 'Action impossible. Réessayez.';
const criterionValue = (value) => {
  if (value == null || value === '') return 'Non renseigné';
  if (value === true || value === 'true') return 'Oui';
  if (value === false || value === 'false') return 'Non';
  return value;
};
const canRetryExtraction = (candidate) => ['PROVIDER_UNAVAILABLE', 'INVALID_RESPONSE'].includes(candidate?.extractionStatus);
const extractionFailureMessage = (candidate) => candidate.extractionError
  || EXTRACTION_FAILURES[candidate.extractionFailureCategory]
  || (candidate.extractionStatus === 'INVALID_RESPONSE' ? 'La réponse IA ne contient pas une extraction exploitable.' : 'Le fournisseur IA est indisponible.');

function ExtractionNotice({ candidate }) {
  if (candidate.extractionStatus === 'MANUAL_REVIEW_REQUIRED') {
    return <p role="status" className="candidate-notice">Le document ne contient pas de texte exploitable. Saisissez et vérifiez les informations manuellement.</p>;
  }
  if (!canRetryExtraction(candidate)) return null;
  const httpStatus = candidate.extractionHttpStatus;
  return <div role="status" className="candidate-notice">
    <strong>{EXTRACTION_STATES[candidate.extractionStatus]}</strong>
    <p>{extractionFailureMessage(candidate)}</p>
    {Number.isInteger(httpStatus) && httpStatus >= 100 && httpStatus <= 599 ? <small>Code de réponse du service distant : HTTP {httpStatus}.</small> : null}
    <p>Le CV est enregistré. Vous pouvez réessayer l’extraction ou vérifier les champs manuellement. Les informations déjà vérifiées et la décision enregistrée sont conservées.</p>
  </div>;
}

function reviewFields(candidate) {
  return Object.fromEntries(FIELDS.map(([key]) => [key, {
    value: candidate.fieldEvidence?.[key]?.value ?? candidate[key]?.toString() ?? '',
    status: candidate.fieldEvidence?.[key]?.status || (candidate[key] == null ? 'MISSING' : 'UNCERTAIN'),
    reviewNote: candidate.fieldEvidence?.[key]?.reviewNote || '',
    edited: false,
  }]));
}

// Fields the AI extracted that nobody has looked at yet; the only thing a "Confirmer" click promotes.
function pendingFields(candidate) {
  return Object.entries(candidate.fieldEvidence || {}).filter(([, field]) => field.status === 'UNCERTAIN' && field.value);
}
function confirmPayload(candidate) {
  return Object.fromEntries(Object.entries(candidate.fieldEvidence || {}).map(([key, field]) =>
    [key, field.status === 'UNCERTAIN' && field.value ? { value: field.value, status: 'VERIFIED', reviewNote: field.reviewNote } : field]));
}

function Score({ candidate }) {
  return <span className="status-badge">
    {candidate.scoreCompatibilite == null ? 'À réévaluer' : `${candidate.scoreCompatibilite}%`}
    {candidate.scoreProvisional && candidate.scoreCompatibilite != null ? ' · provisoire' : ''}
  </span>;
}

function Recommandation({ candidate }) {
  const info = RECOMMANDATIONS[candidate.recommandation];
  if (!info) return <span className="status-badge badge-default">À évaluer</span>;
  return <span className={`status-badge ${info.variant}`}>{info.label}</span>;
}

export default function CandidaturesPage() {
  const [candidates, setCandidates] = useState([]);
  const [profile, setProfile] = useState(null);
  const [profileDraft, setProfileDraft] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [selected, setSelected] = useState(null);
  const [fields, setFields] = useState({});
  const [dirty, setDirty] = useState(false);
  const [notes, setNotes] = useState('');
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL');
  const notesDirty = !!selected && notes !== (selected.notesSuperviseur || '');
  const hasPendingReview = Object.values(fields).some((field) => field.status === 'UNCERTAIN' && field.value !== '');

  useEffect(() => {
    let active = true;
    Promise.all([candidateService.list(), candidateService.requirements()])
      .then(([items, requirements]) => {
        if (!active) return;
        setCandidates(items); setProfile(requirements); setProfileDraft(requirements);
      })
      .catch((failure) => { if (active) setError(errorMessage(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selected) return;
    const close = (event) => { if (event.key === 'Escape' && !busy && !dirty && !notesDirty) setSelected(null); };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [selected, busy, dirty, notesDirty]);

  const showCandidate = (candidate) => {
    setSelected(candidate); setFields(reviewFields(candidate)); setNotes(candidate.notesSuperviseur || ''); setDirty(false);
  };
  const updateCandidate = (candidate) => {
    setCandidates((items) => items.some((item) => item.id === candidate.id)
      ? items.map((item) => item.id === candidate.id ? candidate : item) : [candidate, ...items]);
    if (selected?.id === candidate.id) {
      // Reviewing fields or recalculating a score must not discard the supervisor's note draft.
      setSelected(candidate); setFields(reviewFields(candidate)); setDirty(false);
    } else {
      showCandidate(candidate);
    }
  };
  const run = async (name, action) => {
    if (busy) return;
    setBusy(name);
    try { await action(); }
    catch (failure) { toast.error(errorMessage(failure)); }
    finally { setBusy(''); }
  };
  const upload = (event) => {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    if (!/\.(pdf|docx)$/i.test(file.name)) return toast.error('Choisissez un PDF ou un DOCX.');
    run('upload', async () => {
      const candidate = await candidateService.upload(file); updateCandidate(candidate);
      toast.success('CV enregistré. Vérifiez les informations avant de décider.');
    });
  };
  const saveReview = () => run('review', async () => {
    // Editing a field or leaving an AI-proposed value untouched both count as the supervisor
    // having looked at it; anything left MISSING or flagged INVALID stays that way until fixed.
    const payload = Object.fromEntries(Object.entries(fields).map(([key, field]) => {
      const confirmed = field.edited || field.status === 'UNCERTAIN';
      return [key, { value: field.value, status: confirmed ? (field.value === '' ? 'MISSING' : 'VERIFIED') : field.status, reviewNote: field.reviewNote }];
    }));
    updateCandidate(await candidateService.review(selected.id, payload));
    toast.success('Informations enregistrées et score recalculé.');
  });
  const retryExtraction = () => {
    if (!selected || selected.agentId || dirty || busy) return;
    run('reanalyze', async () => {
      try {
        const candidate = await candidateService.reanalyze(selected.id);
        updateCandidate(candidate);
        if (candidate.extractionStatus === 'EXTRACTED') {
          toast.success('Extraction terminée. Vérifiez les nouvelles informations proposées.');
        } else if (candidate.extractionStatus === 'MANUAL_REVIEW_REQUIRED') {
          toast('Ce document nécessite une saisie manuelle.');
        } else {
          toast.error(extractionFailureMessage(candidate));
        }
      } catch (failure) {
        if (failure.code === 'ECONNABORTED' || failure.code === 'ETIMEDOUT') {
          toast.error('Délai d’attente dépassé. Actualisez la page pour vérifier le résultat avant de relancer.');
          return;
        }
        throw failure;
      }
    });
  };
  const decide = (statut) => run('decision', async () => {
    updateCandidate(await candidateService.decision(selected.id, statut, notes));
    toast.success('Décision enregistrée.');
  });
  const confirmCandidate = (candidate) => run('review', async () => {
    updateCandidate(await candidateService.review(candidate.id, confirmPayload(candidate)));
    toast.success('Informations confirmées et score recalculé.');
  });
  const confirmAll = (list) => run('review', async () => {
    for (const candidate of list) {
      // Sequential: the backend has no batch endpoint, and each save must land before the next starts.
      // eslint-disable-next-line no-await-in-loop
      const updated = await candidateService.review(candidate.id, confirmPayload(candidate));
      setCandidates((items) => items.map((item) => item.id === updated.id ? updated : item));
    }
    toast.success(`${list.length} candidature(s) confirmée(s).`);
  });
  const changeField = (key, value) => {
    setDirty(true);
    setFields((current) => ({ ...current, [key]: { ...current[key], value, edited: true } }));
  };
  const filtered = candidates.filter((candidate) => (filter === 'ALL' || candidate.statut === filter)
    && `${candidate.nom || ''} ${candidate.prenom || ''} ${candidate.cvFileName || ''} ${candidate.email || ''}`.toLowerCase().includes(search.toLowerCase()))
    .sort((a, b) => (RECOMMANDATION_RANK[a.recommandation] ?? 1) - (RECOMMANDATION_RANK[b.recommandation] ?? 1)
      || (b.scoreCompatibilite ?? -1) - (a.scoreCompatibilite ?? -1));
  const confirmable = filtered.filter((candidate) => !candidate.agentId && pendingFields(candidate).length > 0);

  return <div className="agent-page candidates-page">
    <div className="page-header flex-between">
      <div><h1>Candidatures</h1><p>Vérifiez les informations des CV et comparez chaque profil aux critères du poste.</p></div>
      <label className="btn-primary">
        <FiUploadCloud /> {busy === 'upload' ? 'Extraction en cours…' : 'Importer un CV'}
        <input type="file" accept=".pdf,.docx" onChange={upload} disabled={!!busy} hidden />
      </label>
    </div>
    {error ? <p role="alert" className="candidate-notice">{error}</p> : null}
    {profileDraft ? <details className="card candidate-profile">
      <summary>Profil du poste · version {profile.version} · {profile.label}</summary>
      <p>Une modification crée une nouvelle version. Les évaluations existantes restent rattachées à leur version.</p>
      <div className="candidate-profile-inputs">
        <label>Intitulé<input value={profileDraft.label} onChange={(e) => setProfileDraft({ ...profileDraft, label: e.target.value })} /></label>
        <label>Permis acceptés, séparés par virgules<input value={profileDraft.acceptedLicences.join(',')}
          onChange={(e) => setProfileDraft({ ...profileDraft, acceptedLicences: e.target.value.split(',') })} /></label>
        <label>Cible d'expérience (années)<input type="number" min="0.1" step="0.1" value={profileDraft.experienceTargetYears}
          onChange={(e) => setProfileDraft({ ...profileDraft, experienceTargetYears: Number(e.target.value) })} /></label>
      </div>
      <div className="table-container"><table className="data-table"><thead><tr><th>Critère</th><th>Points</th><th>Obligatoire</th></tr></thead><tbody>
        {WEIGHTS.map(([weight, required, label]) => <tr key={weight}><td>{label}</td><td><input aria-label={`Points : ${label}`} type="number" min="0" max="100" value={profileDraft[weight]}
          onChange={(e) => setProfileDraft({ ...profileDraft, [weight]: Number(e.target.value) })} /></td>
          <td><input aria-label={`Obligatoire : ${label}`} type="checkbox" checked={profileDraft[required]} onChange={(e) => setProfileDraft({ ...profileDraft, [required]: e.target.checked })} /></td></tr>)}
      </tbody></table></div>
      <p>Les poids totalisent 100. L'expérience est une cible proportionnelle par défaut. Si elle devient obligatoire, aucun point n'est attribué sous son seuil.</p>
      <button className="btn-secondary" disabled={!!busy} onClick={() => run('profile', async () => {
        const saved = await candidateService.saveRequirements(profileDraft); setProfile(saved); setProfileDraft(saved); toast.success(`Profil version ${saved.version} enregistré.`);
      })}>Enregistrer une nouvelle version</button>
    </details> : null}
    <div className="card">
      <div className="table-toolbar flex-between">
        <div className="search-box"><FiSearch className="search-icon" /><input aria-label="Rechercher un candidat" placeholder="Nom, email ou fichier CV…" value={search} onChange={(e) => setSearch(e.target.value)} /></div>
        <div className="candidate-row-actions">
          <select aria-label="Filtrer les décisions" value={filter} onChange={(e) => setFilter(e.target.value)}><option value="ALL">Toutes les décisions</option>{Object.entries(DECISIONS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
          {confirmable.length > 0 ? <button className="btn-secondary" disabled={!!busy} onClick={() => confirmAll(confirmable)}><FiCheckCircle /> Confirmer tout ({confirmable.length})</button> : null}
        </div>
      </div>
      <div className="table-container">{loading ? <p className="loading-state">Chargement…</p> : filtered.length === 0 ? <p className="empty-state">Aucune candidature. Importez un PDF ou DOCX.</p>
        : <table className="data-table"><thead><tr><th>Candidat</th><th>Permis / conduite</th><th>Points confirmés</th><th>Recommandation</th><th>Critères requis</th><th>Décision</th><th>Action</th></tr></thead><tbody>
          {filtered.map((candidate) => <tr key={candidate.id}><td><strong>{`${candidate.prenom || ''} ${candidate.nom || ''}`.trim() || candidate.cvFileName}</strong><small>{candidate.email || 'Email à renseigner'}</small><small>{EXTRACTION_STATES[candidate.extractionStatus] || 'Extraction à vérifier'}</small></td>
            <td>{candidate.typePermis || 'À vérifier'}<small>{candidate.anneesExperience == null ? 'Durée inconnue' : `${candidate.anneesExperience} an(s)`}</small>{candidate.age != null ? <small>{candidate.age} ans</small> : null}</td>
            <td><Score candidate={candidate} /><small>Profil {candidate.requirementsVersion ? `v${candidate.requirementsVersion}` : 'ancien'}</small></td>
            <td><Recommandation candidate={candidate} /></td>
            <td>{REQUIRED[candidate.requiredCriteriaStatus] || 'À vérifier'}</td><td>{DECISIONS[candidate.statut] || candidate.statut}</td>
            <td className="candidate-row-actions">
              <button className="btn-secondary" disabled={!!busy} onClick={() => showCandidate(candidate)}><FiEye /> Examiner</button>
              {!candidate.agentId && pendingFields(candidate).length > 0
                ? <button className="btn-secondary" disabled={!!busy} onClick={() => confirmCandidate(candidate)}><FiCheckCircle /> Confirmer</button> : null}
            </td></tr>)}
        </tbody></table>}</div>
    </div>

    {selected ? <div className="modal-overlay"><div className="modal-content candidate-modal" role="dialog" aria-modal="true" aria-labelledby="candidate-title">
      <div className="modal-header"><div><h2 id="candidate-title">{`${selected.prenom || ''} ${selected.nom || ''}`.trim() || 'Détails du candidat'}</h2><small>{selected.cvFileName}</small></div>
        <button className="close-btn" aria-label="Fermer la candidature" disabled={!!busy} onClick={() => {
          if ((!dirty && !notesDirty) || window.confirm('Fermer sans enregistrer les modifications ?')) setSelected(null);
        }}><FiX /></button></div>
      <div className="modal-body" aria-busy={busy === 'reanalyze'}>
        <div className="candidate-summary"><div><strong>Points confirmés</strong><div><Score candidate={selected} /></div></div>
          <div><strong>Recommandation</strong><div><Recommandation candidate={selected} /></div></div>
          <div><strong>Âge</strong><div>{selected.age != null ? `${selected.age} ans` : 'Non renseigné'}{selected.age != null ? (selected.age >= 20 && selected.age < 35 ? ' · Conforme [20 - 35 ans[' : ' · Hors critère [20 - 35 ans[') : ''}</div></div>
          <div><strong>Critères obligatoires</strong><div>{REQUIRED[selected.requiredCriteriaStatus] || 'À vérifier'}</div></div>
          <div><strong>Profil utilisé</strong><div>{selected.requirementsVersion ? `Version ${selected.requirementsVersion}` : 'Ancien barème'}</div></div></div>
        {selected.detailScore ? <p className="candidate-notice"><strong>Détail scoring :</strong> {selected.detailScore}</p> : null}
        <p className="candidate-notice">Le score est calculé à partir des informations vérifiées ci-dessous. Pour être recommandé, le candidat doit atteindre au moins 70 points et être âgé de 20 ans à moins de 35 ans (≥ 20 et &lt; 35 ans). La décision finale de recrutement reste humaine.</p>
        <ExtractionNotice candidate={selected} />
        <div className="candidate-actions"><button className="btn-secondary" disabled={!!busy} onClick={() => run('download', () => candidateService.download(selected))}><FiDownload /> Télécharger le CV</button>
          <button className="btn-secondary" disabled={!!busy || dirty} onClick={() => run('evaluate', async () => updateCandidate(await candidateService.evaluate(selected.id)))}>Réévaluer avec le profil v{profile?.version || 'courant'}</button>
          {canRetryExtraction(selected) && !selected.agentId ? <button className="btn-secondary" disabled={!!busy || dirty} onClick={retryExtraction} title={dirty ? 'Enregistrez vos corrections avant de relancer l’extraction.' : undefined}><FiRefreshCw aria-hidden="true" /> {busy === 'reanalyze' ? 'Extraction en cours…' : 'Réessayer l’extraction IA'}</button> : null}
        </div>
        {busy === 'reanalyze' ? <p role="status">Le CV enregistré est en cours d’analyse. Cette opération peut prendre environ une minute.</p> : null}
        {canRetryExtraction(selected) && dirty ? <p role="status">Enregistrez vos corrections avant de réessayer l’extraction IA.</p> : null}
        <div className="table-container"><table className="data-table"><thead><tr><th>Critère</th><th>Valeur / vérification</th><th>Points</th><th>Règle</th></tr></thead><tbody>
          {(selected.criterionScores || []).map((criterion) => <tr key={criterion.key}><td>{criterion.label}<small>{criterion.required ? 'Obligatoire' : 'Scoring'}</small></td>
            <td>{criterionValue(criterion.value)}<small>{FIELD_STATES[criterion.evidenceStatus]} · {REQUIRED[criterion.requiredStatus]}</small></td>
            <td>{criterion.points} / {criterion.maxPoints}</td><td>{criterion.explanation}</td></tr>)}
        </tbody></table></div>
        <h3>Informations du candidat</h3><p>Les valeurs proposées par l'IA sont déjà remplies ; corrigez-les si nécessaire. L'extrait du CV reste affiché pour comparaison.</p>
        <div className="candidate-fields">{FIELDS.map(([key, label, type = 'text', placeholder]) => {
          const field = fields[key]; const evidence = selected.fieldEvidence?.[key];
          return <div className="candidate-field" key={key}>
            <label htmlFor={`candidate-${key}`}>{label}</label>
            {type === 'boolean' ? <select id={`candidate-${key}`} value={field.value} disabled={!!selected.agentId || !!busy} onChange={(e) => changeField(key, e.target.value)}>
              <option value="">Inconnu</option><option value="true">Oui</option><option value="false">Non</option></select>
              : <input id={`candidate-${key}`} type={type} step={type === 'number' ? '0.1' : undefined} min={type === 'number' ? '0' : undefined}
                placeholder={placeholder} value={field.value} disabled={!!selected.agentId || !!busy} onChange={(e) => changeField(key, e.target.value)} />}
            {evidence?.snippet ? <blockquote>{evidence.snippet}<cite>{evidence.location || (evidence.pageNumber ? `Page ${evidence.pageNumber}` : 'Extrait du CV')}</cite></blockquote> : null}
            {field.status === 'INVALID' && field.reviewNote ? <p className="field-warning" role="alert">⚠ {field.reviewNote}</p> : null}
          </div>;
        })}</div>
        <div className="candidate-actions">
          <button className="btn-primary" disabled={!!busy || !!selected.agentId || (!dirty && !hasPendingReview)} onClick={saveReview}><FiCheckCircle /> Enregistrer les modifications</button>
        </div>
        {dirty ? <p role="status">Enregistrez vos modifications avant de prendre une décision.</p> : null}
        {selected.evaluations?.length ? <details className="candidate-history"><summary>Historique des évaluations ({selected.evaluations.length})</summary>
          <ul>{selected.evaluations.map((evaluation, index) => <li key={`${evaluation.evaluatedAt}-${index}`}>Profil v{evaluation.requirements?.version} · {evaluation.score}/100 · {REQUIRED[evaluation.requiredCriteriaStatus]} · {new Date(evaluation.evaluatedAt).toLocaleString('fr-FR')}</li>)}</ul>
        </details> : null}
        {selected.agentId ? <p className="candidate-notice">Compte chauffeur lié : <strong>{selected.agentMatricule || selected.agentId}</strong>. Ses informations se gèrent dans Agents.</p> : <p>Pour créer le chauffeur : renseignez nom, prénom, CIN, email, téléphone, catégories, numéro et expiration du permis (souvent absents du CV). Les critères obligatoires doivent être satisfaits ; deux ans de conduite restent une cible de scoring par défaut.</p>}
        <label className="candidate-note">Notes du superviseur<textarea rows="3" value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
      </div>
      <div className="modal-footer candidate-actions"><button className="btn-secondary" disabled={!!busy || dirty || !!selected.agentId} onClick={() => decide('REJETE')}>Rejeter</button>
        <button className="btn-secondary" disabled={!!busy || dirty || !!selected.agentId} onClick={() => decide('EN_ENTRETIEN')}>Entretien</button>
        <button className="btn-primary" disabled={!!busy || dirty} onClick={() => decide('ACCEPTE')}>{selected.agentId ? 'Enregistrer les notes' : 'Accepter et créer le chauffeur'}</button></div>
    </div></div> : null}
  </div>;
}
