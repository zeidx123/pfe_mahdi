#!/usr/bin/env python3
"""Évaluation de la rédaction facultative (prompt vision-narrative-v1) sur 40 cas structurés déterministes.

    python scripts/evaluation/evaluate_narration.py                 # --dry-run par défaut : aucun accès réseau
    python scripts/evaluation/evaluate_narration.py --live --provider GROQ --sortie <dossier>

--dry-run : construit les 40 cas (graine 42) et enregistre les prompts qui seraient envoyés.
--live    : lit la clé dans GROQ_API_KEY ou OPENROUTER_API_KEY (selon --provider), appelle l'API avec le MÊME
            fichier de prompt que le backend, valide avec narrative_validator.py (mêmes règles V1 à V7), une seule
            nouvelle tentative après un refus du validateur (rappel des règles, sans le texte refusé), aucune
            nouvelle tentative après une erreur HTTP ou un 429 (et arrêt du lot au premier 429).
            Écrit narration_live.csv et narration_live_resume.json. La clé n'est jamais affichée ni écrite.
Le modèle de langage ne reçoit que des données structurées : il ne voit aucune photo.
"""
import argparse
import csv
import json
import os
import random
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from narrative_validator import RACINE, RESSOURCES, Validateur  # noqa: E402

PROMPT = RESSOURCES / "vision-narrative-v1.txt"
CLASSES = ["PROPRE", "DEBORDEMENT", "CANNOT_DETERMINE"]
QUALITES = [None, "TROP_SOMBRE", "FLOUE", "SUREXPOSEE"]
FOURNISSEURS = {  # mêmes valeurs par défaut que AiProperties
    "GROQ": {"url": "https://api.groq.com/openai/v1", "modele": "qwen/qwen3.8-27b", "cle": "GROQ_API_KEY"},
    "OPENROUTER": {"url": "https://openrouter.ai/api/v1", "modele": "qwen/qwen3.8-27b:free", "cle": "OPENROUTER_API_KEY"},
}
REGLES = {  # mêmes rappels que NarrativeService.RULES
    "V1": "2 à 4 phrases, 600 caractères au maximum",
    "V2": "aucun nombre autre que ceux fournis",
    "V3": "aucune description de l'image ni mot interdit (objets, chauffeur, preuve, certitude, incident, faute)",
    "V4": "indiquer que l'analyse est automatique et à confirmer par un contrôle humain",
    "V5": "citer le pourcentage de la classe prédite suivi de « % » et, si besoin, le problème de qualité de la photo",
    "V6": "aucun caractère de mise en forme (* # { } `)",
    "V7": "aucun lien",
}
SCHEMA = {"type": "object", "properties": {"texte": {"type": "string",
          "description": "Résumé de 2 à 4 phrases en français, 600 caractères au maximum."}},
          "required": ["texte"], "additionalProperties": False}


def arrondi_java(x):  # Math.round : demi vers le haut
    return int((x + 0.5) // 1)


def un_decimal(x):  # NarrativeFacts.oneDecimal
    return arrondi_java(x * 10) / 10.0


def construire_cas(n=40, graine=42):
    rng = random.Random(graine)
    cas = []
    for k in range(n):
        classe = CLASSES[k % 3]
        qualite = QUALITES[(k // 3) % 4]
        haut = rng.uniform(0.52, 0.995)  # > 0,5 : la classe prédite reste la plus probable
        reste = 1 - haut
        part = rng.uniform(0.2, 0.8)
        autres = [c for c in CLASSES if c != classe]
        probas = {classe: haut, autres[0]: reste * part, autres[1]: reste * (1 - part)}
        nettete, lum = rng.uniform(120, 900), rng.uniform(40, 220)
        if qualite == "TROP_SOMBRE":
            lum = rng.uniform(3, 29)
        elif qualite == "SUREXPOSEE":
            lum = rng.uniform(231, 254)
        elif qualite == "FLOUE":
            nettete = rng.uniform(2, 100)
        decision = "CONFIRMATION_AUTO" if classe == "PROPRE" and qualite is None and rng.random() < 0.5 else "A_VERIFIER"
        donnees = {"classe_predite": classe,
                   "pourcentages": {c: arrondi_java(probas[c] * 100) for c in CLASSES},
                   "probleme_qualite": qualite, "nettete": un_decimal(nettete), "luminosite": un_decimal(lum),
                   "decision": decision, "mode_photos": rng.choice(["AFTER_ONLY", "BEFORE_AFTER"])}
        cas.append({"cas": f"c{k + 1:02d}", "donnees": donnees})
    return cas


def faits_pour_modele(d):  # NarrativeFacts.forModel
    noms = {"PROPRE": "propre", "DEBORDEMENT": "débordement", "CANNOT_DETERMINE": "indéterminé"}
    qualite = {"TROP_SOMBRE": "trop sombre", "SUREXPOSEE": "surexposée", "FLOUE": "floue"}
    p = d["pourcentages"]
    return {"classe_predite": noms[d["classe_predite"]],
            "pourcentages": {"propre": p["PROPRE"], "débordement": p["DEBORDEMENT"], "indéterminé": p["CANNOT_DETERMINE"]},
            "decision": "confirmation automatique" if d["decision"] == "CONFIRMATION_AUTO" else "à vérifier",
            "probleme_qualite": qualite.get(d["probleme_qualite"]),
            "nettete": d["nettete"], "luminosite": d["luminosite"],
            "photos": "photos AVANT et APRÈS fournies, seule la photo APRÈS est analysée"
            if d["mode_photos"] == "BEFORE_AFTER" else "photo APRÈS seule"}


def construire_prompt(gabarit, d, violations=()):  # NarrativeService.prompt
    texte = gabarit.strip() + "\n\nDonnées :\n" + json.dumps(faits_pour_modele(d), ensure_ascii=False, separators=(",", ":"))
    if violations:
        texte += ("\n\nTa réponse précédente a été refusée. Règles à respecter absolument : "
                  + " ; ".join(REGLES[v] for v in violations) + ".")
    return texte


class Erreur429(Exception):
    pass


def appeler(fournisseur, url, modele, cle, prompt, delai):
    import urllib.error
    import urllib.request
    corps = {"model": modele, "messages": [{"role": "user", "content": [{"type": "text", "text": prompt}]}],
             "stream": False, "temperature": 0, "max_completion_tokens": 1024,
             "response_format": {"type": "json_schema", "json_schema": {"name": "structured_response", "strict": True, "schema": SCHEMA}}}
    if fournisseur == "GROQ":
        corps["reasoning_effort"] = "none"
    req = urllib.request.Request(url.rstrip("/") + "/chat/completions", data=json.dumps(corps).encode("utf-8"),
                                 headers={"Authorization": f"Bearer {cle}", "Content-Type": "application/json",
                                          "Accept": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=delai) as r:
            enveloppe = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        if e.code == 429:
            raise Erreur429()
        raise RuntimeError(f"PROVIDER_HTTP_ERROR {e.code}") from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise RuntimeError("PROVIDER_UNAVAILABLE") from None
    try:
        contenu = enveloppe["choices"][0]["message"]["content"]
        texte = json.loads(contenu)["texte"]
        if not isinstance(texte, str):
            raise ValueError
        return texte
    except (KeyError, IndexError, TypeError, ValueError):
        raise RuntimeError("INVALID_PROVIDER_RESPONSE") from None


def main(argv=None):
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = a.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="par défaut : aucun accès réseau")
    mode.add_argument("--live", action="store_true", help="appels réels avec la clé de l'utilisateur")
    a.add_argument("--provider", choices=list(FOURNISSEURS), default="GROQ")
    a.add_argument("--model", help="modèle (défaut : celui du fournisseur, comme le backend)")
    a.add_argument("--base-url", help="URL de base (défaut : celle du fournisseur)")
    a.add_argument("--timeout", type=float, default=10.0, help="délai par appel en secondes (VISION_NARRATION_TIMEOUT_SECONDS)")
    a.add_argument("--pause", type=float, default=2.0, help="pause entre deux appels, en secondes")
    a.add_argument("--cas", type=int, default=40)
    a.add_argument("--sortie", default=str(RACINE / "evaluation" / "narration"))
    args = a.parse_args(argv)
    sortie = Path(args.sortie)
    sortie.mkdir(parents=True, exist_ok=True)
    gabarit = PROMPT.read_text(encoding="utf-8")
    cas = construire_cas(args.cas)

    if not args.live:
        with open(sortie / "prompts_dry_run.jsonl", "w", encoding="utf-8") as f:
            for c in cas:
                f.write(json.dumps({"cas": c["cas"], "donnees": c["donnees"],
                                    "prompt": construire_prompt(gabarit, c["donnees"])}, ensure_ascii=False) + "\n")
        print(f"Mode --dry-run : {len(cas)} prompts enregistrés dans {sortie / 'prompts_dry_run.jsonl'} (aucun accès réseau).")
        print("Exemple (cas c01) :\n" + construire_prompt(gabarit, cas[0]["donnees"]))
        return 0

    conf = FOURNISSEURS[args.provider]
    cle = os.environ.get(conf["cle"], "")
    if not cle.strip():
        print(f"Variable {conf['cle']} absente : définissez-la dans votre session (sa valeur n'est jamais affichée).")
        return 2
    url, modele = args.base_url or conf["url"], args.model or conf["modele"]
    validateur = Validateur()
    lignes, latences = [], []
    for c in cas:
        d = c["donnees"]
        tentatives, violations, texte, categorie, premiere, dernier_refus = 0, [], None, None, None, None
        debut = time.perf_counter()
        try:
            while tentatives < 2 and texte is None:
                tentatives += 1
                candidat = appeler(args.provider, url, modele, cle, construire_prompt(gabarit, d, violations), args.timeout)
                violations = validateur.valider(candidat, d)
                if premiere is None:
                    premiere = not violations
                if not violations:
                    texte = candidat.strip(" \t\r\n")
                else:
                    dernier_refus = candidat
            if texte is None:
                categorie = "VALIDATION_" + violations[0]
        except Erreur429:
            categorie = "PROVIDER_HTTP_ERROR_429"
        except RuntimeError as e:
            categorie = str(e)
        latence = round((time.perf_counter() - debut) * 1000)
        latences.append(latence)
        lignes.append({"cas": c["cas"], "classe": d["classe_predite"], "qualite": d["probleme_qualite"] or "",
                       "tentatives": tentatives, "accepte_1re_tentative": bool(premiere),
                       "source": "LLM" if texte else "TEMPLATE", "categorie_repli": categorie or "",
                       "motifs": " ".join(violations), "texte": texte if texte else (dernier_refus if categorie and categorie.startswith("VALIDATION") else ""),
                       "latence_ms": latence})
        print(f"{c['cas']} : {lignes[-1]['source']} {categorie or ''}")
        if categorie == "PROVIDER_HTTP_ERROR_429":
            print("Limite de débit atteinte (429) : arrêt du lot, sans nouvelle tentative.")
            break
        time.sleep(args.pause)

    with open(sortie / "narration_live.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(lignes[0]))
        w.writeheader()
        w.writerows(lignes)
    n = len(lignes)
    motifs = {}
    for l in lignes:
        for m in l["motifs"].split():
            motifs[m] = motifs.get(m, 0) + 1
    resume = {"fournisseur": args.provider, "modele": modele, "prompt": PROMPT.name, "cas_traites": n,
              "taux_acceptation_1re_tentative": sum(l["accepte_1re_tentative"] for l in lignes) / n,
              "taux_llm_final": sum(l["source"] == "LLM" for l in lignes) / n,
              "taux_repli_gabarit": sum(l["source"] == "TEMPLATE" for l in lignes) / n,
              "categories_repli": {k: sum(l["categorie_repli"] == k for l in lignes) for k in {l["categorie_repli"] for l in lignes} if k},
              "motifs_rejet_derniere_tentative": dict(sorted(motifs.items(), key=lambda kv: -kv[1])),
              "latence_mediane_ms": statistics.median(latences)}
    (sortie / "narration_live_resume.json").write_text(json.dumps(resume, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(resume, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
