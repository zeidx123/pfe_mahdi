"""Service local d'inférence du modèle de propreté (moteur LOCAL de l'« Agent 3 Vision »).

Bibliothèque standard (http.server) + onnxruntime. Écoute sur 127.0.0.1:8090 par défaut.

  GET  /health   -> {status, model_name, model_sha256, onnxruntime_version}
  POST /predict  -> corps = octets de l'image (Content-Type image/jpeg ou image/png)
                    en-têtes facultatifs : X-Min-Sharpness, X-Min-Brightness, X-Max-Brightness,
                    X-Service-Token (obligatoire si la variable LOCAL_VISION_TOKEN est définie)

Prétraitement : celui de export/exemple_pretraitement.py (RGB, étirement direct en 224x224 sans recadrage,
PIL bilinéaire, /255, normalisation ImageNet, NCHW), précédé de ImageOps.exif_transpose. Le prétraitement
d'entraînement n'appliquait PAS l'orientation EXIF : voir integration/RAPPORT_INTEGRATION.md.
Porte de qualité : mesures de code/e7_qualite_image.py (netteté = variance du Laplacien OpenCV CV_64F sur le
gris de l'image 224x224 étirée ; luminosité = moyenne du gris de l'image entière). Elle est évaluée AVANT le
modèle ; le modèle est quand même appelé et ses probabilités renvoyées.

Limites du modèle (à ne pas oublier) : étiquettes d'entraînement en partie extrapolées, classe CANNOT_DETERMINE
apprise sur des images synthétiques (flou, nuit, recadrage), précision de PROPRE d'environ 0,71 sur le test,
images surtout issues du web chinois, rien validé sur le terrain.

Aucune image n'est écrite sur disque ni journalisée ; le jeton n'est jamais journalisé.
"""
import argparse
import hashlib
import hmac
import io
import json
import logging
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps, UnidentifiedImageError

ICI = Path(__file__).resolve().parent
MODELE_DEFAUT = ICI / "modele" / "proprete_mobilenetv3l.onnx"
FICHE_DEFAUT = ICI / "modele" / "model_card.json"

TAILLE = 224
MOYENNE = np.array([0.485, 0.456, 0.406], dtype=np.float32)
ECART_TYPE = np.array([0.229, 0.224, 0.225], dtype=np.float32)
CLASSES = ["CANNOT_DETERMINE", "DEBORDEMENT", "PROPRE"]
TAILLE_MAX = 12 * 1024 * 1024
TYPES = {"image/jpeg": "JPEG", "image/png": "PNG"}
ORIENTATION_EXIF = 0x0112

log = logging.getLogger("vision_locale")


class ErreurDemarrage(Exception):
    pass


class ErreurRequete(Exception):
    def __init__(self, statut, code, message):
        super().__init__(message)
        self.statut, self.code, self.message = statut, code, message


def sha256_fichier(chemin):
    h = hashlib.sha256()
    with open(chemin, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


class Modele:
    """Session onnxruntime partagée (InferenceSession.run est utilisable depuis plusieurs fils)."""

    def __init__(self, chemin_modele=MODELE_DEFAUT, chemin_fiche=FICHE_DEFAUT):
        chemin_modele, chemin_fiche = Path(chemin_modele), Path(chemin_fiche)
        if not chemin_modele.is_file() or not chemin_fiche.is_file():
            raise ErreurDemarrage(f"Modèle ou fiche introuvable : {chemin_modele.name}, {chemin_fiche.name}")
        fiche = json.loads(chemin_fiche.read_text(encoding="utf-8"))
        attendu = str(fiche.get("formats", {}).get("onnx", {}).get("sha256", "")).lower()
        reel = sha256_fichier(chemin_modele)
        if not attendu or reel != attendu:
            raise ErreurDemarrage(f"SHA-256 du modèle différent de la fiche (attendu {attendu or 'absent'}, obtenu {reel}).")
        if fiche.get("ordre_sorties") != CLASSES:
            raise ErreurDemarrage(f"Ordre des sorties inattendu dans la fiche : {fiche.get('ordre_sorties')}")
        self.nom = fiche.get("nom", "proprete_mobilenetv3l")
        self.sha256 = reel
        self.session = ort.InferenceSession(str(chemin_modele), providers=["CPUExecutionProvider"])
        self.entree = self.session.get_inputs()[0].name

    def probabilites(self, x):
        return self.session.run(None, {self.entree: x})[0][0]


def tenseur(rgb224):
    """Fin de exemple_pretraitement.pretraiter : image RGB déjà étirée en 224x224 -> [1, 3, 224, 224]."""
    x = np.asarray(rgb224, dtype=np.float32) / 255.0
    x = (x - MOYENNE) / ECART_TYPE
    return x.transpose(2, 0, 1)[None].astype(np.float32)


def pretraiter_image(im, appliquer_exif=True):
    """Renvoie (tenseur, image RGB entière, image RGB 224x224, orientation EXIF lue)."""
    orientation = im.getexif().get(ORIENTATION_EXIF, 1)
    if appliquer_exif:
        im = ImageOps.exif_transpose(im)
    rgb = im.convert("RGB")
    rgb224 = rgb.resize((TAILLE, TAILLE), Image.BILINEAR)  # étirement, sans recadrage
    return tenseur(rgb224), rgb, rgb224, orientation


def mesures_qualite(rgb, rgb224):
    """Mêmes calculs que e7_qualite_image.mesures (netteté 224, luminosité sur l'image entière)."""
    g = cv2.cvtColor(np.asarray(rgb), cv2.COLOR_RGB2GRAY)
    g224 = cv2.cvtColor(np.asarray(rgb224), cv2.COLOR_RGB2GRAY)
    return float(cv2.Laplacian(g224, cv2.CV_64F).var()), float(g.mean())


def defaut_qualite(nettete, luminosite, min_nettete, min_lum, max_lum):
    if min_lum is not None and luminosite < min_lum:
        return "TROP_SOMBRE"
    if max_lum is not None and luminosite > max_lum:
        return "SUREXPOSEE"
    if min_nettete is not None and nettete < min_nettete:
        return "FLOUE"
    return None


def seuil(entetes, nom):
    v = (entetes.get(nom) or "").strip()
    if not v:
        return None
    try:
        s = float(v)
    except ValueError:
        raise ErreurRequete(400, "SEUIL_INVALIDE", f"Valeur invalide pour {nom}.")
    if not np.isfinite(s):
        raise ErreurRequete(400, "SEUIL_INVALIDE", f"Valeur invalide pour {nom}.")
    return s


def analyser(modele, octets, type_contenu, entetes):
    if type_contenu not in TYPES:
        raise ErreurRequete(415, "TYPE_REFUSE", "Photo JPEG ou PNG requise.")
    min_nettete = seuil(entetes, "X-Min-Sharpness")
    min_lum = seuil(entetes, "X-Min-Brightness")
    max_lum = seuil(entetes, "X-Max-Brightness")
    debut = time.perf_counter()
    try:
        with Image.open(io.BytesIO(octets)) as im:
            if im.format != TYPES[type_contenu]:
                raise ErreurRequete(400, "IMAGE_ILLISIBLE", "Le contenu ne correspond pas au type annoncé.")
            im.load()
            x, rgb, rgb224, orientation = pretraiter_image(im)
    except ErreurRequete:
        raise
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError, SyntaxError):
        raise ErreurRequete(400, "IMAGE_ILLISIBLE", "Image illisible.")
    nettete, luminosite = mesures_qualite(rgb, rgb224)
    p = modele.probabilites(x)
    return {
        "classes": CLASSES,
        "probabilities": {c: float(v) for c, v in zip(CLASSES, p)},
        "predicted_class": CLASSES[int(np.argmax(p))],
        "quality": {"sharpness": nettete, "mean_brightness": luminosite,
                    "gate_enabled": any(s is not None for s in (min_nettete, min_lum, max_lum)),
                    "issue": defaut_qualite(nettete, luminosite, min_nettete, min_lum, max_lum)},
        "model": {"name": modele.nom, "sha256": modele.sha256},
        "exif_orientation_applied": orientation not in (None, 1),
        "latency_ms": round((time.perf_counter() - debut) * 1000, 2),
    }


def creer_gestionnaire(modele, jeton):
    class Gestionnaire(BaseHTTPRequestHandler):
        server_version = "VisionLocale/1.0"
        sys_version = ""

        def log_message(self, format, *args):  # pas d'en-têtes ni de corps dans les journaux
            pass

        def _json(self, statut, corps):
            donnees = json.dumps(corps, ensure_ascii=False).encode("utf-8")
            self.send_response(statut)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(donnees)))
            self.end_headers()
            self.wfile.write(donnees)
            log.info("%s %s -> %d", self.command, self.path.split("?")[0], statut)

        def _erreur(self, statut, code, message):
            self._json(statut, {"error": code, "message": message})

        def do_GET(self):
            if self.path.split("?")[0] != "/health":
                return self._erreur(404, "INTROUVABLE", "Chemin inconnu.")
            self._json(200, {"status": "ok", "model_name": modele.nom, "model_sha256": modele.sha256,
                             "onnxruntime_version": ort.__version__})

        def do_POST(self):
            try:
                if self.path.split("?")[0] != "/predict":
                    raise ErreurRequete(404, "INTROUVABLE", "Chemin inconnu.")
                if jeton and not hmac.compare_digest(self.headers.get("X-Service-Token", "").encode(), jeton.encode()):
                    raise ErreurRequete(401, "JETON_INVALIDE", "Jeton de service absent ou invalide.")
                try:
                    longueur = int(self.headers.get("Content-Length", ""))
                except ValueError:
                    raise ErreurRequete(400, "LONGUEUR_REQUISE", "En-tête Content-Length requis.")
                if longueur > TAILLE_MAX:
                    self.close_connection = True
                    if longueur <= 4 * TAILLE_MAX:  # vider le corps pour que le client reçoive bien la réponse
                        reste = longueur
                        while reste > 0:
                            lu = len(self.rfile.read(min(1 << 20, reste)))
                            if lu == 0:
                                break
                            reste -= lu
                    raise ErreurRequete(413, "TROP_GROS", "Photo supérieure à 12 Mo.")
                if longueur <= 0:
                    raise ErreurRequete(400, "IMAGE_ILLISIBLE", "Corps vide.")
                octets = self.rfile.read(longueur)
                type_contenu = (self.headers.get("Content-Type") or "").split(";")[0].strip().lower()
                self._json(200, analyser(modele, octets, type_contenu, self.headers))
            except ErreurRequete as e:
                self._erreur(e.statut, e.code, e.message)
            except Exception:  # noqa: BLE001  (aucune trace de pile renvoyée)
                log.error("Erreur interne pendant l'analyse")
                self._erreur(500, "ERREUR_INTERNE", "Erreur interne du service.")

    return Gestionnaire


def creer_serveur(hote, port, modele, jeton):
    return ThreadingHTTPServer((hote, port), creer_gestionnaire(modele, jeton))


def main(argv=None):
    a = argparse.ArgumentParser(description="Service local d'inférence du modèle de propreté")
    a.add_argument("--host", default=os.environ.get("LOCAL_VISION_HOST", "127.0.0.1"))
    a.add_argument("--port", type=int, default=int(os.environ.get("LOCAL_VISION_PORT", "8090")))
    a.add_argument("--modele", default=os.environ.get("LOCAL_VISION_MODEL", str(MODELE_DEFAUT)))
    a.add_argument("--fiche", default=os.environ.get("LOCAL_VISION_MODEL_CARD", str(FICHE_DEFAUT)))
    args = a.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        modele = Modele(args.modele, args.fiche)
    except ErreurDemarrage as e:
        log.error("Démarrage refusé : %s", e)
        return 2
    serveur = creer_serveur(args.host, args.port, modele, os.environ.get("LOCAL_VISION_TOKEN", ""))
    log.info("Service prêt sur http://%s:%d (modèle %s, sha256 %s, jeton %s)", args.host, args.port,
             modele.nom, modele.sha256, "exigé" if os.environ.get("LOCAL_VISION_TOKEN") else "non exigé")
    try:
        serveur.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        serveur.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
