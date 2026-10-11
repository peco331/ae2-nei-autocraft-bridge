"""Verify exact hooks in the installed RC1 bytecode and the bounded wire protocol."""
from pathlib import Path
import subprocess
import sys
import os
import json
import argparse

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from project_paths import require_path, jdk_home
JAVA = jdk_home() / 'bin'
CLIENT = require_path('CLIENT_MODS')
LIB = require_path('LIBRARIES')
SERVER = require_path('SERVER_ROOT')
VERSION = json.loads((ROOT/'mcmod.info').read_text(encoding='utf-8'))[0]['version']
BRIDGE = ROOT/f'dist/release-{VERSION}/ae2-nei-autocraft-bridge-{VERSION}.jar'
CP = os.pathsep.join(map(str, [BRIDGE,
    SERVER/'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar',
    SERVER/'libraries/net/minecraft/launchwrapper/1.15/launchwrapper-1.15.jar',
    LIB/'org/ow2/asm/asm/9.7.1/asm-9.7.1.jar', LIB/'org/ow2/asm/asm-tree/9.7.1/asm-tree-9.7.1.jar',
    LIB/'io/netty/netty-all/4.0.10.Final/netty-all-4.0.10.Final.jar']))

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--bridge', type=Path, default=BRIDGE)
    parser.add_argument('--nei', type=Path, default=CLIENT/'NotEnoughItems-2.8.145-GTNH.jar')
    parser.add_argument('--ae2', type=Path, default=CLIENT/'appliedenergistics2-rv3-beta-1078-GTNH-rc1-terminal-fixes.2.jar')
    args=parser.parse_args()
    CP=str(args.bridge)+os.pathsep+CP.split(os.pathsep,1)[1]
    out=ROOT/'build/test-classes';out.mkdir(exist_ok=True,parents=True)
    subprocess.run([str(JAVA/'javac.exe'),'-J-Xmx256m','--release','8','-encoding','UTF-8','-cp',CP,'-d',str(out),str(ROOT/'tests/Verify.java')],check=True)
    subprocess.run([str(JAVA/'java.exe'),'-Xmx128m','-XX:ActiveProcessorCount=2','-Xverify:all','-cp',str(out)+os.pathsep+CP,'ae2nei.bridge.Verify',
        str(args.nei),str(args.ae2)],check=True)
