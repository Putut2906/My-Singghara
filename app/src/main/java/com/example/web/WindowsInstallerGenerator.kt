package com.example.web

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generator untuk executable binary Windows PE (.exe) mandiri 100% offline.
 * Menghasilkan installer executable Windows native (PE32) yang dapat langsung di-double-click di PC Windows
 * untuk otomatis mendaftarkan domain mysingghara.com, flush DNS, konfigurasi port proxy,
 * dan membuat shortcut Desktop "My Singghara" lengkap dengan ikon bulat resmi.
 */
object WindowsInstallerGenerator {

    /**
     * Membuat file binary PE32 (.exe) yang telah diisi IP dan Port aktif smartphone.
     */
    fun generateInstallerExe(targetIp: String, targetPort: Int): ByteArray {
        val psScript = buildString {
            append("\$targetIp = '$targetIp'\r\n")
            append("\$targetPort = '$targetPort'\r\n")
            append("\$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)\r\n")
            append("if (-not \$isAdmin) {\r\n")
            append("    Start-Process powershell.exe -ArgumentList \"-ExecutionPolicy Bypass -NoProfile -WindowStyle Hidden -EncodedCommand \$env:MY_CMD\" -Verb RunAs\r\n")
            append("    exit\r\n")
            append("}\r\n")
            append("\$hosts = \"\$env:SystemRoot\\System32\\drivers\\etc\\hosts\"\r\n")
            append("try {\r\n")
            append("    if (Test-Path \$hosts) {\r\n")
            append("        \$lines = (Get-Content \$hosts -ErrorAction SilentlyContinue) | Where-Object { \$_ -notmatch 'mysingghara\\.com' }\r\n")
            append("        \$lines += \"\$targetIp`tmysingghara.com\"\r\n")
            append("        Set-Content -Path \$hosts -Value \$lines -Force -ErrorAction SilentlyContinue\r\n")
            append("    }\r\n")
            append("} catch {}\r\n")
            append("Start-Process ipconfig.exe -ArgumentList '/flushdns' -WindowStyle Hidden -Wait -ErrorAction SilentlyContinue\r\n")
            append("try {\r\n")
            append("    Start-Process netsh.exe -ArgumentList \"interface portproxy delete v4tov4 listenport=80 listenaddress=0.0.0.0\" -WindowStyle Hidden -Wait -ErrorAction SilentlyContinue\r\n")
            append("    Start-Process netsh.exe -ArgumentList \"interface portproxy add v4tov4 listenport=80 listenaddress=0.0.0.0 connectport=\$targetPort connectaddress=\$targetIp\" -WindowStyle Hidden -Wait -ErrorAction SilentlyContinue\r\n")
            append("} catch {}\r\n")
            append("\$appDir = \"\$env:ProgramData\\MySingghara\"\r\n")
            append("if (-not (Test-Path \$appDir)) { New-Item -ItemType Directory -Path \$appDir -Force | Out-Null }\r\n")
            append("\$icoPath = \"\$appDir\\favicon.ico\"\r\n")
            append("try {\r\n")
            append("    (New-Object System.Net.WebClient).DownloadFile(\"http://\$targetIp`:\$targetPort/favicon.ico\", \$icoPath)\r\n")
            append("} catch {}\r\n")
            append("\$browserPath = ''\r\n")
            append("\$edgeCandidates = @(\r\n")
            append("    \"\$env:ProgramFiles(x86)\\Microsoft\\Edge\\Application\\msedge.exe\",\r\n")
            append("    \"\$env:ProgramFiles\\Microsoft\\Edge\\Application\\msedge.exe\",\r\n")
            append("    \"\$env:ProgramFiles\\Google\\Chrome\\Application\\chrome.exe\",\r\n")
            append("    \"\$env:ProgramFiles(x86)\\Google\\Chrome\\Application\\chrome.exe\",\r\n")
            append("    \"\$env:LOCALAPPDATA\\Google\\Chrome\\Application\\chrome.exe\",\r\n")
            append("    \"\$env:ProgramFiles\\BraveSoftware\\Brave-Browser\\Application\\brave.exe\"\r\n")
            append(")\r\n")
            append("foreach (\$p in \$edgeCandidates) { if (Test-Path \$p) { \$browserPath = \$p; break } }\r\n")
            append("try {\r\n")
            append("    \$wsh = New-Object -ComObject WScript.Shell\r\n")
            append("    \$dt = [Environment]::GetFolderPath('Desktop')\r\n")
            append("    \$sc = \$wsh.CreateShortcut(\"\$dt\\My Singghara (PWA).lnk\")\r\n")
            append("    if (\$browserPath) {\r\n")
            append("        \$sc.TargetPath = \$browserPath\r\n")
            append("        \$sc.Arguments = \"--app=http://mysingghara.com`:\$targetPort\"\r\n")
            append("    } else {\r\n")
            append("        \$sc.TargetPath = \"http://mysingghara.com`:\$targetPort\"\r\n")
            append("    }\r\n")
            append("    if (Test-Path \$icoPath) { \$sc.IconLocation = \"\$icoPath,0\" }\r\n")
            append("    \$sc.Description = \"My Singghara - Progressive Web App (PWA) 100% Offline\"\r\n")
            append("    \$sc.Save()\r\n")
            append("    \$sm = [Environment]::GetFolderPath('CommonPrograms')\r\n")
            append("    \$sc2 = \$wsh.CreateShortcut(\"\$sm\\My Singghara (PWA).lnk\")\r\n")
            append("    if (\$browserPath) {\r\n")
            append("        \$sc2.TargetPath = \$browserPath\r\n")
            append("        \$sc2.Arguments = \"--app=http://mysingghara.com`:\$targetPort\"\r\n")
            append("    } else {\r\n")
            append("        \$sc2.TargetPath = \"http://mysingghara.com`:\$targetPort\"\r\n")
            append("    }\r\n")
            append("    if (Test-Path \$icoPath) { \$sc2.IconLocation = \"\$icoPath,0\" }\r\n")
            append("    \$sc2.Description = \"My Singghara Progressive Web App (PWA)\"\r\n")
            append("    \$sc2.Save()\r\n")
            append("} catch {}\r\n")
            append("if (\$browserPath) {\r\n")
            append("    Start-Process \$browserPath -ArgumentList \"--app=http://mysingghara.com`:\$targetPort\"\r\n")
            append("} else {\r\n")
            append("    Start-Process \"http://mysingghara.com`:\$targetPort\"\r\n")
            append("}\r\n")
            append("try {\r\n")
            append("    Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue\r\n")
            append("    [System.Windows.Forms.MessageBox]::Show(\"Progressive Web App (PWA) My Singghara Berhasil Terpasang di PC!`n`nMode: Aplikasi Standalone (PWA) 100% Offline`nDomain: http://mysingghara.com`nShortcut 'My Singghara (PWA)' telah aktif di Desktop PC.\", \"My Singghara - PWA Installer PC\", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null\r\n")
            append("} catch {}\r\n")
        }

        val utf16Le = psScript.toByteArray(Charsets.UTF_16LE)
        val b64 = Base64.encodeToString(utf16Le, Base64.NO_WRAP)
        val cmd = "powershell.exe -ExecutionPolicy Bypass -NoProfile -WindowStyle Hidden -Command \"\$env:MY_CMD='$b64'; [ScriptBlock]::Create([Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('$b64'))).Invoke()\""

        return buildPeBinary(cmd)
    }

    private fun buildPeBinary(cmdString: String): ByteArray {
        val cmdBytes = (cmdString + "\u0000").toByteArray(Charsets.US_ASCII)

        val imageBase = 0x00400000
        val secAlign = 0x1000
        val fileAlign = 0x200
        val fileHdrSize = 0x400

        val rvaIat = 0x2000
        val rvaIlt = 0x2010
        val rvaIdt = 0x2020
        val rvaDllName = 0x2050
        val rvaHintWinexec = 0x2060
        val rvaHintExit = 0x2070
        val rvaCmd = 0x2080

        val minRdataLen = 0x80 + cmdBytes.size
        val rdataRawSize = ((minRdataLen + fileAlign - 1) / fileAlign) * fileAlign
        val rdataBuf = ByteBuffer.allocate(rdataRawSize).order(ByteOrder.LITTLE_ENDIAN)

        // IAT at 0x00
        rdataBuf.putInt(0x00, rvaHintWinexec)
        rdataBuf.putInt(0x04, rvaHintExit)

        // ILT at 0x10
        rdataBuf.putInt(0x10, rvaHintWinexec)
        rdataBuf.putInt(0x14, rvaHintExit)

        // IDT at 0x20
        rdataBuf.putInt(0x20, rvaIlt)
        rdataBuf.putInt(0x24, 0)
        rdataBuf.putInt(0x28, 0)
        rdataBuf.putInt(0x2C, rvaDllName)
        rdataBuf.putInt(0x30, rvaIat)

        // DLL name at 0x50
        val dllNameBytes = "KERNEL32.dll\u0000".toByteArray(Charsets.US_ASCII)
        rdataBuf.position(0x50)
        rdataBuf.put(dllNameBytes)

        // WinExec hint at 0x60
        rdataBuf.position(0x60)
        rdataBuf.putShort(0.toShort())
        rdataBuf.put("WinExec\u0000".toByteArray(Charsets.US_ASCII))

        // ExitProcess hint at 0x70
        rdataBuf.position(0x70)
        rdataBuf.putShort(0.toShort())
        rdataBuf.put("ExitProcess\u0000".toByteArray(Charsets.US_ASCII))

        // Cmd at 0x80
        rdataBuf.position(0x80)
        rdataBuf.put(cmdBytes)

        val rdataBytes = rdataBuf.array()

        // .text section (512 bytes)
        val textBuf = ByteBuffer.allocate(0x200).order(ByteOrder.LITTLE_ENDIAN)
        // push 1 (SW_SHOWNORMAL) -> 6A 01
        textBuf.put(0x6A.toByte())
        textBuf.put(0x01.toByte())
        // push (imageBase + rvaCmd) -> 68 [cmd_addr]
        textBuf.put(0x68.toByte())
        textBuf.putInt(imageBase + rvaCmd)
        // call [imageBase + rvaIat] -> FF 15 [iat_addr]
        textBuf.put(0xFF.toByte())
        textBuf.put(0x15.toByte())
        textBuf.putInt(imageBase + rvaIat)
        // push 0 -> 6A 00
        textBuf.put(0x6A.toByte())
        textBuf.put(0x00.toByte())
        // call [imageBase + rvaIat + 4] -> FF 15 [exit_addr]
        textBuf.put(0xFF.toByte())
        textBuf.put(0x15.toByte())
        textBuf.putInt(imageBase + rvaIat + 4)
        // ret -> C3
        textBuf.put(0xC3.toByte())

        val codeLen = textBuf.position()
        val textBytes = textBuf.array()
        val textRawSize = textBytes.size

        val sizeOfImage = 0x1000 + 0x1000 + ((rdataRawSize + secAlign - 1) / secAlign) * secAlign

        // Headers buffer (1024 bytes)
        val hdrBuf = ByteBuffer.allocate(fileHdrSize).order(ByteOrder.LITTLE_ENDIAN)

        // 1. DOS Header (128 bytes)
        hdrBuf.put('M'.code.toByte())
        hdrBuf.put('Z'.code.toByte())
        hdrBuf.position(0x3C)
        hdrBuf.putInt(0x80) // e_lfanew
        hdrBuf.position(0x80)

        // 2. PE Signature
        hdrBuf.put('P'.code.toByte())
        hdrBuf.put('E'.code.toByte())
        hdrBuf.put(0.toByte())
        hdrBuf.put(0.toByte())

        // 3. COFF File Header (20 bytes)
        hdrBuf.putShort(0x014C.toShort()) // Machine i386
        hdrBuf.putShort(2.toShort())      // 2 sections
        hdrBuf.putInt(0)                  // TimeDateStamp
        hdrBuf.putInt(0)                  // PtrToSymbolTable
        hdrBuf.putInt(0)                  // NumSymbols
        hdrBuf.putShort(224.toShort())    // SizeOfOptionalHeader
        hdrBuf.putShort(0x0102.toShort()) // Characteristics

        // 4. Optional Header (96 bytes standard + 128 bytes data directories = 224 bytes)
        hdrBuf.putShort(0x010B.toShort()) // Magic PE32
        hdrBuf.put(1.toByte())            // MajorLinker
        hdrBuf.put(0.toByte())            // MinorLinker
        hdrBuf.putInt(textRawSize)        // SizeOfCode
        hdrBuf.putInt(rdataRawSize)       // SizeOfInitializedData
        hdrBuf.putInt(0)                  // SizeOfUninitializedData
        hdrBuf.putInt(0x1000)             // AddressOfEntryPoint
        hdrBuf.putInt(0x1000)             // BaseOfCode
        hdrBuf.putInt(0x2000)             // BaseOfData
        hdrBuf.putInt(imageBase)          // ImageBase
        hdrBuf.putInt(secAlign)           // SectionAlignment
        hdrBuf.putInt(fileAlign)          // FileAlignment
        hdrBuf.putShort(4.toShort())      // MajorOS
        hdrBuf.putShort(0.toShort())      // MinorOS
        hdrBuf.putShort(0.toShort())      // MajorImage
        hdrBuf.putShort(0.toShort())      // MinorImage
        hdrBuf.putShort(4.toShort())      // MajorSubsystem
        hdrBuf.putShort(0.toShort())      // MinorSubsystem
        hdrBuf.putInt(0)                  // Win32Version
        hdrBuf.putInt(sizeOfImage)        // SizeOfImage
        hdrBuf.putInt(fileHdrSize)        // SizeOfHeaders
        hdrBuf.putInt(0)                  // CheckSum
        hdrBuf.putShort(2.toShort())      // Subsystem (2 = Windows GUI)
        hdrBuf.putShort(0.toShort())      // DllCharacteristics
        hdrBuf.putInt(0x100000)           // SizeOfStackReserve
        hdrBuf.putInt(0x1000)             // SizeOfStackCommit
        hdrBuf.putInt(0x100000)           // SizeOfHeapReserve
        hdrBuf.putInt(0x1000)             // SizeOfHeapCommit
        hdrBuf.putInt(0)                  // LoaderFlags
        hdrBuf.putInt(16)                 // NumberOfRvaAndSizes

        // Data Directories (16 * 8 = 128 bytes)
        val dataDirsPos = hdrBuf.position()
        // Entry 1: Import Table at offset 8 (RVA, Size)
        hdrBuf.position(dataDirsPos + 8)
        hdrBuf.putInt(rvaIdt)
        hdrBuf.putInt(40) // 2 IDT entries (20 bytes each)

        // Entry 12: IAT at offset 12 * 8 = 96 (RVA, Size)
        hdrBuf.position(dataDirsPos + 96)
        hdrBuf.putInt(rvaIat)
        hdrBuf.putInt(8)

        hdrBuf.position(dataDirsPos + 128)

        // 5. Section Headers (40 bytes each)
        // .text section
        val secTextName = ".text\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        hdrBuf.put(secTextName)
        hdrBuf.putInt(codeLen)            // VirtualSize
        hdrBuf.putInt(0x1000)             // VirtualAddress
        hdrBuf.putInt(textRawSize)        // SizeOfRawData
        hdrBuf.putInt(fileHdrSize)        // PointerToRawData (0x400)
        hdrBuf.putInt(0)                  // PointerToRelocations
        hdrBuf.putInt(0)                  // PointerToLinenumbers
        hdrBuf.putShort(0.toShort())      // NumberOfRelocations
        hdrBuf.putShort(0.toShort())      // NumberOfLinenumbers
        hdrBuf.putInt(0x60000020.toInt()) // Characteristics (Code, Execute, Read)

        // .rdata section
        val secRdataName = ".rdata\u0000\u0000".toByteArray(Charsets.US_ASCII)
        hdrBuf.put(secRdataName)
        hdrBuf.putInt(rdataBytes.size)    // VirtualSize
        hdrBuf.putInt(0x2000)             // VirtualAddress
        hdrBuf.putInt(rdataRawSize)       // SizeOfRawData
        hdrBuf.putInt(fileHdrSize + textRawSize) // PointerToRawData (0x600)
        hdrBuf.putInt(0)
        hdrBuf.putInt(0)
        hdrBuf.putShort(0.toShort())
        hdrBuf.putShort(0.toShort())
        hdrBuf.putInt(0x40000040.toInt()) // Characteristics (Initialized Data, Read)

        val headerBytes = hdrBuf.array()

        val fullPe = ByteArray(fileHdrSize + textRawSize + rdataRawSize)
        System.arraycopy(headerBytes, 0, fullPe, 0, fileHdrSize)
        System.arraycopy(textBytes, 0, fullPe, fileHdrSize, textRawSize)
        System.arraycopy(rdataBytes, 0, fullPe, fileHdrSize + textRawSize, rdataRawSize)

        return fullPe
    }
}
