"""Managed full ERP runtime for this checkout's isolated Tableware integration.

Uses the repository Java 17 resolver and real yudao-server artifact. Does not
replace the normal Oakved branch runtime or access its databases/Redis DB 0.
"""
import datetime, json, os, secrets, socket, subprocess, sys, time
from pathlib import Path
from urllib.request import urlopen

ERP=Path(__file__).resolve().parents[1]
ROOT=ERP.parent
CLOUD=next(ERP.glob('*/yudao-cloud'))
UI=CLOUD.parent/'yudao-ui-admin-vue3'
WORK=ROOT/'work/phase4-erp-integration'
LOGS=ROOT/'work/codex-logs'
DATABASE='oakved_tableware_local'
CONFIG=WORK/'runtime-private.json'
STATE=WORK/'runtime.json'
NO_WINDOW=0x08000000 if os.name=='nt' else 0

def command(args,timeout=20,**kw):
    return subprocess.run(args,timeout=timeout,check=True,capture_output=True,**kw)

def sql(statement):
    try:
        return command(['docker','exec','-i','-e','MYSQL_PWD='+os.environ.get('OAKVED_MYSQL_ROOT_PASSWORD','123456'),
            'yudao-mysql-local','mysql','-uroot','--default-character-set=utf8mb4','--batch','--skip-column-names',
            '-e',statement],timeout=30).stdout.decode('utf-8')
    except subprocess.CalledProcessError as error:
        raise RuntimeError('Local MySQL command failed: '+error.stderr.decode('utf-8',errors='replace')) from None

def read_config():
    WORK.mkdir(parents=True,exist_ok=True);LOGS.mkdir(parents=True,exist_ok=True)
    if not CONFIG.exists():
        value={'database':DATABASE,'redisDatabase':9,'platformPassword':secrets.token_urlsafe(10),
               'inquirySecret':secrets.token_urlsafe(36),'createdAt':datetime.datetime.now().isoformat()}
        CONFIG.write_text(json.dumps(value,indent=2),encoding='utf-8')
    value=json.loads(CONFIG.read_text(encoding='utf-8'))
    assert value['database']==DATABASE and value['redisDatabase']==9
    return value

def assert_free(port):
    with socket.socket() as s:
        s.settimeout(.5)
        if s.connect_ex(('127.0.0.1',port))==0: raise RuntimeError(f'Port {port} already occupied; refusing to replace any process')

def save(state): STATE.write_text(json.dumps(state,indent=2),encoding='utf-8')

def start():
    config=read_config();assert_free(48080);assert_free(5174)
    jar=CLOUD/'yudao-server/target/yudao-server.jar'
    if not jar.is_file():raise RuntimeError('Build the full yudao-server artifact first')
    sql(f'CREATE DATABASE IF NOT EXISTS `{DATABASE}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    java_home=command(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(CLOUD/'script/jdk17/Resolve-Jdk17.ps1')]).stdout.decode().strip()
    connection=WORK/'connection-private.json'
    tenant=json.loads(connection.read_text(encoding='utf-8'))['tenantId'] if connection.exists() else None
    spring={
      'server.address':'127.0.0.1','server.port':48080,
      'spring.profiles.active':'local',
      'spring.datasource.dynamic.datasource.master.url':f'jdbc:mysql://127.0.0.1:13306/{DATABASE}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true',
      'spring.data.redis.host':'127.0.0.1','spring.data.redis.port':16379,'spring.data.redis.database':9,
      'spring.quartz.auto-startup':False,'yudao.security.mock-enable':False,'yudao.captcha.enable':False,
      'spring.cloud.nacos.discovery.enabled':False,'spring.cloud.nacos.config.enabled':False,
      'yudao.website-media.public-base-url':'http://127.0.0.1:48080',
      'yudao.website-inquiry.enabled':False,
      'yudao.website-inquiry.sites.trendzgreen.enabled':tenant is not None,
      'yudao.website-inquiry.sites.trendzgreen.shared-secret':config['inquirySecret'],
      'yudao.website-inquiry.sites.trendzgreen.notify-enabled':False,
      'yudao.website-inquiry.sites.trendzgreen.mail-enabled':False,
    }
    spring['spring.datasource.dynamic.datasource.slave.url']=spring['spring.datasource.dynamic.datasource.master.url']
    for ds in ['master','slave']:
        spring[f'spring.datasource.dynamic.datasource.{ds}.username']='root'
        spring[f'spring.datasource.dynamic.datasource.{ds}.password']=os.environ.get('OAKVED_MYSQL_ROOT_PASSWORD','123456')
    if tenant is not None:spring['yudao.website-inquiry.sites.trendzgreen.tenant-id']=tenant
    env=os.environ.copy();env['SPRING_APPLICATION_JSON']=json.dumps(spring)
    stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    log=LOGS/f'{stamp}-tableware-full-erp.log'
    with log.open('wb') as stream:
        p=subprocess.Popen([str(Path(java_home)/'bin/java.exe'),'-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Shanghai','-Dtableware.local.runtime=true',
            '-Xmx2048m','-jar',str(jar)],cwd=CLOUD,env=env,stdout=stream,stderr=subprocess.STDOUT,creationflags=NO_WINDOW)
    state={'database':DATABASE,'redisDatabase':9,'backendPid':p.pid,'backendLog':str(log),'backendUrl':'http://127.0.0.1:48080','adminUrl':'http://127.0.0.1:5174','jar':str(jar),'startedAt':stamp,'ready':False}
    save(state)
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        if p.poll() is not None:raise RuntimeError(f'ERP exited ({p.returncode}); see {log}')
        try:
            with urlopen('http://127.0.0.1:48080/actuator/health',timeout=2) as r:
                if json.load(r).get('status')=='UP':break
        except Exception:pass
        time.sleep(1)
    else:
        command(['taskkill','/PID',str(p.pid),'/T','/F']);raise TimeoutError(f'ERP readiness exceeded 120 seconds; terminated owned process; see {log}')
    # Initialize once only; normal restarts preserve later administrator changes.
    if not config.get('bootstrapComplete'):
        import bcrypt
        password_hash=bcrypt.hashpw(config['platformPassword'].encode(),bcrypt.gensalt()).decode()
        sql(f"UPDATE `{DATABASE}`.system_users SET password='{password_hash}' WHERE id=1 AND tenant_id=1; "
            f"UPDATE `{DATABASE}`.infra_file_config SET master=(id=4) WHERE deleted=0; "
            f"UPDATE `{DATABASE}`.infra_config SET value='false' WHERE config_key='system.user.register-enabled';")
        config['bootstrapComplete']=True
        CONFIG.write_text(json.dumps(config,indent=2),encoding='utf-8')
    env=os.environ.copy();env.update({'VITE_BASE_URL':'http://127.0.0.1:48080','VITE_APP_DEFAULT_LOGIN_TENANT':'Trendz Green',
        'VITE_APP_DEFAULT_LOGIN_USERNAME':'','VITE_APP_DEFAULT_LOGIN_PASSWORD':'','VITE_OPEN':'false','VITE_APP_CAPTCHA_ENABLE':'false',
        'VITE_APP_BAIDU_CODE':'','VITE_FURNITURE_WEB_URL':'http://127.0.0.1:4174','VITE_PORT':'5174'})
    log_ui=LOGS/f'{stamp}-tableware-erp-admin.log'
    with log_ui.open('wb') as stream:
        ui=subprocess.Popen(['node',str(UI/'node_modules/vite/bin/vite.js'),'--mode','env.local','--host','127.0.0.1','--port','5174','--strictPort'],
            cwd=UI,env=env,stdout=stream,stderr=subprocess.STDOUT,creationflags=NO_WINDOW)
    state.update(adminPid=ui.pid,adminLog=str(log_ui));save(state)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        if ui.poll() is not None:raise RuntimeError(f'Admin UI exited; see {log_ui}')
        try:
            with urlopen(state['adminUrl'],timeout=2) as r:
                if r.status==200:state['ready']=True;save(state);print(json.dumps(state));return
        except Exception:pass
        time.sleep(1)
    command(['taskkill','/PID',str(ui.pid),'/T','/F']);raise TimeoutError(f'Admin UI startup exceeded 30 seconds; see {log_ui}')

def stop():
    if not STATE.exists():print('No recorded Tableware ERP runtime');return
    state=json.loads(STATE.read_text(encoding='utf-8'))
    for key,marker in [('backendPid','-Dtableware.local.runtime=true'),('adminPid',str(UI/'node_modules/vite/bin/vite.js'))]:
        pid=state.get(key)
        if not pid:continue
        script=f"[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); $p=Get-CimInstance Win32_Process -Filter 'ProcessId={int(pid)}'; if($p){{$p.CommandLine}}"
        line=command(['powershell.exe','-NoProfile','-Command',script]).stdout.decode('utf-8',errors='replace')
        if not line:continue
        if marker not in line:raise RuntimeError(f'PID {pid} ownership mismatch; refusing to stop')
        command(['taskkill','/PID',str(pid),'/T','/F'])
    state['ready']=False;state['stoppedAt']=datetime.datetime.now().isoformat();save(state);print('Owned Tableware ERP processes stopped')

if __name__=='__main__':
    action=sys.argv[1] if len(sys.argv)>1 else 'status'
    if action=='start':start()
    elif action=='stop':stop()
    elif action=='status':print(STATE.read_text(encoding='utf-8') if STATE.exists() else 'No runtime')
    else:raise SystemExit('Use start, stop or status')
