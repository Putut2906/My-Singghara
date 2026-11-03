// Service Worker untuk Progressive Web App (PWA) My Singghara - Akses PC 100% Offline
const CACHE_NAME = 'my-singghara-pwa-v4';

const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/manifest.json',
    '/favicon.svg',
    '/favicon.ico',
    '/favicon.png',
    '/favicon-light-32x32.png',
    '/favicon-dark-32x32.png',
    '/favicon-light.ico',
    '/favicon-dark.ico',
    '/apple-touch-icon.png',
    '/app-logo.png',
    '/app-logo-light.png',
    '/app-logo-dark.png',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/icons/favicon.ico',
    '/icons/favicon.png',
    '/assets/css/bootstrap.min.css',
    '/assets/css/bootstrap-icons.css',
    '/assets/js/bootstrap.bundle.min.js',
    '/assets/js/qrcode.min.js',
    '/assets/js/chart.min.js',
    '/assets/js/html2canvas.min.js',
    '/assets/js/html5-qrcode.min.js'
];

// Install: Cache all static shell files immediately
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('[SW] Caching core static assets for offline access');
            return cache.addAll(STATIC_ASSETS).catch((err) => {
                console.warn('[SW] Cache addAll partial warning:', err);
            });
        }).then(() => self.skipWaiting())
    );
});

// Activate: Clean up old cache versions
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) => {
            return Promise.all(
                keys.map((key) => {
                    if (key !== CACHE_NAME) {
                        console.log('[SW] Removing old cache version:', key);
                        return caches.delete(key);
                    }
                })
            );
        }).then(() => self.clients.claim())
    );
});

// Fetch: Stale-While-Revalidate / Cache-First strategy for offline capability
self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);

    // Bypass non-GET requests (e.g. POST for API)
    if (event.request.method !== 'GET') {
        return;
    }

    // API calls: Network-first, return cached response or offline json if network down
    if (url.pathname.startsWith('/api/')) {
        event.respondWith(
            fetch(event.request).catch(async () => {
                const cached = await caches.match(event.request);
                if (cached) return cached;
                return new Response(JSON.stringify({ status: 'offline', offline: true }), {
                    headers: { 'Content-Type': 'application/json' }
                });
            })
        );
        return;
    }

    // Halaman dashboard: Network-first. Dashboard disusun ulang oleh aplikasi di HP, jadi
    // cache-first akan mengunci PC pada versi lama (fitur baru tidak pernah muncul).
    const isPageRequest = event.request.mode === 'navigate' ||
        url.pathname === '/' || url.pathname === '/index.html' || url.pathname.endsWith('.html');
    if (isPageRequest) {
        event.respondWith(
            fetch(event.request).then((networkResponse) => {
                if (networkResponse && networkResponse.status === 200) {
                    const copy = networkResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
                }
                return networkResponse;
            }).catch(async () => {
                const cached = await caches.match(event.request);
                return cached || caches.match('/') || new Response('Server My Singghara di HP sedang mati.', {
                    status: 503,
                    headers: { 'Content-Type': 'text/plain; charset=utf-8' }
                });
            })
        );
        return;
    }

    // Static assets & navigation: Cache First with Network Revalidation
    event.respondWith(
        caches.match(event.request).then((cachedResponse) => {
            const fetchPromise = fetch(event.request).then((networkResponse) => {
                if (networkResponse && networkResponse.status === 200) {
                    const responseToCache = networkResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, responseToCache);
                    });
                }
                return networkResponse;
            }).catch(() => {
                // If network fails and it's a page navigation, return cached root/index.html
                if (event.request.mode === 'navigate') {
                    return caches.match('/') || caches.match('/index.html');
                }
                return cachedResponse;
            });

            return cachedResponse || fetchPromise;
        })
    );
});
