"""Launch only a fresh loopback RC1 server; never use or copy a production world."""
from pathlib import Path
import shutil
import subprocess
import time
import json
import hashlib
import sys
import argparse
import runpy
from datetime import datetime
import ctypes
from ctypes import wintypes
from verify import ROOT, JAVA, SERVER, CP

def limit_test_cpu(pid):
    """Restrict this newly launched test JVM to two available logical CPUs on Windows."""
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
    kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.GetProcessAffinityMask.argtypes=[wintypes.HANDLE,ctypes.POINTER(ctypes.c_size_t),ctypes.POINTER(ctypes.c_size_t)]
    kernel.SetProcessAffinityMask.argtypes=[wintypes.HANDLE,ctypes.c_size_t]
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    handle=kernel.OpenProcess(0x0200|0x1000,False,pid)
    if not handle:raise ctypes.WinError(ctypes.get_last_error())
    try:
        available=ctypes.c_size_t();system=ctypes.c_size_t()
        if not kernel.GetProcessAffinityMask(handle,ctypes.byref(available),ctypes.byref(system)):raise ctypes.WinError(ctypes.get_last_error())
        bits=[1<<i for i in range(available.value.bit_length()) if available.value&(1<<i)]
        mask=sum(bits[-2:])
        if not mask or not kernel.SetProcessAffinityMask(handle,mask):raise ctypes.WinError(ctypes.get_last_error())
        return hex(mask)
    finally:kernel.CloseHandle(handle)

parser=argparse.ArgumentParser()
parser.add_argument('--reuse',action='store_true')
parser.add_argument('--runtime-name',default='isolated-server')
parser.add_argument('--bridge',type=Path,help='Explicit bridge JAR for version comparison')
parser.add_argument('--harness-source',type=Path,default=ROOT/'tests/NativeCraftingAudit.java')
args=parser.parse_args()
bridge=(args.bridge or Path(CP.split(';',1)[0])).resolve()
if not bridge.is_file() or not args.harness_source.is_file():raise SystemExit('Selected bridge JAR or harness source is missing')
CP=str(bridge)+';'+CP.split(';',1)[1]
# Enforce the resource rule before copying or launching any RC1 runtime.
offline=runpy.run_path(str(ROOT/'install.py'))['offline']
offline()
if not args.runtime_name.startswith('isolated-') or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789-' for c in args.runtime_name):raise SystemExit('Invalid isolated directory name')
runtime=ROOT/'build'/args.runtime_name
if (runtime/'run.json').exists():
    prior_pid=int(json.loads((runtime/'run.json').read_text())['pid'])
    processes=subprocess.check_output(['tasklist','/FI',f'PID eq {prior_pid}','/FO','CSV','/NH'],text=True)
    if f'"{prior_pid}"' in processes:raise SystemExit('The prior isolated process may still be active; do not overwrite its files.')
if runtime.exists() and '--reuse' not in sys.argv:
    raise SystemExit('Isolation directory already exists; preserve its evidence and choose a new test directory explicitly.')
runtime.mkdir(parents=True,exist_ok=True)
(runtime/'mods').mkdir(exist_ok=True)
if '--reuse' in sys.argv:
    # Preserve every earlier result; reuse only this explicitly named isolated directory.
    stamp=time.strftime('%Y%m%d-%H%M%S')
    for path in [runtime/'console.log',runtime/'native-audit.txt',runtime/'run.json']:
        if path.exists():shutil.move(str(path),str(path.with_name(path.name+'.'+stamp)))
for name in ['forge-1.7.10-10.13.4.1614-1.7.10-universal.jar','minecraft_server.1.7.10.jar','lwjgl3ify-forgePatches.jar','java9args.txt']:
    shutil.copy2(SERVER/name,runtime/name)
for name in ['config','scripts']:
    if (SERVER/name).exists() and not (runtime/name).exists(): shutil.copytree(SERVER/name,runtime/name)
# Read-only shared libraries; no cleanup traverses this junction.
if not (runtime/'libraries').exists():subprocess.run(['cmd.exe','/c','mklink','/J',str(runtime/'libraries'),str(SERVER/'libraries')],check=True,capture_output=True)
if (runtime/'libraries').resolve() != (SERVER/'libraries').resolve():raise SystemExit('Unexpected libraries junction target')
for jar in (SERVER/'mods').glob('*.jar'):
    if any(word in jar.name.lower() for word in ['ae2-nei-autocraft','scribe-','gtnh-activity-recorder']): continue
    shutil.copy2(jar,runtime/'mods'/jar.name)
for jar in (runtime/'mods').glob('ae2-nei-autocraft-bridge-*.jar'):
    previous=runtime/'prior-bridge-jars';previous.mkdir(exist_ok=True)
    shutil.move(str(jar),str(previous/(jar.name+'.'+time.strftime('%Y%m%d-%H%M%S'))))
shutil.copy2(bridge,runtime/'mods')
(runtime/'eula.txt').write_text('eula=true\n',encoding='utf-8')
(runtime/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25587\nonline-mode=false\nlevel-name=isolated-ae2-test\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\nallow-nether=false\nspawn-protection=0\nmax-players=1\nview-distance=2\n',encoding='utf-8')
classes=ROOT/'build/native-test-classes';classes.mkdir(exist_ok=True)
subprocess.run([str(JAVA/'javac.exe'),'-J-Xmx256m','--release','8','-encoding','UTF-8','-cp',CP,'-d',str(classes),str(args.harness_source)],check=True)
subprocess.run([str(JAVA/'jar.exe'),'cf',str(runtime/'mods/ae2-nei-native-audit.jar'),'-C',str(classes),'.'],check=True)
console=runtime/'console.log'
with console.open('w',encoding='utf-8') as log:
    offline()
    command=[str(JAVA/'java.exe'),'-Xms1G','-Xmx4G','-XX:ActiveProcessorCount=2','-XX:+UseG1GC','-Dfml.readTimeout=180','-Duser.language=en','-Dae2nei.auditBaseline='+str(ROOT/'dist/ae2-nei-autocraft-bridge-1.1.0.jar'),'@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui']
    proc=subprocess.Popen(command,cwd=runtime,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
    print('Isolated PID:',proc.pid,'port: 127.0.0.1:25587',flush=True)
    record={'pid':proc.pid,'started':datetime.now().astimezone().isoformat(),'target':str(runtime),'command':command,'port':25587,'world':'isolated-ae2-test','production_world_used':False,
        'bridge':str(bridge.resolve()),'bridge_sha256':hashlib.sha256(bridge.read_bytes()).hexdigest(),'harness_source':str(args.harness_source.resolve())}
    (runtime/'run.json').write_text(json.dumps(record,indent=2))
    start=time.monotonic()
    try:
        record['affinity_mask']=limit_test_cpu(proc.pid)
        (runtime/'run.json').write_text(json.dumps(record,indent=2))
        while proc.poll() is None and time.monotonic()-start<300:
            if (runtime/'native-audit.txt').exists():break
            time.sleep(2)
        if (runtime/'native-audit.txt').exists(): print((runtime/'native-audit.txt').read_text(),flush=True)
        else: print('No test result. Inspect',console,flush=True)
    finally:
        if proc.poll() is None:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=45)
            except subprocess.TimeoutExpired:
                print('Normal stop timed out; terminating this isolated process:',proc.pid,flush=True)
                proc.terminate();proc.wait(timeout=15)
        record['exit_code']=proc.poll();record['ended']=datetime.now().astimezone().isoformat()
        (runtime/'run.json').write_text(json.dumps(record,indent=2))
        print('Isolated exit:',proc.poll(),flush=True)
result=runtime/'native-audit.txt'
if not result.exists() or 'PASS=true' not in result.read_text():raise SystemExit(1)
