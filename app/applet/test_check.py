import subprocess, tempfile, re, os

with open('app/src/main/java/com/example/web/LocalHttpServer.kt', 'r', encoding='utf-8') as f:
    local_server = f.read()

with open('app/src/main/java/com/example/web/KeuanganWebScript.kt', 'r', encoding='utf-8') as f:
    keuangan_script = f.read()

with open('app/src/main/java/com/example/web/KaryawanMuridWebScript.kt', 'r', encoding='utf-8') as f:
    karyawan_script = f.read()

with open('app/src/main/java/com/example/web/GajiWebScript.kt', 'r', encoding='utf-8') as f:
    gaji_script = f.read()

def extract_kotlin_string(code, fun_name):
    idx = code.find(fun_name)
    if idx == -1: return ''
    trip = code.find('"""', idx)
    if trip == -1: return ''
    trip_end = code.rfind('"""')
    if trip_end == -1 or trip_end <= trip: return ''
    content = code[trip+3:trip_end]
    content = content.replace('${dol}', '$').replace('${"$" + "{"}', '${')
    return content

keuangan_js = extract_kotlin_string(keuangan_script, 'fun getScript')
karyawan_js = extract_kotlin_string(karyawan_script, 'fun getScript')
gaji_js = extract_kotlin_string(gaji_script, 'fun getScript')

html = extract_kotlin_string(local_server, 'fun getWebUiHtml')
html = html.replace('${KeuanganWebScript.getScript(dol)}', keuangan_js)
html = html.replace('${KaryawanMuridWebScript.getScript(dol)}', karyawan_js)
html = html.replace('${GajiWebScript.getScript(dol)}', gaji_js)

scripts = re.findall(r'<script(?:\s+[^>]*)?>(.*?)</script>', html, re.DOTALL)
print(f'Found {len(scripts)} script blocks in HTML')

for idx, s in enumerate(scripts):
    s_clean = s.strip()
    if not s_clean:
        continue
    with tempfile.NamedTemporaryFile(suffix='.js', mode='w', encoding='utf-8', delete=False) as tf:
        tf.write(s_clean)
        tf_name = tf.name
    
    res = subprocess.run(['node', '--check', tf_name], capture_output=True, text=True)
    if res.returncode != 0:
        print(f'ERROR in script block {idx+1}:')
        print(res.stderr)
        lines = s_clean.splitlines()
        m = re.search(r':(\d+)', res.stderr)
        if m:
            err_line = int(m.group(1))
            start_l = max(0, err_line - 10)
            end_l = min(len(lines), err_line + 10)
            for li in range(start_l, end_l):
                print(f'{li+1}: {lines[li]}')
    else:
        print(f'Script block {idx+1} PASSED syntax check ({len(s_clean)} chars)')

try:
    os.remove('test_check.py')
except:
    pass
