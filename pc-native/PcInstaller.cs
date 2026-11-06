using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

namespace MySinggharaPcInstaller
{
    // Pemasang aplikasi PC native My Singghara.
    // Satu jendela WinForms (tanpa browser, tanpa terminal): menyalakan berkas
    // aplikasi ke %LOCALAPPDATA%\My Singghara, membuat shortcut, lalu menjalankan.

    static class Program
    {
        [STAThread]
        public static void Main(string[] args)
        {
            Dictionary<string, string> parsed = ParseArgs(args);
            if (parsed.ContainsKey("check"))
            {
                RunCheck(parsed);
                return;
            }
            if (parsed.ContainsKey("uninstall"))
            {
                RunUninstall(parsed.ContainsKey("silent"));
                return;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new InstallerForm(parsed));
        }

        private static Dictionary<string, string> ParseArgs(string[] args)
        {
            Dictionary<string, string> map = new Dictionary<string, string>();
            for (int i = 0; i < args.Length; i++)
            {
                string key = args[i].ToLowerInvariant();
                if (key == "--host" && i + 1 < args.Length) { map["host"] = args[++i]; }
                else if (key == "--port" && i + 1 < args.Length) { map["port"] = args[++i]; }
                else if (key == "--url" && i + 1 < args.Length) { map["url"] = args[++i]; }
                else if (key == "--silent") { map["silent"] = "1"; }
                else if (key == "--uninstall") { map["uninstall"] = "1"; }
                else if (key == "--check") { map["check"] = "1"; }
                else if (key == "--report" && i + 1 < args.Length) { map["report"] = args[++i]; }
            }
            return map;
        }

        // Mode periksa: memastikan paket tertanam terbaca tanpa mengubah sistem.
        public static void RunCheck(Dictionary<string, string> parsed)
        {
            StringBuilder sb = new StringBuilder();
            sb.AppendLine("CHECK MySingghara-Pc-Installer");
            sb.AppendLine("target=" + InstallLayout.TargetDir());
            sb.AppendLine("64bit-os=" + Environment.Is64BitOperatingSystem);
            try
            {
                InstallerForm form = new InstallerForm(parsed);
                sb.AppendLine("preset-url=" + form.PresetUrlForCheck());
            }
            catch (Exception ex)
            {
                sb.AppendLine("preset-url ERROR " + ex.Message);
            }
            try
            {
                string[] entries = InstallLayout.PayloadEntryNames();
                sb.AppendLine("payload-entries=" + entries.Length);
                for (int i = 0; i < entries.Length; i++) sb.AppendLine("  " + entries[i]);
            }
            catch (Exception ex)
            {
                sb.AppendLine("payload ERROR " + ex.Message);
            }
            string text = sb.ToString();
            if (parsed.ContainsKey("report"))
            {
                try { File.WriteAllText(parsed["report"], text, Encoding.UTF8); } catch { }
            }
            else
            {
                MessageBox.Show(text, "My Singghara PC - Check");
            }
        }

        private static void RunUninstall(bool silent)
        {
            try
            {
                InstallLayout.Remove();
                if (!silent)
                {
                    MessageBox.Show("Aplikasi PC My Singghara berhasil dibersihkan.",
                        "My Singghara PC", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            }
            catch (Exception ex)
            {
                if (!silent)
                {
                    MessageBox.Show("Gagal membersihkan: " + ex.Message, "My Singghara PC",
                        MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
        }
    }

    public class InstallerForm : Form
    {
        private const string CONFIG_MARKER = "#MY_SINGGHARA_CFG#";

        private ListBox logBox;
        private Button startButton;
        private Button closeButton;
        private TextBox urlBox;
        private Label stepLabel;
        private Dictionary<string, string> args;
        private bool running;

        public InstallerForm(Dictionary<string, string> parsedArgs)
        {
            args = parsedArgs;

            Text = "Pemasang Aplikasi PC My Singghara";
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(520, 360);
            BackColor = Color.FromArgb(248, 250, 252);
            try { Icon = LoadOwnIcon(); } catch { }

            Label title = new Label();
            title.Text = "My Singghara - Aplikasi PC Native";
            title.Font = new Font("Segoe UI", 13f, FontStyle.Bold);
            title.ForeColor = Color.FromArgb(14, 165, 233);
            title.Location = new Point(20, 16);
            title.AutoSize = true;
            Controls.Add(title);

            Label subtitle = new Label();
            subtitle.Text = "Aplikasi mandiri 100% offline: satu jendela khusus, tanpa browser dan tanpa terminal.";
            subtitle.Font = new Font("Segoe UI", 8.5f);
            subtitle.ForeColor = Color.FromArgb(71, 85, 105);
            subtitle.Location = new Point(22, 42);
            subtitle.AutoSize = true;
            Controls.Add(subtitle);

            Label urlLabel = new Label();
            urlLabel.Text = "Alamat Web Connect HP";
            urlLabel.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
            urlLabel.Location = new Point(20, 72);
            urlLabel.AutoSize = true;
            Controls.Add(urlLabel);

            urlBox = new TextBox();
            urlBox.Location = new Point(22, 92);
            urlBox.Size = new Size(410, 25);
            urlBox.Font = new Font("Segoe UI", 10f);
            urlBox.TextChanged += delegate { startButton.Enabled = !running; };
            Controls.Add(urlBox);

            Label guess = new Label();
            guess.Text = "(IP:Port, lihat halaman Web Connect di HP)";
            guess.Font = new Font("Segoe UI", 8f);
            guess.ForeColor = Color.FromArgb(100, 116, 139);
            guess.Location = new Point(24, 120);
            guess.AutoSize = true;
            Controls.Add(guess);

            startButton = new Button();
            startButton.Text = "Pasang & Jalankan";
            startButton.Location = new Point(436, 90);
            startButton.Size = new Size(72, 30);
            startButton.FlatStyle = FlatStyle.Flat;
            startButton.BackColor = Color.FromArgb(14, 165, 233);
            startButton.ForeColor = Color.White;
            startButton.Enabled = false;
            startButton.Click += delegate { StartInstall(); };
            Controls.Add(startButton);

            stepLabel = new Label();
            stepLabel.Text = "Belum dipasang.";
            stepLabel.Font = new Font("Segoe UI", 9f);
            stepLabel.ForeColor = Color.FromArgb(51, 65, 85);
            stepLabel.Location = new Point(20, 148);
            stepLabel.AutoSize = true;
            Controls.Add(stepLabel);

            logBox = new ListBox();
            logBox.Location = new Point(22, 170);
            logBox.Size = new Size(476, 150);
            logBox.BorderStyle = BorderStyle.None;
            logBox.BackColor = Color.White;
            logBox.Font = new Font("Consolas", 9f);
            logBox.IntegralHeight = false;
            Controls.Add(logBox);

            closeButton = new Button();
            closeButton.Text = "Tutup";
            closeButton.Location = new Point(420, 330);
            closeButton.Size = new Size(78, 24);
            closeButton.Visible = false;
            closeButton.Click += delegate { Close(); };
            Controls.Add(closeButton);

            string preset = ResolvePresetUrl();
            urlBox.Text = preset;
            if (args.ContainsKey("silent"))
            {
                StartInstall();
            }
            else if (!string.IsNullOrEmpty(preset))
            {
                Log("Alamat HP terbaca dari berkas pemasangan: " + preset);
            }
        }

        public string PresetUrlForCheck()
        {
            return urlBox.Text;
        }

        private string ResolvePresetUrl()
        {
            if (args.ContainsKey("url")) return args["url"];
            if (args.ContainsKey("host"))
            {
                string url = "http://" + args["host"];
                if (args.ContainsKey("port")) url += ":" + args["port"];
                return url;
            }
            string embedded = ReadEmbeddedConfig();
            if (!string.IsNullOrEmpty(embedded)) return embedded;
            return "";
        }

        // Berkas .exe yang disajikan aplikasi HP ditambahkan penanda alamat di
        // ekornya, jadi pemasangan cukup double-click tanpa mengetik IP.
        private string ReadEmbeddedConfig()
        {
            try
            {
                string path = Assembly.GetExecutingAssembly().Location;
                FileInfo info = new FileInfo(path);
                long tailSize = Math.Min(info.Length, 65536);
                using (FileStream stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
                {
                    stream.Seek(-tailSize, SeekOrigin.End);
                    byte[] tail = new byte[tailSize];
                    int read = stream.Read(tail, 0, tail.Length);
                    string text = Encoding.ASCII.GetString(tail, 0, read);
                    int index = text.LastIndexOf(CONFIG_MARKER);
                    if (index < 0) return "";
                    string rest = text.Substring(index + CONFIG_MARKER.Length);
                    StringBuilder clean = new StringBuilder();
                    for (int i = 0; i < rest.Length; i++)
                    {
                        char c = rest[i];
                        if (c == '\0' || c == '\r' || c == '\n') break;
                        clean.Append(c);
                    }
                    string raw = clean.ToString();
                    string[] parts = raw.Split(';');
                    string ip = "";
                    string port = "";
                    for (int i = 0; i < parts.Length; i++)
                    {
                        int eq = parts[i].IndexOf('=');
                        if (eq <= 0) continue;
                        string key = parts[i].Substring(0, eq).Trim().ToLowerInvariant();
                        string value = parts[i].Substring(eq + 1).Trim();
                        if (key == "ip") ip = value;
                        else if (key == "port") port = value;
                    }
                    if (ip.Length == 0) return "";
                    if (port.Length == 0) return "http://" + ip;
                    return "http://" + ip + ":" + port;
                }
            }
            catch
            {
                return "";
            }
        }

        private void StartInstall()
        {
            if (running) return;
            string url = urlBox.Text == null ? "" : urlBox.Text.Trim();
            if (url.Length == 0)
            {
                MessageBox.Show("Isi alamat Web Connect HP dulu, contoh 192.168.1.10:8080",
                    "My Singghara PC", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
                return;
            }
            if (url.IndexOf("://") < 0) url = "http://" + url;
            if (!Environment.Is64BitOperatingSystem)
            {
                MessageBox.Show("Aplikasi PC native membutuhkan Windows 64-bit.\n\n" +
                    "Di Windows 32-bit gunakan installer PWA yang ada di halaman Web Connect.",
                    "My Singghara PC", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
                Close();
                return;
            }
            running = true;
            startButton.Enabled = false;
            urlBox.Enabled = false;
            Log("Memasang ke " + InstallLayout.TargetDir());
            Application.DoEvents();
            try
            {
                InstallLayout.ExtractPayload(delegate(string step) { Log(step); });
                InstallLayout.WriteConfig(url);
                InstallLayout.CreateShortcuts();
                InstallLayout.RegisterUninstall();
                stepLabel.Text = "Selesai dipasang.";
                Log("Selesai. Aplikasi dijalankan.");
                InstallLayout.LaunchApp();
            }
            catch (Exception ex)
            {
                stepLabel.Text = "Pemasangan gagal.";
                Log("GAGAL: " + ex.Message);
                MessageBox.Show("Pemasangan gagal:\n\n" + ex.Message, "My Singghara PC",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            running = false;
            closeButton.Visible = true;
            if (args.ContainsKey("silent")) Close();
        }

        private void Log(string message)
        {
            logBox.Items.Add(message);
            logBox.TopIndex = logBox.Items.Count - 1;
            stepLabel.Text = message;
            Application.DoEvents();
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

    // ----------------------------------------------------------------------
    // Lokasi pemasangan & isi paket
    // ----------------------------------------------------------------------

    public static class InstallLayout
    {
        public const string APP_FOLDER = "My Singghara";
        public const string PC_SUBFOLDER = "PC";
        public const string EXE_NAME = "MySinggharaPc.exe";
        public const string SHORTCUT_NAME = "My Singghara PC";

        public static string RootDir()
        {
            return Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                APP_FOLDER);
        }

        public static string TargetDir()
        {
            return Path.Combine(RootDir(), PC_SUBFOLDER);
        }

        public static string AppExe()
        {
            return Path.Combine(TargetDir(), EXE_NAME);
        }

        private const string PAYLOAD_RESOURCE_SUFFIX = ".payload.zip";
        private const string ICON_RESOURCE_SUFFIX = ".ico";

        private static Stream OpenPayloadStream()
        {
            Assembly asm = Assembly.GetExecutingAssembly();
            string[] names = asm.GetManifestResourceNames();
            for (int i = 0; i < names.Length; i++)
            {
                if (names[i].EndsWith(PAYLOAD_RESOURCE_SUFFIX, StringComparison.OrdinalIgnoreCase))
                {
                    return asm.GetManifestResourceStream(names[i]);
                }
            }
            throw new InvalidOperationException("Berkas aplikasi tidak ditemukan di dalam installer.");
        }

        public static string[] PayloadEntryNames()
        {
            using (Stream stream = OpenPayloadStream())
            {
                using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
                {
                    string[] names = new string[archive.Entries.Count];
                    int index = 0;
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        names[index++] = entry.FullName + " = " + entry.Length + " byte";
                    }
                    return names;
                }
            }
        }

        public static void ExtractPayload(Action<string> report)
        {
            string target = TargetDir();
            Directory.CreateDirectory(target);
            using (Stream stream = OpenPayloadStream())
            {
                using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
                {
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        string name = Path.GetFileName(entry.FullName);
                        if (name.Length == 0) continue;
                        string destination = Path.Combine(target, name);
                        if (report != null) report("Menyalin " + name);
                        entry.ExtractToFile(destination, true);
                    }
                }
            }
        }

        public static void WriteConfig(string url)
        {
            Directory.CreateDirectory(RootDir());
            StringBuilder sb = new StringBuilder();
            sb.AppendLine("# Konfigurasi aplikasi PC My Singghara");
            sb.AppendLine("url=" + url);
            sb.AppendLine("window=1280x820");
            File.WriteAllText(Path.Combine(RootDir(), "pc-config.ini"), sb.ToString(), Encoding.UTF8);
        }

        public static void CreateShortcuts()
        {
            string exe = AppExe();
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            if (shellType == null) return;
            object shellObj = Activator.CreateInstance(shellType);
            try
            {
                string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                string programs = Environment.GetFolderPath(Environment.SpecialFolder.StartMenu);
                MakeShortcut(shellObj, Path.Combine(desktop, SHORTCUT_NAME + ".lnk"), exe);
                MakeShortcut(shellObj, Path.Combine(programs, SHORTCUT_NAME + ".lnk"), exe);
            }
            finally
            {
                Marshal.ReleaseComObject(shellObj);
            }
        }

        private static void MakeShortcut(object shell, string linkPath, string exe)
        {
            object link = shell.GetType().InvokeMember("CreateShortcut",
                BindingFlags.InvokeMethod, null, shell, new object[] { linkPath });
            Type linkType = link.GetType();
            linkType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { exe });
            linkType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, link,
                new object[] { Path.GetDirectoryName(exe) });
            linkType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, link,
                new object[] { exe + ",0" });
            linkType.InvokeMember("Description", BindingFlags.SetProperty, null, link,
                new object[] { "My Singghara - aplikasi PC native 100% offline" });
            linkType.InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
        }

        public static void RegisterUninstall()
        {
            try
            {
                string installer = Assembly.GetExecutingAssembly().Location;
                Microsoft.Win32.RegistryKey key = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(
                    @"Software\Microsoft\Windows\CurrentVersion\Uninstall\MySinggharaPc");
                if (key == null) return;
                using (key)
                {
                    key.SetValue("DisplayName", "My Singghara (Aplikasi PC)");
                    key.SetValue("DisplayIcon", AppExe() + ",0");
                    key.SetValue("InstallLocation", TargetDir());
                    key.SetValue("UninstallString", "\"" + installer + "\" --uninstall --silent");
                    key.SetValue("Publisher", "My Singghara");
                }
            }
            catch
            {
                // Entri daftar program hanya kenyamanan, bukan syarat berjalan.
            }
        }

        public static void LaunchApp()
        {
            ProcessStartInfo info = new ProcessStartInfo(AppExe());
            info.WorkingDirectory = TargetDir();
            info.UseShellExecute = true;
            Process.Start(info);
        }

        public static void Remove()
        {
            string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
            string programs = Environment.GetFolderPath(Environment.SpecialFolder.StartMenu);
            TryDelete(Path.Combine(desktop, SHORTCUT_NAME + ".lnk"));
            TryDelete(Path.Combine(programs, SHORTCUT_NAME + ".lnk"));
            Microsoft.Win32.Registry.CurrentUser.DeleteSubKeyTree(
                @"Software\Microsoft\Windows\CurrentVersion\Uninstall\MySinggharaPc", false);
            string webviewData = Path.Combine(RootDir(), "webview2");
            if (Directory.Exists(webviewData)) Directory.Delete(webviewData, true);
            string target = TargetDir();
            if (Directory.Exists(target)) Directory.Delete(target, true);
            TryDelete(Path.Combine(RootDir(), "pc-config.ini"));
        }

        private static void TryDelete(string path)
        {
            try { if (File.Exists(path)) File.Delete(path); } catch { }
        }
    }
}
