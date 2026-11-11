"""Start/stop isolated CMS fixture after the documented Maven integration tests.
No ERP environment files or external services are loaded. Credentials stay in work/.
"""
import argparse,json,os,pathlib,subprocess,time,urllib.request,datetime,locale

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['start','stop','status'])
args=parser.parse_args()
repo=pathlib.Path(__file__).resolve().parents[1]
root=repo.parent
module=repo/'yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server'
work=root/'work/phase2-cms-migration'
work.mkdir(parents=True,exist_ok=True)
state=work/'local-cms-process.json'
def healthy():
    try:
        with urllib.request.urlopen('http://127.0.0.1:48081/health',timeout=2) as response:
            return json.load(response).get('mode')=='isolated-local-cms'
    except Exception:return False
if args.action=='status':
    print(json.dumps({'healthy':healthy(),'processRecord':str(state)}))
elif args.action=='stop':
    if state.exists():
        record=json.loads(state.read_text())
        # Require our explicit main class in the OS command line; a recycled Java PID is insufficient.
        query=f"(Get-CimInstance Win32_Process -Filter 'ProcessId = {int(record['pid'])}').CommandLine"
        found=subprocess.run(['powershell.exe','-NoProfile','-Command',query],timeout=10,check=True,capture_output=True,text=True).stdout
        if found.strip():
            if 'cn.iocoder.yudao.module.seo.local.LocalCmsMain' not in found:raise RuntimeError('PID does not identify LocalCmsMain; refusing stop')
            subprocess.run(['taskkill','/PID',str(record['pid']),'/T','/F'],timeout=15,check=True,capture_output=True)
            remaining=subprocess.run(['powershell.exe','-NoProfile','-Command',query],timeout=10,check=True,capture_output=True,text=True).stdout
            if remaining.strip():raise RuntimeError('Recorded CMS process did not terminate')
        state.unlink()
    print('Local CMS stopped')
else:
    if healthy():print('Local CMS already ready');raise SystemExit(0)
    cp=module/'target/local-cms-classpath.txt'
    if not cp.exists():raise RuntimeError('Run TablewareCmsServiceTest first to compile and record the real reactor classpath')
    java=pathlib.Path(os.environ.get('JAVA_HOME',r'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'))/'bin/java.exe'
    timestamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    log=root/f'work/codex-logs/{timestamp}-local-cms-runtime.log';log.parent.mkdir(exist_ok=True)
    argfile=work/'local-cms-java.args'
    argfile.write_text('-Dfile.encoding=UTF-8\n-Dspring.cloud.nacos.config.enabled=false\n-Dspring.cloud.nacos.discovery.enabled=false\n-Dspring.cloud.nacos.config.import-check.enabled=false\n-Dspring.sql.init.mode=always\n-cp\n"'+cp.read_text(encoding='utf-8').replace('\\','/')+'"\n',encoding=locale.getpreferredencoding(False))
    with log.open('wb') as output:
        proc=subprocess.Popen([str(java),'@'+str(argfile),'cn.iocoder.yudao.module.seo.local.LocalCmsMain',str(root)],cwd=module,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    state.write_text(json.dumps({'pid':proc.pid,'log':str(log),'startedAt':timestamp,'main':'cn.iocoder.yudao.module.seo.local.LocalCmsMain'},indent=2))
    until=time.monotonic()+50
    while time.monotonic()<until:
        if healthy():print(json.dumps({'ready':True,'pid':proc.pid,'log':str(log),'url':'http://127.0.0.1:48081'}));break
        if proc.poll() is not None:raise RuntimeError(f'Local CMS exited {proc.returncode}; see {log}')
        time.sleep(1)
    else:
        subprocess.run(['taskkill','/PID',str(proc.pid),'/T','/F'],timeout=15,check=False,capture_output=True)
        raise TimeoutError(f'Local CMS startup exceeded 50 seconds; see {log}')
