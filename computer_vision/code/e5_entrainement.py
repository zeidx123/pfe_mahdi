"""Étape 5 : entraînement d'UN modèle mobile (MobileNetV3-Large ImageNet, tête à 3 sorties).

Phase 1 : tête seule (model.classifier), 2 époques, AdamW lr 1e-3, réseau de base gelé (BN figées).
Phase 2 : réseau entier, AdamW (wd 1e-4), cosinus sur 12 époques, lot 64, arrêt anticipé patience 3
          sur la macro-F1 de valid ∩ sous_reel ; grille lr ∈ {3e-5, 1e-4}, chaque essai part du même
          point de fin de phase 1. Meilleur point = meilleure macro-F1 valid ∩ sous_reel (toutes époques
          de phase 2, deux lr confondus).
Puis : temperature scaling sur valid ∩ sous_reel, ECE avant/après, courbe couverture / exactitude.
Aucune métrique sur test.
"""
import json
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("CUBLAS_WORKSPACE_CONFIG", ":4096:8")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision.models import MobileNet_V3_Large_Weights, mobilenet_v3_large

import config as C
from commun import ece, macro_f1
from donnees_image import JeuImages, init_worker

POIDS = MobileNet_V3_Large_Weights.IMAGENET1K_V2
DEV = torch.device("cuda" if torch.cuda.is_available() else "cpu")
WORKERS = 6


def construire():
    m = mobilenet_v3_large(weights=POIDS)
    m.classifier[3] = nn.Linear(m.classifier[3].in_features, 3)
    return m


@torch.no_grad()
def logits_de(model, loader):
    model.eval()
    out = []
    for x, _ in loader:
        out.append(model(x.to(DEV, non_blocking=True)).float().cpu())
    return torch.cat(out).numpy()


def une_epoque(model, loader, opt, perte, sched=None, tete_seule=False):
    model.train()
    if tete_seule:
        model.features.eval()
    tot, n = 0.0, 0
    for x, y in loader:
        x, y = x.to(DEV, non_blocking=True), y.to(DEV, non_blocking=True)
        opt.zero_grad(set_to_none=True)
        l = perte(model(x), y)
        l.backward()
        opt.step()
        if sched is not None:
            sched.step()
        tot += l.item() * len(y)
        n += len(y)
    return tot / n


def evaluer(model, loader, y, perte):
    lg = logits_de(model, loader)
    vl = float(perte(torch.from_numpy(lg).to(DEV), torch.from_numpy(y).to(DEV)).item())
    return lg, vl, macro_f1(y, lg.argmax(1))


def ajuster_temperature(lg, y):
    """T minimisant la NLL (recherche sur grille log puis affinage)."""
    lg_t, y_t = torch.from_numpy(lg).double(), torch.from_numpy(y)
    nll = lambda t: float(nn.functional.cross_entropy(lg_t / t, y_t).item())
    grille = np.exp(np.linspace(np.log(0.05), np.log(10), 2001))
    t0 = grille[int(np.argmin([nll(t) for t in grille]))]
    fine = np.linspace(t0 * 0.99, t0 * 1.01, 401)
    t = float(fine[int(np.argmin([nll(t) for t in fine]))])
    return t, nll(1.0), nll(t)


def softmax(z):
    z = z - z.max(1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(1, keepdims=True)


def main():
    C.fixer_graine()
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True
    torch.use_deterministic_algorithms(True, warn_only=True)
    t_debut = time.time()

    df = pd.read_csv(C.SOUS_ENSEMBLES, encoding="utf-8", dtype={"famille": str, "parent_id": str})
    decision = json.loads(C.DECISION.read_text(encoding="utf-8"))["decision"]
    tr = df["split"] == "train"
    if decision == "train_sous_reel":
        tr &= df["sous_reel"]
    d_tr = df[tr].reset_index(drop=True)
    va = df[df["split"] == "valid"].reset_index(drop=True)
    m_va_reel = va["sous_reel"].to_numpy()
    y_va = va["y"].to_numpy()
    y_vr = y_va[m_va_reel]

    n_c = np.bincount(d_tr["y"], minlength=3)
    poids = len(d_tr) / (3 * n_c)
    perte = nn.CrossEntropyLoss(weight=torch.tensor(poids, dtype=torch.float32, device=DEV),
                                label_smoothing=C.LABEL_SMOOTHING)

    g = torch.Generator().manual_seed(C.GRAINE)
    dl_tr = DataLoader(JeuImages(d_tr, entrainement=True), batch_size=C.LOT, shuffle=True, generator=g,
                       num_workers=WORKERS, worker_init_fn=init_worker, persistent_workers=True,
                       pin_memory=DEV.type == "cuda", drop_last=False)
    dl_va = DataLoader(JeuImages(va), batch_size=128, shuffle=False, num_workers=WORKERS,
                       persistent_workers=True, pin_memory=DEV.type == "cuda")
    dl_vr = DataLoader(JeuImages(va[m_va_reel]), batch_size=128, shuffle=False, num_workers=WORKERS,
                       persistent_workers=True, pin_memory=DEV.type == "cuda")

    epoques_max = C.EPOQUES_MAX
    budget_s = None if DEV.type == "cuda" else C.BUDGET_CPU_MIN * 60
    journal = {"appareil": str(DEV), "gpu": torch.cuda.get_device_name() if DEV.type == "cuda" else None,
               "jeu_entrainement": decision, "n_train": len(d_tr),
               "effectifs_train": {C.NOMS[k]: int(n_c[k]) for k in range(3)},
               "poids_classes": {C.NOMS[k]: float(poids[k]) for k in range(3)},
               "poids_pretraines": str(POIDS), "phase1": [], "phase2": {}}

    # ---- Phase 1 : tête seule
    C.fixer_graine()
    model = construire().to(DEV)
    for p in model.features.parameters():
        p.requires_grad = False
    opt = torch.optim.AdamW(model.classifier.parameters(), lr=C.LR_TETE, weight_decay=C.WEIGHT_DECAY)
    for ep in range(C.EPOQUES_TETE):
        t0 = time.time()
        lt = une_epoque(model, dl_tr, opt, perte, tete_seule=True)
        _, vl, f1 = evaluer(model, dl_vr, y_vr, perte)
        journal["phase1"].append({"epoque": ep + 1, "perte_train": lt, "perte_valid_reel": vl,
                                  "macro_f1_valid_reel": f1, "duree_s": time.time() - t0})
        print("phase1", journal["phase1"][-1], flush=True)
    etat_p1 = {k: v.detach().clone() for k, v in model.state_dict().items()}

    # ---- Phase 2 : réseau entier, grille de lr
    meilleur = {"f1": -1}
    for lr in C.GRILLE_LR:
        C.fixer_graine()
        g.manual_seed(C.GRAINE)
        model.load_state_dict(etat_p1)
        for p in model.parameters():
            p.requires_grad = True
        opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=C.WEIGHT_DECAY)
        sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=epoques_max * len(dl_tr))
        hist, best_f1, sans_progres = [], -1, 0
        for ep in range(epoques_max):
            if budget_s and time.time() - t_debut > budget_s:
                print("budget CPU atteint", flush=True)
                break
            t0 = time.time()
            lt = une_epoque(model, dl_tr, opt, perte, sched)
            _, vl, f1 = evaluer(model, dl_vr, y_vr, perte)
            hist.append({"epoque": ep + 1, "perte_train": lt, "perte_valid_reel": vl, "macro_f1_valid_reel": f1,
                         "lr_fin": sched.get_last_lr()[0], "duree_s": time.time() - t0})
            print("phase2 lr", lr, hist[-1], flush=True)
            if f1 > best_f1:
                best_f1, sans_progres = f1, 0
                torch.save(model.state_dict(), C.MODELES / f"mobilenet_lr{lr:g}.pt")
            else:
                sans_progres += 1
                if sans_progres >= C.PATIENCE:
                    break
            if f1 > meilleur["f1"]:
                meilleur = {"f1": f1, "lr": lr, "epoque": ep + 1}
        journal["phase2"][str(lr)] = {"historique": hist, "meilleure_macro_f1_valid_reel": best_f1,
                                      "meilleure_epoque": int(np.argmax([h["macro_f1_valid_reel"] for h in hist]) + 1),
                                      "arret_anticipe": len(hist) < epoques_max}
    journal["meilleur"] = meilleur
    journal["duree_entrainement_s"] = time.time() - t_debut

    # ---- Modèle final
    model.load_state_dict(torch.load(C.MODELES / f"mobilenet_lr{meilleur['lr']:g}.pt", map_location=DEV))
    torch.save({"state_dict": model.state_dict(), "lr": meilleur["lr"], "epoque": meilleur["epoque"],
                "classes": C.NOMS, "poids_pretraines": str(POIDS)}, C.MODELES / "mobilenet_final.pt")
    lg_va = logits_de(model, dl_va)
    np.savez(C.MODELES / "logits_valid.npz", ids=va["id"].to_numpy().astype(str), logits=lg_va)
    f1_check = macro_f1(y_vr, lg_va[m_va_reel].argmax(1))

    # ---- Étalonnage
    T_opt, nll1, nllT = ajuster_temperature(lg_va[m_va_reel], y_vr)
    P1, PT = softmax(lg_va), softmax(lg_va / T_opt)
    etal = {"temperature": T_opt, "ajustee_sur": "valid ∩ sous_reel", "nll_valid_reel_avant": nll1,
            "nll_valid_reel_apres": nllT,
            "ece_15_bins": {"valid_tout": {"avant": ece(P1, y_va), "apres": ece(PT, y_va)},
                            "valid_sous_reel": {"avant": ece(P1[m_va_reel], y_vr), "apres": ece(PT[m_va_reel], y_vr)}},
            "note": "l'ECE sur valid ∩ sous_reel est mesurée sur le jeu qui a servi à ajuster T (optimiste)."}
    courbe = []
    Pr = PT[m_va_reel]
    for s in np.round(np.arange(0.5, 0.951, 0.05), 2):
        k = Pr.max(1) >= s
        courbe.append({"seuil": float(s), "couverture": float(k.mean()),
                       "exactitude": float((Pr[k].argmax(1) == y_vr[k]).mean()) if k.any() else None,
                       "macro_f1": macro_f1(y_vr[k], Pr[k].argmax(1)) if k.any() else None, "n": int(k.sum())})
    etal["couverture_exactitude_valid_reel"] = courbe
    C.ecrire_json(C.MODELES / "temperature.json", {"temperature": T_opt})
    journal["etalonnage"] = etal
    journal["macro_f1_valid_reel_modele_final"] = f1_check
    journal["versions"] = {"torch": torch.__version__, "torchvision": __import__("torchvision").__version__,
                           "cuda": torch.version.cuda, "cudnn": torch.backends.cudnn.version()}
    C.ecrire_json(C.MODELES / "journal_entrainement.json", journal)

    # ---- Courbes
    fig, ax = plt.subplots(1, 2, figsize=(12, 4.5))
    p1 = journal["phase1"]
    for lr in C.GRILLE_LR:
        h = journal["phase2"][str(lr)]["historique"]
        x = [len(p1) + e["epoque"] for e in h]
        ax[0].plot(x, [e["perte_train"] for e in h], "-o", ms=3, label=f"train lr={lr:g}")
        ax[0].plot(x, [e["perte_valid_reel"] for e in h], "--o", ms=3, label=f"valid∩réel lr={lr:g}")
        ax[1].plot(x, [e["macro_f1_valid_reel"] for e in h], "-o", ms=3, label=f"lr={lr:g}")
    xp = [e["epoque"] for e in p1]
    ax[0].plot(xp, [e["perte_train"] for e in p1], "k-o", ms=3, label="train (tête seule)")
    ax[0].plot(xp, [e["perte_valid_reel"] for e in p1], "k--o", ms=3, label="valid∩réel (tête seule)")
    ax[1].plot(xp, [e["macro_f1_valid_reel"] for e in p1], "k-o", ms=3, label="tête seule")
    ax[0].set(title="Perte (entropie croisée pondérée, lissage 0,1)", xlabel="époque")
    ax[1].set(title="Macro-F1 valid ∩ sous_reel", xlabel="époque")
    for a in ax:
        a.axvline(len(p1) + 0.5, color="grey", lw=0.8)
        a.legend(fontsize=7)
        a.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(C.MODELES / "courbes_apprentissage.png", dpi=120)

    fig, ax = plt.subplots(figsize=(5, 4))
    ax.plot([c["couverture"] for c in courbe], [c["exactitude"] for c in courbe], "-o")
    for c in courbe:
        ax.annotate(f'{c["seuil"]:.2f}', (c["couverture"], c["exactitude"]), fontsize=6)
    ax.set(xlabel="couverture", ylabel="exactitude", title="valid ∩ sous_reel (probas étalonnées)")
    ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(C.MODELES / "couverture_exactitude_valid.png", dpi=120)
    print(json.dumps({"meilleur": meilleur, "etalonnage": {k: v for k, v in etal.items() if k != "couverture_exactitude_valid_reel"},
                      "duree_s": journal["duree_entrainement_s"]}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
