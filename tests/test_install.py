"""Exercise paired install, recovery, and write failure only in disposable test directories."""
from pathlib import Path
import importlib.util
import sys
from unittest import mock
import json
import hashlib

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
spec=importlib.util.spec_from_file_location('bridge_installer',ROOT/'install.py')
installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)
fixture=ROOT/'build'/('installer-fixture'+('-'+sys.argv[1] if len(sys.argv)>1 else ''))
if fixture.exists():raise SystemExit('Fixture already exists; preserve earlier evidence or choose a new directory.')
fixture.mkdir(parents=True)
installer.SERVER=fixture/'server';installer.CLIENT=fixture/'client';installer.BACKUPS=fixture/'backups';installer.CANDIDATE=fixture/'ae2-nei-autocraft-bridge-1.1.1.jar'
installer.CANDIDATE.write_bytes(b'candidate')
for base in [installer.SERVER,installer.CLIENT]:
    (base/'mods').mkdir(parents=True)
    (base/'mods/ae2-nei-autocraft-bridge-1.0.0.jar').write_bytes(b'original')
    (base/'mods/unrelated.jar').write_bytes(b'untouched')
(installer.SERVER/'server.properties').write_text('level-name=World\n')
(installer.SERVER/'World/region').mkdir(parents=True)
(installer.SERVER/'World/level.dat').write_bytes(b'current saved world')
(installer.SERVER/'World/region/r.0.0.mca').write_bytes(b'chunk contents')
(installer.CLIENT/'saves/Test').mkdir(parents=True)
(installer.CLIENT/'saves/Test/level.dat').write_bytes(b'current singleplayer')
installer.BACKUPS.mkdir()
with mock.patch.object(installer,'offline'):
    with mock.patch.object(sys,'argv',['install.py']):installer.main()
    assert not list(installer.BACKUPS.iterdir()),'preview must be read-only'
    with mock.patch.object(sys,'argv',['install.py','--execute']):installer.main()
    manifest=list(installer.BACKUPS.glob('*/deployment.json'))[0]
    for base in [installer.SERVER,installer.CLIENT]:
        assert (base/'mods/ae2-nei-autocraft-bridge-1.1.1.jar').read_bytes()==b'candidate'
        assert not (base/'mods/ae2-nei-autocraft-bridge-1.0.0.jar').exists()
        assert (base/'mods/unrelated.jar').read_bytes()==b'untouched'
    # Use a distinct timestamp namespace without sleeps.
    installer.BACKUPS=fixture/'rollback-backups';installer.BACKUPS.mkdir()
    with mock.patch.object(sys,'argv',['install.py','--execute','--rollback',str(manifest)]):installer.main()
    for base in [installer.SERVER,installer.CLIENT]:
        assert (base/'mods/ae2-nei-autocraft-bridge-1.0.0.jar').read_bytes()==b'original'
        assert not (base/'mods/ae2-nei-autocraft-bridge-1.1.1.jar').exists()
    assert (installer.SERVER/'World/level.dat').read_bytes()==b'current saved world'
    assert (installer.CLIENT/'saves/Test/level.dat').read_bytes()==b'current singleplayer'
    installer.BACKUPS=fixture/'failure-backups';installer.BACKUPS.mkdir()
    copy=installer.shutil.copy2
    def fail_second(source,destination,*a,**kw):
        if Path(source)==installer.CANDIDATE and Path(destination).parent==installer.SERVER/'mods':raise OSError('injected second-endpoint failure')
        return copy(source,destination,*a,**kw)
    with mock.patch.object(installer.shutil,'copy2',side_effect=fail_second):
        try:
            with mock.patch.object(sys,'argv',['install.py','--execute']):installer.main()
        except OSError:pass
        else:raise AssertionError('write failure must surface')
    for base in [installer.SERVER,installer.CLIENT]:
        assert (base/'mods/ae2-nei-autocraft-bridge-1.0.0.jar').read_bytes()==b'original','both original endpoints restored'
        assert not (base/'mods/ae2-nei-autocraft-bridge-1.1.1.jar').exists()
    reused=fixture/'already-backed-up.jar';reused.write_bytes(b'original')
    installer.BACKUPS=fixture/'reuse-failure-backups';installer.BACKUPS.mkdir()
    with mock.patch.object(installer.shutil,'copy2',side_effect=fail_second):
        try:
            with mock.patch.object(sys,'argv',['install.py','--execute','--existing-jar-backup',str(reused)]):installer.main()
        except OSError:pass
        else:raise AssertionError('reused-backup write failure must surface')
    for base in [installer.SERVER,installer.CLIENT]:
        assert (base/'mods/ae2-nei-autocraft-bridge-1.0.0.jar').read_bytes()==b'original'
        assert not (base/'mods/ae2-nei-autocraft-bridge-1.1.1.jar').exists()
    installer.BACKUPS=fixture/'reuse-backups';installer.BACKUPS.mkdir()
    with mock.patch.object(sys,'argv',['install.py','--execute','--existing-jar-backup',str(reused)]):installer.main()
    reused_manifest=list(installer.BACKUPS.glob('*/deployment.json'))[0]
    record=json.loads(reused_manifest.read_text(encoding='utf-8'))
    assert record['old_jar_backup_reused'] and all(r['backup']==str(reused.resolve()) for r in record['originals'])
    assert not list(reused_manifest.parent.glob('*-jars')),'must not create additional old-JAR copies'
    for base in [installer.SERVER,installer.CLIENT]:
        assert (base/'mods/ae2-nei-autocraft-bridge-1.1.1.jar').read_bytes()==b'candidate'
        assert not (base/'mods/ae2-nei-autocraft-bridge-1.0.0.jar').exists()
        assert not (base/'_audit-backups'/reused_manifest.parent.name).exists()
        assert (base/'mods/unrelated.jar').read_bytes()==b'untouched'
print('PASS: preview, paired install, verified complete world ZIPs, rollback retains current worlds, partial write restores both endpoints; unrelated mods preserved.')
print('PASS: existing identical JAR backup reused without additional copies; paired failure recovery also passed.')
