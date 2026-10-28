"""Hors ligne : construction déterministe des cas et des prompts de evaluate_narration.py (aucun réseau)."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import evaluate_narration as ev  # noqa: E402


class TestEvaluateNarration(unittest.TestCase):
    def test_quarante_cas_deterministes_et_coherents(self):
        cas = ev.construire_cas()
        self.assertEqual(len(cas), 40)
        self.assertEqual(cas, ev.construire_cas())
        for c in cas:
            d = c["donnees"]
            p = d["pourcentages"]
            self.assertEqual(max(p, key=p.get), d["classe_predite"])
            if d["decision"] == "CONFIRMATION_AUTO":
                self.assertEqual((d["classe_predite"], d["probleme_qualite"]), ("PROPRE", None))
        self.assertEqual({c["donnees"]["probleme_qualite"] for c in cas}, set(ev.QUALITES))

    def test_prompt_ne_contient_que_les_donnees_structurees(self):
        gabarit = ev.PROMPT.read_text(encoding="utf-8")
        d = ev.construire_cas()[0]["donnees"]
        prompt = ev.construire_prompt(gabarit, d)
        self.assertIn("Tu n'as PAS vu la photo", prompt)
        self.assertIn('"classe_predite":"propre"', prompt)
        self.assertNotIn("refusée", prompt)
        self.assertNotIn("http", prompt.split("Données :")[1])
        relance = ev.construire_prompt(gabarit, d, ["V2", "V5"])
        self.assertIn(ev.REGLES["V2"], relance)

    def test_dry_run_sans_reseau(self):
        with tempfile.TemporaryDirectory() as dossier, mock.patch("urllib.request.urlopen", side_effect=AssertionError("réseau")):
            self.assertEqual(ev.main(["--sortie", dossier]), 0)
            lignes = (Path(dossier) / "prompts_dry_run.jsonl").read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lignes), 40)

    def test_live_sans_cle_refuse_sans_appel(self):
        with tempfile.TemporaryDirectory() as dossier, mock.patch.dict("os.environ", {"GROQ_API_KEY": ""}), \
                mock.patch("urllib.request.urlopen", side_effect=AssertionError("réseau")):
            self.assertEqual(ev.main(["--live", "--sortie", dossier]), 2)


if __name__ == "__main__":
    unittest.main()
