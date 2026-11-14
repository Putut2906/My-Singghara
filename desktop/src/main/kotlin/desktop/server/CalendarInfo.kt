package desktop.server

// Salinan dari HomeScreen.kt (Android): kedua helper ini ikut tertanam di berkas layar Compose
// sehingga tidak bisa dipakai bersama lewat Gradle. Isinya murni aritmetika kalender.
// Tahap 2: pindahkan ke berkas sendiri di app/ supaya cukup satu sumber.

data class JawaDate(val dina: String, val pasaran: String, val wuku: String)

object JavaneseCalendarHelper {
    private val DINA_JAWA = listOf("Ahad", "Senen", "Selasa", "Rebo", "Kemis", "Jemuwah", "Setu")
    private val PASARAN = listOf("Legi", "Pahing", "Pon", "Wage", "Kliwon")
    private val WUKU = listOf(
        "Sinta", "Landep", "Wukir", "Kurantil", "Tolu", "Gumbreg", "Warigalit",
        "Warigagung", "Julungwangi", "Sungsang", "Galungan", "Kuningan", "Langkir",
        "Mandhasiya", "Julungpujut", "Pahang", "Kuruwelut", "Marakeh", "Tambir",
        "Medhangkungan", "Maktal", "Wuye", "Manail", "Prangbakat", "Bala", "Wugu",
        "Wayang", "Kulawu", "Dukut", "Watugunung"
    )

    fun calculateJawa(year: Int, month: Int, day: Int): JawaDate {
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = y / 100
        val b = a / 4
        val c = 2 - a + b
        val e = (365.25 * (y + 4716)).toLong()
        val f = (30.6001 * (m + 1)).toLong()
        val jdn = c + day + e + f - 1524

        val pasaranIdx = ((jdn % 5 + 5) % 5).toInt()
        val dinaIdx = (((jdn + 1) % 7 + 7) % 7).toInt()
        val wukuIdx = (((jdn + 2) / 7) % 30 + 30).toInt() % 30

        return JawaDate(DINA_JAWA[dinaIdx], PASARAN[pasaranIdx], WUKU[wukuIdx])
    }
}

object HijriCalendarHelper {
    private val MONTHS = listOf(
        "Muharram", "Safar", "Rabi'ul Awwal", "Rabi'ul Akhir",
        "Jumadil Awwal", "Jumadil Akhir", "Rajab", "Sya'ban",
        "Ramadhan", "Syawwal", "Dzulqa'dah", "Dzulhijjah"
    )

    fun getHijriDate(year: Int, month: Int, day: Int, offsetDays: Int = -1): String {
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = y / 100
        val b = a / 4
        val c = 2 - a + b
        val e = (365.25 * (y + 4716)).toLong()
        val f = (30.6001 * (m + 1)).toLong()

        val jd = c + day + e + f - 1524 + offsetDays

        val days = jd - 1948439
        val cycle = days / 10631
        val cycleRemainder = days % 10631

        val yearInCycle = ((30 * cycleRemainder + 11) / 10631).toInt()
        val hYear = cycle * 30 + yearInCycle + 1

        val dayOfYearInCycle = cycleRemainder - ((yearInCycle * 10631 + 14) / 30)
        val hMonthNum = ((dayOfYearInCycle * 2) / 59).toInt() + 1
        val hDay = (dayOfYearInCycle - ((hMonthNum - 1) * 59 + 1) / 2).toInt() + 1

        val finalMonthIdx = (hMonthNum - 1).coerceIn(0, 11)
        val finalDay = hDay.coerceAtLeast(1)

        return "$finalDay ${MONTHS[finalMonthIdx]} $hYear H"
    }
}
