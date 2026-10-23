"""Run the pure Kotlin tests without Android SDK, FMM libraries or signing keys."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(tempfile.gettempdir()) / 'utag-kotlin'
CACHE.mkdir(exist_ok=True)

def dependency(row):
    target = CACHE / (row['name'] + '.jar')
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != row['sha256']:
        with urllib.request.urlopen(row['url'], timeout=120) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != row['sha256']:
            raise RuntimeError('Dependency checksum mismatch: ' + row['name'])
        target.write_bytes(data)
    return str(target)

with ThreadPoolExecutor(max_workers=5) as pool:
    jars = list(pool.map(dependency, json.loads((ROOT / 'scripts/core-test-dependencies.json').read_text())))
classpath = os.pathsep.join(jars)
model = ROOT / 'app/src/main/java/com/kieronquinn/app/utag/model'
tests = ROOT / 'app/src/test/java/com/kieronquinn/app/utag/model'
sources = [model / 'ModVersionPolicy.kt', *sorted((model / 'petwalk').glob('*.kt')),
           tests / 'ModVersionPolicyTest.kt', *sorted((tests / 'petwalk').glob('*.kt'))]
with tempfile.TemporaryDirectory(prefix='utag-core-tests-') as output:
    subprocess.run(['java', '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', classpath,
                    '-d', output, *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', classpath + os.pathsep + output, 'org.junit.runner.JUnitCore',
                    'com.kieronquinn.app.utag.model.ModVersionPolicyTest',
                    'com.kieronquinn.app.utag.model.petwalk.PetWalkGeometryTest',
                    'com.kieronquinn.app.utag.model.petwalk.PetWalkGpxTest'], check=True)
