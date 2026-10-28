"""Bout en bout du moteur de vision (bibliothèque standard Python). NON EXÉCUTÉ sur le poste A.

Appelé par 04_e2e.ps1, avec un backend déjà démarré sur une base MongoDB de TEST (comptes de
TestDataSeeder : app.seed.enabled=true) :

    python e2e_vision.py --mode LOCAL --api http://127.0.0.1:18080/api
    python e2e_vision.py --mode LLM   --api http://127.0.0.1:18080/api

Déroulé (endpoints réels lus dans le code) :
  POST /auth/login (superviseur et chauffeur de test) ; POST /camions ; POST /points ; POST /tournees ;
  démarrage : POST /tournees/{id}/optimiser puis /start. L'optimisation exige OpenRouteService (clé et réseau,
  aucun repli dans le code) ; si elle échoue, la tournée est passée EN_COURS directement dans la base de TEST
  (docker exec ... mongosh), ce qui est signalé dans le résumé ;
  POST /tournees/{id}/points/{p}/arrive ; pour chaque image : POST .../photo (stage=AFTER) puis POST .../analyse ;
  GET /ai/vision/analyses/{analysisId} ; enfin POST .../complete et POST /tournees/{id}/finish.
Contrôles :
  LOCAL : probabilités enregistrées = reference_coherence.json à 1e-5 ; même classe ; statut conforme à la
          correspondance ; decision = A_VERIFIER partout (seuil vide) ; confidence nulle ; provider LOCAL ;
          justification non vide ; la décision ne bloque pas la fin de tournée.
  LLM (sans clé) : ANALYSIS_UNAVAILABLE / NOT_CONFIGURED, aucun champ du moteur local.
"""
import argparse
import csv
import json
import random
import string
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date
from pathlib import Path

ICI = Path(__file__).resolve().parent
CLASSES = ["CANNOT_DETERMINE", "DEBORDEMENT", "PROPRE"]
STATUT_ATTENDU = {"PROPRE": "CLEANED", "DEBORDEMENT": "NOT_CLEANED", "CANNOT_DETERMINE": "CANNOT_DETERMINE"}
SUPERVISEUR = ("superviseur.test@gesticollecte.local", "Superviseur123!")  # TestDataSeeder (base de test)
CHAUFFEUR = ("chauffeur1.test@gesticollecte.local", "09012345")
TOLERANCE = 1e-5


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def appel(self, methode, chemin, jeton=None, json_corps=None, corps=None, type_=None, attendu=(200, 201)):
        entetes = {"Accept": "application/json"}
        if jeton:
            entetes["Authorization"] = f"Bearer {jeton}"
        if json_corps is not None:
            corps, type_ = json.dumps(json_corps).encode("utf-8"), "application/json"
        if type_:
            entetes["Content-Type"] = type_
        req = urllib.request.Request(self.base + chemin, data=corps, headers=entetes, method=methode)
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                texte = r.read().decode("utf-8")
                statut = r.status
        except urllib.error.HTTPError as e:
            texte, statut = e.read().decode("utf-8", "replace"), e.code
        if statut not in attendu:
            raise RuntimeError(f"{methode} {chemin} -> HTTP {statut} : {texte[:300]}")
        return json.loads(texte) if texte.strip() else None

    def connexion(self, email, mot_de_passe):
        return self.appel("POST", "/auth/login", json_corps={"email": email, "password": mot_de_passe})

    def photo(self, jeton, tour, point, chemin):
        limite = "----e2e" + uuid.uuid4().hex
        type_ = "image/png" if chemin.suffix.lower() == ".png" else "image/jpeg"
        morceaux = [f"--{limite}\r\nContent-Disposition: form-data; name=\"stage\"\r\n\r\nAFTER\r\n".encode(),
                    f"--{limite}\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"{chemin.name}\"\r\n"
                    f"Content-Type: {type_}\r\n\r\n".encode(), chemin.read_bytes(), f"\r\n--{limite}--\r\n".encode()]
        return self.appel("POST", f"/tournees/{tour}/points/{point}/photo", jeton, corps=b"".join(morceaux),
                          type_=f"multipart/form-data; boundary={limite}")


def attendre_backend(api, delai):
    fin = time.time() + delai
    derniere = None
    while time.time() < fin:
        try:
            return api.connexion(*SUPERVISEUR)
        except Exception as e:  # noqa: BLE001
            derniere = e
            time.sleep(3)
    raise RuntimeError(f"Backend injoignable ou comptes de test absents : {derniere}")


def demarrer_tournee(api, sup, tour_id, conteneur, base_mongo):
    try:
        api.appel("POST", f"/tournees/{tour_id}/optimiser", sup)
        api.appel("POST", f"/tournees/{tour_id}/start", sup)
        return "API (optimiser + start)"
    except RuntimeError as e:
        script = ("var r = db.tournees.updateOne({_id: ObjectId('%s')}, {$set: {statut: 'EN_COURS', "
                  "heureDebutReelle: new Date(), visits: [{pointId: db.tournees.findOne({_id: ObjectId('%s')})"
                  ".pointsCollecteIds[0], order: NumberInt(0), status: 'PENDING', plannedServiceSeconds: NumberInt(300), "
                  "legacyPhoto: null, legacyProgressMissing: false}]}}); print(r.modifiedCount);") % (tour_id, tour_id)
        r = subprocess.run(["docker", "exec", conteneur, "mongosh", "--quiet", base_mongo, "--eval", script],
                           capture_output=True, text=True)
        if r.returncode != 0 or r.stdout.strip().splitlines()[-1:] != ["1"]:
            raise RuntimeError(f"Démarrage impossible (API : {e}) ; mise à jour de la base de test : {r.stderr[-300:]}")
        return f"base de TEST (optimisation impossible sans OpenRouteService : {str(e)[:120]})"


def main(argv=None):
    a = argparse.ArgumentParser()
    a.add_argument("--mode", choices=["LOCAL", "LLM"], required=True)
    a.add_argument("--api", default="http://127.0.0.1:18080/api")
    a.add_argument("--jeu", default=str(ICI / "jeu_coherence"))
    a.add_argument("--sortie", default=str(ICI / "resultats_autre_pc"))
    a.add_argument("--conteneur", default="gesticollecte-mongo-test")
    a.add_argument("--base-mongo", default="gesticollecte_e2e")
    a.add_argument("--delai", type=int, default=240)
    a.add_argument("--max-images", type=int, default=0, help="0 = toutes (LOCAL) ; 3 par défaut en LLM")
    args = a.parse_args(argv)
    jeu, sortie = Path(args.jeu), Path(args.sortie)
    sortie.mkdir(parents=True, exist_ok=True)
    references = json.loads((jeu / "reference_coherence.json").read_text(encoding="utf-8"))["images"]
    n = args.max_images or (len(references) if args.mode == "LOCAL" else 3)
    references = references[:n]

    api = Api(args.api)
    sup = attendre_backend(api, args.delai)["token"]
    chauffeur = api.connexion(*CHAUFFEUR)
    suffixe = "".join(random.choices(string.ascii_uppercase + string.digits, k=6))
    camion = api.appel("POST", "/camions", sup, json_corps={"matricule": f"E2E-{suffixe}", "marque": "Test",
                                                              "statut": "DISPONIBLE", "actif": True})
    point = api.appel("POST", "/points", sup, json_corps={"reference": f"E2E-P-{suffixe}", "adresse": "Point de test e2e",
                                                            "latitude": 36.80, "longitude": 10.18, "actif": True})
    tour = api.appel("POST", "/tournees", sup, json_corps={
        "reference": f"E2E-T-{suffixe}", "nom": f"Tournée de test vision {args.mode}", "datePrevue": date.today().isoformat(),
        "camionId": camion["id"], "chauffeurId": chauffeur["id"], "pointsCollecteIds": [point["id"]]})
    demarrage = demarrer_tournee(api, sup, tour["id"], args.conteneur, args.base_mongo)
    api.appel("POST", f"/tournees/{tour['id']}/points/{point['id']}/arrive", chauffeur["token"])

    lignes, echecs = [], []
    for ref in references:
        api.photo(chauffeur["token"], tour["id"], point["id"], jeu / ref["fichier"])
        reponse = api.appel("POST", f"/tournees/{tour['id']}/points/{point['id']}/analyse", chauffeur["token"])
        visite = reponse["visits"][0]
        analyse = api.appel("GET", f"/ai/vision/analyses/{visite['analysisId']}", sup)
        ligne = {"fichier": ref["fichier"], "type": ref["type"], "statut": visite.get("analysisStatus"),
                 "decision": visite.get("analysisDecision"), "qualite": visite.get("analysisQualityIssue"),
                 "provider": analyse.get("provider"), "failureCategory": analyse.get("failureCategory")}
        probleme = []
        if args.mode == "LOCAL":
            p = visite.get("analysisProbabilities") or {}
            ecart = max(abs(p.get(c, float("inf")) - ref["probabilites_reference"][c]) for c in CLASSES)
            classe = max(CLASSES, key=lambda c: p.get(c, -1))
            ligne.update({"ecart_max": f"{ecart:.3e}", "classe": classe})
            if ecart > TOLERANCE:
                probleme.append(f"écart {ecart:.2e}")
            if classe != ref["classe_reference"]:
                probleme.append("classe différente")
            if visite.get("analysisStatus") != STATUT_ATTENDU[ref["classe_reference"]]:
                probleme.append(f"statut {visite.get('analysisStatus')}")
            if visite.get("analysisDecision") != "A_VERIFIER" or analyse.get("decision") != "A_VERIFIER":
                probleme.append("decision différente de A_VERIFIER")
            if analyse.get("confidence") is not None or analyse.get("provider") != "LOCAL":
                probleme.append("confidence non nulle ou provider différent de LOCAL")
            if not (analyse.get("description") or "").strip():
                probleme.append("justification vide")
        else:
            if visite.get("analysisStatus") != "ANALYSIS_UNAVAILABLE" or analyse.get("failureCategory") != "NOT_CONFIGURED":
                probleme.append(f"attendu ANALYSIS_UNAVAILABLE/NOT_CONFIGURED, obtenu {visite.get('analysisStatus')}/"
                                f"{analyse.get('failureCategory')}")
            if visite.get("analysisProbabilities") is not None or visite.get("analysisDecision") is not None:
                probleme.append("champs du moteur local présents en mode LLM")
        ligne["conforme"] = not probleme
        ligne["probleme"] = " ; ".join(probleme)
        lignes.append(ligne)
        if probleme:
            echecs.append(f"{ref['fichier']} : {ligne['probleme']}")

    # La décision n'a aucun effet sur la visite ni sur la fin de tournée.
    api.appel("POST", f"/tournees/{tour['id']}/points/{point['id']}/complete", chauffeur["token"],
              json_corps={"outcome": "COMPLETED"})
    fin = api.appel("POST", f"/tournees/{tour['id']}/finish", chauffeur["token"], json_corps={"returnedToDepot": True})
    if fin.get("statut") != "TERMINEE":
        echecs.append(f"fin de tournée : statut {fin.get('statut')}")

    nom = f"e2e_{args.mode.lower()}"
    with open(sortie / f"{nom}.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=sorted({k for l in lignes for k in l}))
        w.writeheader()
        w.writerows(lignes)
    resume = {"mode": args.mode, "images": len(lignes), "conformes": sum(l["conforme"] for l in lignes),
              "demarrage_tournee": demarrage, "fin_tournee": fin.get("statut"), "echecs": echecs}
    (sortie / f"{nom}_resume.json").write_text(json.dumps(resume, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(resume, ensure_ascii=False, indent=2))
    return 0 if not echecs else 1


if __name__ == "__main__":
    sys.exit(main())
