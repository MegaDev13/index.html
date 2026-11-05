import re,sys
from pathlib import Path
actual=re.search(r'certificate SHA-256 digest: ([0-9a-fA-F]+)',Path(sys.argv[1]).read_text())
expected=Path(sys.argv[2]).read_text().strip().lower()
if not actual or actual[1].lower()!=expected:
    raise SystemExit('Signing certificate mismatch; do not install or distribute.')
print('Permanent signing certificate verified.')
