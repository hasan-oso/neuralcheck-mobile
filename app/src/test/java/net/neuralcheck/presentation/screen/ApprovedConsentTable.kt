package net.neuralcheck.presentation.screen

/**
 * The consent screen's texts of VERSION 2, AS REVIEWED (2026-10-23, under Hasan's delegation of 2026-10-22; `texts-review.md`, section «الموافقة») - the one table the tests pin the resources against (rule 29: a copy
 * carries its source). Each [Entry] says which slot ([ConsentText] by name), which resource, which row and where it came from, and the Arabic and Turkish wording character for character. BOTH languages exist for
 * every text: the customer sees this screen. The Turkish is PROPOSED and needs a speaker. Final legal wording: pending Hasan, the KVKK lawyer later.
 *
 * Source: `neuralcheck-workspace/evidence/run-2026-10-18/DECISIONS-FOR-HASAN-2026-10-21.md`, sha256 [SHEET_SHA256], rows A7-ت1 to A7-ت6 and A7-ف1 to A7-ف4, and the user's approved version-1 wording of the
 * three texts that did not change. Hand-edit nothing: a change of wording is a change of the decision, made in the sheet first, and it raises the version (`ConsentTextVersionTest`).
 */
internal object ApprovedConsentTable {
    const val SHEET_SHA256 = "65a6d3da6a1362adddf3bf33d1bf02482bda62dbd96e34849487a7b87d00398d"

    class Entry(val resource: String, val id: String, val source: String, val ar: String, val tr: String, val slot: String)

    val entries: List<Entry> = listOf(
        Entry("consent_title", "م1/A7-ت1", "DECISIONS-FOR-HASAN-2026-10-21.md م1/A7-ت1: Arabic: the user's approved v1 wording, unchanged (consent-text-proposal-2026-10-09.md, the Arabic column); Turkish: the sheet's A7-ت1", "قبل أن يبدأ الفحص", "Test başlamadan önce", "Title"),
        Entry("consent_measures", "م2/A7-ت2", "DECISIONS-FOR-HASAN-2026-10-21.md م2/A7-ت2: reworded in the review (A7-ت2): «يحفظ» and «لا يمكن أن يُستعاد» are not provable by the code", "يقيس هذا الفحص عتاد الهاتف: المعالج، والبطارية، والحرارة، والشاشة، والحسّاسات. ويحسب رمزاً من معرّفات الجهاز بدل المعرّف نفسه.", "Bu test telefonun donanımını ölçer: işlemci, pil, sıcaklık, ekran ve sensörler. Cihaz kimliğinin kendisi yerine, cihaz kimliklerinden hesaplanan bir kod kullanır.", "Measures"),
        Entry("consent_does_not_read", "م3/A7-ت3", "DECISIONS-FOR-HASAN-2026-10-21.md م3/A7-ت3: reworded in the review (A7-ت3): the microphone sentence is its own slot", "لا يقرأ الفحص صورك ولا رسائلك ولا ملفّاتك ولا حساباتك، ويختبر استقبال الأقمار الصناعية دون أن يحفظ موقعك.", "Test fotoğraflarınızı, mesajlarınızı, dosyalarınızı veya hesaplarınızı okumaz; konumunuzu kaydetmeden uydu sinyali alımını test eder.", "DoesNotRead"),
        Entry("consent_microphone", "م4′/A7-ت3", "DECISIONS-FOR-HASAN-2026-10-21.md م4′/A7-ت3: new slot replacing the retention sentence (A7-ت4: not written): the microphone sentence of A7-ت3, with «مقطعاً قصيراً» in place of the number", "واختبار الميكروفون يسجّل مقطعاً قصيراً ليُسمعك إيّاه، ثم يحذفه من الهاتف ولا يرسله.", "Mikrofon testi, size dinletmek için kısa bir kayıt yapar, ardından kaydı telefondan siler ve göndermez.", "Microphone"),
        Entry("consent_accept", "م5′/A7-ت5", "DECISIONS-FOR-HASAN-2026-10-21.md م5′/A7-ت5: Arabic: the user's approved v1 wording, unchanged; Turkish: the sheet's A7-ت5", "أوافق وأبدأ الفحص", "Kabul ediyorum, testi başlat", "Accept"),
        Entry("consent_decline", "م6′/A7-ت6", "DECISIONS-FOR-HASAN-2026-10-21.md م6′/A7-ت6: Arabic: the user's approved v1 wording, unchanged; Turkish: the sheet's A7-ت6", "لا أوافق", "Kabul etmiyorum", "Decline"),
        Entry("consent_record_failed", "A7-ف1", "DECISIONS-FOR-HASAN-2026-10-21.md A7-ف1: A7-ف1", "تعذّر تسجيل الموافقة. أعد المحاولة.", "Onay kaydedilemedi. Tekrar deneyin.", "RecordFailed"),
        Entry("consent_record_session_not_scanning", "A7-ف2", "DECISIONS-FOR-HASAN-2026-10-21.md A7-ف2: A7-ف2", "تعذّر تسجيل الموافقة لأنّ هذه الجلسة لم تعد قيد الفحص.", "Onay kaydedilemedi çünkü bu oturumda artık test yapılmıyor.", "RecordSessionNotScanning"),
        Entry("consent_record_stored_unreadable", "A7-ف3", "DECISIONS-FOR-HASAN-2026-10-21.md A7-ف3: A7-ف3 (the last sentence of A7-ف4: «الفنّي», by the user's decision of 2026-10-10)", "تعذّر تسجيل الموافقة على هذه الجلسة. اطلب من الفنّي فتح جلسة جديدة.", "Bu oturum için onay kaydedilemedi. Teknisyenden yeni bir oturum açmasını isteyin.", "RecordStoredUnreadable")
    )

    /** The six texts the version pins, in the screen's order: the digest of version 2 is over these. */
    val versionTexts: List<String> = listOf("Title", "Measures", "DoesNotRead", "Microphone", "Accept", "Decline")
}
