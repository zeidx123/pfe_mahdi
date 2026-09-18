import { useEffect, useState } from 'react';
import { FiUploadCloud, FiSearch, FiEye, FiX, FiDownload, FiCheckCircle } from 'react-icons/fi';
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
  ['adresse', 'Adresse'], ['dateNaissance', 'Date de naissance', 'date'],
  ['experienceChauffeur', 'Expérience de conduite — détail'], ['experienceCollecte', 'Collecte — détail'],
  ['formations', 'Formations'], ['certifications', 'Certifications'],
];
const FIELD_STATES = { MISSING: 'Manquant', UNCERTAIN: 'À vérifier', INVALID: 'Invalide', VERIFIED: 'Vérifié' };
const REQUIRED = { MET: 'Satisfaits', UNMET: 'Non satisfaits', NEEDS_REVIEW: 'À vérifier', NOT_REQUIRED: 'Facultatif' };
const DECISIONS = { EN_ATTENTE: 'En attente', EN_ENTRETIEN: 'Entretien', ACCEPTE: 'Accepté', REJETE: 'Rejeté' };
const WEIGHTS = [
  ['licenceWeight', 'licenceRequired', 'Permis accepté'],
  ['experienceWeight', 'experienceRequired', 'Expérience de conduite'],
  ['safetyWeight', 'safetyRequired', 'Formation sécurité'],
  ['collectionWeight', 'collectionRequired', 'Expérience de collecte'],
];
const errorMessage = (error) => error.response?.data?.message || error.message || 'Action impossible. Réessayez.';

function reviewFields(candidate) {
  return Object.fromEntries(FIELDS.map(([key]) => [key, {
    value: candidate.fieldEvidence?.[key]?.value ?? candidate[key]?.toString() ?? '',
    status: candidate.fieldEvidence?.[key]?.status || (candidate[key] == null ? 'MISSING' : 'UNCERTAIN'),
    reviewNote: candidate.fieldEvidence?.[key]?.reviewNote || '',
  }]));
}

function Score({ candidate }) {
  return <span className="status-badge">
    {candidate.scoreCompatibilite == null ? 'À réévaluer' : `${candidate.scoreCompatibilite}/100`}
    {candidate.scoreProvisional && candidate.scoreCompatibilite != null ? ' · provisoire' : ''}
  </span>;
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
    updateCandidate(await candidateService.review(selected.id, fields));
    toast.success('Vérification enregistrée et score recalculé.');
  });
  const decide = (statut) => run('decision', async () => {
    updateCandidate(await candidateService.decision(selected.id, statut, notes));
    toast.success('Décision enregistrée.');
  });
  const changeField = (key, property, value) => {
    setDirty(true);
    setFields((current) => ({ ...current, [key]: { ...current[key], [property]: value,
      ...(property === 'value' ? { status: value === '' ? 'MISSING' : 'UNCERTAIN' } : {}),
    } }));
  };
  const filtered = candidates.filter((candidate) => (filter === 'ALL' || candidate.statut === filter)
    && `${candidate.nom || ''} ${candidate.prenom || ''} ${candidate.cvFileName || ''} ${candidate.email || ''}`.toLowerCase().includes(search.toLowerCase()));

  return <div className="agent-page candidates-page">
    <div className="page-header flex-between">
      <div><h1>Espace des candidatures</h1><p>Vérifier les faits du CV, comparer au profil du poste, puis décider.</p></div>
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
        <select aria-label="Filtrer les décisions" value={filter} onChange={(e) => setFilter(e.target.value)}><option value="ALL">Toutes les décisions</option>{Object.entries(DECISIONS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
      </div>
      <div className="table-container">{loading ? <p className="loading-state">Chargement…</p> : filtered.length === 0 ? <p className="empty-state">Aucune candidature. Importez un PDF ou DOCX.</p>
        : <table className="data-table"><thead><tr><th>Candidat</th><th>Permis / conduite</th><th>Points confirmés</th><th>Critères requis</th><th>Décision</th><th>Action</th></tr></thead><tbody>
          {filtered.map((candidate) => <tr key={candidate.id}><td><strong>{`${candidate.prenom || ''} ${candidate.nom || ''}`.trim() || candidate.cvFileName}</strong><small>{candidate.email || 'Email à renseigner'}</small></td>
            <td>{candidate.typePermis || 'À vérifier'}<small>{candidate.anneesExperience == null ? 'Durée inconnue' : `${candidate.anneesExperience} an(s)`}</small></td>
            <td><Score candidate={candidate} /><small>Profil {candidate.requirementsVersion ? `v${candidate.requirementsVersion}` : 'ancien'}</small></td>
            <td>{REQUIRED[candidate.requiredCriteriaStatus] || 'À vérifier'}</td><td>{DECISIONS[candidate.statut] || candidate.statut}</td>
            <td><button className="btn-secondary" onClick={() => showCandidate(candidate)}><FiEye /> Examiner</button></td></tr>)}
        </tbody></table>}</div>
    </div>

    {selected ? <div className="modal-overlay"><div className="modal-content candidate-modal" role="dialog" aria-modal="true" aria-labelledby="candidate-title">
      <div className="modal-header"><div><h2 id="candidate-title">{`${selected.prenom || ''} ${selected.nom || ''}`.trim() || 'Vérification du CV'}</h2><small>{selected.cvFileName}</small></div>
        <button className="close-btn" aria-label="Fermer la candidature" disabled={!!busy} onClick={() => {
          if ((!dirty && !notesDirty) || window.confirm('Fermer sans enregistrer les modifications ?')) setSelected(null);
        }}><FiX /></button></div>
      <div className="modal-body">
        <div className="candidate-summary"><div><strong>Points confirmés</strong><div><Score candidate={selected} /></div></div>
          <div><strong>Critères obligatoires</strong><div>{REQUIRED[selected.requiredCriteriaStatus] || 'À vérifier'}</div></div>
          <div><strong>Profil utilisé</strong><div>{selected.requirementsVersion ? `Version ${selected.requirementsVersion}` : 'Ancien barème'}</div></div></div>
        <p className="candidate-notice">Le score est calculé par les règles du poste à partir des champs vérifiés. Les informations manquantes restent à examiner. L'âge, le CIN et le téléphone ne donnent aucun point. La décision de recrutement reste humaine.</p>
        {selected.extractionStatus === 'PROVIDER_UNAVAILABLE' ? <p role="status" className="candidate-notice">Le fournisseur IA est indisponible. Les champs proposés proviennent d'une extraction locale limitée ; vérifiez le CV.</p> : null}
        {selected.extractionStatus === 'MANUAL_REVIEW_REQUIRED' ? <p role="status" className="candidate-notice">Le document ne contient pas de texte exploitable. Saisissez et vérifiez les informations manuellement.</p> : null}
        <div className="candidate-actions"><button className="btn-secondary" disabled={!!busy} onClick={() => run('download', () => candidateService.download(selected))}><FiDownload /> Télécharger le CV</button>
          <button className="btn-secondary" disabled={!!busy || dirty} onClick={() => run('evaluate', async () => updateCandidate(await candidateService.evaluate(selected.id)))}>Réévaluer avec le profil v{profile?.version || 'courant'}</button></div>
        <div className="table-container"><table className="data-table"><thead><tr><th>Critère</th><th>Valeur / vérification</th><th>Points</th><th>Règle</th></tr></thead><tbody>
          {(selected.criterionScores || []).map((criterion) => <tr key={criterion.key}><td>{criterion.label}<small>{criterion.required ? 'Obligatoire' : 'Scoring'}</small></td>
            <td>{criterion.value ?? 'Non renseigné'}<small>{FIELD_STATES[criterion.evidenceStatus]} · {REQUIRED[criterion.requiredStatus]}</small></td>
            <td>{criterion.points} / {criterion.maxPoints}</td><td>{criterion.explanation}</td></tr>)}
        </tbody></table></div>
        <h3>Vérification des informations</h3><p>Corrigez la valeur si nécessaire, puis choisissez « Vérifié ». Les extraits restent disponibles pour comparer avec le document.</p>
        <div className="candidate-fields">{FIELDS.map(([key, label, type = 'text', placeholder]) => {
          const field = fields[key]; const evidence = selected.fieldEvidence?.[key];
          return <div className="candidate-field" key={key}>
            <label htmlFor={`candidate-${key}`}>{label}</label>
            {type === 'boolean' ? <select id={`candidate-${key}`} value={field.value} disabled={!!selected.agentId} onChange={(e) => changeField(key, 'value', e.target.value)}>
              <option value="">Inconnu</option><option value="true">Oui</option><option value="false">Non</option></select>
              : <input id={`candidate-${key}`} type={type} step={type === 'number' ? '0.1' : undefined} min={type === 'number' ? '0' : undefined}
                placeholder={placeholder} value={field.value} disabled={!!selected.agentId} onChange={(e) => changeField(key, 'value', e.target.value)} />}
            <select aria-label={`Vérification : ${label}`} value={field.status} disabled={!!selected.agentId} onChange={(e) => changeField(key, 'status', e.target.value)}>
              {Object.entries(FIELD_STATES).map(([value, name]) => <option key={value} value={value}>{name}</option>)}
            </select>
            {evidence?.snippet ? <blockquote>{evidence.snippet}<cite>{evidence.location || (evidence.pageNumber ? `Page ${evidence.pageNumber}` : 'Extrait du CV')}</cite></blockquote> : <small>Aucun extrait disponible.</small>}
            <input aria-label={`Note de vérification : ${label}`} placeholder="Note de vérification (facultative)" value={field.reviewNote} disabled={!!selected.agentId} onChange={(e) => changeField(key, 'reviewNote', e.target.value)} />
          </div>;
        })}</div>
        <button className="btn-primary" disabled={!!busy || !dirty || !!selected.agentId} onClick={saveReview}><FiCheckCircle /> Enregistrer la vérification</button>
        {dirty ? <p role="status">Enregistrez la vérification avant de prendre une décision.</p> : null}
        {selected.evaluations?.length ? <details className="candidate-history"><summary>Historique des évaluations ({selected.evaluations.length})</summary>
          <ul>{selected.evaluations.map((evaluation, index) => <li key={`${evaluation.evaluatedAt}-${index}`}>Profil v{evaluation.requirements?.version} · {evaluation.score}/100 · {REQUIRED[evaluation.requiredCriteriaStatus]} · {new Date(evaluation.evaluatedAt).toLocaleString('fr-FR')}</li>)}</ul>
        </details> : null}
        {selected.agentId ? <p className="candidate-notice">Compte chauffeur lié : <strong>{selected.agentMatricule || selected.agentId}</strong>. Ses informations se gèrent dans Agents.</p> : <p>Pour créer le chauffeur : vérifiez nom, prénom, CIN, email, téléphone, catégories, numéro et expiration du permis. Les critères obligatoires doivent être satisfaits ; deux ans de conduite restent une cible de scoring par défaut.</p>}
        <label className="candidate-note">Notes du superviseur<textarea rows="3" value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
      </div>
      <div className="modal-footer candidate-actions"><button className="btn-secondary" disabled={!!busy || dirty || !!selected.agentId} onClick={() => decide('REJETE')}>Rejeter</button>
        <button className="btn-secondary" disabled={!!busy || dirty || !!selected.agentId} onClick={() => decide('EN_ENTRETIEN')}>Entretien</button>
        <button className="btn-primary" disabled={!!busy || dirty} onClick={() => decide('ACCEPTE')}>{selected.agentId ? 'Enregistrer les notes' : 'Accepter et créer le chauffeur'}</button></div>
    </div></div> : null}
  </div>;
}
