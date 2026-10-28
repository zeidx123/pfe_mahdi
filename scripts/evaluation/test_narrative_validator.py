"""Vecteurs partagés avec NarrativeValidatorTest (Java) : python -m unittest discover -s scripts/evaluation -p "test_*.py"."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from narrative_validator import RACINE, Validateur  # noqa: E402

CAS = RACINE / "BACK" / "src" / "test" / "resources" / "vision" / "narrative_validator_cases.json"


class TestNarrativeValidator(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.validateur = Validateur()
        cls.cas = json.loads(CAS.read_text(encoding="utf-8"))["cas"]

    def test_vecteurs_partages(self):
        self.assertGreaterEqual(len(self.cas), 30)
        for c in self.cas:
            with self.subTest(cas=c["id"]):
                obtenu = self.validateur.valider(c["texte"], c["donnees"])
                self.assertEqual(obtenu, c["motifs"])
                self.assertEqual(c["attendu"], "accepte" if not obtenu else "rejete")

    def test_chaque_regle_est_couverte(self):
        couverts = {m for c in self.cas for m in c["motifs"]}
        self.assertEqual(couverts, {f"V{i}" for i in range(1, 8)})
        self.assertTrue(any(c["attendu"] == "accepte" for c in self.cas))


if __name__ == "__main__":
    unittest.main()
