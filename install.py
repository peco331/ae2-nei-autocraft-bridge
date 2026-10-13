"""Preview by default. Offline paired install/rollback with verified recovery material."""
from pathlib import Path
from datetime import datetime
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile
from project_paths import setting

ROOT=Path(__file__).resolve().parent
SERVER=Path(setting('SERVER_ROOT')) if setting('SERVER_ROOT') else None
CLIENT=Path(setting('CLIENT_ROOT')) if setting('CLIENT_ROOT') else None
BACKUPS=Path(setting('BACKUP_ROOT')) if setting('BACKUP_ROOT') else None
VERSION=json.loads((ROOT/'mcmod.info').read_text(encoding='utf-8'))[0]['version']
CANDIDATE=ROOT/f'dist/release-{VERSION}/ae2-nei-autocraft-bridge-{VERSION}.jar'

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def linked(path):
    return path.is_symlink() or bool(getattr(path.stat(follow_symlinks=False),'st_file_attributes',0)&0x400)

def offline():
    command="Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') } | Select-Object ProcessId,Name,CommandLine | ConvertTo-Json -Compress"
    data=subprocess.check_output(['powershell','-NoProfile','-Command',command],text=True,encoding='utf-8').strip()
    processes=json.loads(data) if data else []
    if isinstance(processes,dict):processes=[processes]
    for process in processes:
        cmd=(process.get('CommandLine') or '').lower()
        if any(x in cmd for x in ['prismlauncherdata','gtnh-server','lwjgl3ify-forgepatches','net.minecraft','launchwrapper']):
            raise RuntimeError(f"Minecraft may be running (PID {process['ProcessId']}); save and exit before installation.")

def snapshot(world,destination):
    print('Backing up world:',world,flush=True)
    if linked(world):raise RuntimeError(f'World is linked: {world}')
    files=[]
    for directory,dirs,names in os.walk(world,followlinks=False):
        for name in dirs+names:
            if linked(Path(directory)/name):raise RuntimeError('Linked world entry; manual backup required')
        files.extend(Path(directory)/name for name in names)
    manifest={p.relative_to(world).as_posix():sha(p) for p in sorted(files)}
    if not manifest:raise RuntimeError(f'Empty world: {world}')
    archive=destination.with_suffix('.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=1) as z:
        for path in sorted(files):z.write(path,path.relative_to(world))
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:raise RuntimeError('Unreadable backup')
        for name,digest in manifest.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise RuntimeError('Backup hash mismatch')
    for path in files:
        if sha(path)!=manifest[path.relative_to(world).as_posix()]:raise RuntimeError('World changed during backup')
    destination.with_suffix('.manifest.json').write_text(json.dumps({'source':str(world),'files':manifest,'zip_sha256':sha(archive)},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Verified world backup:',archive,'files:',len(manifest),flush=True)

def main():
    global SERVER, CLIENT, BACKUPS
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-root',type=Path,default=SERVER)
    parser.add_argument('--client-root',type=Path,default=CLIENT)
    parser.add_argument('--backup-root',type=Path,default=BACKUPS)
    parser.add_argument('--execute',action='store_true')
    parser.add_argument('--rollback',type=Path,help='Path to a previous deployment.json; retains current worlds')
    parser.add_argument('--existing-jar-backup',type=Path,help='Reuse a verified identical prior JAR backup instead of creating new old-JAR copies')
    args=parser.parse_args()
    if any(value is None for value in (args.server_root,args.client_root,args.backup_root)):
        parser.error('Configure --server-root, --client-root and --backup-root before preview or installation')
    SERVER,CLIENT,BACKUPS=args.server_root,args.client_root,args.backup_root
    if args.rollback and args.existing_jar_backup:parser.error('Existing JAR backup is only supported for upgrades')
    old={str(base):sorted((base/'mods').glob('ae2-nei-autocraft-bridge-*.jar')) for base in [CLIENT,SERVER]}
    print('Action:', 'rollback' if args.rollback else 'paired upgrade to '+VERSION)
    print('Targets:',CLIENT,SERVER,sep='\n')
    for base,jars in old.items():print('Existing bridge JARs:',base,', '.join(p.name for p in jars) or '(none)')
    if not args.execute:
        print('PREVIEW ONLY: no changes. Execute only after approval and normal save/exit. Complete worlds will be backed up; existing identical JAR recovery material can be reused. No automatic game launch.')
        return
    offline()
    if any(linked(base) or linked(base/'mods') for base in [CLIENT,SERVER]):raise RuntimeError('Linked installation root')
    if not args.rollback and not CANDIDATE.is_file():raise RuntimeError('Build the candidate first')
    reused=args.existing_jar_backup.resolve() if args.existing_jar_backup else None
    if reused:
        if not reused.is_file() or linked(reused):raise RuntimeError('Invalid existing JAR backup')
        digest=sha(reused)
        for jars in old.values():
            for jar in jars:
                if linked(jar) or sha(jar)!=digest:raise RuntimeError('Existing JAR backup does not match every installed bridge JAR')
    previous=json.loads(args.rollback.read_text(encoding='utf-8')) if args.rollback else None
    if previous:
        for record in previous['originals']:
            source=Path(record['backup'])
            if sha(source)!=record['sha256']:raise RuntimeError('Rollback source hash mismatch')
            if Path(record['original']).parent not in [CLIENT/'mods',SERVER/'mods']:raise RuntimeError('Invalid rollback target')
    backup=BACKUPS/('ae2-nei-bridge-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir()
    properties=dict(line.split('=',1) for line in (SERVER/'server.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
    name=properties.get('level-name','world')
    world=(SERVER/name).resolve()
    if world.parent!=SERVER.resolve():raise RuntimeError('World path escapes server root')
    snapshot(world,backup/'server-world')
    for world in sorted((CLIENT/'saves').glob('*')):
        if world.is_dir() and (world/'level.dat').exists():snapshot(world,backup/('client-world-'+world.name))
    for base in [CLIENT,SERVER]:
        for log in [base/'logs/latest.log',base/'logs/fml-client-latest.log',base/'logs/fml-server-latest.log']:
            if log.is_file():shutil.copy2(log,backup/(('client-' if base==CLIENT else 'server-')+log.name))
    originals=[]
    for base,jars in old.items():
        target=backup/('client-jars' if Path(base)==CLIENT else 'server-jars')
        if not reused:target.mkdir()
        for jar in jars:
            if linked(jar):raise RuntimeError('Linked mod JAR')
            saved=reused or target/jar.name
            if not reused:shutil.copy2(jar,saved)
            digest=sha(jar)
            if sha(saved)!=digest:raise RuntimeError('JAR backup failed')
            originals.append({'original':str(jar),'backup':str(saved),'sha256':digest})
    document=Path(setting('MAINTENANCE_DOCUMENT')) if setting('MAINTENANCE_DOCUMENT') else None
    if document and document.exists():shutil.copy2(document,backup/document.name)
    manifest={'time':datetime.now().isoformat(),'originals':originals,'installed':[],'worlds_retained_on_rollback':True,'old_jar_backup_reused':bool(reused)}
    (backup/'deployment.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    # Keep originals in a local rollback subdirectory rather than deleting or overwriting them.
    sources=[(Path(r['backup']),Path(r['original']),r['sha256']) for r in previous['originals']] if previous else [(CANDIDATE,base/'mods'/CANDIDATE.name,sha(CANDIDATE)) for base in [CLIENT,SERVER]]
    moved=[];removed=[];written=[]
    offline()
    try:
        for base,jars in old.items():
            if reused:
                for jar in jars:
                    if sha(jar)!=sha(reused):raise RuntimeError('Installed JAR changed before replacement')
                    jar.unlink();removed.append(jar)
                continue
            displaced=Path(base)/'_audit-backups'/backup.name;displaced.mkdir(parents=True)
            for jar in jars:
                saved=displaced/jar.name;shutil.move(str(jar),str(saved));moved.append((saved,jar))
        for source,destination,digest in sources:
            tmp=destination.with_suffix('.jar.pending')
            if tmp.exists():raise RuntimeError('Pending installation already exists; inspect it first')
            shutil.copy2(source,tmp)
            if sha(tmp)!=digest:raise RuntimeError('Installation hash mismatch')
            os.replace(tmp,destination);written.append(destination)
            manifest['installed'].append({'path':str(destination),'sha256':digest})
            (backup/'deployment.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    except Exception:
        # Undo a partial paired installation. Keep failed new files as recovery evidence.
        for path in written:
            if path.exists():
                failed=path.parent.parent/'_audit-backups'/backup.name/('failed-'+path.name)
                failed.parent.mkdir(parents=True,exist_ok=True)
                shutil.move(str(path),str(failed))
        for saved,original in moved:
            if saved.exists() and not original.exists():shutil.move(str(saved),str(original))
        for original in removed:
            if not original.exists():shutil.copy2(reused,original)
        raise
    print('OFFLINE FILE INSTALL COMPLETE; game operation remains unverified. Recovery manifest:',backup/'deployment.json')

if __name__=='__main__':main()
