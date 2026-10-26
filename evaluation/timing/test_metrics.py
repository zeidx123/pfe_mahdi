import unittest
from metrics import evaluate
class TimingTest(unittest.TestCase):
    def test_known_errors_and_missing_measurement(self):
        result=evaluate([{'reference':'a','estimatedAtStartSeconds':100,'actualDurationSeconds':120},
                         {'reference':'b','estimatedAtStartSeconds':100,'actualDurationSeconds':80},
                         {'reference':'missing','estimatedAtStartSeconds':100,'actualDurationSeconds':None}])
        self.assertEqual(20,result['totalMAESeconds']);self.assertAlmostEqual((100/6+25)/2,result['totalMAPEPercent']);self.assertEqual(2,result['measuredTours']);self.assertEqual(1,len(result['excludedTours']))
    def test_partial_service_is_not_compared_with_all_stops(self):
        result=evaluate([{'estimatedAtStartSeconds':100,'actualDurationSeconds':100,'estimatedServiceAtStartSeconds':60,'observedServiceSeconds':30,'measuredServiceStops':1,'visits':[{},{}]}])
        self.assertIsNone(result['serviceMAESeconds'])
    def test_zero_actual_has_no_percentage_and_complete_service_compares(self):
        result=evaluate([{'estimatedAtStartSeconds':60,'actualDurationSeconds':0,'estimatedServiceAtStartSeconds':30,'observedServiceSeconds':0,'measuredServiceStops':1,'visits':[{}]}])
        self.assertEqual(60,result['totalMAESeconds']);self.assertIsNone(result['totalMAPEPercent']);self.assertEqual(30,result['serviceMAESeconds'])
if __name__=='__main__':unittest.main()
