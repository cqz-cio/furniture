"""Idempotently provision the named tenant through the real local ERP APIs.
No remote hosts, default password resets, or direct business-table seeding.
"""
import datetime, json, secrets, string, sys
from pathlib import Path
import requests

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'work/phase4-erp-integration'
BASE='http://127.0.0.1:48080'
ACCOUNT_FILE=WORK/'accounts-private.json'
TENANT_CODE='TRENDZGREEN'
TENANT_NAME='Trendz Green'

def api(path, method='GET', data=None, token=None, tenant=1):
    headers={'tenant-id':str(tenant)}
    if token:headers['Authorization']='Bearer '+token
    r=requests.request(method,BASE+'/admin-api'+path,json=data,headers=headers,timeout=20)
    try:body=r.json()
    except ValueError:raise RuntimeError(f'{method} {path}: HTTP {r.status_code}')
    if r.status_code!=200 or body.get('code')!=0:raise RuntimeError(f'{method} {path}: {body.get("code")} {body.get("msg")}')
    return body.get('data')

def login(account,tenant):
    return api('/system/auth/login','POST',{'username':account['username'],'password':account['password']},tenant=tenant)['accessToken']

def credentials():
    if ACCOUNT_FILE.exists():return json.loads(ACCOUNT_FILE.read_text(encoding='utf-8'))
    chars=string.ascii_letters+string.digits
    data={'tenantCode':TENANT_CODE,'tenantName':TENANT_NAME,'adminUrl':'http://127.0.0.1:5174','accounts':{}}
    for role in ['admin','editor','publisher','sales','viewer']:
        data['accounts'][role]={'username':'trendz'+role,'password':''.join(secrets.choice(chars) for _ in range(16))}
    ACCOUNT_FILE.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    return data

def provision():
    config=json.loads((WORK/'runtime-private.json').read_text(encoding='utf-8'))
    assert config['database']=='oakved_tableware_local'
    accounts=credentials()
    platform=login({'username':'admin','password':config['platformPassword']},1)
    menus=api('/system/menu/list',token=platform)
    catalog=next(m for m in menus if m.get('component')=='seo/catalog/index')
    # Independent parent chain: ERP drops orphaned action grants from navigation.
    for order,action in enumerate(('query','update','preview','publish'),1):
        permission='seo:page:'+action
        if not any(m.get('parentId')==catalog['id'] and m.get('permission')==permission for m in menus):
            api('/system/menu/create','POST',{'name':{'query':'查看目录','update':'编辑目录','preview':'预览目录','publish':'发布目录'}[action],
                'parentId':catalog['id'],'permission':permission,'type':3,'sort':order,'status':0,
                'path':'','icon':'','component':'','componentName':'','visible':True,'keepAlive':True,'alwaysShow':True},platform)
    menus=api('/system/menu/list',token=platform)
    menu_map={m['id']:m for m in menus}
    base={'seo:site-config:query','seo:page:query','seo:blog:query','seo:media:query','seo:operations:query'}
    editor=base|{'seo:page:update','seo:page:preview','seo:blog:create','seo:blog:update','seo:blog:preview','seo:media:upload','seo:media:update','seo:media:archive','seo:operations:update'}
    publisher=editor|{'seo:page:publish','seo:blog:publish','seo:blog:delete','seo:operations:approve','seo:operations:publish'}
    sales={'crm:clue:query','crm:clue:triage'}
    admin=publisher|sales|{'seo:site-config:update'}|{m['permission'] for m in menus if (m.get('permission') or '').startswith(('system:user:','system:role:','system:permission:'))}
    permissions={'viewer':base,'editor':editor,'publisher':publisher,'sales':sales,'admin':admin}
    present={m.get('permission') for m in menus}
    missing=admin-present
    if missing:raise RuntimeError('Missing ERP menu permissions: '+','.join(sorted(missing)))
    def menu_ids(grants):
        legacy={m['id'] for m in menus if m.get('component')=='seo/page-content/index'}
        def under_legacy(menu):
            while menu:
                if menu['id'] in legacy:return True
                menu=menu_map.get(menu.get('parentId'))
            return False
        ids={m['id'] for m in menus if m.get('permission') in grants and m.get('status')==0 and not under_legacy(m)}
        if 'seo:page:query' in grants:ids.update(m['id'] for m in menus if m.get('component')=='seo/catalog/index')
        for mid in list(ids):
            current=menu_map.get(mid)
            while current and current.get('parentId'):
                ids.add(current['parentId']);current=menu_map.get(current['parentId'])
        return sorted(ids)
    package_rows=api('/system/tenant-package/page?pageNo=1&pageSize=100',token=platform)['list']
    matching=[p for p in package_rows if p['name']=='Trendz Green CMS']
    if len(matching)>1:raise RuntimeError('Duplicate Tableware packages')
    package_data={'name':'Trendz Green CMS','status':0,'remark':'tableware-local-v1: isolated local ERP integration','menuIds':menu_ids(admin)}
    if matching:
        package_id=matching[0]['id'];api('/system/tenant-package/update','PUT',{**package_data,'id':package_id},platform)
    else:package_id=api('/system/tenant-package/create','POST',package_data,platform)
    tenants=api('/system/tenant/page?pageNo=1&pageSize=100',token=platform)['list']
    matches=[t for t in tenants if t.get('code')==TENANT_CODE]
    if len(matches)>1:raise RuntimeError('Duplicate Tableware tenants')
    if matches:
        tenant_id=matches[0]['id']
        if matches[0]['name']!=TENANT_NAME or matches[0]['packageId']!=package_id:raise RuntimeError('Existing tenant belongs to a different setup; refusing to overwrite')
    else:
        tenant_id=api('/system/tenant/create','POST',{'name':TENANT_NAME,'code':TENANT_CODE,'contactName':'Trendz Green 本地管理员',
          'status':0,'websites':['127.0.0.1:4174'],'businessMode':'B2B','websiteProductFields':[],
          'packageId':package_id,'expireTime':int(datetime.datetime(2036,11,11,23,59,59,tzinfo=datetime.timezone(datetime.timedelta(hours=8))).timestamp()*1000),
          'accountCount':20,**accounts['accounts']['admin']},platform)
    accounts['tenantId']=tenant_id
    ACCOUNT_FILE.write_text(json.dumps(accounts,ensure_ascii=False,indent=2),encoding='utf-8')
    admin_token=login(accounts['accounts']['admin'],tenant_id)
    roles=api('/system/role/page?pageNo=1&pageSize=100',token=admin_token,tenant=tenant_id)['list']
    users=api('/system/user/page?pageNo=1&pageSize=100',token=admin_token,tenant=tenant_id)['list']
    role_ids={}
    for n,grants in permissions.items():
        if n=='admin':continue
        code='tableware_'+n
        role=next((r for r in roles if r['code']==code),None)
        role_id=role['id'] if role else api('/system/role/create','POST',{'name':{'editor':'网站编辑','publisher':'网站发布员','sales':'询价跟进','viewer':'内容只读'}[n],
            'code':code,'sort':10+len(role_ids),'status':0,'remark':'Tableware local integration'},admin_token,tenant_id)
        role_ids[n]=role_id
        api('/system/permission/assign-role-menu','POST',{'roleId':role_id,'menuIds':menu_ids(grants)},admin_token,tenant_id)
        account=accounts['accounts'][n]
        user=next((u for u in users if u['username']==account['username']),None)
        if user:
            api('/system/permission/assign-user-role','POST',{'userId':user['id'],'roleIds':[role_id]},admin_token,tenant_id)
        else:
            uid=api('/system/user/create','POST',{**account,'nickname':{'editor':'餐具编辑','publisher':'餐具发布员','sales':'餐具询价跟进','viewer':'餐具内容只读'}[n],
                 'roleId':role_id,'postIds':[],'sex':0,'remark':'本地 ERP 联调账号'},admin_token,tenant_id)
            account['userId']=uid
        login(account,tenant_id) # Existing credentials must still work; never silently reset them.
    site=api('/seo/site-config/get?siteId=1',token=admin_token,tenant=tenant_id)
    if site and site.get('navigationTemplate')!='TABLEWARE_B2B':raise RuntimeError('Existing site template mismatch')
    if not site:
        api('/seo/site-config/save','PUT',{'siteId':1,'siteName':'Trendz Green','siteUrl':'http://127.0.0.1:4174',
            'previewBaseUrl':'http://127.0.0.1:4174','defaultTitleSuffix':'Trendz Green','defaultDescription':'B2B food packaging and tableware',
            'defaultRobots':'noindex, nofollow','defaultOgImage':'','defaultLocale':'en','navigationTemplate':'TABLEWARE_B2B'},admin_token,tenant_id)
    connection={'mode':'erp-local','baseUrl':BASE,'adminUrl':'http://127.0.0.1:5174','tenantId':tenant_id,'siteId':1,
        'inquiry':{'siteKey':'trendzgreen','sharedSecret':config['inquirySecret'],'enabled':True,'mailEnabled':False,'notifyEnabled':False}}
    (WORK/'connection-private.json').write_text(json.dumps(connection,indent=2),encoding='utf-8')
    ACCOUNT_FILE.write_text(json.dumps(accounts,ensure_ascii=False,indent=2),encoding='utf-8')
    report={'tenantCode':TENANT_CODE,'tenantId':tenant_id,'siteId':1,'packageId':package_id,'roles':role_ids,
       'accounts':{role:account['username'] for role,account in accounts['accounts'].items()},'externalDelivery':False,
       'database':config['database'],'persistentCredentials':True}
    (WORK/'provision-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':provision()
