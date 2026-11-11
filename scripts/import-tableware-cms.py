"""Import the preserved local CMS snapshot through the real, loopback ERP API.

No database writes, account creation, password resets, deployment, or mail calls.
Default: validate and prepare lossless PNG files only. --execute enables the
explicitly requested local API import. All HTTP calls have finite timeouts.
"""
from __future__ import annotations
import argparse,copy,datetime,hashlib,io,json,mimetypes,os,re,sys,time
from pathlib import Path
from urllib.parse import urlparse
import requests
from PIL import Image,ImageOps

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'work/phase4-erp-integration'
PUBLIC=ROOT/'website/trendzgreen-homepage/public'
BASE='http://127.0.0.1:48080'
KEY={'siteId':1,'pageKey':'catalog','locale':'en'}
CN=datetime.timezone(datetime.timedelta(hours=8))
FIELDS=('siteId','locale','slug','legacyPath','title','titleLines','category','label','summary','coverImageUrl','coverImageAlt','heroImageUrl','sections','visible','publishedAt','sortOrder','seoTitle','seoDescription')

def digest(value):
    if not isinstance(value,bytes):value=json.dumps(value,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()
    return hashlib.sha256(value).hexdigest()

def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_suffix(path.suffix+'.tmp')
    temporary.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    temporary.replace(path)

def millis(value):
    if value is None or value=='':return None
    if isinstance(value,(int,float)) or (isinstance(value,str) and value.isdigit()):return int(value)
    date=datetime.datetime.fromisoformat(value.replace('Z','+00:00'))
    if date.tzinfo is None:date=date.replace(tzinfo=CN)
    return int(date.timestamp()*1000)

def article_payload(value):
    """Only writable fields; normalize ERP timestamps and existing hero fallback."""
    result={k:copy.deepcopy(value.get(k)) for k in FIELDS}
    for k in FIELDS:
        if k not in ('siteId','titleLines','sections','visible','publishedAt','sortOrder'):
            # Match WebsiteBlogServiceImpl.trim: source SEO fallback snapshots
            # can end in a space at the 320-character cut; re-saving trims it.
            result[k]=(result[k] or '').strip(''.join(chr(i) for i in range(33)))
    result['siteId']=int(result['siteId'] or 1)
    result['locale']=result['locale'] or 'en'
    result['titleLines']=result['titleLines'] or [result['title']]
    result['visible']=True if result['visible'] is None else bool(result['visible'])
    result['sortOrder']=int(result['sortOrder'] or 0)
    result['publishedAt']=millis(result['publishedAt'])
    result['heroImageUrl']=result['heroImageUrl'] or result['coverImageUrl']
    result['sections']=[{'id':s.get('id'),'title':s.get('title') or '',
       'paragraphs':s.get('paragraphs') or [],'blocks':s.get('blocks') or []} for s in (result['sections'] or [])]
    return result

def public_article_payload(value):
    if value is None:return None
    draft={**value,'siteId':1,'locale':'en','legacyPath':value.get('path'),
       'coverImageUrl':value.get('coverImage',{}).get('url'),
       'coverImageAlt':value.get('coverImage',{}).get('alt'),
       'heroImageUrl':value.get('heroImage'),'visible':True}
    return article_payload(draft)

def rewrite(value,mapping):
    if isinstance(value,str):return mapping.get(value,value)
    if isinstance(value,list):return [rewrite(v,mapping) for v in value]
    if isinstance(value,dict):return {k:rewrite(v,mapping) for k,v in value.items()}
    return value

def local_references(value):
    if isinstance(value,str):return {value} if value.startswith('/assets/') else set()
    if isinstance(value,(dict,list)):
        values=value.values() if isinstance(value,dict) else value
        return set().union(*(local_references(v) for v in values))
    return set()

def pixel_hash(data):
    with Image.open(io.BytesIO(data)) as image:
        image=ImageOps.exif_transpose(image).convert('RGBA')
        return digest(str(image.size).encode()+image.tobytes())

def prepare_media():
    prepared=[];cache=WORK/'media-import-png';cache.mkdir(parents=True,exist_ok=True)
    for folder in ('products','articles'):
        for path in sorted((PUBLIC/'assets'/folder).iterdir()):
            if not path.is_file() or path.suffix.lower() not in ('.webp','.jpg','.jpeg','.png'):continue
            source=path.read_bytes();source_hash=digest(source)
            target=cache/(source_hash+'.png')
            if not target.exists():
                with Image.open(io.BytesIO(source)) as image:
                    image=ImageOps.exif_transpose(image)
                    if image.width>12000 or image.height>12000 or image.width*image.height>25000000:raise RuntimeError('Source image exceeds ERP dimensions')
                    image.convert('RGBA' if 'A' in image.getbands() else 'RGB').save(target,format='PNG',optimize=True)
            upload=target.read_bytes()
            if len(upload)>10*1024*1024:raise RuntimeError('Converted image exceeds ERP upload limit')
            if pixel_hash(source)!=pixel_hash(upload):raise RuntimeError('Image conversion changed pixels')
            prepared.append({'sourcePath':'/'+path.relative_to(PUBLIC).as_posix(),
                'sourceSha256':source_hash,'uploadSha256':digest(upload),'pixelSha256':pixel_hash(upload),
                'uploadName':'trendz-import-'+source_hash+'.png','preparedFile':str(target),'bytes':len(upload)})
            if len(prepared)%10==0:print(f'Source images prepared: {len(prepared)}',flush=True)
    return prepared

class ApiError(RuntimeError):
    def __init__(self,path,status,code):
        self.code=code
        super().__init__(f'ERP request rejected: {path.split("?")[0]} HTTP {status}, code {code}')

class Api:
    def __init__(self,tenant,accounts):
        self.tenant=tenant;self.sessions={};self.write_count=0
        # ERP app-api validates member/admin token types before PermitAll.
        # Published content is anonymous and must never carry an admin token.
        self.public_session=requests.Session();self.public_session.trust_env=False
        self.public_session.headers.update({'tenant-id':str(tenant)})
        for role in ('editor','publisher'):
            session=requests.Session();session.trust_env=False
            session.headers.update({'tenant-id':str(tenant)})
            self.sessions[role]=session
            account=accounts[role]
            result=self.call('/admin-api/system/auth/login',role=role,method='POST',body={'username':account['username'],'password':account['password']})
            token=result.get('accessToken')
            if not token:raise RuntimeError('ERP login returned no access token')
            session.headers['Authorization']='Bearer '+token
        self.write_count=0

    def call(self,path,role='editor',method='GET',body=None,params=None,files=None,data=None):
        if not path.startswith(('/admin-api/seo/','/app-api/seo/','/admin-api/system/auth/')) or '://' in path:raise RuntimeError('Import API path outside declared scope')
        if path.startswith('/app-api/') and method!='GET':raise RuntimeError('Importer public API access is read-only')
        session=self.public_session if path.startswith('/app-api/') else self.sessions[role]
        response=session.request(method,BASE+path,json=body,params=params,files=files,data=data,timeout=(5,25),allow_redirects=False)
        try:value=response.json()
        except ValueError:raise ApiError(path,response.status_code,'non-json') from None
        if response.status_code!=200 or value.get('code')!=0:raise ApiError(path,response.status_code,value.get('code'))
        if method!='GET':self.write_count+=1
        return value.get('data')

    def page(self,path,params=None):
        result=[];page=1
        while True:
            data=self.call(path,params={**(params or {}),'pageNo':page,'pageSize':100})
            items=data.get('list') or [];result.extend(items)
            if len(result)>=int(data.get('total') or 0):return result
            if not items or page>=100:raise RuntimeError('Incomplete ERP pagination')
            page+=1

    def public_article(self,slug):
        return self.call('/app-api/seo/blog/public/'+slug,params={'siteId':1,'locale':'en'})

    def approved(self,kind,target,version):
        review=self.call('/admin-api/seo/operations/review/submit',method='POST',body={'siteId':1,'kind':kind,'targetId':str(target),'version':version})
        if review['status']=='pending':
            self.call('/admin-api/seo/operations/review/decide',role='publisher',method='POST',body={'siteId':1,'id':review['id'],'version':review['version'],'status':'approved','note':'Preserved local CMS migration; published source and image hashes verified.'})
        elif review['status']!='approved':raise RuntimeError('Migration review is not approved')

    def verify_media(self,row,prepared):
        url=row['url'];parsed=urlparse(url)
        if parsed.scheme!='http' or parsed.netloc!='127.0.0.1:48080' or not re.fullmatch(r'/admin-api/infra/file/\d+/get/website-media/'+str(self.tenant)+r'/[A-Za-z0-9/.-]+',parsed.path) or parsed.query or parsed.fragment or '..' in parsed.path:
            raise RuntimeError('ERP media URL is not local and tenant-scoped')
        # Do not forward ERP tokens to file URLs. Re-encoding may change bytes,
        # so compare decoded pixels against the prepared lossless source.
        with requests.Session() as public:
            public.trust_env=False
            response=public.get(url,timeout=(5,20),allow_redirects=False)
        if response.status_code!=200 or len(response.content)>10*1024*1024:raise RuntimeError('ERP media file unavailable')
        if pixel_hash(response.content)!=prepared['pixelSha256']:raise RuntimeError('ERP media pixels differ from source')
        return digest(response.content)

class Importer:
    def __init__(self,source,source_hash,prepared,api,ledger_path):
        self.source=source;self.prepared=prepared;self.api=api;self.path=ledger_path
        if ledger_path.exists():
            self.ledger=json.loads(ledger_path.read_text(encoding='utf-8'))
            if self.ledger.get('tenantId')!=api.tenant or self.ledger.get('sourceSha256')!=source_hash:raise RuntimeError('Existing import ledger belongs to different source or tenant; refusing overwrite')
        else:self.ledger={'schemaVersion':1,'tenantId':api.tenant,'siteId':1,'sourceSha256':source_hash,'media':{},'catalog':{},'articles':{}}
        self.events=[]
    def save(self):write_json(self.path,self.ledger)
    def event(self,kind,key,status):self.events.append({'kind':kind,'key':key,'status':status})

    def media(self):
        existing=self.api.page('/admin-api/seo/media/page',{'kind':'image','archived':False})+self.api.page('/admin-api/seo/media/page',{'kind':'image','archived':True})
        by_id={r['id']:r for r in existing};by_name={}
        for row in existing:by_name.setdefault(row['name'],[]).append(row)
        hashes={}
        for n,item in enumerate(self.prepared):
            source=item['sourcePath'];entry=self.ledger['media'].get(source)
            if entry and entry['sourceSha256']!=item['sourceSha256']:raise RuntimeError('A source image changed since import')
            row=by_id.get(entry['mediaId']) if entry else hashes.get(item['sourceSha256'])
            if entry and not row:raise RuntimeError('Previously imported ERP media was removed; preserving user decision')
            if row is None:
                matches=by_name.get(item['uploadName'],[])
                if len(matches)>1:raise RuntimeError('Ambiguous pre-existing imported media name')
                row=matches[0] if matches else None
            if row is None:
                with Path(item['preparedFile']).open('rb') as stream:
                    row=self.api.call('/admin-api/seo/media/upload',method='POST',files={'file':(item['uploadName'],stream,'image/png')},data={'alt':Path(source).stem})
                by_id[row['id']]=row;by_name.setdefault(row['name'],[]).append(row)
                self.event('media',source,'uploaded')
            else:self.event('media',source,'reused')
            stored_hash=self.api.verify_media(row,item)
            self.ledger['media'][source]={**{k:item[k] for k in ('sourceSha256','uploadSha256','pixelSha256')},'mediaId':row['id'],'url':row['url'],'storedSha256':stored_hash}
            hashes[item['sourceSha256']]=row;self.save()
            if (n+1)%10==0:print(f'Media verified: {n+1}/{len(self.prepared)}',flush=True)
        return {key:value['url'] for key,value in self.ledger['media'].items()}

    def catalog(self,mapping):
        published=rewrite(self.source['catalog']['published'],mapping);draft=rewrite(self.source['catalog']['draft'],mapping)
        try:current=self.api.call('/admin-api/seo/page/draft',params=KEY)
        except ApiError as error:
            if error.code!=1070008001:raise
            current=self.api.call('/admin-api/seo/page/initialize',method='POST',body=KEY)
        try:public=self.api.call('/app-api/seo/page/public',params=KEY)
        except ApiError as error:
            if error.code!=1070008004:raise
            public=None
        entry=self.ledger['catalog']
        if entry.get('complete'):
            unchanged=digest(current['content'])==entry['draftHash'] and digest(public['content'] if public else None)==entry['publishedHash']
            self.event('catalog','catalog','unchanged' if unchanged else 'preserved_erp_edits');return
        if public is not None and public['content']!=published:raise RuntimeError('Existing ERP published catalog differs; refusing overwrite')
        if current['content'] not in (published,draft) and current['content'].get('products'):
            raise RuntimeError('Existing ERP catalog draft differs; refusing overwrite')
        if public is None:
            if current['content']!=published:
                if current['content'].get('products'):raise RuntimeError('Refusing to replace an existing unpublished catalog')
                current=self.api.call('/admin-api/seo/page/draft',method='PUT',body={**KEY,'expectedVersion':current['version'],'content':published})
                entry.update({'stage':'saved','version':current['version']});self.save()
            self.api.approved('catalog','catalog',current['version'])
            current=self.api.call('/admin-api/seo/page/publish',role='publisher',method='POST',body={**KEY,'expectedVersion':current['version']})
            entry.update({'stage':'published'});self.save()
        if current['content']!=draft:
            if current['content']!=published:raise RuntimeError('ERP catalog changed during import')
            current=self.api.call('/admin-api/seo/page/draft',method='PUT',body={**KEY,'expectedVersion':current['version'],'content':draft})
        public=self.api.call('/app-api/seo/page/public',params=KEY)
        if current['content']!=draft or public['content']!=published:raise RuntimeError('Catalog roundtrip mismatch')
        entry.update({'complete':True,'draftHash':digest(current['content']),'publishedHash':digest(public['content']),'version':current['version']});self.save();self.event('catalog','catalog','imported')

    def articles(self,mapping):
        existing=self.api.page('/admin-api/seo/blog/page',{'siteId':1,'locale':'en'})
        by_slug={row['slug']:row for row in existing}
        for index,source in enumerate(self.source['articles']):
            if not source['publiclyPublished'] or not source['publishedWritePayload']['visible']:
                raise RuntimeError('This preserved source contains non-public articles; separate import handling required')
            published=article_payload(rewrite(source['publishedWritePayload'],mapping))
            draft=article_payload(rewrite(source['draft'],mapping));slug=published['slug']
            entry=self.ledger['articles'].setdefault(slug,{'sourceId':source['sourceId']})
            row=by_slug.get(slug)
            if entry.get('id') and (not row or row['id']!=entry['id']):raise RuntimeError('Previously imported ERP article was removed or replaced')
            if row:row=self.api.call('/admin-api/seo/blog/get',params={'id':row['id']})
            public=public_article_payload(self.api.public_article(slug)) if row else None
            if entry.get('complete'):
                unchanged=digest(article_payload(row))==entry['draftHash'] and digest(public)==entry['publishedHash']
                self.event('article',slug,'unchanged' if unchanged else 'preserved_erp_edits');continue
            if public is not None and public!=published:raise RuntimeError('Existing ERP article publication differs; refusing overwrite')
            if row and article_payload(row) not in (published,draft):raise RuntimeError('Existing ERP article draft differs; refusing overwrite')
            if row is None:
                article_id=self.api.call('/admin-api/seo/blog/create',method='POST',body=published)
                entry.update({'id':article_id,'stage':'created'});self.save()
                row=self.api.call('/admin-api/seo/blog/get',params={'id':article_id});by_slug[slug]=row
            if public is None:
                if article_payload(row)!=published:raise RuntimeError('Refusing to replace an existing unpublished article')
                self.api.approved('article',row['id'],row['version'])
                self.api.call('/admin-api/seo/blog/publish',role='publisher',method='POST',body={'id':row['id'],'version':row['version']})
                row=self.api.call('/admin-api/seo/blog/get',params={'id':row['id']})
                entry.update({'stage':'published'});self.save()
            if article_payload(row)!=draft:
                if article_payload(row)!=published:raise RuntimeError('ERP article changed during import')
                self.api.call('/admin-api/seo/blog/update',method='PUT',body={**draft,'id':row['id'],'version':row['version']})
                row=self.api.call('/admin-api/seo/blog/get',params={'id':row['id']})
            public=public_article_payload(self.api.public_article(slug))
            if article_payload(row)!=draft or public!=published:raise RuntimeError('Article roundtrip mismatch for source '+str(source['sourceId']))
            entry.update({'id':row['id'],'complete':True,'draftHash':digest(article_payload(row)),'publishedHash':digest(public),'version':row['version']});self.save()
            self.event('article',slug,'imported');print(f'Article verified: {index+1}/{len(self.source["articles"])}',flush=True)

    def run(self):
        site=self.api.call('/admin-api/seo/site-config/get',params={'siteId':1})
        if not site or site.get('navigationTemplate')!='TABLEWARE_B2B' or site.get('siteUrl','').rstrip('/')!='http://127.0.0.1:4174':raise RuntimeError('Expected the dedicated TABLEWARE_B2B local site')
        mapping=self.media();references=local_references(self.source)
        if references-set(mapping):raise RuntimeError('A referenced source asset has no ERP media mapping')
        self.catalog(mapping);self.articles(mapping)
        report={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'tenantId':self.api.tenant,'siteId':1,'sourceSha256':self.ledger['sourceSha256'],
          'sourceFiles':len(self.prepared),'referencedSourcePaths':len(references),'uniqueErpMedia':len({x['mediaId'] for x in self.ledger['media'].values()}),
          'catalogComplete':self.ledger['catalog'].get('complete',False),'articlesComplete':sum(x.get('complete',False) for x in self.ledger['articles'].values()),
          'sourceCounts':{'products':len(self.source['catalog']['published']['products']),'specifications':sum(len(p.get('specs',[])) for p in self.source['catalog']['published']['products']),'articles':len(self.source['articles']),'blocks':sum(len(s.get('blocks',[])) for a in self.source['articles'] for s in a['publishedWritePayload']['sections'])},
          'writeRequestsThisRun':self.api.write_count,'events':self.events,'sourceToErpImages':self.ledger['media'],
          'notes':['Only local ERP HTTP API used; no direct SQL writes or external notification calls.','Original WebP files preserved; lossless PNG conversion and ERP stored pixels verified.','Source dates converted to epoch milliseconds using Asia/Shanghai, matching the ERP LocalDateTime contract.','13 blank draft hero images are semantically equal to the existing cover fallback; no artificial unpublished edits created.','Scalar field edge whitespace is normalized by the existing ERP save contract; raw source snapshots remain in source-cms-export.json. Full article blocks are preserved.','Completed imported records are never overwritten on rerun; subsequent ERP edits are reported and preserved.']}
        write_json(WORK/'cms-import-report.json',report)
        return {k:report[k] for k in ('sourceFiles','referencedSourcePaths','uniqueErpMedia','catalogComplete','articlesComplete','sourceCounts','writeRequestsThisRun')}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--execute',action='store_true');args=parser.parse_args()
    source_path=WORK/'source-cms-export.json';source_bytes=source_path.read_bytes();source=json.loads(source_bytes)
    prepared=prepare_media()
    if local_references(source)-{p['sourcePath'] for p in prepared}:raise RuntimeError('A source image is missing from the migration input')
    manifest={'sourceSha256':digest(source_bytes),'sourceFiles':len(prepared),'referencedSourcePaths':len(local_references(source)),'media':prepared}
    write_json(WORK/'media-import-manifest.json',manifest)
    if not args.execute:
        print(json.dumps({'mode':'prepared-only','sourceFiles':len(prepared),'referencedSourcePaths':manifest['referencedSourcePaths'],'networkCalls':0}));return
    accounts=json.loads((WORK/'accounts-private.json').read_text(encoding='utf-8'))
    tenant=accounts['tenantId']
    if not isinstance(tenant,int) or tenant<=0 or tenant in (1,121,162,9001):raise RuntimeError('A dedicated ERP tenant is required')
    api=Api(tenant,accounts['accounts'])
    importer=Importer(source,digest(source_bytes),prepared,api,WORK/'cms-import-ledger.json')
    print(json.dumps(importer.run()))

if __name__=='__main__':
    try:main()
    except Exception as error:
        # Never print credentials, response bodies, tokens, or full request data.
        print('Import stopped: '+(str(error) if isinstance(error,(RuntimeError,ApiError)) else type(error).__name__),file=sys.stderr)
        sys.exit(1)
