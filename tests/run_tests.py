"""Compatibility entry point; the earlier length-only tests are superseded."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name('verify.py')),run_name='__main__')
