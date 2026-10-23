"""Check published mod metadata against the embedded module before packaging."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "xposed-core/build.gradle.kts").read_text()
release = json.loads((root / "SmartThings.json").read_text())
name = re.search(r'val xposedName = "([^"\n]+)"', source).group(1)
code = int(re.search(r"val xposedCode = (\d+)", source).group(1))
if (name, code) != (release["version"], release["version_code"]):
    raise SystemExit(f"Mod mismatch: embedded {name}/{code}, published "
                     f"{release['version']}/{release['version_code']}")
print(f"Mod metadata consistent: {name}/{code}")
