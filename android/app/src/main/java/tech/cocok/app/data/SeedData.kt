package tech.cocok.app.data

import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.FitmentGroup
import tech.cocok.app.domain.model.GroupPart
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.Sumber
import tech.cocok.app.domain.model.Vehicle

/**
 * Data contoh untuk pengembangan tampilan.
 *
 * Isinya sengaja mengikuti contoh di PRD.md §1 dan ARCHITECTURE.md §7 supaya
 * setiap skenario bisa dicoba: Hilux Vigo vs Reborn, Avanza antar-generasi,
 * barang pengganti merek lain, dan satu kasus "belum ada data".
 *
 * Saat API Cloudflare siap, kelas ini diganti pembacaan Room; bentuk datanya
 * sudah sama dengan skema D1 (§4.2).
 */
object SeedData {

    // --- Mobil -------------------------------------------------------------
    val vigo = Vehicle("v-vigo", "Toyota", "Hilux", "Vigo", 2005, 2015, mapOf("kabin" to "double cabin"))
    val reborn = Vehicle("v-reborn", "Toyota", "Hilux", "Reborn", 2016, 2020, mapOf("mesin" to "2.4 diesel"))
    val hiluxBaru = Vehicle("v-hilux26", "Toyota", "Hilux", "Rocco", 2021, 2026, mapOf("mesin" to "2.4 diesel"), verified = false)
    val avanzaLama = Vehicle("v-avanza12", "Toyota", "Avanza", "generasi 2", 2012, 2015)
    val avanzaBaru = Vehicle("v-avanza16", "Toyota", "Avanza", "generasi 3", 2016, 2021)
    val fortuner = Vehicle("v-fortuner", "Toyota", "Fortuner", "VRZ", 2016, 2020)

    val kendaraan = listOf(vigo, reborn, hiluxBaru, avanzaLama, avanzaBaru, fortuner)

    // --- Jenis barang ------------------------------------------------------
    val lampuStop = PartCategory("c-stop", "Lampu stop", "belakang")
    val kacaDepan = PartCategory("c-kaca", "Kaca depan")
    val kampasRem = PartCategory("c-kampas", "Kampas rem", "depan")
    val filterOli = PartCategory("c-filter", "Filter oli")
    val lampuDepan = PartCategory("c-lampudpn", "Lampu depan")

    val kategori = listOf(lampuStop, kacaDepan, kampasRem, filterOli, lampuDepan)

    // --- Kelompok barang yang sama ----------------------------------------
    val gStopReborn = FitmentGroup("g-stop-reborn", lampuStop.id, "Lampu stop kiri Hilux Reborn", "Termasuk Fortuner 2016–2020.")
    val gStopVigo = FitmentGroup("g-stop-vigo", lampuStop.id, "Lampu stop kiri Hilux Vigo")
    val gStopAvanza = FitmentGroup("g-stop-avanza", lampuStop.id, "Lampu stop Avanza generasi 3")
    val gKacaReborn = FitmentGroup("g-kaca-reborn", kacaDepan.id, "Kaca depan Hilux Reborn", "Hanya model standar.")
    val gKacaVigo = FitmentGroup("g-kaca-vigo", kacaDepan.id, "Kaca depan Hilux Vigo")
    val gKacaHilux26 = FitmentGroup("g-kaca-26", kacaDepan.id, "Kaca depan Hilux 2021 ke atas")
    val gKampasAvanza = FitmentGroup("g-kampas-avanza", kampasRem.id, "Kampas rem depan Avanza", "Sama untuk generasi 2 dan 3.")
    val gFilterAvanza = FitmentGroup("g-filter-avanza", filterOli.id, "Filter oli Avanza")
    val gLampuDepanReborn = FitmentGroup("g-lampudpn-reborn", lampuDepan.id, "Lampu depan Hilux Reborn")

    val grup = listOf(
        gStopReborn, gStopVigo, gStopAvanza,
        gKacaReborn, gKacaVigo, gKacaHilux26,
        gKampasAvanza, gFilterAvanza, gLampuDepanReborn,
    )

    // --- Barang ------------------------------------------------------------
    val pStopRebornAsli = Part(
        "p-stop-reborn-oem", lampuStop.id, "Lampu stop kiri asli", "Toyota", "81560-0K290", true,
        notes = "Sudah dicek terpasang di Reborn 2018.",
    )
    val pStopRebornDepo = Part(
        "p-stop-reborn-depo", lampuStop.id, "Lampu stop kiri merek lain", "Depo", "DEP-312-19A6L", false,
        notes = "Bentuk sama dengan asli, belum diuji jangka panjang.",
    )
    val pStopVigoAsli = Part("p-stop-vigo-oem", lampuStop.id, "Lampu stop kiri asli", "Toyota", "81560-0K010", true)
    val pStopAvanzaAsli = Part("p-stop-avanza-oem", lampuStop.id, "Lampu stop kiri asli", "Toyota", "81560-BZ150", true)
    val pKacaRebornAsli = Part("p-kaca-reborn", kacaDepan.id, "Kaca depan asli", "Toyota", "56111-0K520", true, notes = "Hanya untuk model standar.")
    val pKacaVigoAsli = Part("p-kaca-vigo", kacaDepan.id, "Kaca depan asli", "Toyota", "56111-0K310", true)
    val pKaca26Asli = Part("p-kaca-26", kacaDepan.id, "Kaca depan asli", "Toyota", "56111-0K700", true)
    val pKampasAvanzaAsli = Part("p-kampas-avanza-oem", kampasRem.id, "Kampas rem depan asli", "Toyota", "04465-BZ040", true)
    val pKampasAvanzaAkebono = Part("p-kampas-avanza-akebono", kampasRem.id, "Kampas rem depan merek lain", "Akebono", "AN-612K", false)
    val pFilterAvanza = Part("p-filter-avanza", filterOli.id, "Filter oli", "Toyota", "90915-YZZD2", true)
    val pLampuDepanReborn = Part("p-lampudpn-reborn", lampuDepan.id, "Lampu depan kiri asli", "Toyota", "81150-0K290", true)
    val pLampuDepanRebornDepo = Part("p-lampudpn-reborn-depo", lampuDepan.id, "Lampu depan kiri merek lain", "Depo", "DEP-212-11PXL", false)

    val parts = listOf(
        pStopRebornAsli, pStopRebornDepo, pStopVigoAsli, pStopAvanzaAsli,
        pKacaRebornAsli, pKacaVigoAsli, pKaca26Asli,
        pKampasAvanzaAsli, pKampasAvanzaAkebono, pFilterAvanza,
        pLampuDepanReborn, pLampuDepanRebornDepo,
    )

    // --- Isi kelompok ------------------------------------------------------
    val isiGrup = listOf(
        GroupPart(gStopReborn.id, pStopRebornAsli.id, Status.PASTI),
        GroupPart(gStopReborn.id, pStopRebornDepo.id, Status.MUNGKIN),
        GroupPart(gStopVigo.id, pStopVigoAsli.id, Status.PASTI),
        GroupPart(gKacaReborn.id, pKacaRebornAsli.id, Status.PASTI),
        GroupPart(gStopAvanza.id, pStopAvanzaAsli.id, Status.MUNGKIN),
        GroupPart(gKacaHilux26.id, pKaca26Asli.id, Status.MUNGKIN),
        GroupPart(gKampasAvanza.id, pKampasAvanzaAsli.id, Status.PASTI),
        GroupPart(gKampasAvanza.id, pKampasAvanzaAkebono.id, Status.PASTI),
        GroupPart(gFilterAvanza.id, pFilterAvanza.id, Status.PASTI),
        GroupPart(gLampuDepanReborn.id, pLampuDepanReborn.id, Status.PASTI),
        GroupPart(gLampuDepanReborn.id, pLampuDepanRebornDepo.id, Status.MUNGKIN),
    )

    // --- Kecocokan --------------------------------------------------------
    val fitments = listOf(
        // Lampu stop Reborn: satu kelompok untuk Reborn + Fortuner (contoh SAMA).
        Fitment("f-1", gStopReborn.id, reborn.id, 2016, 2020, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        Fitment("f-2", gStopReborn.id, fortuner.id, 2016, 2020, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        // Lampu stop Reborn TIDAK cocok di Vigo — dicatat, bukan disimpulkan dari ketiadaan data.
        Fitment("f-3", gStopReborn.id, vigo.id, 2005, 2015, status = Status.TIDAK_COCOK, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK, confirmedAt = "2026-10-02"),
        Fitment("f-4", gStopVigo.id, vigo.id, 2005, 2015, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        // Lampu stop Avanza: baru dari internet -> mungkin, belum diperiksa.
        Fitment("f-5", gStopAvanza.id, avanzaBaru.id, 2016, 2021, status = Status.MUNGKIN, sumber = Sumber.INTERNET),
        // Kaca depan: Vigo vs Reborn beda, keduanya pasti -> BEDA saat dibandingkan.
        Fitment("f-6", gKacaReborn.id, reborn.id, 2016, 2020, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        Fitment("f-7", gKacaVigo.id, vigo.id, 2005, 2015, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        Fitment("f-8", gKacaHilux26.id, hiluxBaru.id, 2021, 2026, status = Status.MUNGKIN, sumber = Sumber.INTERNET),
        // Kampas rem Avanza: satu kelompok lintas generasi -> SAMA saat dibandingkan.
        Fitment("f-9", gKampasAvanza.id, avanzaLama.id, 2012, 2015, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        Fitment("f-10", gKampasAvanza.id, avanzaBaru.id, 2016, 2021, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
        Fitment("f-11", gFilterAvanza.id, avanzaBaru.id, 2016, 2021, status = Status.PASTI, sumber = Sumber.KATALOG, confirmedByRole = Role.PEMILIK),
        Fitment("f-12", gLampuDepanReborn.id, reborn.id, 2016, 2020, status = Status.PASTI, sumber = Sumber.TOKO, confirmedByRole = Role.PEMILIK),
    )

    // --- Sinonim istilah toko ---------------------------------------------
    val sinonim = mapOf(
        "rebon" to "reborn",
        "robon" to "reborn",
        "vigo" to "vigo",
        "avansa" to "avanza",
        "cakrem" to "cakram",
        "kampas" to "kampas",
        "stop" to "stop",
        "lampu" to "lampu",
        "kaca" to "kaca",
        "filter" to "filter",
        "oli" to "oli",
    )

    val catalog = Catalog(
        vehicles = kendaraan,
        categories = kategori,
        parts = parts,
        groups = grup,
        groupParts = isiGrup,
        fitments = fitments,
        synonyms = sinonim,
    )
}
