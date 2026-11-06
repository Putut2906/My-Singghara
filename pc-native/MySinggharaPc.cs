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

        // Strip judul: judul, petunjuk mode, dan tempat menyeret jendela.
        private Panel topStrip;
        private Label stripTitle;
        private Label modeHint;

        // Pesan singkat yang muncul setelah berganti mode.
        private Label modeToast;
        private System.Windows.Forms.Timer modeToastTimer;

        // Caption buttons: minimize, layar penuh/pulih, dan close selalu tampil di
        // pojok kanan atas, baik saat berjendela maupun saat layar penuh.
        // Tombol keempat menyembunyikan seluruh caption buttons (Alt+H); saat
        // tersembunyi, kursor yang menyentuh tepi atas jendela memunculkan kembali.
        private Panel chromePanel;
        private Button chromeMinButton;
        private Button chromeMaxButton;
        private Button chromeHideButton;
        private Button chromeCloseButton;
        private Font chromeGlyphFont;
        private bool chromeUsesMdl;
        private ToolTip chromeTips;
        private bool chromeHidden;
        private bool chromeRevealed;
        private System.Windows.Forms.Timer revealTimer;

        private string appDataDir;
        private string configPath;
        private string currentUrl;
        private bool webViewReady;

        // Dua sumber layar penuh: preferensi pengguna (Alt+F) dan video HTML5.
        private bool appFullScreen;
        private bool videoFullScreen;
        private Rectangle windowedBounds;
        // Bounds yang sedang dipasang oleh kode sendiri tidak boleh dicatat sebagai
        // ukuran jendela biasa, kalau tidak layar penuh kehilangan bentuk aslinya.
        private bool applyingBounds;

        // Menyeret jendela lewat strip judul
        private bool draggingStrip;
        private Point dragStartCursor;
        private Rectangle dragStartBounds;

        private const int STRIP_HEIGHT = 38;

        // Geometri caption buttons: empat tombol rata tepi kanan, setinggi strip judul.
        private const int CHROME_BUTTON_W = 46;
        private const int CHROME_H = STRIP_HEIGHT;
        private const int CHROME_W = CHROME_BUTTON_W * 4;
        // Glif Segoe MDL2 Assets (minimize, maximize, restore, close, sembunyi/tampil).
        private const string GLYPH_MINIMIZE = "\uE921";
        private const string GLYPH_MAXIMIZE = "\uE922";
        private const string GLYPH_RESTORE = "\uE923";
        private const string GLYPH_CLOSE = "\uE8BB";
        private const string GLYPH_HIDE = "\uE70E";
        private const string GLYPH_SHOW = "\uE70D";

        private const int HOTKEY_ALT_F = 0x5F01;
        private const int HOTKEY_ALT_H = 0x5F02;
        private const uint MOD_ALT = 0x0001;
        private const uint VK_F = 0x46;
        private const uint VK_H = 0x48;
        private const int WM_HOTKEY = 0x0312;
        private const int WM_ACTIVATEAPP = 0x001C;

        // Layar penuh sejati: jendela harus menutupi satu monitor penuh dan shell
        // perlu diberitahu lewat SWP_FRAMECHANGED supaya taskbar mundur sendiri.
        [DllImport("user32.dll")]
        private static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y,
            int width, int height, uint flags);
        private const uint SWP_NOZORDER = 0x0004;
        private const uint SWP_FRAMECHANGED = 0x0020;
        private const uint SWP_NOACTIVATE = 0x0010;

        // Tanpa bingkai jendela, Windows tidak punya batas untuk menyeret ukuran.
        // WS_THICKFRAME mengaktifkan proses resize yang hasilnya dilaporkan WM_NCHITTEST.
        private const int WS_THICKFRAME = 0x00040000;
        private const int WM_NCHITTEST = 0x0084;
        private const int HTLEFT = 10;
        private const int HTRIGHT = 11;
        private const int HTTOP = 12;
        private const int HTTOPLEFT = 13;
        private const int HTTOPRIGHT = 14;
        private const int HTBOTTOM = 15;
        private const int HTBOTTOMLEFT = 16;
        private const int HTBOTTOMRIGHT = 17;
        private const int GRIP_WIDTH = 9;

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
            // Tombol minimize buatan sendiri butuh gaya WS_MINIMIZEBOX supaya
            // WindowState = Minimized benar-benar dijalankan oleh Windows.
            MinimizeBox = true;
            ShowInTaskbar = true;
            MinimumSize = new Size(760, 480);
            try { Icon = LoadOwnIcon(); } catch { }

            appFullScreen = ReadFullScreenPreference();
            chromeHidden = ReadChromeHiddenPreference();
            windowedBounds = ReadWindowBounds();
            StartPosition = FormStartPosition.Manual;
            Bounds = appFullScreen ? Screen.FromPoint(Cursor.Position).Bounds : windowedBounds;

            connectPanel = BuildConnectPanel();
            Controls.Add(connectPanel);

            webView = new WebView2();
            Controls.Add(webView);

            topStrip = BuildTopStrip();
            Controls.Add(topStrip);

            modeToast = BuildModeToast();
            Controls.Add(modeToast);
            modeToastTimer = new System.Windows.Forms.Timer();
            modeToastTimer.Interval = 2200;
            modeToastTimer.Tick += delegate
            {
                modeToastTimer.Stop();
                modeToast.Visible = false;
            };

            chromePanel = BuildChromePanel();
            Controls.Add(chromePanel);
            chromePanel.BringToFront();

            // Timer kecil ini yang menjaga caption buttons tetap tersembunyi sampai
            // kursor benar-benar menyentuh tepi atas jendela.
            revealTimer = new System.Windows.Forms.Timer();
            revealTimer.Interval = 120;
            revealTimer.Tick += OnRevealTick;
            revealTimer.Start();

            connectPanel.BringToFront();

            Resize += delegate { Relayout(); };
            Move += delegate { RecordWindowedBounds(); };
            SizeChanged += delegate { RecordWindowedBounds(); };
            Relayout();

            KeyPreview = true;
            KeyDown += OnFormKeyDown;

            currentUrl = ResolveStartUrl(args);
            Shown += delegate
            {
                ApplyWindowMode();
                ShowModeToast(DescribeMode());
                StartWebView();
            };
            FormClosing += delegate
            {
                try { UnregisterHotKey(Handle, HOTKEY_ALT_F); } catch { }
                try { UnregisterHotKey(Handle, HOTKEY_ALT_H); } catch { }
                revealTimer.Stop();
                SaveWindowSettings();
            };
        }

        // ------------------------------------------------------------------
        // Strip judul: judul, petunjuk mode, dan tempat menyeret jendela.
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

            modeHint = new Label();
            modeHint.Text = "Alt+F layar penuh";
            modeHint.ForeColor = Color.FromArgb(100, 116, 139);
            modeHint.Font = new Font("Segoe UI", 8.5f);
            modeHint.TextAlign = ContentAlignment.MiddleRight;
            strip.Controls.Add(modeHint);

            return strip;
        }

        private Label BuildModeToast()
        {
            Label toast = new Label();
            toast.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            toast.ForeColor = Color.FromArgb(226, 232, 240);
            toast.BackColor = Color.FromArgb(15, 23, 42);
            toast.TextAlign = ContentAlignment.MiddleCenter;
            toast.Visible = false;
            return toast;
        }

        private void ShowModeToast(string message)
        {
            modeToast.Text = message;
            Relayout();
            modeToast.Visible = true;
            modeToast.BringToFront();
            modeToastTimer.Stop();
            modeToastTimer.Start();
        }

        // ------------------------------------------------------------------
        // Caption buttons: minimize, layar penuh/pulih, dan close.
        // ------------------------------------------------------------------

        private Panel BuildChromePanel()
        {
            chromeUsesMdl = UsesMdl2Font();
            chromeGlyphFont = new Font(chromeUsesMdl ? "Segoe MDL2 Assets" : "Segoe UI", 11f);
            chromeTips = new ToolTip();

            chromeMinButton = BuildChromeButton(ChromeGlyph(GLYPH_MINIMIZE, "-"), "Minimize");
            chromeMinButton.Click += delegate { WindowState = FormWindowState.Minimized; };

            chromeMaxButton = BuildChromeButton(ChromeGlyph(GLYPH_MAXIMIZE, "[]"), "Layar penuh");
            chromeMaxButton.Click += delegate { ToggleAppFullScreen(); };

            chromeHideButton = BuildChromeButton(ChromeGlyph(GLYPH_HIDE, "--"), "Sembunyikan tombol jendela");
            chromeHideButton.Click += delegate { ToggleChromeHidden(); };

            chromeCloseButton = BuildChromeButton(ChromeGlyph(GLYPH_CLOSE, "x"), "Tutup");
            chromeCloseButton.Click += delegate { Close(); };

            Panel panel = new Panel();
            panel.BackColor = Color.FromArgb(30, 41, 59);
            panel.Controls.Add(chromeMinButton);
            panel.Controls.Add(chromeMaxButton);
            panel.Controls.Add(chromeHideButton);
            panel.Controls.Add(chromeCloseButton);
            return panel;
        }

        private Button BuildChromeButton(string glyph, string tip)
        {
            Button button = new Button();
            button.Text = glyph;
            button.Font = chromeGlyphFont;
            button.ForeColor = Color.FromArgb(226, 232, 240);
            button.BackColor = Color.FromArgb(30, 41, 59);
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = 0;
            button.FlatAppearance.MouseOverBackColor = Color.FromArgb(51, 65, 85);
            button.FlatAppearance.MouseDownBackColor = Color.FromArgb(71, 85, 105);
            button.TabStop = false;
            button.Size = new Size(CHROME_BUTTON_W, CHROME_H);
            chromeTips.SetToolTip(button, tip);
            return button;
        }

        // Windows 10 punya Segoe MDL2 Assets; kalau tidak ada, GDI+ mengembalikan
        // font pengganti dan glif wilayah pribadi akan tampil sebagai kotak kosong.
        private static bool UsesMdl2Font()
        {
            try
            {
                Font probe = new Font("Segoe MDL2 Assets", 11f);
                bool ok = string.Equals(probe.Name, "Segoe MDL2 Assets", StringComparison.OrdinalIgnoreCase);
                probe.Dispose();
                return ok;
            }
            catch { return false; }
        }

        private string ChromeGlyph(string mdl, string plain)
        {
            return chromeUsesMdl ? mdl : plain;
        }

        private void UpdateChromeGlyphs()
        {
            if (chromeMaxButton == null) return;
            bool full = appFullScreen;
            chromeMaxButton.Text = ChromeGlyph(full ? GLYPH_RESTORE : GLYPH_MAXIMIZE,
                full ? "><" : "[]");
            chromeMaxButton.AccessibleName = full ? "Pulih ke jendela" : "Layar penuh";
            chromeTips.SetToolTip(chromeMaxButton, full ? "Pulih ke jendela (Alt+F)" : "Layar penuh (Alt+F)");
            if (chromeHideButton != null)
            {
                bool hidden = chromeHidden;
                chromeHideButton.Text = ChromeGlyph(hidden ? GLYPH_SHOW : GLYPH_HIDE, hidden ? "++" : "--");
                chromeHideButton.AccessibleName = hidden ? "Tampilkan tombol jendela" : "Sembunyikan tombol jendela";
                chromeTips.SetToolTip(chromeHideButton, hidden
                    ? "Tampilkan tombol jendela (Alt+H)" : "Sembunyikan tombol jendela (Alt+H)");
            }
        }

        private void LayoutChrome()
        {
            if (chromePanel == null) return;
            chromeMinButton.Location = new Point(0, 0);
            chromeMaxButton.Location = new Point(CHROME_BUTTON_W, 0);
            chromeHideButton.Location = new Point(CHROME_BUTTON_W * 2, 0);
            chromeCloseButton.Location = new Point(CHROME_BUTTON_W * 3, 0);
        }

        // Tombol tetap dipakai saat layar penuh; hanya mengalah ketika halaman
        // web meminta layar penuh sendiri (misalnya pemutaran video). Saat pengguna
        // menyembunyikannya, panel hanya muncul kembali bila kursor menyentuh tepi atas.
        private void UpdateChromeVisibility()
        {
            if (chromePanel == null) return;
            chromePanel.Visible = !videoFullScreen && (!chromeHidden || chromeRevealed);
            if (chromePanel.Visible) chromePanel.BringToFront();
        }

        private void ToggleChromeHidden()
        {
            chromeHidden = !chromeHidden;
            chromeRevealed = false;
            UpdateChromeVisibility();
            UpdateChromeGlyphs();
            Relayout();
            SaveWindowSettings();
            ShowModeToast(chromeHidden
                ? "Tombol jendela disembunyikan  ·  sentuh tepi atas atau Alt+H"
                : "Tombol jendela ditampilkan");
        }

        private void OnRevealTick(object sender, EventArgs e)
        {
            if (!chromeHidden || videoFullScreen) return;
            if (WindowState != FormWindowState.Normal) return;
            Rectangle area = Bounds;
            Point cursor = Cursor.Position;
            bool withinWidth = cursor.X >= area.Left && cursor.X < area.Right;
            if (!chromeRevealed)
            {
                if (withinWidth && cursor.Y >= area.Top && cursor.Y <= area.Top + 3)
                {
                    chromeRevealed = true;
                    UpdateChromeVisibility();
                    chromePanel.BringToFront();
                }
            }
            else if (!withinWidth || cursor.Y > area.Top + CHROME_H + 12)
            {
                chromeRevealed = false;
                UpdateChromeVisibility();
            }
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

            // Posisi tombol selalu rata tepi kanan; saat tersembunyi judul boleh
            // memakai seluruh lebar, tetapi batas panel tombol tidak boleh ikut bergeser,
            // kalau tidak munculnya kembali terlihat terpotong.
            int chromeLeft = Math.Max(0, width - CHROME_W);
            bool chromeShown = !videoFullScreen && (!chromeHidden || chromeRevealed);
            int captionLeft = chromeShown ? chromeLeft : Math.Max(0, width - 16);
            topStrip.SetBounds(0, 0, width, STRIP_HEIGHT);
            modeHint.SetBounds(Math.Max(0, captionLeft - 210), 0, 196, STRIP_HEIGHT);
            stripTitle.SetBounds(14, 0, Math.Max(60, captionLeft - 224), STRIP_HEIGHT);

            if (modeToast != null)
            {
                int toastWidth = Math.Min(520, Math.Max(240, width - 40));
                modeToast.SetBounds((width - toastWidth) / 2, contentTop + 18, toastWidth, 38);
            }

            if (chromePanel != null)
            {
                chromePanel.SetBounds(chromeLeft, 0, CHROME_W, CHROME_H);
                LayoutChrome();
                UpdateChromeVisibility();
                UpdateChromeGlyphs();
                chromePanel.BringToFront();
            }

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
            shortcutHint.Text = "Alt+F layar penuh tanpa taskbar / pulihkan  ·  Alt+H tampil-sembunyi tombol jendela  ·  F5 muat ulang  ·  Ctrl+L ganti alamat  ·  Alt+F4 tutup";
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
            RaiseChrome();
        }

        private void HidePanel()
        {
            connectPanel.Visible = false;
            webView.BringToFront();
            RaiseChrome();
        }

        // Panel koneksi dan WebView menutupi seluruh klien, jadi tombol jendela
        // perlu selalu didahulukan setiap salah satunya dinaikkan.
        private void RaiseChrome()
        {
            if (chromePanel != null) chromePanel.BringToFront();
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
            ShowModeToast(DescribeMode());
        }

        private string DescribeMode()
        {
            return appFullScreen
                ? "Layar penuh  ·  Alt+F kembali ke jendela"
                : "Mode jendela  ·  Alt+F layar penuh  ·  seret strip atas untuk memindahkan";
        }

        private void ApplyWindowMode()
        {
            applyingBounds = true;
            try
            {
                if (InFullScreen)
                {
                    WindowState = FormWindowState.Normal;
                    Rectangle screen = Screen.FromControl(this).Bounds;
                    Bounds = screen;
                    // Tugas (taskbar) hanya mau mundur kalau shell diberitahu bahwa batas
                    // jendela berubah; tanpa SWP_FRAMECHANGED jendela tetap kalah oleh taskbar.
                    try
                    {
                        SetWindowPos(Handle, IntPtr.Zero, screen.Left, screen.Top,
                            screen.Width, screen.Height, SWP_NOZORDER | SWP_FRAMECHANGED | SWP_NOACTIVATE);
                    }
                    catch { }
                }
                else
                {
                    WindowState = FormWindowState.Normal;
                    windowedBounds = ClampToScreen(windowedBounds);
                    Bounds = windowedBounds;
                }
            }
            finally
            {
                applyingBounds = false;
            }
            // Strip hanya diperlukan saat berjendela; layar penuh memakai seluruh piksel.
            topStrip.Visible = !InFullScreen;
            modeHint.Text = appFullScreen ? "Alt+F jendela" : "Alt+F layar penuh";
            UpdateChromeVisibility();
            Relayout();
        }

        private void RecordWindowedBounds()
        {
            if (applyingBounds || InFullScreen) return;
            if (WindowState != FormWindowState.Normal) return;
            windowedBounds = Bounds;
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

        // FormBorderStyle.None menghilangkan bingkai, tetapi WS_THICKFRAME needed
        // supaya Windows mau menjalankan proses resize hasil dari WM_NCHITTEST di bawah.
        protected override CreateParams CreateParams
        {
            get
            {
                CreateParams cp = base.CreateParams;
                cp.Style |= WS_THICKFRAME;
                return cp;
            }
        }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == WM_HOTKEY && (int)m.WParam == HOTKEY_ALT_F)
            {
                ToggleAppFullScreen();
                return;
            }
            if (m.Msg == WM_HOTKEY && (int)m.WParam == HOTKEY_ALT_H)
            {
                ToggleChromeHidden();
                return;
            }
            if (m.Msg == WM_ACTIVATEAPP)
            {
                // Hotkey hanya dipakai selama jendela ini aktif di foreground.
                try
                {
                    if (m.WParam == IntPtr.Zero)
                    {
                        UnregisterHotKey(Handle, HOTKEY_ALT_F);
                        UnregisterHotKey(Handle, HOTKEY_ALT_H);
                    }
                    else
                    {
                        RegisterHotKey(Handle, HOTKEY_ALT_F, MOD_ALT, VK_F);
                        RegisterHotKey(Handle, HOTKEY_ALT_H, MOD_ALT, VK_H);
                    }
                }
                catch { }
            }
            if (m.Msg == WM_NCHITTEST && !InFullScreen && WindowState == FormWindowState.Normal)
            {
                int grip = ResizeGripRegion(m.LParam);
                if (grip != 0)
                {
                    m.Result = (IntPtr)grip;
                    return;
                }
            }
            base.WndProc(ref m);
        }

        // Jendela ini tidak punya bingkai, jadi Windows tidak tahu di mana batas resize.
        // Sembilan piksel terakhir dilaporkan sebagai tepi; titik lain dibiarkan
        // ditangani default supaya strip judul tetap bisa diseret.
        private int ResizeGripRegion(IntPtr lParam)
        {
            int x = (short)((int)lParam & 0xFFFF);
            int y = (short)(((int)lParam >> 16) & 0xFFFF);
            Rectangle edge = RectangleToScreen(ClientRectangle);
            bool left = x <= edge.Left + GRIP_WIDTH;
            bool right = x >= edge.Right - GRIP_WIDTH;
            bool top = y <= edge.Top + GRIP_WIDTH;
            bool bottom = y >= edge.Bottom - GRIP_WIDTH;
            if (left && top) return HTTOPLEFT;
            if (right && top) return HTTOPRIGHT;
            if (left && bottom) return HTBOTTOMLEFT;
            if (right && bottom) return HTBOTTOMRIGHT;
            if (left) return HTLEFT;
            if (right) return HTRIGHT;
            if (top) return HTTOP;
            if (bottom) return HTBOTTOM;
            return 0;
        }

        private void OnFormKeyDown(object sender, KeyEventArgs e)
        {
            if (e.Alt && e.KeyCode == Keys.F)
            {
                e.SuppressKeyPress = true;
                ToggleAppFullScreen();
                return;
            }
            if (e.Alt && e.KeyCode == Keys.H)
            {
                e.SuppressKeyPress = true;
                ToggleChromeHidden();
                return;
            }
            if (e.Alt && e.KeyCode == Keys.F4)
            {
                e.SuppressKeyPress = true;
                Close();
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

        private bool ReadChromeHiddenPreference()
        {
            string raw;
            if (!ReadConfig().TryGetValue("chromehidden", out raw)) return false;
            return raw.Trim() == "1";
        }

        private void SaveWindowSettings()
        {
            try
            {
                RecordWindowedBounds();
                Directory.CreateDirectory(appDataDir);
                Dictionary<string, string> map = ReadConfig();
                map["url"] = currentUrl;
                map["fullscreen"] = appFullScreen ? "1" : "0";
                map["chromehidden"] = chromeHidden ? "1" : "0";
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
