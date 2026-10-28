"""Tests du service local (unittest). Depuis ce dossier : python -m unittest -v test_service

Le service est lancé dans un fil, sur un port libre de 127.0.0.1, avec le vrai modèle de modele/.
"""
import io
import json
import shutil
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image

ICI = Path(__file__).resolve().parent
sys.path.insert(0, str(ICI))
import app  # noqa: E402

EXEMPLES = ICI / "exemples"
REFERENCE_PRETRAITEMENT = ICI.parent / "export" / "exemple_pretraitement.py"


def demarrer(jeton=""):
    modele = app.Modele()
    serveur = app.creer_serveur("127.0.0.1", 0, modele, jeton)
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    return serveur, f"http://127.0.0.1:{serveur.server_address[1]}"


def appeler(url, chemin="/predict", corps=None, entetes=None, methode="POST"):
    req = urllib.request.Request(url + chemin, data=corps, headers=entetes or {}, method=methode)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))


def png(im):
    b = io.BytesIO()
    im.save(b, format="PNG")
    return b.getvalue()


def texture(gris_moyen=128, amplitude=255, graine=42):
    """Bruit uniforme centré sur gris_moyen ; amplitude 0 = image uniforme (variance du Laplacien nulle)."""
    rng = np.random.default_rng(graine)
    x = (rng.random((320, 320, 3)) - 0.5) * amplitude + gris_moyen
    return Image.fromarray(np.clip(np.rint(x), 0, 255).astype(np.uint8), "RGB")


class TestService(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.serveur, cls.url = demarrer()
        cls.attendus = json.loads((EXEMPLES / "attendus.json").read_text(encoding="utf-8"))

    @classmethod
    def tearDownClass(cls):
        cls.serveur.shutdown()
        cls.serveur.server_close()

    def predire(self, octets, type_="image/jpeg", entetes=None):
        return appeler(self.url, corps=octets, entetes={"Content-Type": type_, **(entetes or {})})

    def test_health(self):
        statut, r = appeler(self.url, "/health", methode="GET")
        self.assertEqual(statut, 200)
        self.assertEqual(r["status"], "ok")
        self.assertEqual(r["model_name"], "proprete_mobilenetv3l")
        self.assertEqual(r["model_sha256"], app.sha256_fichier(app.MODELE_DEFAUT))
        self.assertTrue(r["onnxruntime_version"])

    def test_prediction_sur_trois_images(self):
        for a in self.attendus:
            with self.subTest(fichier=a["fichier"]):
                statut, r = self.predire((EXEMPLES / a["fichier"]).read_bytes())
                self.assertEqual(statut, 200)
                self.assertEqual(r["classes"], app.CLASSES)
                for c in app.CLASSES:
                    self.assertAlmostEqual(r["probabilities"][c], a["probabilites"][c], delta=1e-5)
                self.assertAlmostEqual(sum(r["probabilities"].values()), 1.0, delta=1e-3)
                self.assertEqual(r["predicted_class"], max(a["probabilites"], key=a["probabilites"].get))
                self.assertFalse(r["quality"]["gate_enabled"])
                self.assertIsNone(r["quality"]["issue"])
                self.assertFalse(r["exif_orientation_applied"])
                self.assertEqual(r["model"]["sha256"], app.sha256_fichier(app.MODELE_DEFAUT))

    @unittest.skipUnless(REFERENCE_PRETRAITEMENT.is_file(), "export/exemple_pretraitement.py absent")
    def test_pretraitement_identique_bit_a_bit_a_la_reference(self):
        sys.path.insert(0, str(REFERENCE_PRETRAITEMENT.parent))
        from exemple_pretraitement import pretraiter
        for a in self.attendus:
            chemin = EXEMPLES / a["fichier"]
            with Image.open(chemin) as im:
                x, _, _, _ = app.pretraiter_image(im)
            self.assertTrue(np.array_equal(x, pretraiter(chemin)), a["fichier"])

    def test_orientation_exif_appliquee(self):
        chemin = EXEMPLES / self.attendus[0]["fichier"]
        with Image.open(chemin) as im:
            droite = im.convert("RGB")
        exif = Image.Exif()
        exif[app.ORIENTATION_EXIF] = 6
        b = io.BytesIO()
        droite.transpose(Image.ROTATE_90).save(b, format="PNG", exif=exif.tobytes())
        statut, r = self.predire(b.getvalue(), "image/png")
        self.assertEqual(statut, 200)
        self.assertTrue(r["exif_orientation_applied"])
        for c in app.CLASSES:
            self.assertAlmostEqual(r["probabilities"][c], self.attendus[0]["probabilites"][c], delta=1e-5)

    def test_octets_invalides(self):
        statut, r = self.predire(b"pas une image")
        self.assertEqual((statut, r["error"]), (400, "IMAGE_ILLISIBLE"))
        statut, r = self.predire(png(texture()), "image/jpeg")  # contenu PNG annoncé JPEG
        self.assertEqual((statut, r["error"]), (400, "IMAGE_ILLISIBLE"))

    def test_type_refuse(self):
        statut, r = self.predire(png(texture()), "image/webp")
        self.assertEqual((statut, r["error"]), (415, "TYPE_REFUSE"))

    def test_image_trop_grosse(self):
        statut, r = self.predire(b"\0" * (app.TAILLE_MAX + 1))
        self.assertEqual((statut, r["error"]), (413, "TROP_GROS"))

    def test_seuil_invalide(self):
        statut, r = self.predire(png(texture()), "image/png", {"X-Min-Sharpness": "abc"})
        self.assertEqual((statut, r["error"]), (400, "SEUIL_INVALIDE"))

    def test_chemin_inconnu(self):
        statut, r = appeler(self.url, "/autre", methode="GET")
        self.assertEqual((statut, r["error"]), (404, "INTROUVABLE"))

    def test_porte_de_qualite(self):
        seuils = {"X-Min-Sharpness": "100", "X-Min-Brightness": "30", "X-Max-Brightness": "230"}
        cas = [(texture(128), None), (texture(128, amplitude=0), "FLOUE"), (texture(10, amplitude=16), "TROP_SOMBRE"),
               (texture(245, amplitude=0), "SUREXPOSEE"), (texture(10, amplitude=0), "TROP_SOMBRE")]  # luminosité avant netteté
        for im, attendu in cas:
            with self.subTest(attendu=attendu):
                statut, r = self.predire(png(im), "image/png", seuils)
                self.assertEqual(statut, 200)
                self.assertTrue(r["quality"]["gate_enabled"])
                self.assertEqual(r["quality"]["issue"], attendu)
                self.assertAlmostEqual(sum(r["probabilities"].values()), 1.0, delta=1e-3)  # modèle quand même appelé
        statut, r = self.predire(png(texture(10, amplitude=0)), "image/png")  # porte désactivée sans seuil
        self.assertEqual((statut, r["quality"]["gate_enabled"], r["quality"]["issue"]), (200, False, None))

    def test_mesures_identiques_a_e7(self):
        chemin = EXEMPLES / self.attendus[0]["fichier"]
        with Image.open(chemin) as im:
            _, rgb, rgb224, _ = app.pretraiter_image(im)
        nettete, lum = app.mesures_qualite(rgb, rgb224)
        import cv2
        with Image.open(chemin) as im:  # recopie littérale de e7_qualite_image.mesures
            im = im.convert("RGB")
            g640 = cv2.cvtColor(np.asarray(im), cv2.COLOR_RGB2GRAY)
            g224 = cv2.cvtColor(np.asarray(im.resize((224, 224), Image.BILINEAR)), cv2.COLOR_RGB2GRAY)
        self.assertEqual(nettete, cv2.Laplacian(g224, cv2.CV_64F).var())
        self.assertEqual(lum, float(g640.mean()))


class TestJeton(unittest.TestCase):
    def test_jeton_absent_ou_faux(self):
        serveur, url = demarrer(jeton="secret-de-test")
        try:
            octets = (EXEMPLES / json.loads((EXEMPLES / "attendus.json").read_text(encoding="utf-8"))[0]["fichier"]).read_bytes()
            for entetes in ({}, {"X-Service-Token": "faux"}):
                statut, r = appeler(url, corps=octets, entetes={"Content-Type": "image/jpeg", **entetes})
                self.assertEqual((statut, r["error"]), (401, "JETON_INVALIDE"))
            statut, _ = appeler(url, corps=octets, entetes={"Content-Type": "image/jpeg", "X-Service-Token": "secret-de-test"})
            self.assertEqual(statut, 200)
        finally:
            serveur.shutdown()
            serveur.server_close()


class TestDemarrage(unittest.TestCase):
    def test_sha256_errone_refuse_de_demarrer(self):
        with tempfile.TemporaryDirectory() as d:
            fiche = json.loads(app.FICHE_DEFAUT.read_text(encoding="utf-8"))
            fiche["formats"]["onnx"]["sha256"] = "0" * 64
            chemin_fiche = Path(d) / "model_card.json"
            chemin_fiche.write_text(json.dumps(fiche), encoding="utf-8")
            with self.assertRaises(app.ErreurDemarrage):
                app.Modele(app.MODELE_DEFAUT, chemin_fiche)
            self.assertEqual(app.main(["--port", "0", "--fiche", str(chemin_fiche)]), 2)

    def test_modele_modifie_refuse_de_demarrer(self):
        with tempfile.TemporaryDirectory() as d:
            copie = Path(d) / "modele.onnx"
            shutil.copyfile(app.MODELE_DEFAUT, copie)
            with open(copie, "ab") as f:
                f.write(b"\0")
            with self.assertRaises(app.ErreurDemarrage):
                app.Modele(copie, app.FICHE_DEFAUT)


if __name__ == "__main__":
    unittest.main()
