"""Small JVM regression of real NEI worker/planner bytecode with solid-material value fixtures."""
from pathlib import Path
import argparse,os,subprocess
from verify import ROOT,JAVA,CP,CLIENT
parser=argparse.ArgumentParser();parser.add_argument('--bridge',type=Path,required=True);parser.add_argument('--nei',type=Path,default=CLIENT/'NotEnoughItems-2.8.145-GTNH.jar');args=parser.parse_args()
out=ROOT/'build/headless-test-classes';out.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join([str(args.bridge.resolve()),str(args.nei.resolve()),CP.split(os.pathsep,1)[1]])
sources=list((ROOT/'tests/headless_runtime').rglob('*.java'))+[ROOT/'tests/HeadlessChainVerify.java']
subprocess.run([str(JAVA/'javac.exe'),'-J-Xmx256m','--release','8','-Xlint:-options','-encoding','UTF-8','-cp',classpath,'-d',str(out)]+list(map(str,sources)),check=True)
subprocess.run([str(JAVA/'java.exe'),'-Xmx128m','-XX:ActiveProcessorCount=2','-Xverify:all','-cp',str(out)+os.pathsep+classpath,'ae2nei.bridge.HeadlessChainVerify',str(args.nei)],check=True)
