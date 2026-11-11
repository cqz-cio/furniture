"""Real local MySQL/ERP/site acceptance. Restores catalog and removes PDF/task fixtures."""
import copy, datetime, json, uuid
from pathlib import Path
import requests
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'work/phase4-erp-integration'
CONFIG=json.loads((WORK/'accounts-private.json').read_text(encoding='utf-8'))
BASE='http://127.0.0.1:48080';SITE='http://127.0.0.1:4174';TENANT=CONFIG['tenantId'];TOKENS={};CHECKS=[]
def raw(path,role='admin',method='GET',body=None,headers=None,files=None):
    h={'tenant-id':str(TENANT)}
    if role:h['Authorization']='Bearer '+TOKENS[role]
    if headers:h.update(headers)
    return requests.request(method,BASE+path,json=body,headers=h,files=files,timeout=15)
def api(path,role='admin',method='GET',body=None):
    r=raw('/admin-api'+path,role,method,body);b=r.json();assert r.status_code==200 and b.get('code')==0,(path,r.status_code,b.get('msg'));return b.get('data')
def denied(r):
    assert r.status_code in (400,401,403,404,409) or r.status_code==200 and r.json().get('code') not in (None,0,500),(r.status_code,r.text[:200])
def passed(name):CHECKS.append({'name':name,'passed':True});print('PASS '+name,flush=True)
def version(value):return {'siteId':1,'id':value['id'],'version':value['version']}
def approve(kind,target,v):
    r=api('/seo/operations/review/submit','editor','POST',{'siteId':1,'kind':kind,'targetId':str(target),'version':v})
    return api('/seo/operations/review/decide','publisher','POST',{**version(r),'status':'approved','note':'Local ERP acceptance'})
key='/seo/page/draft?siteId=1&pageKey=catalog&locale=en'
original=None;catalog_touched=False;doc=None;asset=None;task=None;inquiry=None
try:
    for role,account in CONFIG['accounts'].items():
        r=requests.post(BASE+'/admin-api/system/auth/login',json={k:account[k] for k in ('username','password')},headers={'tenant-id':str(TENANT)},timeout=15)
        b=r.json();assert b.get('code')==0,(role,b.get('msg'));TOKENS[role]=b['data']['accessToken']
    passed('five persistent ERP accounts log in through real authentication')
    denied(raw('/admin-api/seo/operations/state?siteId=1',None));passed('anonymous management access rejected')
    for role in ('viewer','editor','publisher'):
        denied(raw('/admin-api/crm/clue/page?pageNo=1&pageSize=10',role))
    denied(raw('/admin-api/seo/page/draft?siteId=1&pageKey=catalog&locale=en','sales'))
    denied(raw('/admin-api/crm/customer/page?pageNo=1&pageSize=10','sales'))
    passed('content roles cannot read inquiries; sales cannot read CMS or customer records')
    denied(raw('/admin-api/seo/operations/state?siteId=1','publisher',headers={'tenant-id':'163'}))
    denied(raw('/admin-api/seo/operations/state?siteId=1','publisher',headers={'visit-tenant-id':'163'}))
    denied(raw('/admin-api/seo/operations/state?siteId=2','publisher'))
    passed('forged tenant headers and another site cannot cross tenant boundaries')
    for role in ('admin','editor','publisher','viewer'):
        p=api('/system/auth/get-permission-info',role);assert '/seo/catalog' in p['furnitureNavigationMenuPaths'];assert '/seo/operations' in p['furnitureNavigationMenuPaths']
        if role=='admin':assert '/system/user' in p['furnitureNavigationMenuPaths'] and '/system/role' in p['furnitureNavigationMenuPaths']
        else:assert '/system/user' not in p['furnitureNavigationMenuPaths']
    passed('ERP navigation exposes only granted website and account-management pages')
    original=api(key,'editor');products=original['content']['products'];assert len(products)==22 and sum(len(p['specs']) for p in products)==124
    articles=api('/seo/blog/page?siteId=1&locale=en&pageNo=1&pageSize=100','editor')['list'];assert len(articles)==14
    blocks=sum(len(s.get('blocks') or []) for a in articles for s in api('/seo/blog/get?id='+str(a['id']),'editor')['sections']);assert blocks==994
    passed('22 products, 124 specifications, 14 articles and 994 blocks preserved in ERP')
    for role in ('viewer','editor'):
        denied(raw('/admin-api/seo/page/publish',role,'POST',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':original['version']}))
    denied(raw('/admin-api/seo/page/draft','viewer','PUT',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':original['version'],'content':original['content']}))
    denied(raw('/admin-api/seo/blog/delete?id='+str(articles[0]['id']),'editor','DELETE'))
    denied(raw('/admin-api/seo/blog/delete?id='+str(articles[0]['id']),'publisher','DELETE'))
    passed('viewer cannot edit, editor cannot publish/delete, published articles cannot be deleted')
    changed=copy.deepcopy(original['content']);changed['products'][0]['name']+=' [LOCAL TEST]'
    draft=api('/seo/page/draft','editor','PUT',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':original['version'],'content':changed});catalog_touched=True
    denied(raw('/admin-api/seo/page/publish','publisher','POST',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':draft['version']}))
    public=raw('/app-api/seo/page/public?siteId=1&pageKey=catalog&locale=en',None).json()['data'];assert public['content']==original['content']
    pending=api('/seo/operations/review/submit','editor','POST',{'siteId':1,'kind':'catalog','targetId':'catalog','version':draft['version']})
    denied(raw('/admin-api/seo/operations/review/decide','editor','POST',{**version(pending),'status':'approved','note':'Must be denied'}))
    draft=api('/seo/page/draft','editor','PUT',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':draft['version'],'content':original['content']})
    denied(raw('/admin-api/seo/operations/review/decide','publisher','POST',{**version(pending),'status':'approved','note':'Stale review must fail'}))
    approve('catalog','catalog',draft['version']);api('/seo/page/publish','publisher','POST',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':draft['version']});catalog_touched=False
    passed('draft isolation and exact-version approval enforced by direct ERP publish APIs')
    r=raw('/admin-api/seo/operations/file/upload?siteId=1','editor','POST',files={'file':('LOCAL-ACCEPTANCE-NOT-SUPPLIER.pdf',b'%PDF-1.4\n% local acceptance only\n%%EOF','application/pdf')});b=r.json();assert b['code']==0,b.get('msg');asset=b['data']
    denied(raw('/admin-api/seo/operations/file/download?siteId=1&id='+str(asset['id']),None))
    reviewed=raw('/admin-api/seo/operations/file/download?siteId=1&id='+str(asset['id']),'publisher');assert reviewed.headers.get('content-type','').startswith('application/pdf')
    doc=api('/seo/operations/document','editor','PUT',{'siteId':1,'title':'LOCAL ACCEPTANCE PDF','kind':'guide','language':'en','holder':'Local test','scope':'No supplier claims','market':'Local only','owner':'Local reviewer','issuedAt':'','expiresAt':'','assetId':asset['id']})
    denied(raw('/app-api/seo/operations/document/download?siteId=1&id='+str(doc['id']),None))
    denied(raw('/admin-api/seo/operations/document/publish','publisher','POST',version(doc)))
    denied(raw('/admin-api/seo/operations/file/delete','publisher','POST',{'siteId':1,'id':asset['id']}))
    passed('PDF original is authenticated; draft downloads and deletion of referenced files rejected')
    approve('document',doc['id'],doc['version']);doc=api('/seo/operations/document/publish','publisher','POST',version(doc))
    download=requests.get(SITE+'/downloads/erp/'+str(doc['id'])+'/',timeout=15);assert download.status_code==200 and download.headers['content-type'].startswith('application/pdf')
    doc=api('/seo/operations/document','editor','PUT',{**doc,'siteId':1,'title':'PRIVATE MODIFIED TITLE'})
    published=raw('/app-api/seo/operations/documents?siteId=1',None).json()['data'];assert any(d['id']==doc['id'] and d['title']=='LOCAL ACCEPTANCE PDF' for d in published)
    doc=api('/seo/operations/document/offline','publisher','POST',version(doc));assert requests.get(SITE+'/downloads/erp/'+str(doc['id'])+'/',timeout=15).status_code==404
    passed('approved PDF downloads; edits remain private and offline revokes website access')
    task=api('/seo/operations/task','editor','PUT',{'siteId':1,'title':'LOCAL ACCEPTANCE TASK','owner':'Local reviewer','due':datetime.date.today().isoformat(),'status':'open'})
    check=api('/seo/operations/check','editor','POST',{'siteId':1});assert len(check['issues'])>=2
    task=api('/seo/operations/task','editor','PUT',{**task,'siteId':1,'status':'done'})
    passed('maintenance tasks and content inspection persist in ERP')
    backup=api('/seo/operations/backup?siteId=1','publisher');assert api('/seo/operations/backup/check','publisher','POST',{'siteId':1,'backup':backup})['products']==22
    tampered=copy.deepcopy(backup);tampered['payload']['catalog']['products'][0]['name']='tampered'
    denied(raw('/admin-api/seo/operations/backup/check','publisher','POST',{'siteId':1,'backup':tampered}));passed('ERP backup checks reject tampering')
    payload={'externalInquiryId':str(uuid.uuid4()),'name':'Local ERP Acceptance','email':'phase4-acceptance@example.test','companyName':'LOCAL TEST ONLY',
      'subject':'LOCAL TEST - tableware quote','message':'Test only. Model PLA-C12-90; quantity 100. No email requested.','category':'Cups & Lids','inquiryType':'quote','sourcePage':'/request-quote/','locale':'en'}
    def send():return requests.post(SITE+'/api/inquiry',json=payload,headers={'Origin':SITE},timeout=20)
    a=send();assert a.status_code==200 and a.json().get('ok'),a.text
    b=send();assert b.status_code==200 and b.json().get('ok'),b.text
    rows=api('/crm/clue/page?pageNo=1&pageSize=100&email=phase4-acceptance%40example.test','sales')['list'];matching=[r for r in rows if r.get('externalInquiryId')==payload['externalInquiryId']]
    assert len(matching)==1,(len(rows),a.json(),b.json());inquiry=matching[0]
    api('/crm/clue/process-status','sales','PUT',{'id':inquiry['id'],'processStatus':10,'remark':'Local ERP acceptance completed; no external delivery'})
    assert api('/crm/clue/get?id='+str(inquiry['id']),'sales')['processStatus']==10
    passed('website inquiry reaches correct real CRM tenant once; sales can follow up')
    redirect=requests.get(SITE+'/cms/',allow_redirects=False,timeout=10);assert redirect.status_code==302 and redirect.headers['Location'].startswith('http://127.0.0.1:5174/')
    assert requests.post(SITE+'/api/local-inquiry',json={},headers={'Origin':SITE},timeout=10).status_code==410
    passed('website management opens ERP and old standalone inbox is disabled')
finally:
    if catalog_touched and original:
        d=api(key,'editor');api('/seo/page/draft','editor','PUT',{'siteId':1,'pageKey':'catalog','locale':'en','expectedVersion':d['version'],'content':original['content']})
    if TOKENS.get('publisher'):
        state=api('/seo/operations/state?siteId=1','publisher')
        if doc:
            d=next((x for x in state['documents'] if x['id']==doc['id']),None)
            if d:
                if d.get('published'):d=api('/seo/operations/document/offline','publisher','POST',version(d))
                api('/seo/operations/document/delete','publisher','POST',version(d))
        if asset:api('/seo/operations/file/delete','publisher','POST',{'siteId':1,'id':asset['id']})
        if task:api('/seo/operations/task/delete','publisher','POST',version(task))
    report={'at':datetime.datetime.now().isoformat(),'checks':CHECKS,'tenantId':TENANT,'temporaryDocumentAndTaskRemoved':True,
        'testInquiryId':inquiry['id'] if inquiry else None,'testInquiryExternalId':payload['externalInquiryId'] if inquiry else None}
    (WORK/'http-acceptance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'{len(CHECKS)} real ERP/site checks passed')
