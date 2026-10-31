package com.example.data.arisan

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class ArisanRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("arisan_keluarga_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(ArisanDataState::class.java)

    private val _dataState = MutableStateFlow(loadInitialData())
    val dataState: StateFlow<ArisanDataState> = _dataState.asStateFlow()

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "data_state_json") {
            reloadFromPrefs()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadInitialData(): ArisanDataState {
        val purged = prefs.getBoolean("demo_data_purged_v2", false)
        if (!purged) {
            prefs.edit().putBoolean("demo_data_purged_v2", true).remove("data_state_json").apply()
            return createDefaultSeedData()
        }

        val rawJson = prefs.getString("data_state_json", null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val parsed = adapter.fromJson(rawJson)
                if (parsed != null) {
                    return parsed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return createDefaultSeedData()
    }

    private fun createDefaultSeedData(): ArisanDataState {
        val config = ArisanConfig(
            namaArisan = "Arisan Keluarga besar singghara",
            periodeAktif = 1,
            iuranPokokPerSlot = 100000.0,
            iuranKasPerSlot = 20000.0,
            jadwalPertemuanBerikutnya = "",
            waktuPertemuanBerikutnya = "",
            lokasiPertemuanBerikutnya = "",
            namaKetuaArisan = "",
            namaBendahara = "",
            catatanPertemuan = ""
        )

        val state = ArisanDataState(
            config = config,
            anggotaList = emptyList(),
            pembayaranList = emptyList(),
            kasSosialList = emptyList(),
            riwayatPemenangList = emptyList(),
            siklusKe = 1
        )

        saveToPrefs(state)
        return state
    }

    private fun saveToPrefs(state: ArisanDataState) {
        scope.launch {
            try {
                val json = adapter.toJson(state)
                prefs.edit().putString("data_state_json", json).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateState(transform: (ArisanDataState) -> ArisanDataState) {
        val newState = transform(_dataState.value)
        _dataState.value = newState
        saveToPrefs(newState)
    }

    // Member Management
    fun tambahAnggota(
        nama: String,
        hubunganKeluarga: String,
        nomorWa: String,
        jumlahSlot: Int,
        catatan: String
    ) {
        val newMember = AnggotaArisan(
            nama = nama.trim(),
            hubunganKeluarga = hubunganKeluarga.trim(),
            nomorWa = nomorWa.trim(),
            jumlahSlot = if (jumlahSlot < 1) 1 else jumlahSlot,
            sudahMenang = false,
            catatan = catatan.trim()
        )
        updateState { current ->
            val updatedMembers = current.anggotaList + newMember
            // Sync active period payment
            val newPayment = PembayaranIuran(
                periode = current.config.periodeAktif,
                anggotaId = newMember.id,
                namaAnggota = newMember.nama,
                jumlahSlot = newMember.jumlahSlot,
                iuranPokok = newMember.jumlahSlot * current.config.iuranPokokPerSlot,
                iuranKas = newMember.jumlahSlot * current.config.iuranKasPerSlot,
                totalTagihan = newMember.jumlahSlot * (current.config.iuranPokokPerSlot + current.config.iuranKasPerSlot),
                isLunas = false
            )
            current.copy(
                anggotaList = updatedMembers,
                pembayaranList = current.pembayaranList + newPayment
            )
        }
    }

    fun updateAnggota(updated: AnggotaArisan) {
        updateState { current ->
            val updatedMembers = current.anggotaList.map {
                if (it.id == updated.id) updated else it
            }
            val updatedPayments = current.pembayaranList.map { p ->
                if (p.anggotaId == updated.id) {
                    val totalTagihan = updated.jumlahSlot * (current.config.iuranPokokPerSlot + current.config.iuranKasPerSlot)
                    p.copy(
                        namaAnggota = updated.nama,
                        jumlahSlot = updated.jumlahSlot,
                        iuranPokok = updated.jumlahSlot * current.config.iuranPokokPerSlot,
                        iuranKas = updated.jumlahSlot * current.config.iuranKasPerSlot,
                        totalTagihan = totalTagihan
                    )
                } else p
            }
            current.copy(anggotaList = updatedMembers, pembayaranList = updatedPayments)
        }
    }

    fun hapusAnggota(id: String) {
        updateState { current ->
            current.copy(
                anggotaList = current.anggotaList.filter { it.id != id },
                pembayaranList = current.pembayaranList.filter { it.anggotaId != id }
            )
        }
    }

    // Config Update
    fun updateConfig(config: ArisanConfig) {
        updateState { current ->
            // Recalculate payment dues if prices changed
            val updatedPayments = current.pembayaranList.map { p ->
                val total = p.jumlahSlot * (config.iuranPokokPerSlot + config.iuranKasPerSlot)
                p.copy(
                    iuranPokok = p.jumlahSlot * config.iuranPokokPerSlot,
                    iuranKas = p.jumlahSlot * config.iuranKasPerSlot,
                    totalTagihan = total
                )
            }
            current.copy(config = config, pembayaranList = updatedPayments)
        }
    }

    // Payments per period
    fun syncPembayaranPeriode(periode: Int) {
        updateState { current ->
            val existing = current.pembayaranList.filter { it.periode == periode }.associateBy { it.anggotaId }
            val newPayments = current.anggotaList.map { member ->
                existing[member.id] ?: PembayaranIuran(
                    periode = periode,
                    anggotaId = member.id,
                    namaAnggota = member.nama,
                    jumlahSlot = member.jumlahSlot,
                    iuranPokok = member.jumlahSlot * current.config.iuranPokokPerSlot,
                    iuranKas = member.jumlahSlot * current.config.iuranKasPerSlot,
                    totalTagihan = member.jumlahSlot * (current.config.iuranPokokPerSlot + current.config.iuranKasPerSlot),
                    isLunas = false
                )
            }
            val others = current.pembayaranList.filter { it.periode != periode }
            current.copy(pembayaranList = others + newPayments)
        }
    }

    fun setPembayaranStatus(
        pembayaranId: String,
        isLunas: Boolean,
        metodePembayaran: String = "Tunai",
        tanggalBayar: String = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
    ) {
        updateState { current ->
            val updated = current.pembayaranList.map { p ->
                if (p.id == pembayaranId) {
                    p.copy(
                        isLunas = isLunas,
                        metodePembayaran = metodePembayaran,
                        tanggalBayar = if (isLunas) tanggalBayar else ""
                    )
                } else p
            }
            current.copy(pembayaranList = updated)
        }
    }

    fun tandaiSemuaLunas(periode: Int, metode: String = "Tunai") {
        val today = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
        updateState { current ->
            val updated = current.pembayaranList.map { p ->
                if (p.periode == periode) {
                    p.copy(isLunas = true, metodePembayaran = metode, tanggalBayar = today)
                } else p
            }
            current.copy(pembayaranList = updated)
        }
    }

    // Lottery / Kocokan
    fun dapatkanKandidatBelumMenang(): List<AnggotaArisan> {
        return _dataState.value.anggotaList.filter { !it.sudahMenang }
    }

    fun tetapkanPemenang(
        anggota: AnggotaArisan,
        nominalTarikan: Double,
        tanggalPengundian: String = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date()),
        catatan: String = ""
    ): RiwayatPemenangArisan {
        val current = _dataState.value
        val receiptNumber = "KWT-ARS-${current.config.periodeAktif.toString().padStart(3, '0')}-${Random.nextInt(1000, 9999)}"
        val riwayat = RiwayatPemenangArisan(
            periode = current.config.periodeAktif,
            tanggalPengundian = tanggalPengundian,
            anggotaId = anggota.id,
            namaPemenang = anggota.nama,
            hubunganKeluarga = anggota.hubunganKeluarga,
            nomorWa = anggota.nomorWa,
            slotKe = 1,
            nominalTarikan = nominalTarikan,
            nomorKuitansi = receiptNumber,
            catatan = catatan.ifBlank { "Penyerahan uang tarikan arisan periode putaran ke-${current.config.periodeAktif}" }
        )

        val nextPeriod = current.config.periodeAktif + 1

        updateState { curr ->
            val updatedMembers = curr.anggotaList.map { m ->
                if (m.id == anggota.id) {
                    m.copy(sudahMenang = true, periodeMenang = curr.config.periodeAktif, tanggalMenang = tanggalPengundian)
                } else m
            }
            val updatedConfig = curr.config.copy(periodeAktif = nextPeriod)
            val updatedRiwayat = listOf(riwayat) + curr.riwayatPemenangList

            // Also automatically prepare next period payments for all members
            val nextPeriodPayments = updatedMembers.map { m ->
                PembayaranIuran(
                    periode = nextPeriod,
                    anggotaId = m.id,
                    namaAnggota = m.nama,
                    jumlahSlot = m.jumlahSlot,
                    iuranPokok = m.jumlahSlot * updatedConfig.iuranPokokPerSlot,
                    iuranKas = m.jumlahSlot * updatedConfig.iuranKasPerSlot,
                    totalTagihan = m.jumlahSlot * (updatedConfig.iuranPokokPerSlot + updatedConfig.iuranKasPerSlot),
                    isLunas = false
                )
            }

            curr.copy(
                config = updatedConfig,
                anggotaList = updatedMembers,
                pembayaranList = curr.pembayaranList + nextPeriodPayments,
                riwayatPemenangList = updatedRiwayat
            )
        }
        return riwayat
    }

    fun resetSiklusArisan() {
        updateState { current ->
            val resetMembers = current.anggotaList.map {
                it.copy(sudahMenang = false, periodeMenang = null, tanggalMenang = null)
            }
            val newConfig = current.config.copy(periodeAktif = 1)
            val nextSiklus = current.siklusKe + 1

            // Prepare payments for cycle's period 1
            val paymentsPeriod1 = resetMembers.map { m ->
                PembayaranIuran(
                    periode = 1,
                    anggotaId = m.id,
                    namaAnggota = m.nama,
                    jumlahSlot = m.jumlahSlot,
                    iuranPokok = m.jumlahSlot * newConfig.iuranPokokPerSlot,
                    iuranKas = m.jumlahSlot * newConfig.iuranKasPerSlot,
                    totalTagihan = m.jumlahSlot * (newConfig.iuranPokokPerSlot + newConfig.iuranKasPerSlot),
                    isLunas = false
                )
            }

            current.copy(
                config = newConfig,
                anggotaList = resetMembers,
                pembayaranList = paymentsPeriod1,
                siklusKe = nextSiklus
            )
        }
    }

    // Social Cash Ledger
    fun tambahKasSosial(
        tanggal: String,
        jenis: String,
        kategori: String,
        deskripsi: String,
        nominal: Double
    ) {
        val entry = KasSosialArisan(
            tanggal = tanggal,
            jenis = jenis,
            kategori = kategori,
            deskripsi = deskripsi.trim(),
            nominal = nominal
        )
        updateState { current ->
            current.copy(kasSosialList = listOf(entry) + current.kasSosialList)
        }
    }

    fun hapusKasSosial(id: String) {
        updateState { current ->
            current.copy(kasSosialList = current.kasSosialList.filter { it.id != id })
        }
    }

    fun hapusRiwayatPemenang(id: String) {
        updateState { current ->
            val item = current.riwayatPemenangList.find { it.id == id }
            val updatedMembers = if (item != null) {
                current.anggotaList.map { m ->
                    if (m.id == item.anggotaId && m.periodeMenang == item.periode) {
                        m.copy(sudahMenang = false, periodeMenang = null, tanggalMenang = null)
                    } else m
                }
            } else current.anggotaList

            current.copy(
                riwayatPemenangList = current.riwayatPemenangList.filter { it.id != id },
                anggotaList = updatedMembers
            )
        }
    }

    // Reset All Data to Default
    fun resetDataDefault() {
        val defaultState = createDefaultSeedData()
        _dataState.value = defaultState
        saveToPrefs(defaultState)
    }

    // Reload from SharedPreferences (called when Web Connect updates data)
    fun reloadFromPrefs() {
        val rawJson = prefs.getString("data_state_json", null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val parsed = adapter.fromJson(rawJson)
                if (parsed != null) {
                    _dataState.value = parsed
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _dataState.value = createDefaultSeedData()
    }
}
