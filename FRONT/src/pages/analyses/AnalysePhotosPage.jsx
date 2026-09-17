import React, { useState, useEffect } from 'react';
import { aiService } from '../../services/aiService';

export default function AnalysePhotosPage() {
  const [analyses, setAnalyses] = useState([]);
  const [problems, setProblems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('ANALYSES');
  const [filterStatus, setFilterStatus] = useState('TOUS');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [aData, pData] = await Promise.all([
        aiService.getPhotoAnalyses(),
        aiService.getAiProblems(),
      ]);
      setAnalyses(aData || []);
      setProblems(pData || []);
    } catch (err) {
      console.error('Erreur chargement analyses photos:', err);
    } finally {
      setLoading(false);
    }
  };

  const getConfidenceBadge = (confidence) => {
    const pct = Math.round((confidence || 0) * 100);
    if (pct >= 85) return <span className="px-2 py-0.5 rounded text-xs font-bold bg-emerald-100 text-emerald-800">Confiance : {pct}%</span>;
    if (pct >= 65) return <span className="px-2 py-0.5 rounded text-xs font-bold bg-amber-100 text-amber-800">Confiance : {pct}%</span>;
    return <span className="px-2 py-0.5 rounded text-xs font-bold bg-rose-100 text-rose-800">Incertain ({pct}%)</span>;
  };

  const getStatusBadge = (status, type) => {
    if (status === 'ANOMALIE') {
      return (
        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-red-500 text-white uppercase">
          🚨 {type ? type.replace('CONTENEUR_', '') : 'ANOMALIE'}
        </span>
      );
    }
    return (
      <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-500 text-white uppercase">
        ✅ OK - NORMAL
      </span>
    );
  };

  const filteredAnalyses = analyses.filter((a) => {
    if (filterStatus === 'ANOMALIE') return a.status === 'ANOMALIE';
    if (filterStatus === 'NORMAL') return a.status === 'NORMAL';
    return true;
  });

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center bg-white p-6 rounded-xl shadow-sm border">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">📷 Vision IA — Inspection Photos</h1>
          <p className="text-sm text-slate-500">
            Analyse visuelle automatique des photos transmises par les chauffeurs sur le terrain via Gemini Vision API.
          </p>
        </div>
        <div className="flex gap-2 bg-slate-100 p-1 rounded-lg">
          <button
            onClick={() => setActiveTab('ANALYSES')}
            className={`px-4 py-2 text-xs font-bold rounded-md transition ${activeTab === 'ANALYSES' ? 'bg-white shadow text-slate-800' : 'text-slate-500'}`}
          >
            Photos Inspectées ({analyses.length})
          </button>
          <button
            onClick={() => setActiveTab('PROBLEMES')}
            className={`px-4 py-2 text-xs font-bold rounded-md transition ${activeTab === 'PROBLEMES' ? 'bg-white shadow text-slate-800' : 'text-slate-500'}`}
          >
            Problèmes IA Détectés ({problems.length})
          </button>
        </div>
      </div>

      {activeTab === 'ANALYSES' && (
        <>
          {/* Filtres */}
          <div className="flex gap-2">
            {['TOUS', 'ANOMALIE', 'NORMAL'].map((st) => (
              <button
                key={st}
                onClick={() => setFilterStatus(st)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-bold transition border ${
                  filterStatus === st ? 'bg-slate-800 text-white border-slate-800' : 'bg-white text-slate-600 border-slate-200'
                }`}
              >
                {st === 'TOUS' ? 'Toutes les photos' : st === 'ANOMALIE' ? '🚨 Anomalies seulement' : '✅ Normales'}
              </button>
            ))}
          </div>

          {/* Grille de cartes Photos */}
          {loading ? (
            <div className="p-8 text-center text-slate-500">Chargement des photos...</div>
          ) : filteredAnalyses.length === 0 ? (
            <div className="p-8 text-center text-slate-500 bg-white rounded-xl border">Aucune photo enregistrée pour le moment.</div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {filteredAnalyses.map((item) => (
                <div key={item.id} className="bg-white rounded-xl shadow-sm border overflow-hidden flex flex-col">
                  {/* Photo preview */}
                  <div className="relative h-48 bg-slate-900 overflow-hidden">
                    <img
                      src={`http://localhost:8080/${item.photoUrl}`}
                      alt="Point de collecte"
                      className="w-full h-full object-cover"
                      onError={(e) => { e.target.src = 'https://via.placeholder.com/400x300?text=Photo+Non+Disponible'; }}
                    />
                    <div className="absolute top-3 left-3">{getStatusBadge(item.status, item.typeAnomalie)}</div>
                    <div className="absolute bottom-3 right-3">{getConfidenceBadge(item.confidence)}</div>
                  </div>

                  {/* Body Info */}
                  <div className="p-4 space-y-3 flex-1 flex flex-col justify-between">
                    <div>
                      <div className="flex justify-between items-start mb-1">
                        <span className="font-bold text-slate-800 text-sm">Point : {item.pointCollecteId || 'N/C'}</span>
                        <span className="text-[11px] text-slate-400">
                          {item.createdAt ? new Date(item.createdAt).toLocaleString('fr-FR') : ''}
                        </span>
                      </div>
                      <p className="text-xs text-slate-600 mt-2 bg-slate-50 p-2.5 rounded-lg border">
                        {item.description}
                      </p>
                    </div>

                    {/* Driver confirmation status */}
                    <div className="border-t pt-2 text-xs flex justify-between items-center text-slate-500">
                      <span>Validation Chauffeur :</span>
                      {item.valideeParChauffeur === true ? (
                        <span className="text-emerald-600 font-bold">✅ Confirmé</span>
                      ) : item.valideeParChauffeur === false ? (
                        <span className="text-amber-600 font-bold">✏️ Corrigé ({item.correctionChauffeur})</span>
                      ) : (
                        <span className="text-slate-400">En attente</span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {activeTab === 'PROBLEMES' && (
        <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
          <div className="p-4 border-b font-bold text-slate-800">Problèmes créés automatiquement par l'Agent IA</div>
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 border-b text-slate-600 font-semibold">
              <tr>
                <th className="p-4">Type de problème</th>
                <th className="p-4">Sévérité</th>
                <th className="p-4">Description</th>
                <th className="p-4">Confiance IA</th>
                <th className="p-4">Statut</th>
                <th className="p-4">Date</th>
              </tr>
            </thead>
            <tbody className="divide-y text-slate-700">
              {problems.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="p-4 font-bold text-slate-800">{p.typeProbleme}</td>
                  <td className="p-4">
                    <span className={`px-2 py-0.5 rounded text-xs font-bold ${p.severite === 'CRITICAL' ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-700'}`}>
                      {p.severite}
                    </span>
                  </td>
                  <td className="p-4 text-xs max-w-xs truncate">{p.description}</td>
                  <td className="p-4">{getConfidenceBadge(p.scoreConfiance)}</td>
                  <td className="p-4">
                    <span className="px-2 py-1 bg-slate-100 text-slate-800 rounded text-xs font-bold">{p.statut}</span>
                  </td>
                  <td className="p-4 text-xs text-slate-400">{new Date(p.createdAt).toLocaleDateString('fr-FR')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
