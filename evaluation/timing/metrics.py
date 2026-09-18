#!/usr/bin/env python3
"""Compare frozen estimates with observed tour durations from authenticated API exports."""
import argparse, json, math, pathlib, statistics

def number(value):
    return isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and value >= 0

def evaluate(tours):
    rows=[]; excluded=[]
    for tour in tours:
        identifier=tour.get('reference',tour.get('id','unknown'))
        estimate=tour.get('estimatedAtStartSeconds'); actual=tour.get('actualDurationSeconds')
        if not number(estimate) or not number(actual):
            excluded.append({'tour':identifier,'reason':'Missing/invalid frozen estimate or observed total'}); continue
        row={'tour':identifier,'estimatedSeconds':estimate,'actualSeconds':actual,'signedErrorSeconds':actual-estimate,'absoluteErrorSeconds':abs(actual-estimate),
             'relativeToEstimatePct':100*(actual-estimate)/estimate if estimate>0 else None,
             'absolutePercentageErrorPct':100*abs(actual-estimate)/actual if actual>0 else None}
        service_estimate=tour.get('estimatedServiceAtStartSeconds'); service_actual=tour.get('observedServiceSeconds')
        visits=tour.get('visits',[])
        if visits and tour.get('measuredServiceStops')==len(visits) and number(service_estimate) and number(service_actual):
            row['serviceAbsoluteErrorSeconds']=abs(service_actual-service_estimate)
        else: row['serviceAbsoluteErrorSeconds']=None
        rows.append(row)
    errors=[r['absoluteErrorSeconds'] for r in rows]; percentage=[r['absolutePercentageErrorPct'] for r in rows if r['absolutePercentageErrorPct'] is not None]
    service_errors=[r['serviceAbsoluteErrorSeconds'] for r in rows if r['serviceAbsoluteErrorSeconds'] is not None]
    return {'measuredTours':len(rows),'excludedTours':excluded,'totalMAESeconds':statistics.mean(errors) if errors else None,
            'totalMAPEPercent':statistics.mean(percentage) if percentage else None,'MAPETours':len(percentage),
            'serviceMAESeconds':statistics.mean(service_errors) if service_errors else None,'completeServiceMeasurements':len(service_errors),
            'rows':rows,'limitations':'Elapsed time includes waiting/breaks. Missing measurements are excluded, not estimated. No observed driving distance is inferred.'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('exports',type=pathlib.Path);args=parser.parse_args()
    data=json.loads(args.exports.read_text());tours=data if isinstance(data,list) else [data]
    print(json.dumps(evaluate(tours),indent=2,ensure_ascii=False))
if __name__=='__main__':main()
