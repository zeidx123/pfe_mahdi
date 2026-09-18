import unittest
from metrics import summarize


def row(scene, mode, prediction, reference):
    return dict(sceneId=scene, split="test", inputMode=mode, prediction=prediction,
                referenceLabel=reference, reviewer="fixture-reviewer", reviewDate="2026-09-17",
                model="mock-model", promptVersion="test", reviewStatus="REVIEWED", independentReview=True)


class MetricsTest(unittest.TestCase):
    def test_unreviewed_transport_only_has_coverage_and_no_accuracy(self):
        result_row = row("a", "AFTER_ONLY", "CLEANED", None)
        result_row.update(reviewStatus="UNREVIEWED", reviewer=None, reviewDate=None, independentReview=False,
                          dataKind="ABSTRACT_SYNTHETIC_TRANSPORT_ONLY")
        result = summarize([result_row])[0]
        self.assertEqual(result["analysisCoverage"], 1)
        self.assertEqual(result["reviewedCount"], 0)
        self.assertFalse(result["accuracyAvailable"])
        self.assertIsNone(result["classes"]["CLEANED"]["precision"])
        self.assertIsNone(result["macroF1RepresentedClasses"])
        self.assertIsNone(result["matchedSceneMetrics"])

    def test_unreviewed_predictions_do_not_dilute_reviewed_scores(self):
        unreviewed = row("b", "AFTER_ONLY", "NOT_CLEANED", None)
        unreviewed.update(reviewStatus="UNREVIEWED", reviewer=None, reviewDate=None, independentReview=False)
        result = summarize([row("a", "AFTER_ONLY", "CLEANED", "CLEANED"), unreviewed])[0]
        self.assertEqual(result["classes"]["CLEANED"]["f1"], 1)
        self.assertEqual(result["classificationAvailableCount"], 1)
        self.assertEqual(result["reviewCoverage"], .5)

    def test_incomplete_review_is_not_silently_treated_as_unreviewed(self):
        with self.assertRaisesRegex(ValueError, "Reviewed results"):
            summarize([row("a", "AFTER_ONLY", "CLEANED", None)])
        contradicted = row("a", "AFTER_ONLY", "CLEANED", "CLEANED")
        contradicted.update(reviewStatus="UNREVIEWED")
        with self.assertRaisesRegex(ValueError, "UNREVIEWED"):
            summarize([contradicted])

    def test_pair_comparison_requires_both_modes_reviewed(self):
        unreviewed = row("a", "BEFORE_AFTER", "CLEANED", None)
        unreviewed.update(reviewStatus="UNREVIEWED", reviewer=None, reviewDate=None, independentReview=False)
        reports = summarize([row("a", "AFTER_ONLY", "CLEANED", "CLEANED"), unreviewed])
        self.assertEqual(reports[0]["pairedSceneCount"], 1)
        self.assertEqual(reports[0]["reviewedPairedSceneCount"], 0)
        self.assertIsNone(reports[0]["matchedSceneMetrics"])

    def test_explicit_review_must_be_independent(self):
        reviewed = row("a", "AFTER_ONLY", "CLEANED", "CLEANED")
        reviewed.update(reviewStatus="REVIEWED", independentReview=False)
        with self.assertRaisesRegex(ValueError, "independentReview"):
            summarize([reviewed])

    def test_legacy_labels_cannot_bypass_independent_review_requirement(self):
        for declaration in (None, False):
            with self.subTest(independentReview=declaration):
                legacy = row("legacy", "AFTER_ONLY", "CLEANED", "CLEANED")
                legacy.pop("reviewStatus")
                if declaration is None:
                    legacy.pop("independentReview")
                else:
                    legacy["independentReview"] = declaration
                with self.assertRaisesRegex(ValueError, "independentReview"):
                    summarize([legacy])

    def test_unavailable_excluded_from_classification_denominator(self):
        result = summarize([row("a", "AFTER_ONLY", "NOT_CLEANED", "NOT_CLEANED"),
                            row("b", "AFTER_ONLY", "ANALYSIS_UNAVAILABLE", "NOT_CLEANED")])[0]
        self.assertEqual(result["analysisCoverage"], .5)
        self.assertEqual(result["classes"]["NOT_CLEANED"]["recall"], 1)

    def test_all_unavailable_has_no_classification_score(self):
        result = summarize([row("a", "AFTER_ONLY", "ANALYSIS_UNAVAILABLE", "CLEANED")])[0]
        self.assertIsNone(result["macroF1RepresentedClasses"])
        self.assertEqual(result["analysisCoverage"], 0)

    def test_matched_scene_metrics_exclude_unmatched_scene(self):
        result = summarize([row("a", "AFTER_ONLY", "CLEANED", "NOT_CLEANED"),
                            row("b", "AFTER_ONLY", "NOT_CLEANED", "NOT_CLEANED"),
                            row("a", "BEFORE_AFTER", "NOT_CLEANED", "NOT_CLEANED")])
        after = result[0]
        self.assertEqual(after["notCleanedPredictedCleanedRate"], .5)
        self.assertEqual(after["matchedSceneMetrics"]["notCleanedPredictedCleanedRate"], 1)
        self.assertEqual(result[1]["matchedSceneMetrics"]["notCleanedPredictedCleanedRate"], 0)

    def test_same_scene_cannot_cross_splits(self):
        a = row("a", "AFTER_ONLY", "CLEANED", "CLEANED")
        b = row("a", "BEFORE_AFTER", "CLEANED", "CLEANED")
        b["split"] = "development"
        with self.assertRaises(ValueError):
            summarize([a, b])


if __name__ == "__main__":
    unittest.main()
