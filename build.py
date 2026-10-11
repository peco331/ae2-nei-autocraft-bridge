import os
import argparse
import json
import sys
import shutil
import subprocess
from pathlib import Path
from project_paths import setting, jdk_home

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
parser = argparse.ArgumentParser(description="Build the standalone bridge JAR")
parser.add_argument('--server-root', default=setting('SERVER_ROOT'))
parser.add_argument('--client-mods', default=setting('CLIENT_MODS'))
parser.add_argument('--libraries', default=setting('LIBRARIES'))
parser.add_argument('--jdk', default=setting('JDK'))
parser.add_argument('--output-dir')
args = parser.parse_args()
for option in ('server_root', 'client_mods', 'libraries'):
    if not getattr(args, option):
        parser.error('Missing --' + option.replace('_', '-') + ' or matching local/environment configuration')
version = json.loads((Path(PROJECT_DIR) / 'mcmod.info').read_text(encoding='utf-8'))[0]['version']
SERVER_ROOT = args.server_root
CLIENT_MODS = args.client_mods
LIBRARY_ROOT = args.libraries
JDK_DIR = str(Path(args.jdk) if args.jdk else jdk_home())

JAVAC = os.path.join(JDK_DIR, "bin", "javac.exe")
JAR = os.path.join(JDK_DIR, "bin", "jar.exe")

forge_jar = os.path.join(SERVER_ROOT, "forge-1.7.10-10.13.4.1614-1.7.10-universal.jar")
launch_jar = os.path.join(SERVER_ROOT, "libraries", "net", "minecraft", "launchwrapper", "1.15", "launchwrapper-1.15.jar")
asm_jar = os.path.join(LIBRARY_ROOT, "org", "ow2", "asm", "asm", "9.7.1", "asm-9.7.1.jar")
asm_tree_jar = os.path.join(LIBRARY_ROOT, "org", "ow2", "asm", "asm-tree", "9.7.1", "asm-tree-9.7.1.jar")
nei_jar = os.path.join(CLIENT_MODS, "NotEnoughItems-2.8.145-GTNH.jar")
netty_jar = os.path.join(LIBRARY_ROOT, "io", "netty", "netty-all", "4.0.10.Final", "netty-all-4.0.10.Final.jar")

classpath = os.pathsep.join([forge_jar, launch_jar, asm_jar, asm_tree_jar, nei_jar, netty_jar])
for dependency in [JAVAC, JAR, forge_jar, launch_jar, asm_jar, asm_tree_jar, nei_jar, netty_jar]:
    if not Path(dependency).is_file():
        raise SystemExit('Missing build dependency (configure the path options): ' + dependency)

build_classes = os.path.join(PROJECT_DIR, "build", "classes")
dist_dir = str(Path(args.output_dir).resolve()) if args.output_dir else os.path.join(PROJECT_DIR, "dist", "release-" + version)

if os.path.exists(build_classes):
    root = Path(build_classes)
    if root.resolve() != (Path(PROJECT_DIR).resolve() / "build" / "classes"):
        raise RuntimeError("Build directory resolves outside the expected workspace")
    for path in [root] + list(root.rglob("*")):
        if path.is_symlink() or getattr(path.stat(follow_symlinks=False), "st_file_attributes", 0) & 0x400:
            raise RuntimeError("Linked build content: " + str(path))
    shutil.rmtree(build_classes)
os.makedirs(build_classes, exist_ok=True)
os.makedirs(dist_dir, exist_ok=True)

sources = []
src_dir = os.path.join(PROJECT_DIR, "src")
for root, _, files in os.walk(src_dir):
    for f in files:
        if f.endswith(".java"):
            sources.append(os.path.join(root, f))

print(f"Compiling {len(sources)} source files...")
cmd_compile = [
    JAVAC,
    "-J-Xmx256m",
    "-Xlint:-options",
    "--release", "8",
    "-encoding", "UTF-8",
    "-cp", classpath,
    "-d", build_classes
] + sources

res = subprocess.run(cmd_compile)
if res.returncode != 0:
    print("Compilation failed!")
    sys.exit(1)

# Copy mcmod.info if exists
mcmod_file = os.path.join(PROJECT_DIR, "mcmod.info")
if os.path.exists(mcmod_file):
    shutil.copy(mcmod_file, build_classes)

for notice in ['LICENSE', 'THIRD_PARTY.md']:
    shutil.copy(Path(PROJECT_DIR) / notice, build_classes)
output_jar = os.path.join(dist_dir, "ae2-nei-autocraft-bridge-" + version + ".jar")
manifest_file = os.path.join(PROJECT_DIR, "manifest.mf")

print("Packaging jar...")
cmd_jar = [
    JAR,
    "cfm",
    output_jar,
    manifest_file,
    "-C", build_classes,
    "."
]

res = subprocess.run(cmd_jar)
if res.returncode != 0:
    print("Packaging failed!")
    sys.exit(1)

print("Build successful:", output_jar)
