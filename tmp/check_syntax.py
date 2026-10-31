import re
import subprocess
import tempfile

def test_script(name, s):
    cleaned = s.replace('${dol}', '$')
    cleaned = re.sub(r'\$\{get[A-Za-z0-9_]+\(\)\}', '/* sub-script */', cleaned)
    cleaned = re.sub(r'\$\{[a-zA-Z0-9_\.]+\}', '""', cleaned)
    with tempfile.NamedTemporaryFile('w', suffix='.js', delete=False) as tf:
        tf.write(cleaned)
        tf_name = tf.name
    res = subprocess.run(['node', '--check', tf_name], capture_output=True, text=True)
    if res.returncode != 0:
        print(f'{name} ERROR:')
        print(res.stderr[:400])
    else:
        print(f'{name}: VALID JS!')

with open('app/src/main/java/com/example/web/LocalHttpServer.kt', 'r') as f:
    c = f.read()
scripts = re.findall(r'<script[^>]*>(.*?)</script>', c, re.DOTALL)
for i, s in enumerate(scripts):
    test_script(f'LocalHttpServer script {i}', s)

for fpath in ['KaryawanMuridWebScript.kt', 'KeuanganWebScript.kt', 'GajiWebScript.kt']:
    with open(f'app/src/main/java/com/example/web/{fpath}', 'r') as f:
        t = f.read()
    m = re.search(r'"""(.*?)"""', t, re.DOTALL)
    if m:
        test_script(fpath, m.group(1))
