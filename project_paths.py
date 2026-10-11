"""Machine-local paths. Configuration is excluded from published source."""
from pathlib import Path
import json
import os
import shutil

ROOT = Path(__file__).resolve().parent
CONFIG = ROOT / '.local-paths.json'
LOCAL = json.loads(CONFIG.read_text(encoding='utf-8')) if CONFIG.is_file() else {}


def setting(name, default=None):
    return os.getenv('AE2NEI_' + name) or LOCAL.get(name.lower()) or default


def require_path(name):
    value = setting(name)
    if not value:
        raise SystemExit('Configure AE2NEI_' + name + ' or .local-paths.json before using this tool.')
    return Path(value)


def jdk_home():
    value = setting('JDK') or os.getenv('JAVA_HOME')
    if value:
        return Path(value)
    compiler = shutil.which('javac')
    if compiler:
        return Path(compiler).resolve().parent.parent
    raise SystemExit('Configure AE2NEI_JDK or JAVA_HOME with a JDK before using this tool.')
