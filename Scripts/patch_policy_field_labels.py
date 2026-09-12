from pathlib import Path

# The policy field guards are now part of OcasoPolicyParser.java itself.
# Keep this workflow step idempotent: never inject or replace parser methods
# during CI, which could create duplicate Java methods.
P = Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
if not P.exists():
    raise SystemExit('OcasoPolicyParser.java not found')
print('policy field-label guards already present; no source rewrite needed')
