"""Validateur déterministe des résumés rédigés (règles V1 à V7), miroir de NarrativeValidator.java.

Les deux implémentations lisent la même liste de mots interdits
(BACK/src/main/resources/prompts/vision-narrative-forbidden.txt) et sont vérifiées sur les mêmes vecteurs
(BACK/src/test/resources/vision/narrative_validator_cases.json). Bibliothèque standard uniquement.

Données attendues (« donnees ») : {"classe_predite": "PROPRE"|"DEBORDEMENT"|"CANNOT_DETERMINE",
"pourcentages": {"PROPRE": int, "DEBORDEMENT": int, "CANNOT_DETERMINE": int},
"probleme_qualite": null|"TROP_SOMBRE"|"SUREXPOSEE"|"FLOUE", "nettete": float (1 décimale), "luminosite": float}.
"""
import re
import unicodedata
from decimal import Decimal
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
RESSOURCES = RACINE / "BACK" / "src" / "main" / "resources" / "prompts"
MOTS_INTERDITS = RESSOURCES / "vision-narrative-forbidden.txt"
LONGUEUR_MAX = 600
NOMBRE = re.compile(r"(?<![0-9])[0-9]+(?:[.,][0-9]+)?")
URL = re.compile(r"https?://|www\.")
ESPACES = re.compile(r"[ \t\r\n\f\v]+")
BALISAGE = set("*#{}`")
HUMAIN = ["controle humain", "verification humaine", "verifier", "confirmer"]
MOTS_QUALITE = {"TROP_SOMBRE": "sombre", "FLOUE": "flou", "SUREXPOSEE": "surexpos"}


def normaliser(texte):
    t = texte.replace(" ", " ").replace(" ", " ").replace(" ", " ")
    t = "".join(c for c in unicodedata.normalize("NFD", t) if not unicodedata.category(c).startswith("M"))
    t = t.lower().replace("’", "'").replace("‘", "'").replace("ʼ", "'")
    return ESPACES.sub(" ", t)


def nettoyer(texte):
    return (texte or "").strip(" \t\r\n")


def charger_interdits(chemin=MOTS_INTERDITS):
    motifs = []
    for ligne in Path(chemin).read_text(encoding="utf-8").splitlines():
        entree = ligne.strip()
        if entree and not entree.startswith("#"):
            motifs.append(re.compile(r"(?<![a-z0-9])" + re.escape(normaliser(entree)) + r"(?:e|s|es|x)?(?![a-z0-9])"))
    return motifs


class Validateur:
    def __init__(self, chemin_interdits=MOTS_INTERDITS):
        self.interdits = charger_interdits(chemin_interdits)

    def valider(self, texte, donnees):
        """Liste triée des codes de règles violées ; liste vide = texte accepté."""
        propre = nettoyer(texte)
        if not propre:
            return ["V1"]
        codes = set()
        if len(propre) > LONGUEUR_MAX:
            codes.add("V1")
        n = normaliser(propre)
        p = donnees["pourcentages"]
        autorises = [Decimal(int(p[c])) for c in ("PROPRE", "DEBORDEMENT", "CANNOT_DETERMINE")]
        autorises += [Decimal(repr(float(donnees["nettete"]))), Decimal(repr(float(donnees["luminosite"])))]
        for m in NOMBRE.finditer(n):
            v = Decimal(m.group().replace(",", "."))
            if not any(v == a for a in autorises):
                codes.add("V2")
                break
        if any(motif.search(n) for motif in self.interdits):
            codes.add("V3")
        if "automatique" not in n or not any(h in n for h in HUMAIN):
            codes.add("V4")
        pourcent = int(p[donnees["classe_predite"]])
        v5 = not re.search(r"(?<![0-9])(?<![0-9][.,])" + str(pourcent) + r"(?![.,]?[0-9]) ?(?:%|pour cent)", n)
        probleme = donnees.get("probleme_qualite")
        if probleme is not None:
            mot = MOTS_QUALITE.get(probleme)
            if mot is None or mot not in n or "photo" not in n:
                v5 = True
        if v5:
            codes.add("V5")
        if any(c in BALISAGE for c in propre):
            codes.add("V6")
        if URL.search(n):
            codes.add("V7")
        return sorted(codes)
