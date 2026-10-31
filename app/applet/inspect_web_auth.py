import os

# Let's inspect WebConnectScreen, WebConnectViewModel, WebAuthPromptActivity, LocalHttpServer
files = [
    'app/src/main/java/com/example/web/WebConnectScreen.kt',
    'app/src/main/java/com/example/web/WebConnectViewModel.kt',
    'app/src/main/java/com/example/web/WebAuthPromptActivity.kt'
]

for p in files:
    if os.path.exists(p):
        with open(p, 'r', encoding='utf-8') as f:
            print(f'=== {p} ({len(f.readlines())} lines) ===')
            f.seek(0)
            print(f.read()[:1500])
