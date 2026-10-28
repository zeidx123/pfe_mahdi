"""Génère resultats/tableaux_rapport.md (tableaux Markdown repris dans RAPPORT_ENTRAINEMENT.md)."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import config as C

M = json.loads((C.RESULTATS / "metriques.json").read_text(encoding="utf-8"))["resultats"]
MOD = ["CLIP_all", "CLIP_reel", "B1", "B2", "MobileNet_PyTorch", "ONNX", "TFLite_float32", "TFLite_float16", "TFLite_int8"]
out = []


def f(x, n=3):
    return "–" if x is None else f"{x:.{n}f}"


def ic(r, k):
    a, b = r["ic95"][f"{k}_ic95"]
    return f"{f(r['ic95'][k])} [{f(a)} ; {f(b)}]"


def titre(t):
    out.append(f"\n### {t}\n")


titre("Principal : test ∩ sous_reel (macro-F1 et rappel PROPRE, IC 95 % bootstrap par groupes de lieux)")
out.append("| modèle | n | macro-F1 [IC 95 %] | rappel PROPRE [IC 95 %] | exactitude équilibrée | n groupes |")
out.append("|---|---|---|---|---|---|")
for m in MOD:
    r = M["test"][m]["sous_reel"]
    out.append(f"| {m} | {r['n']} | {ic(r, 'macro_f1')} | {ic(r, 'rappel_propre')} | {f(r['exactitude_equilibree'])} | {r['ic95']['n_groupes']} |")

for split in ["valid", "test"]:
    titre(f"Macro-F1 par sous-ensemble ({split})")
    sous = [s for s in M[split]["MobileNet_PyTorch"]]
    out.append("| sous-ensemble | n | " + " | ".join(MOD) + " |")
    out.append("|---|---|" + "---|" * len(MOD))
    for s in sous:
        n = M[split]["MobileNet_PyTorch"][s]["n"]
        out.append(f"| {s} | {n} | " + " | ".join(f(M[split][m][s]["macro_f1"]) if s in M[split][m] else "–" for m in MOD) + " |")

for split in ["valid", "test"]:
    titre(f"Rappel PROPRE par sous-ensemble ({split})")
    sous = [s for s in M[split]["MobileNet_PyTorch"] if M[split]["MobileNet_PyTorch"][s]["par_classe"]["PROPRE"]["support"] > 0]
    out.append("| sous-ensemble | n PROPRE | " + " | ".join(MOD) + " |")
    out.append("|---|---|" + "---|" * len(MOD))
    for s in sous:
        n = M[split]["MobileNet_PyTorch"][s]["par_classe"]["PROPRE"]["support"]
        out.append(f"| {s} | {n} | " + " | ".join(f(M[split][m][s]["par_classe"]["PROPRE"]["rappel"]) for m in MOD) + " |")

titre("IC 95 % sur test : sous_reel, sous_strict, ia_probable, tout, gbs / hors gbs")
out.append("| modèle | sous-ensemble | n | macro-F1 [IC 95 %] | rappel PROPRE [IC 95 %] |")
out.append("|---|---|---|---|---|")
for m in ["MobileNet_PyTorch", "CLIP_all", "CLIP_reel", "B1", "B2"]:
    for s in ["tout", "sous_reel", "sous_strict", "ia_probable", "gbs", "hors_gbs", "sous_reel_gbs", "sous_reel_hors_gbs"]:
        r = M["test"][m][s]
        out.append(f"| {m} | {s} | {r['n']} | {ic(r, 'macro_f1')} | {ic(r, 'rappel_propre')} |")

titre("Par classe, test ∩ sous_reel (précision / rappel / F1)")
out.append("| modèle | " + " | ".join(f"{c} P / R / F1" for c in C.NOMS) + " |")
out.append("|---|---|---|---|")
for m in MOD:
    pc = M["test"][m]["sous_reel"]["par_classe"]
    out.append(f"| {m} | " + " | ".join(f"{f(pc[c]['precision'])} / {f(pc[c]['rappel'])} / {f(pc[c]['f1'])}" for c in C.NOMS) + " |")

titre("Matrices de confusion, test ∩ sous_reel (lignes = vrai, colonnes = prédit ; ordre CD, DEB, PRO)")
for m in MOD:
    cm = M["test"][m]["sous_reel"]["confusion"]["matrice"]
    out.append(f"- **{m}** : CD {cm[0]} · DEB {cm[1]} · PRO {cm[2]}")
titre("Matrices de confusion, test ∩ sous_strict")
for m in ["MobileNet_PyTorch", "CLIP_all", "B2"]:
    cm = M["test"][m]["sous_strict"]["confusion"]["matrice"]
    out.append(f"- **{m}** : CD {cm[0]} · DEB {cm[1]} · PRO {cm[2]}")

titre("« 2 classes » PROPRE contre DEBORDEMENT (CANNOT_DETERMINE ignorée), test")
out.append("| modèle | sous-ensemble | n | macro-F1 | exactitude équilibrée | rappel PROPRE | précision PROPRE | confusion [[D→D, D→P], [P→D, P→P]] |")
out.append("|---|---|---|---|---|---|---|---|")
for m in MOD:
    for s in ["sous_reel", "sous_strict", "ia_probable", "tout"]:
        r = M["test"][m][s]["deux_classes"]
        out.append(f"| {m} | {s} | {r['n']} | {f(r['macro_f1'])} | {f(r['exactitude_equilibree'])} | "
                   f"{f(r['par_classe']['PROPRE']['rappel'])} | {f(r['par_classe']['PROPRE']['precision'])} | {r['confusion']['matrice']} |")

(C.RESULTATS / "tableaux_rapport.md").write_text("\n".join(out) + "\n", encoding="utf-8")
print("\n".join(out))
