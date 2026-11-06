using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace MySinggharaPc
{
    // Aplikasi PC native My Singghara: satu jendela WinForms yang memuat UI
    // Web Connect dari HP lewat WebView2 (bukan browser, bukan terminal).

    static class Program
    {
        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            try
            {
                Application.Run(new MainForm(args));
            }
            catch (Exception ex)
            {
                MessageBox.Show("Aplikasi berhenti karena galat:\n\n" + ex.Message,
                    "My Singghara PC", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }

    public class MainForm : Form
    {
        private const string APP_TITLE = "My Singghara";
        private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);

        private WebView2 webView;
        private Panel connectPanel;
        private TextBox urlBox;
        private Label statusLabel;
        private Button connectButton;
        private Button reloadButton;

        // Strip judul bawaan aplikasi: hanya berisi tombol minimize dan layar penuh.
        // Tombol close dan maximize/restore memang tidak ada (Alt+F untuk beralih mode).
        private Panel topStrip;
        private Label stripTitle;
        private Label modeHint;
        private Button minButton;
        private Button fsButton;

        private string appDataDir;
        private string configPath;
        private string currentUrl;
        private bool webViewReady;

        // Dua sumber layar penuh: preferensi pengguna (Alt+F) dan video HTML5.
        private bool appFullScreen;
        private bool videoFullScreen;
        private Rectangle windowedBounds;

        // Menyeret jendela lewat strip judul
        private bool draggingStrip;
        private Point dragStartCursor;
        private Rectangle dragStartBounds;

        private const int STRIP_HEIGHT = 38;
        private const int HOTKEY_ALT_F = 0x5F01;
        private const uint MOD_ALT = 0x0001;
        private const uint VK_F = 0x46;
        private const int WM_HOTKEY = 0x0312;
        private const int WM_ACTIVATEAPP = 0x001C;

        [DllImport("user32.dll")]
        private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

        [DllImport("user32.dll")]
        private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

        public MainForm(string[] args)
        {
            appDataDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "My Singghara");
            configPath = Path.Combine(appDataDir, "pc-config.ini");

            Text = APP_TITLE;
            Name = "MySinggharaPc";
            BackColor = Color.FromArgb(8, 15, 30);
            FormBorderStyle = FormBorderStyle.None;
            ControlBox = false;
            MaximizeBox = false;
            MinimizeBox = false;
            try { Icon = LoadOwnIcon(); } catch { }

            appFullScreen = ReadFullScreenPreference();
            windowedBounds = ReadWindowBounds();
            StartPosition = FormStartPosition.Manual;
            Bounds = appFullScreen ? Screen.FromPoint(Cursor.Position).Bounds : windowedBounds;

            connectPanel = BuildConnectPanel();
            Controls.Add(connectPanel);

            webView = new WebView2();
            Controls.Add(webView);

            topStrip = BuildTopStrip();
            Controls.Add(topStrip);
            connectPanel.BringToFront();

            Resize += delegate { Relayout(); };
            Relayout();

            KeyPreview = true;
            KeyDown += OnFormKeyDown;

            currentUrl = ResolveStartUrl(args);
            Shown += delegate
            {
                ApplyWindowMode();
                StartWebView();
            };
            FormClosing += delegate
            {
                try { UnregisterHotKey(Handle, HOTKEY_ALT_F); } catch { }
                SaveWindowSettings();
            };
        }

        // ------------------------------------------------------------------
        // Strip judul: minimize + tombol layar penuh, tanpa close/maximize
        // ------------------------------------------------------------------

        private Panel BuildTopStrip()
        {
            Panel strip = new Panel();
            strip.BackColor = Color.FromArgb(8, 15, 30);
            strip.MouseDown += OnStripMouseDown;
            strip.MouseMove += OnStripMouseMove;
            strip.MouseUp += OnStripMouseUp;
            strip.DoubleClick += delegate { ToggleAppFullScreen(); };

            stripTitle = new Label();
            stripTitle.Text = APP_TITLE + "  ·  Web Connect";
            stripTitle.ForeColor = Color.FromArgb(56, 189, 248);
            stripTitle.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
            stripTitle.TextAlign = ContentAlignment.MiddleLeft;
            stripTitle.BackColor = Color.Transparent;
            strip.Controls.Add(stripTitle);

            minButton = MakeStripButton("–", "Perkecil jendela");
            minButton.Click += delegate { WindowState = FormWindowState.Minimized; };
            strip.Controls.Add(minButton);

            fsButton = MakeStripButton("⤢", "Layar penuh / jendela (Alt+F)");
            fsButton.Click += delegate { ToggleAppFullScreen(); };
            strip.Controls.Add(fsButton);

            modeHint = new Label();
            modeHint.Text = "Alt+F";
            modeHint.ForeColor = Color.FromArgb(100, 116, 139);
            modeHint.Font = new Font("Segoe UI", 8.5f);
            modeHint.TextAlign = ContentAlignment.MiddleCenter;
            strip.Controls.Add(modeHint);

            return strip;
        }

        private Button MakeStripButton(string glyph, string tip)
        {
            Button button = new Button();
            button.Text = glyph;
            button.Font = new Font("Segoe UI Symbol", 12f);
            button.ForeColor = Color.FromArgb(203, 213, 225);
            button.BackColor = Color.FromArgb(15, 23, 42);
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = 0;
            button.TabStop = false;
            ToolTip tips = new ToolTip();
            tips.SetToolTip(button, tip);
            return button;
        }

        private void OnStripMouseDown(object sender, MouseEventArgs e)
        {
            if (e.Button != MouseButtons.Left || InFullScreen) return;
            draggingStrip = true;
            dragStartCursor = Cursor.Position;
            dragStartBounds = Bounds;
        }

        private void OnStripMouseMove(object sender, MouseEventArgs e)
        {
            if (!draggingStrip) return;
            int dx = Cursor.Position.X - dragStartCursor.X;
            int dy = Cursor.Position.Y - dragStartCursor.Y;
            windowedBounds = new Rectangle(dragStartBounds.X + dx, dragStartBounds.Y + dy,
                dragStartBounds.Width, dragStartBounds.Height);
            Bounds = windowedBounds;
        }

        private void OnStripMouseUp(object sender, MouseEventArgs e)
        {
            if (!draggingStrip) return;
            draggingStrip = false;
            SaveWindowSettings();
        }

        private void Relayout()
        {
            if (topStrip == null || webView == null || connectPanel == null) return;
            int width = ClientSize.Width;
            int height = ClientSize.Height;
            int contentTop = topStrip.Visible ? STRIP_HEIGHT : 0;

            topStrip.SetBounds(0, 0, width, STRIP_HEIGHT);
            minButton.SetBounds(width - STRIP_HEIGHT, 0, STRIP_HEIGHT, STRIP_HEIGHT);
            fsButton.SetBounds(width - STRIP_HEIGHT * 2, 0, STRIP_HEIGHT, STRIP_HEIGHT);
            modeHint.SetBounds(width - STRIP_HEIGHT * 2 - 104, 0, 104, STRIP_HEIGHT);
            stripTitle.SetBounds(14, 0, Math.Max(60, width - STRIP_HEIGHT * 2 - 124), STRIP_HEIGHT);

            int contentHeight = Math.Max(1, height - contentTop);
            webView.SetBounds(0, contentTop, width, contentHeight);
            connectPanel.SetBounds(0, contentTop, width, contentHeight);
        }

        // ------------------------------------------------------------------
        // Panel koneksi (native): muncul saat server HP belum terjangkau
        // ------------------------------------------------------------------

        private Panel BuildConnectPanel()
        {
            Panel panel = new Panel();
            panel.BackColor = Color.FromArgb(15, 23, 42);
            panel.Visible = false;

            TableLayoutInner(panel);
            return panel;
        }

        private void TableLayoutInner(Panel panel)
        {
            TableLayoutPanel grid = new TableLayoutPanel();
            grid.Dock = DockStyle.Fill;
            grid.ColumnCount = 1;
            grid.RowCount = 6;
            grid.RowStyles.Add(new RowStyle(SizeType.Percent, 30f));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.RowStyles.Add(new RowStyle(SizeType.Percent, 40f));
            grid.Padding = new Padding(28, 0, 28, 0);

            Label logo = new Label();
            logo.Text = "My Singghara";
            logo.Font = new Font("Segoe UI", 26f, FontStyle.Bold);
            logo.ForeColor = Color.FromArgb(56, 189, 248);
            logo.TextAlign = ContentAlignment.BottomCenter;
            logo.Dock = DockStyle.Fill;
            grid.Controls.Add(logo, 0, 0);

            statusLabel = new Label();
            statusLabel.Text = "Menghubungkan ke HP...";
            statusLabel.Font = new Font("Segoe UI", 11f);
            statusLabel.ForeColor = Color.FromArgb(203, 213, 225);
            statusLabel.TextAlign = ContentAlignment.TopCenter;
            statusLabel.Dock = DockStyle.Fill;
            statusLabel.AutoSize = false;
            statusLabel.Height = 92;
            grid.Controls.Add(statusLabel, 0, 1);

            TableLayoutPanel row = new TableLayoutPanel();
            row.AutoSize = true;
            row.AutoSizeMode = AutoSizeMode.GrowAndShrink;
            row.ColumnCount = 3;
            row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            row.Margin = new Padding(0, 6, 0, 6);
            row.Anchor = AnchorStyles.None;

            urlBox = new TextBox();
            urlBox.Font = new Font("Segoe UI", 12f);
            urlBox.Width = 300;
            urlBox.BorderStyle = BorderStyle.FixedSingle;
            urlBox.Anchor = AnchorStyles.Left;
            urlBox.KeyDown += OnUrlKeyDown;
            row.Controls.Add(urlBox, 0, 0);

            connectButton = new Button();
            connectButton.Text = "Sambungkan";
            connectButton.AutoSize = true;
            connectButton.Padding = new Padding(14, 6, 14, 6);
            connectButton.Margin = new Padding(8, 0, 0, 0);
            connectButton.Click += delegate { Connect(ReadBoxUrl()); };
            row.Controls.Add(connectButton, 1, 0);

            reloadButton = new Button();
            reloadButton.Text = "Coba Lagi";
            reloadButton.AutoSize = true;
            reloadButton.Padding = new Padding(14, 6, 14, 6);
            reloadButton.Margin = new Padding(6, 0, 0, 0);
            reloadButton.Click += delegate { Connect(currentUrl); };
            row.Controls.Add(reloadButton, 2, 0);

            panel.Tag = grid;
            grid.Controls.Add(row, 0, 2);

            Label hint = new Label();
            hint.Text = "Buka aplikasi My Singghara di HP, aktifkan Web Connect (Akses PC), lalu masukkan alamat yang tampil di sana, contoh 192.168.1.10:8080";
            hint.ForeColor = Color.FromArgb(148, 163, 184);
            hint.Font = new Font("Segoe UI", 9f);
            hint.TextAlign = ContentAlignment.TopCenter;
            hint.Dock = DockStyle.Fill;
            hint.AutoSize = false;
            hint.Height = 46;
            grid.Controls.Add(hint, 0, 3);

            Label shortcutHint = new Label();
            shortcutHint.Text = "Alt+F layar penuh/jendela  ·  F5 muat ulang  ·  Ctrl+L ganti alamat  ·  Alt+F4 tutup";
            shortcutHint.ForeColor = Color.FromArgb(100, 116, 139);
            shortcutHint.Font = new Font("Segoe UI", 8.5f);
            shortcutHint.TextAlign = ContentAlignment.TopCenter;
            shortcutHint.Dock = DockStyle.Fill;
            shortcutHint.AutoSize = false;
            shortcutHint.Height = 26;
            grid.Controls.Add(shortcutHint, 0, 4);

            panel.Controls.Add(grid);
        }

        private string ReadBoxUrl()
        {
            string raw = urlBox.Text == null ? "" : urlBox.Text.Trim();
            return NormalizeUrl(raw);
        }

        private void OnUrlKeyDown(object sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Enter)
            {
                e.Handled = true;
                e.SuppressKeyPress = true;
                Connect(ReadBoxUrl());
            }
        }

        private static string NormalizeUrl(string raw)
        {
            if (string.IsNullOrEmpty(raw)) return "";
            string value = raw.Trim();
            if (value.IndexOf("://") < 0) value = "http://" + value;
            Uri parsed;
            if (!Uri.TryCreate(value, UriKind.Absolute, out parsed)) return "";
            if (parsed.Scheme != "http" && parsed.Scheme != "https") return "";
            return parsed.GetLeftPart(UriPartial.Path);
        }

        // ------------------------------------------------------------------
        // Siklus koneksi
        // ------------------------------------------------------------------

        private void StartWebView()
        {
            ShowPanel("Menghubungkan ke HP...");
            TryProbeAndConnect();
        }

        private void Connect(string url)
        {
            if (string.IsNullOrEmpty(url))
            {
                statusLabel.Text = "Alamat tidak valid. Contoh: 192.168.1.10:8080";
                return;
            }
            currentUrl = url;
            SaveConfigUrl(url);
            ShowPanel("Menghubungkan ke " + url + " ...");
            TryProbeAndConnect();
        }

        private void TryProbeAndConnect()
        {
            string url = currentUrl;
            ThreadPool.QueueUserWorkItem(delegate
            {
                bool reachable = ProbeUrl(url);
                Action onUi = delegate
                {
                    if (IsDisposed) return;
                    if (!reachable)
                    {
                        ShowPanel("Server HP belum terjangkau di\n" + url +
                                  "\n\nPastikan Web Connect (Akses PC) di HP aktif dan PC satu jaringan WiFi.");
                        urlBox.Text = url;
                        return;
                    }
                    EnsureWebViewThenNavigate(url);
                };
                try { BeginInvoke(onUi); } catch { }
            });
        }

        private static bool ProbeUrl(string url)
        {
            if (string.IsNullOrEmpty(url)) return false;
            try
            {
                HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
                request.Method = "GET";
                request.Timeout = 4000;
                request.ReadWriteTimeout = 4000;
                request.UserAgent = "MySinggharaPc/1.0";
                request.AllowAutoRedirect = false;
                using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
                {
                    int code = (int)response.StatusCode;
                    return code >= 200 && code < 500;
                }
            }
            catch (WebException)
            {
                return false;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private void EnsureWebViewThenNavigate(string url)
        {
            if (webViewReady)
            {
                NavigateTo(url);
                return;
            }
            statusLabel.Text = "Menyiapkan mesin tampilan...";
            try
            {
                string userData = Path.Combine(appDataDir, "webview2");
                Directory.CreateDirectory(userData);
                System.Threading.Tasks.Task<CoreWebView2Environment> envTask =
                    CoreWebView2Environment.CreateAsync(null, userData, null);
                envTask.ContinueWith(delegate(System.Threading.Tasks.Task<CoreWebView2Environment> created)
                {
                    Action onUi = delegate
                    {
                        if (IsDisposed) return;
                        if (created.IsFaulted)
                        {
                            ShowPanel("WebView2 Runtime tidak terpasang di PC ini.\n\n" +
                                      Unwrap(created.Exception) +
                                      "\n\nPasang \"Microsoft Edge WebView2 Runtime\", lalu jalankan ulang aplikasi.");
                            urlBox.Text = url;
                            return;
                        }
                        StartCoreWebView(created.Result, url);
                    };
                    try { BeginInvoke(onUi); } catch { }
                });
            }
            catch (Exception ex)
            {
                ShowPanel("WebView2 Runtime tidak terpasang di PC ini.\n\n" + ex.Message +
                          "\n\nPasang \"Microsoft Edge WebView2 Runtime\", lalu jalankan ulang aplikasi.");
                urlBox.Text = url;
            }
        }

        private void StartCoreWebView(CoreWebView2Environment environment, string url)
        {
            System.Threading.Tasks.Task init = webView.EnsureCoreWebView2Async(environment);
            init.ContinueWith(delegate(System.Threading.Tasks.Task task)
            {
                Action onUi = delegate
                {
                    if (IsDisposed) return;
                    if (task.IsFaulted)
                    {
                        ShowPanel("Mesin tampilan gagal disiapkan.\n\n" + Unwrap(task.Exception));
                        urlBox.Text = url;
                        return;
                    }
                    PrepareWebView();
                    NavigateTo(url);
                };
                try { BeginInvoke(onUi); } catch { }
            }, System.Threading.Tasks.TaskScheduler.FromCurrentSynchronizationContext());
        }

        private static string Unwrap(Exception ex)
        {
            Exception inner = ex;
            while (inner != null && inner.InnerException != null) inner = inner.InnerException;
            return inner == null ? "Tidak ada detail." : inner.Message;
        }

        private void PrepareWebView()
        {
            CoreWebView2 core = webView.CoreWebView2;
            core.Settings.AreDevToolsEnabled = true;
            core.Settings.AreDefaultContextMenusEnabled = true;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.AreBrowserAcceleratorKeysEnabled = false;

            core.DocumentTitleChanged += delegate
            {
                string title = core.DocumentTitle;
                Text = string.IsNullOrEmpty(title) ? APP_TITLE : APP_TITLE + " - " + title;
            };

            core.ContainsFullScreenElementChanged += delegate
            {
                videoFullScreen = core.ContainsFullScreenElement;
                ApplyWindowMode();
            };

            core.NewWindowRequested += delegate(object sender, CoreWebView2NewWindowRequestedEventArgs e)
            {
                e.Handled = true;
                try { core.Navigate(e.Uri); } catch { }
            };

            webView.NavigationCompleted += delegate(object sender, CoreWebView2NavigationCompletedEventArgs e)
            {
                if (!e.IsSuccess)
                {
                    ShowPanel("Koneksi ke HP terputus.\n" + currentUrl +
                              "\n\nAktifkan lagi Web Connect di HP lalu klik Coba Lagi.");
                }
            };

            webViewReady = true;
        }

        private void NavigateTo(string url)
        {
            if (!webViewReady) return;
            HidePanel();
            try
            {
                webView.CoreWebView2.Navigate(url);
            }
            catch (Exception ex)
            {
                ShowPanel("Gagal memuat halaman:\n" + ex.Message);
            }
        }

        private void ShowPanel(string message)
        {
            if (statusLabel != null) statusLabel.Text = message;
            if (urlBox != null && string.IsNullOrEmpty(urlBox.Text)) urlBox.Text = currentUrl;
            connectPanel.Visible = true;
            connectPanel.BringToFront();
        }

        private void HidePanel()
        {
            connectPanel.Visible = false;
            webView.BringToFront();
        }

        // ------------------------------------------------------------------
        // Mode jendela / layar penuh & pintasan papan ketik
        // ------------------------------------------------------------------

        private bool InFullScreen
        {
            get { return appFullScreen || videoFullScreen; }
        }

        private void ToggleAppFullScreen()
        {
            appFullScreen = !appFullScreen;
            ApplyWindowMode();
            SaveWindowSettings();
        }

        private void ApplyWindowMode()
        {
            if (InFullScreen)
            {
                WindowState = FormWindowState.Normal;
                Bounds = Screen.FromControl(this).Bounds;
            }
            else
            {
                WindowState = FormWindowState.Normal;
                windowedBounds = ClampToScreen(windowedBounds);
                Bounds = windowedBounds;
            }
            topStrip.Visible = !videoFullScreen;
            fsButton.Text = appFullScreen ? "⤡" : "⤢";
            modeHint.Text = appFullScreen ? "Alt+F jendela" : "Alt+F layar";
            Relayout();
        }

        private Rectangle ClampToScreen(Rectangle requested)
        {
            Rectangle working = Screen.FromPoint(new Point(requested.X + requested.Width / 2,
                requested.Y + requested.Height / 2)).WorkingArea;
            int width = Math.Min(requested.Width, working.Width);
            int height = Math.Min(requested.Height, working.Height);
            int x = Math.Max(working.Left, Math.Min(requested.X, working.Right - width));
            int y = Math.Max(working.Top, Math.Min(requested.Y, working.Bottom - height));
            return new Rectangle(x, y, width, height);
        }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == WM_HOTKEY && (int)m.WParam == HOTKEY_ALT_F)
            {
                ToggleAppFullScreen();
                return;
            }
            if (m.Msg == WM_ACTIVATEAPP)
            {
                // Hotkey hanya dipakai selama jendela ini aktif di foreground.
                try
                {
                    if (m.WParam == IntPtr.Zero) UnregisterHotKey(Handle, HOTKEY_ALT_F);
                    else RegisterHotKey(Handle, HOTKEY_ALT_F, MOD_ALT, VK_F);
                }
                catch { }
            }
            base.WndProc(ref m);
        }

        private void OnFormKeyDown(object sender, KeyEventArgs e)
        {
            if (e.Alt && e.KeyCode == Keys.F)
            {
                e.SuppressKeyPress = true;
                ToggleAppFullScreen();
                return;
            }
            if (videoFullScreen && e.KeyCode == Keys.Escape)
            {
                videoFullScreen = false;
                ApplyWindowMode();
                return;
            }
            if (e.KeyCode == Keys.F5)
            {
                e.SuppressKeyPress = true;
                Reload();
                return;
            }
            if (e.Control && e.KeyCode == Keys.R)
            {
                e.SuppressKeyPress = true;
                Reload();
                return;
            }
            if (e.Control && e.KeyCode == Keys.L)
            {
                e.SuppressKeyPress = true;
                ShowPanel("Masukkan alamat Web Connect HP, lalu tekan Enter.");
                urlBox.Focus();
                urlBox.SelectAll();
            }
        }

        private void Reload()
        {
            if (!webViewReady)
            {
                TryProbeAndConnect();
                return;
            }
            try { webView.CoreWebView2.Reload(); } catch { }
        }

        // ------------------------------------------------------------------
        // Konfigurasi
        // ------------------------------------------------------------------

        private string ResolveStartUrl(string[] args)
        {
            string host = null;
            string port = null;
            for (int i = 0; i + 1 < args.Length; i++)
            {
                if (args[i] == "--host") host = args[i + 1];
                else if (args[i] == "--port") port = args[i + 1];
            }
            if (!string.IsNullOrEmpty(host))
            {
                string url = "http://" + host;
                if (!string.IsNullOrEmpty(port)) url += ":" + port;
                return NormalizeUrl(url);
            }

            Dictionary<string, string> config = ReadConfig();
            string saved;
            if (config.TryGetValue("url", out saved) && !string.IsNullOrEmpty(saved))
            {
                string normalized = NormalizeUrl(saved);
                if (!string.IsNullOrEmpty(normalized)) return normalized;
            }
            return "http://mysingghara.com";
        }

        private Dictionary<string, string> ReadConfig()
        {
            Dictionary<string, string> map = new Dictionary<string, string>();
            try
            {
                if (!File.Exists(configPath)) return map;
                string[] lines = File.ReadAllLines(configPath, Encoding.UTF8);
                for (int i = 0; i < lines.Length; i++)
                {
                    string line = lines[i].Trim();
                    if (line.Length == 0 || line.StartsWith("#")) continue;
                    int eq = line.IndexOf('=');
                    if (eq <= 0) continue;
                    map[line.Substring(0, eq).Trim().ToLowerInvariant()] = line.Substring(eq + 1).Trim();
                }
            }
            catch { }
            return map;
        }

        private void SaveConfigUrl(string url)
        {
            try
            {
                Directory.CreateDirectory(appDataDir);
                Dictionary<string, string> map = ReadConfig();
                map["url"] = url;
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("# Konfigurasi aplikasi PC My Singghara");
                foreach (KeyValuePair<string, string> pair in map)
                {
                    sb.AppendLine(pair.Key + "=" + pair.Value);
                }
                if (!map.ContainsKey("fullscreen")) sb.AppendLine("fullscreen=1");
                File.WriteAllText(configPath, sb.ToString(), Utf8NoBom);
            }
            catch { }
        }

        private bool ReadFullScreenPreference()
        {
            Dictionary<string, string> map = ReadConfig();
            string raw;
            if (!map.TryGetValue("fullscreen", out raw)) return true;
            return raw.Trim() != "0";
        }

        private Rectangle ReadWindowBounds()
        {
            Rectangle working = Screen.FromPoint(Cursor.Position).WorkingArea;
            int width = Math.Min(1280, working.Width - 60);
            int height = Math.Min(820, working.Height - 60);
            try
            {
                string raw;
                if (ReadConfig().TryGetValue("bounds", out raw))
                {
                    string[] parts = raw.Split(',');
                    if (parts.Length == 4)
                    {
                        int x = int.Parse(parts[0].Trim());
                        int y = int.Parse(parts[1].Trim());
                        int w = int.Parse(parts[2].Trim());
                        int h = int.Parse(parts[3].Trim());
                        if (w >= 640 && h >= 480 && w <= 7680 && h <= 4320)
                            return new Rectangle(x, y, w, h);
                    }
                }
            }
            catch { }
            return new Rectangle(working.Left + (working.Width - width) / 2,
                working.Top + (working.Height - height) / 2, width, height);
        }

        private void SaveWindowSettings()
        {
            try
            {
                Directory.CreateDirectory(appDataDir);
                Dictionary<string, string> map = ReadConfig();
                map["url"] = currentUrl;
                map["fullscreen"] = appFullScreen ? "1" : "0";
                if (!InFullScreen)
                    map["bounds"] = windowedBounds.X + "," + windowedBounds.Y + "," +
                                    windowedBounds.Width + "," + windowedBounds.Height;
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("# Konfigurasi aplikasi PC My Singghara");
                foreach (KeyValuePair<string, string> pair in map)
                {
                    sb.AppendLine(pair.Key + "=" + pair.Value);
                }
                File.WriteAllText(configPath, sb.ToString(), Utf8NoBom);
            }
            catch { }
        }

        private Icon LoadOwnIcon()
        {
            Assembly asm = Assembly.GetExecutingAssembly();
            string[] names = asm.GetManifestResourceNames();
            for (int i = 0; i < names.Length; i++)
            {
                if (names[i].EndsWith(".ico", StringComparison.OrdinalIgnoreCase))
                {
                    using (Stream stream = asm.GetManifestResourceStream(names[i]))
                    {
                        if (stream != null) return new Icon(stream);
                    }
                }
            }
            return null;
        }
    }
}
