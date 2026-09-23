#!/usr/bin/env python3
"""Local HTTP integration check. Road/model responses are explicit fixtures, not real AI/roads.
Uses disposable MongoDB/uploads on ports 27019/18080/18888, never the application database.
Build BACK first. --serve keeps the staged backend available for browser inspection.
"""
import argparse, base64, http.server, io, json, os, pathlib, shutil, signal, socket, struct, subprocess, tempfile, threading, time, urllib.request, urllib.error, uuid, zipfile, zlib
from xml.sax.saxutils import escape
ROOT=pathlib.Path(__file__).resolve().parents[1]
API='http://127.0.0.1:18080'
RESULTS=[]
class Fixture(http.server.BaseHTTPRequestHandler):
    vision_available=True
    def log_message(self,*args): pass
    def do_POST(self):
        data=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or '{}')
        status=200
        if self.path.startswith('/v2/matrix/'):
            n=len(data['locations']); seconds=[[0 if i==j else 30+abs(i-j)*100+(i*7)%13 for j in range(n)] for i in range(n)]
            body={'durations':seconds,'distances':[[v*7 for v in row] for row in seconds]}
        elif self.path.startswith('/v2/directions/'):
            body={'features':[{'geometry':{'type':'LineString','coordinates':data['coordinates']}}]}
        elif ':generateContent' in self.path and Fixture.vision_available:
            body={'candidates':[{'content':{'parts':[{'text':json.dumps({'status':'CLEANED','description':'Réponse SIMULÉE pour vérifier le contrat HTTP, aucune inférence réelle.'})}]}}]}
        else: status=503; body={'error':{'message':'Explicit fixture provider outage'}}
        payload=json.dumps(body).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(payload)

def request(method,path,data=None,token=None,expected=200,raw=None,content_type=None):
    headers={}
    if token: headers['Authorization']='Bearer '+token
    if raw is None and data is not None: raw=json.dumps(data).encode();content_type='application/json'
    if content_type: headers['Content-Type']=content_type
    req=urllib.request.Request(API+path,data=raw,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=60) as response: status=response.status; body=response.read(); mime=response.headers.get('Content-Type','')
    except urllib.error.HTTPError as error: status=error.code;body=error.read();mime=error.headers.get('Content-Type','')
    if status!=expected: raise AssertionError(f'{method} {path}: expected {expected}, got {status}: {body[:400].decode(errors="replace")}')
    RESULTS.append({'method':method,'path':path,'status':status})
    return json.loads(body) if body and 'json' in mime else body

def multipart(path,field,filename,content,mime,token):
    boundary=uuid.uuid4().hex
    raw=(f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n').encode()+content+f'\r\n--{boundary}--\r\n'.encode()
    return request('POST',path,token=token,raw=raw,content_type='multipart/form-data; boundary='+boundary)

def png():
    def chunk(kind,data): return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',32,32,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+b'\x88\x88\x88'*32)*32))+chunk(b'IEND',b'')

def staged_cv():
    """An explicit fictional, labelled DOCX fixture; no real candidate data."""
    lines=['CV FICTIF — vérification HTTP, pas un benchmark d’extraction', 'Nom : Staged',
           'Prénom : Candidate', 'CIN : 22222222', 'Téléphone : 22222222',
           'Email : candidate@staged.test', 'Permis : C', 'Numéro permis : STAGED-C',
           'Expiration : 2030-12-31', '1 an de conduite', 'Formation sécurité : oui',
           'Expérience de collecte : non']
    document='<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'+''.join('<w:p><w:r><w:t>'+escape(line)+'</w:t></w:r></w:p>' for line in lines)+'</w:body></w:document>'
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as doc:
        doc.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        doc.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        doc.writestr('word/document.xml',document)
    return out.getvalue()

def verify(folder, restart_backend):
    admin=request('POST','/api/auth/login',{'email':'supervisor@staged.test','password':'StagedDemo2026!'})['token']
    drivers=[]
    for i in (1,2):
        person={'nom':'Staged','prenom':f'Driver{i}','cin':f'1111111{i}','email':f'driver{i}@staged.test','telephone':f'2111111{i}','typePermis':'C','numeroPermis':f'DEMO{i}','dateEmbauche':'2026-09-17'}
        driver=request('POST','/api/agents',person,admin,201)
        login=request('POST','/api/auth/login',{'email':person['email'],'password':person['cin']})
        assert login['id']==driver['id'] and login['role']=='CHAUFFEUR'
        drivers.append((driver,login['token']))
    driver,token=drivers[0];other=drivers[1][1]
    truck=request('POST','/api/camions',{'matricule':'STAGED-TRUCK','marque':'Demo','statut':'DISPONIBLE','actif':True},admin,201)
    points=[]
    for i in range(3):
        point=request('POST','/api/points',{'reference':f'STAGED-P{i+1}','adresse':f'Point simulé {i+1}','latitude':36.723+i*.001,'longitude':10.284+i*.001,'typeDechet':'GENERAL','niveauRemplissage':80,'actif':True},admin,201)
        points.append(point['id'])
    tour=request('POST','/api/tournees',{'reference':'STAGED-T1','nom':'Vérification — routes et IA simulées','datePrevue':'2026-09-17','camionId':truck['id'],'chauffeurId':driver['id'],'pointsCollecteIds':points},admin,201)
    path='/api/tournees/'+tour['id']
    request('GET',path,token=other,expected=403)
    assert request('GET','/api/tournees',token=other)==[]
    request('POST',path+'/start',token=token,expected=409)
    tour=request('POST',path+'/optimiser',token=admin)
    assert len(tour['route']['legs'])==4 and len(tour['route']['comparisons'])==4
    assert tour['route']['depot']['latitude']==36.722857
    assert tour['route']['depot']['longitude']==10.283535 and tour['route']['depot']['label']=='Dépôt'
    tour=request('POST',path+'/start',token=token);started=tour['heureDebutReelle']
    assert request('POST',path+'/start',token=token)['heureDebutReelle']==started
    request('POST','/api/tracking/position',{'camionId':truck['id'],'tourneeId':tour['id'],'latitude':36.722857,'longitude':10.283535,'vitesse':12},token)
    assert request('GET','/api/tracking/positions',token=admin)[0]['stale'] is False
    request('GET','/api/tracking/positions',token=token,expected=403)
    visit_ids=[v['pointId'] for v in tour['visits']]
    for index,point_id in enumerate(visit_ids):
        stop=path+'/points/'+point_id
        if index==2:
            tour=request('POST',stop+'/complete',{'outcome':'ISSUE','notes':'Accès dangereux simulé; photo impossible.'},token)
            continue
        request('POST',stop+'/arrive',token=token)
        request('POST',stop+'/complete',{'outcome':'COMPLETED'},token,409)
        if index==0: multipart(stop+'/photo?stage=BEFORE','photo','before.png',png(),'image/png',token)
        tour=multipart(stop+'/photo?stage=AFTER','photo','after.png',png(),'image/png',token)
        visit=next(v for v in tour['visits'] if v['pointId']==point_id)
        request('GET','/uploads/photos/'+visit['afterPhoto'],token=other,expected=403)
        request('GET','/uploads/photos/'+visit['afterPhoto'],token=token)
        Fixture.vision_available=index==0
        tour=request('POST',stop+'/analyse',token=token)
        analysis=next(v for v in tour['visits'] if v['pointId']==point_id)
        assert analysis['analysisStatus']==('CLEANED' if index==0 else 'ANALYSIS_UNAVAILABLE')
        request('POST',stop+'/complete',{'outcome':'COMPLETED','driverAssessment':'CLEANED'},token)
        assert next(v for v in request('GET',path,token=token)['visits'] if v['pointId']==point_id)['status']=='COMPLETED_REPORTED'
        if index==0:
            restart_backend()
            resumed=request('GET',path,token=token)
            assert resumed['heureDebutReelle']==started and resumed['visits'][0]['status']=='COMPLETED_REPORTED'
            assert resumed['visits'][0]['afterPhoto']==visit['afterPhoto']
            assert request('POST',path+'/start',token=token)['heureDebutReelle']==started
            # The active driver resumes sending GPS after reconnect; no position is invented at restart.
            request('POST','/api/tracking/position',{'camionId':truck['id'],'tourneeId':tour['id'],'latitude':36.723,'longitude':10.284,'vitesse':0},token)
    request('POST',path+'/finish',{'returnedToDepot':False},token,400)
    finished=request('POST',path+'/finish',{'returnedToDepot':True},token)
    assert finished['statut']=='TERMINEE' and finished['actualDurationSeconds']>=0 and finished['photoFin'] is None
    assert request('GET','/api/tracking/positions',token=admin)[0]['stale'] is True
    request('POST',path+'/start',token=token,expected=409)
    reviewed=request('PATCH',path+'/points/'+visit_ids[0]+'/review',{'label':'NOT_CLEANED','notes':'Correction humaine simulée'},admin)
    assert reviewed['visits'][0]['reviewLabel']=='NOT_CLEANED' and reviewed['visits'][0]['analysisStatus']=='CLEANED'
    pdf_path='/api/rapports/tournee/'+tour['id']+'/pdf'
    request('GET',pdf_path,token=token,expected=403)
    report=request('GET',pdf_path,token=admin)
    assert report.startswith(b'%PDF') and len(report)>1000
    (folder/'staged-tour-report.pdf').write_bytes(report)
    doc=multipart('/api/ai/documents','file','staged-procedure.txt','Document SIMULÉ. Le code de contrôle de cette démonstration est DEMO-42.'.encode(),'text/plain',admin)
    for _ in range(60):
        doc=request('GET','/api/ai/documents/'+doc['id'],token=admin)
        if doc['status'] in ('TEXT_READY','INDEXED','FAILED'): break
        time.sleep(.2)
    assert doc['status']=='TEXT_READY' and not doc['approved']
    request('GET','/api/ai/documents/'+doc['id']+'/source',token=token,expected=403)
    request('PATCH','/api/ai/documents/'+doc['id']+'/approval',{'approved':True},admin)
    answer=request('POST','/api/ai/rag/query',{'question':'Quel code de contrôle pour cette démonstration ?'},token)
    assert answer['status']=='SOURCE_EXCERPTS' and 'DEMO-42' in answer['answer'] and answer['sources']
    request('POST','/api/ai/rag/query',{'question':'suite','conversationId':answer['conversationId']},other,403)
    request('GET',answer['sources'][0]['sourceUrl'],token=token)
    assert request('GET','/api/camions/'+truck['id'],token=admin)['statut']=='DISPONIBLE'
    request('GET','/api/ai/cv/requirements',token=token,expected=403)
    profile=request('GET','/api/ai/cv/requirements',token=admin)
    assert profile['experienceTargetYears']==2 and profile['experienceRequired'] is False
    candidate=multipart('/api/ai/cv/upload','cv','staged-cv.docx',staged_cv(),'application/vnd.openxmlformats-officedocument.wordprocessingml.document',admin)
    cp='/api/ai/candidates/'+candidate['id']
    assert candidate['extractionStatus']=='PROVIDER_UNAVAILABLE'
    assert candidate['fieldEvidence']['cin']['value']=='22222222' and candidate['scoreProvisional'] is True
    request('PATCH',cp+'/decision',{'statut':'ACCEPTE'},admin,400)
    request('PATCH',cp+'/review',{'fields':{'typePermis':{'value':'C'}}},admin,400)
    request('PATCH',cp+'/review',{'fields':{'typePermis':{'value':'AUCUN,C','status':'VERIFIED'}}},admin,400)
    values={'nom':'Staged','prenom':'Candidate','cin':'22222222','telephone':'22222222','email':'candidate@staged.test','typePermis':'C','numeroPermis':'STAGED-C','dateExpirationPermis':'2030-12-31','anneesExperience':'1','formationSecurite':'true','experienceCollecteConfirmee':'false'}
    candidate=request('PATCH',cp+'/review',{'fields':{key:{'value':value,'status':'VERIFIED','reviewNote':'Revue de données fictives pour test HTTP'} for key,value in values.items()}},admin)
    assert candidate['scoreCompatibilite']==70 and candidate['requiredCriteriaStatus']=='MET'
    prior_version=candidate['requirementsVersion']
    profile['experienceTargetYears']=4
    updated_profile=request('PUT','/api/ai/cv/requirements',profile,admin)
    assert updated_profile['version']==prior_version+1
    request('PATCH',cp+'/decision',{'statut':'ACCEPTE'},admin,400)
    candidate=request('POST',cp+'/evaluate',token=admin)
    assert candidate['scoreCompatibilite']==60 and candidate['requirementsVersion']==updated_profile['version']
    accepted=request('PATCH',cp+'/decision',{'statut':'ACCEPTE','notesSuperviseur':'Décision humaine fictive'},admin)
    accepted_again=request('PATCH',cp+'/decision',{'statut':'ACCEPTE','notesSuperviseur':'Répétition simulée'},admin)
    assert accepted['agentId'] and accepted['agentId']==accepted_again['agentId']
    agents=request('GET','/api/agents',token=admin)
    assert len([agent for agent in agents if agent['email']=='candidate@staged.test'])==1
    return {'tourId':tour['id'],'documentId':doc['id'],'candidateId':candidate['id'],'backendRestartVerified':True,'assertionRequests':len(RESULTS)}

def main():
    global API
    parser=argparse.ArgumentParser()
    parser.add_argument('--serve',action='store_true')
    parser.add_argument('--api-port',type=int,default=18080)
    parser.add_argument('--mongo-port',type=int,default=27019)
    parser.add_argument('--fixture-port',type=int,default=18888)
    parser.add_argument('--output',type=pathlib.Path,default=ROOT/'output/staged-workflow.json')
    parser.add_argument('--java',help='Java 17 executable; defaults to java from PATH')
    parser.add_argument('--mongod',help='MongoDB server executable; defaults to mongod from PATH')
    args=parser.parse_args()
    ports=(args.api_port,args.mongo_port,args.fixture_port)
    if len(set(ports))!=3 or any(port<1024 or port>65535 for port in ports):
        parser.error('Use three different unprivileged ports (1024–65535).')
    API=f'http://127.0.0.1:{args.api_port}'
    for port in ports:
        with socket.socket() as sock:
            if sock.connect_ex(('127.0.0.1',port))==0: raise SystemExit(f'Port {port} in use; refusing to touch existing services')
    folder=pathlib.Path(tempfile.mkdtemp(prefix='gesticollecte-staged-'));(folder/'mongo').mkdir()
    processes=[]; fixture=http.server.ThreadingHTTPServer(('127.0.0.1',args.fixture_port),Fixture)
    threading.Thread(target=fixture.serve_forever,daemon=True).start()
    try:
        java=args.java or shutil.which('java')
        mongod=args.mongod or shutil.which('mongod')
        if not java: raise RuntimeError('Java 17 was not found in PATH; use --java with its executable path')
        if not mongod: raise RuntimeError('mongod was not found in PATH; use --mongod with its executable path')
        mongo_log=open(folder/'mongo.log','w'); processes.append(subprocess.Popen([mongod,'--dbpath',str(folder/'mongo'),'--port',str(args.mongo_port),'--bind_ip','127.0.0.1','--quiet'],stdout=mongo_log,stderr=subprocess.STDOUT))
        env=os.environ.copy();env['JWT_SECRET']=base64.b64encode(os.urandom(64)).decode()
        jar=ROOT/'BACK/target/gestion-tournees-1.0.0.jar'
        if not jar.exists(): raise RuntimeError('Run mvn -f BACK/pom.xml package first')
        backend_log=open(folder/'backend.log','w')
        command=[java,'-jar',str(jar),f'--server.port={args.api_port}',f'--spring.data.mongodb.uri=mongodb://127.0.0.1:{args.mongo_port}/gesticollecte_staged','--app.bootstrap.enabled=true','--app.bootstrap.email=supervisor@staged.test','--app.bootstrap.password=StagedDemo2026!',f'--ai.gemini.base-url=http://127.0.0.1:{args.fixture_port}/v1beta','--ai.gemini.api-key=staged-fixture',f'--routing.base-url=http://127.0.0.1:{args.fixture_port}','--routing.api-key=staged-fixture','--spring.mail.host=127.0.0.1','--spring.mail.port=9','--spring.mail.properties.mail.smtp.connectiontimeout=500','--spring.mail.properties.mail.smtp.timeout=500']
        def start_backend():
            processes.append(subprocess.Popen(command,cwd=folder,env=env,stdout=backend_log,stderr=subprocess.STDOUT))
            for _ in range(120):
                if processes[-1].poll() is not None: raise RuntimeError(f'Backend stopped; inspect {folder}/backend.log')
                try:
                    request('GET','/api/auth/me',expected=401);return
                except (urllib.error.URLError,ConnectionError): time.sleep(.5)
            raise RuntimeError(f'Backend did not become ready; inspect {folder}/backend.log')
        def restart_backend():
            processes[-1].terminate();processes[-1].wait(timeout=15)
            start_backend()
        print(f'Staged services starting; logs {folder}',flush=True)
        start_backend()
        result=verify(folder,restart_backend);report={'kind':'ISOLATED_HTTP_INTEGRATION_WITH_SIMULATED_PROVIDERS','result':'PASS',**result,'requests':RESULTS}
        output=args.output;output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps({'result':'PASS',**result,'report':str(output)}),flush=True)
        if args.serve:
            print(f'Staged backend {API} — supervisor@staged.test / StagedDemo2026! (disposable demo only)',flush=True)
            while True: time.sleep(1)
    finally:
        fixture.shutdown()
        for process in reversed(processes):
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: process.kill();process.wait()
        print(f'Staged processes stopped. Disposable files retained at {folder}',flush=True)
if __name__=='__main__':
    try: main()
    except KeyboardInterrupt: pass
