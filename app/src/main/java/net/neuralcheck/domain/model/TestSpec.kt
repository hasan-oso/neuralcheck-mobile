package net.neuralcheck.domain.model

/**
 * Complete specification of all 125 tests (104 automatic + 21 manual)
 * with ADR-defined test IDs, categories, and requirements.
 * (Phase 1.15: M-07 secondary mic removed — CAMCORDER capture couldn't
 *  isolate the secondary mic, so it was a false-pass risk. The "16 manual"
 *  in the old comment was stale; the enum actually carried 22 → now 21.)
 *
 * A69 (2026-09-20): nameAr/nameTr below are a documentation-only reference
 * index — no production code reads them (only .category and, in a few
 * spots, .requiredPermission are consumed; grep confirms no callers of
 * TestSpec.entries[i].nameAr/nameTr outside this file). They had drifted
 * out of sync with the actual R.string.test_*_ar/_tr resources each
 * automatic Tests.kt class uses at runtime — 18 identifiers total, the
 * whole N-01..N-06 cellular block plus N-09/N-10/N-12/N-13 in wireless
 * (both fully offset from the real test set; e.g. "VoLTE support" /
 * "airplane mode" described tests that no longer exist), plus one swapped
 * pair each in battery (B-03/B-08), CPU (C-03/C-05), GPU (G-02/G-05), and
 * storage (S-05/S-06). The R.string values are the ones actually shown to
 * the user and were verified correct against each test's real
 * implementation (BatteryTests.kt, CpuTests.kt, hardware/gpu, StorageTests.kt,
 * NetworkTests.kt) before this enum was corrected to match them — do not
 * "fix" the strings.xml side to match this enum instead.
 */
enum class TestSpec(
    val id: String,
    val category: TestCategory,
    val nameAr: String,
    val nameTr: String,
    val isAutomatic: Boolean,
    val minApi: Int = 26,
    val requiredPermission: String? = null
) {
    // Battery Tests (B-01 to B-08)
    B_01("B-01", TestCategory.BATTERY, "صحة البطارية", "Pil Sağlığı", true),
    B_02("B-02", TestCategory.BATTERY, "سعة البطارية المصممة", "Tasarım Kapasitesi", true),
    B_03("B-03", TestCategory.BATTERY, "حرارة البطارية", "Pil Sıcaklığı", true),
    B_04("B-04", TestCategory.BATTERY, "جهد البطارية", "Pil Voltajı", true),
    B_05("B-05", TestCategory.BATTERY, "تيار الشحن/التفريغ", "Şarj/Deşarj Akımı", true),
    B_06("B-06", TestCategory.BATTERY, "معدل استنزاف البطارية تحت الضغط", "Stres Altında Tüketim", true),
    B_07("B-07", TestCategory.BATTERY, "تقنية الشحن السريع", "Hızlı Şarj Teknolojisi", true),
    B_08("B-08", TestCategory.BATTERY, "دورات الشحن", "Şarj Döngüsü", true),

    // CPU Tests (C-01 to C-07)
    C_01("C-01", TestCategory.CPU, "عدد أنوية المعالج", "İşlemci Çekirdek Sayısı", true),
    C_02("C-02", TestCategory.CPU, "التردد الأقصى للمعالج", "Maksimum Frekans", true),
    C_03("C-03", TestCategory.CPU, "التردد الحالي عند الراحة", "Boşta CPU Frekansı", true),
    C_04("C-04", TestCategory.CPU, "أداء المعالج (MFLOPS)", "İşlemci Performansı", true),
    C_05("C-05", TestCategory.CPU, "طراز المعالج", "İşlemci Modeli", true),
    C_06("C-06", TestCategory.CPU, "نسبة الاختناق الحراري", "Termal Kısıtlama Oranı", true),
    C_07("C-07", TestCategory.CPU, "استقرار أداء المعالج", "Performans Kararlılığı", true),

    // GPU Tests (G-01 to G-05)
    G_01("G-01", TestCategory.GPU, "اسم وحدة معالجة الرسومات", "Grafik İşlemci Adı", true),
    G_02("G-02", TestCategory.GPU, "دعم Vulkan", "Vulkan Desteği", true),
    G_03("G-03", TestCategory.GPU, "نسبة انخفاض FPS", "FPS Düşüş Oranı", true),
    G_04("G-04", TestCategory.GPU, "اختناق حراري للرسومات", "Grafik Termal Kısıtlama", true),
    G_05("G-05", TestCategory.GPU, "أدنى FPS مستدام", "En düşük FPS", true),

    // Storage Tests (S-01 to S-06)
    S_01("S-01", TestCategory.STORAGE, "سعة التخزين الكلية", "Toplam Depolama", true),
    S_02("S-02", TestCategory.STORAGE, "المساحة المتاحة", "Kullanılabilir Alan", true),
    S_03("S-03", TestCategory.STORAGE, "سرعة الكتابة المتسلسلة", "Sıralı Yazma Hızı", true),
    S_04("S-04", TestCategory.STORAGE, "سرعة القراءة المتسلسلة", "Sıralı Okuma Hızı", true),
    S_05("S-05", TestCategory.STORAGE, "قراءة عشوائية", "Rastgele Okuma", true),
    S_06("S-06", TestCategory.STORAGE, "كتابة عشوائية", "Rastgele Yazma", true),

    // Thermal Tests (T-01 to T-04)
    T_01("T-01", TestCategory.THERMAL, "أقصى حرارة مسجلة", "Kaydedilen Maks Sıcaklık", true),
    T_02("T-02", TestCategory.THERMAL, "معدل ارتفاع الحرارة", "Sıcaklık Artış Hızı", true),
    T_03("T-03", TestCategory.THERMAL, "معدل انخفاض الحرارة", "Sıcaklık Düşüş Hızı", true),
    T_04("T-04", TestCategory.THERMAL, "عدد مناطق الحرارة النشطة", "Aktif Isı Bölgesi Sayısı", true),

    // Memory Tests (R-01 to R-03)
    R_01("R-01", TestCategory.MEMORY, "إجمالي ذاكرة الوصول العشوائي", "Toplam RAM", true),
    R_02("R-02", TestCategory.MEMORY, "الذاكرة المتاحة", "Kullanılabilir RAM", true),
    R_03("R-03", TestCategory.MEMORY, "عتبة الذاكرة المنخفضة", "Düşük Bellek Eşiği", true),

    // Network Tests - Cellular (N-01 to N-07)
    N_01("N-01", TestCategory.CELLULAR, "إصدار مودم الاتصال", "Modem Sürümü", true),
    N_02("N-02", TestCategory.CELLULAR, "شريحة الاتصال 1", "SIM 1 Durumu", true, requiredPermission = "android.permission.READ_PHONE_STATE"),
    N_03("N-03", TestCategory.CELLULAR, "شريحة الاتصال 2", "SIM 2 Durumu", true, requiredPermission = "android.permission.READ_PHONE_STATE"),
    N_04("N-04", TestCategory.CELLULAR, "نوع الشبكة", "Ağ Türü", true, requiredPermission = "android.permission.READ_PHONE_STATE"),
    N_05("N-05", TestCategory.CELLULAR, "قوة إشارة الشبكة", "İşaret Gücü", true, requiredPermission = "android.permission.READ_PHONE_STATE"),
    N_06("N-06", TestCategory.CELLULAR, "مشغّل الشبكة", "Operatör", true, requiredPermission = "android.permission.READ_PHONE_STATE"),
    N_07("N-07", TestCategory.CELLULAR, "دعم eSIM", "eSIM Desteği", true),

    // Network Tests - Wireless (N-08 to N-13)
    N_08("N-08", TestCategory.WIRELESS, "حالة Wi-Fi", "Wi-Fi Durumu", true),
    N_09("N-09", TestCategory.WIRELESS, "سرعة اتصال Wi-Fi", "Wi-Fi Bağlantı Hızı", true, requiredPermission = "android.permission.ACCESS_FINE_LOCATION"),
    N_10("N-10", TestCategory.WIRELESS, "قوة إشارة Wi-Fi", "Wi-Fi İşaret Gücü", true),
    N_11("N-11", TestCategory.WIRELESS, "حالة Bluetooth", "Bluetooth Durumu", true, minApi = 31, requiredPermission = "android.permission.BLUETOOTH_CONNECT"),
    N_12("N-12", TestCategory.WIRELESS, "بلوتوث منخفض الطاقة", "Düşük Güç Bluetooth", true),
    N_13("N-13", TestCategory.WIRELESS, "الاتصال القريب NFC", "NFC", true),

    // GPS Tests (L-01 to L-03)
    L_01("L-01", TestCategory.GPS, "توفر GPS", "GPS Kullanılabilirliği", true, requiredPermission = "android.permission.ACCESS_FINE_LOCATION"),
    L_02("L-02", TestCategory.GPS, "عدد الأقمار الصناعية", "Uydu Sayısı", true, requiredPermission = "android.permission.ACCESS_FINE_LOCATION"),
    L_03("L-03", TestCategory.GPS, "دقة تحديد الموقع", "Konum Doğruluğu", true, requiredPermission = "android.permission.ACCESS_FINE_LOCATION"),

    // Sensor Tests (SN-01 to SN-12)
    SN_01("SN-01", TestCategory.SENSORS, "مقياس التسارع", "İvmeölçer", true),
    SN_02("SN-02", TestCategory.SENSORS, "الجيروسكوب", "Jiroskop", true),
    SN_03("SN-03", TestCategory.SENSORS, "مستشعر القرب", "Yakınlık Sensörü", true),
    SN_04("SN-04", TestCategory.SENSORS, "مستشعر الضوء المحيط", "Ortam Işığı Sensörü", true),
    SN_05("SN-05", TestCategory.SENSORS, "البوصلة", "Pusula", true),
    SN_06("SN-06", TestCategory.SENSORS, "مقياس الضغط الجوي", "Barometre", true),
    SN_07("SN-07", TestCategory.SENSORS, "مستشعر درجة الحرارة المحيطة", "Ortam Sıcaklık Sensörü", true),
    SN_08("SN-08", TestCategory.SENSORS, "كاشف الخطوات", "Adım Algılayıcı", true),
    SN_09("SN-09", TestCategory.SENSORS, "عداد الخطوات", "Adım Sayacı", true),
    SN_10("SN-10", TestCategory.SENSORS, "مستشعر الجاذبية", "Yerçekimi Sensörü", true),
    SN_11("SN-11", TestCategory.SENSORS, "مستشعر التسارع الخطي", "Doğrusal İvme Sensörü", true),
    SN_12("SN-12", TestCategory.SENSORS, "مستشعر متجه الدوران", "Dönüş Vektörü Sensörü", true),

    // Camera & Biometric Tests (CM-01 to CM-11)
    CM_01("CM-01", TestCategory.CAMERA, "الكاميرا الخلفية — التشغيل", "Arka Kamera — Çalışma", true, requiredPermission = "android.permission.CAMERA"),
    CM_02("CM-02", TestCategory.CAMERA, "الكاميرا الأمامية — التشغيل", "Ön Kamera — Çalışma", true, requiredPermission = "android.permission.CAMERA"),
    CM_03("CM-03", TestCategory.CAMERA, "ضبط تلقائي للكاميرا الخلفية", "Arka Kamera Otomatik Odaklama", true, requiredPermission = "android.permission.CAMERA"),
    CM_04("CM-04", TestCategory.CAMERA, "الفلاش", "Flaş", true, requiredPermission = "android.permission.CAMERA"),
    CM_05("CM-05", TestCategory.CAMERA, "تسجيل الفيديو", "Video Kaydı", true, requiredPermission = "android.permission.CAMERA"),
    CM_06("CM-06", TestCategory.CAMERA, "دقة الكاميرا الخلفية", "Arka Kamera Çözünürlüğü", true, requiredPermission = "android.permission.CAMERA"),
    CM_07("CM-07", TestCategory.CAMERA, "دقة الكاميرا الأمامية", "Ön Kamera Çözünürlüğü", true, requiredPermission = "android.permission.CAMERA"),
    CM_08("CM-08", TestCategory.CAMERA, "مستشعر بصمة الإصبع", "Parmak İzi Sensörü", true),
    CM_09("CM-09", TestCategory.CAMERA, "التعرف على الوجه", "Yüz Tanıma", true),
    CM_10("CM-10", TestCategory.CAMERA, "الاهتزاز", "Titreşim Motoru", true),
    CM_11("CM-11", TestCategory.CAMERA, "مصباح LED للإشعارات", "Bildirim LED'i", true),

    // Display Tests (D-01 to D-03)
    D_01("D-01", TestCategory.DISPLAY, "دقة الشاشة", "Ekran Çözünürlüğü", true),
    D_02("D-02", TestCategory.DISPLAY, "كثافة البكسل", "Piksel Yoğunluğu", true),
    D_03("D-03", TestCategory.DISPLAY, "معدل تحديث الشاشة", "Ekran Yenileme Hızı", true),

    // Security Tests (SEC-01 to SEC-11)
    SEC_01("SEC-01", TestCategory.SECURITY, "حالة قفل الشاشة", "Ekran Kilidi Durumu", true),
    SEC_02("SEC-02", TestCategory.SECURITY, "حالة التشفير", "Şifreleme Durumu", true),
    SEC_03("SEC-03", TestCategory.SECURITY, "مستوى تصحيح الأمان", "Güvenlik Yaması Seviyesi", true),
    SEC_04("SEC-04", TestCategory.SECURITY, "صلاحيات الروت", "Root Yetkisi", true),
    SEC_05("SEC-05", TestCategory.SECURITY, "وضع المطور", "Geliştirici Modu", true),
    SEC_06("SEC-06", TestCategory.SECURITY, "تصحيح USB", "USB Hata Ayıklama", true),
    SEC_07("SEC-07", TestCategory.SECURITY, "مصادر غير معروفة", "Bilinmeyen Kaynaklar", true),
    SEC_08("SEC-08", TestCategory.SECURITY, "Google Play Protect", "Google Play Protect", true),
    SEC_09("SEC-09", TestCategory.SECURITY, "إصدار نظام التشغيل", "İşletim Sistemi Sürümü", true),
    SEC_10("SEC-10", TestCategory.SECURITY, "مستوى API", "API Seviyesi", true),
    SEC_11("SEC-11", TestCategory.SECURITY, "محمل الإقلاع", "Önyükleyici", true),

    // Manual Tests (M-01 to M-16) — categories & names aligned with the UI
    // labels shown in ReportScreen.manualTestItems / strings.xml manual_*.
    // Only TestSpec.category is consumed downstream (ReportScreen save path);
    // nameAr/nameTr are kept in sync so the enum stays self-documenting.
    M_01("M-01", TestCategory.DISPLAY, "النقاط الميتة في الشاشة", "Ölü Pikseller", false),
    M_02("M-02", TestCategory.DISPLAY, "استجابة اللمس", "Dokunmatik Tepkisi", false),
    M_03("M-03", TestCategory.DISPLAY, "اللمس المتعدد", "Çoklu Dokunma", false),
    // Bug 5 follow-up (2026-05-17): re-bucketed M-04..M-11 + M-20..M-22 out
    // of CAMERA. The previous CAMERA tag was a "miscellaneous user-perceptible
    // hardware" bucket that was inert while categoryName never reached the AI,
    // but becomes actively harmful once the wire payload starts carrying the
    // field — a healthy speaker pass would inflate the camera grade. See
    // docs/audits/bug_5_manual_tests/catalog-rebucketing.md for the audit.
    //
    //   M-04..M-08 → AUDIO   (speakers, mics, headphone jack)
    //   M-09..M-11 → INPUT   (volume + power hardware buttons)
    //   M-17..M-19 stay CAMERA (genuinely camera tests — photos + flash)
    //   M-20, M-21 → SECURITY (biometric auth; fingerprint + face unlock
    //                          live under the BiometricPrompt API which
    //                          is part of the SECURITY surface, not
    //                          the camera module)
    //   M-22 → SENSORS       (vibration motor is an actuator that sits
    //                          on the motion-hardware shelf next to the
    //                          accel/gyro/proximity sensors; no perfect
    //                          home but SENSORS is closer than CAMERA
    //                          and avoids polluting Audio/Input)
    M_04("M-04", TestCategory.AUDIO,    "السماعة الخارجية", "Harici Hoparlör", false),
    M_05("M-05", TestCategory.AUDIO,    "سماعة الأذن", "Kulaklık Hoparlörü", false),
    M_06("M-06", TestCategory.AUDIO,    "الميكروفون الأساسي", "Ana Mikrofon", false, requiredPermission = "android.permission.RECORD_AUDIO"),
    // M-07 (secondary mic) removed Phase 1.15 — CAMCORDER capture couldn't isolate the secondary mic (false-pass risk).
    M_08("M-08", TestCategory.AUDIO,    "منفذ السماعات", "Kulaklık Girişi", false),
    M_09("M-09", TestCategory.INPUT,    "زر رفع الصوت", "Ses Açma Düğmesi", false),
    M_10("M-10", TestCategory.INPUT,    "زر خفض الصوت", "Ses Kısma Düğmesi", false),
    M_11("M-11", TestCategory.INPUT,    "زر الطاقة", "Güç Düğmesi", false),
    M_12("M-12", TestCategory.BATTERY,  "منفذ الشحن", "Şarj Girişi", false),
    M_13("M-13", TestCategory.STORAGE,  "دعم OTG", "OTG Desteği", false),
    M_14("M-14", TestCategory.BATTERY,  "الشحن اللاسلكي", "Kablosuz Şarj", false),
    M_15("M-15", TestCategory.STORAGE,  "فتحة بطاقة الذاكرة", "Hafıza Kartı Yuvası", false),
    M_16("M-16", TestCategory.SENSORS,  "الأشعة تحت الحمراء", "Kızılötesi Vericisi", false),
    M_17("M-17", TestCategory.CAMERA,   "صور الكاميرا الخلفية", "Arka Kamera Fotoğrafları", false, requiredPermission = "android.permission.CAMERA"),
    M_18("M-18", TestCategory.CAMERA,   "صور الكاميرا الأمامية", "Ön Kamera Fotoğrafları", false, requiredPermission = "android.permission.CAMERA"),
    M_19("M-19", TestCategory.CAMERA,   "الفلاش", "Flaş", false),
    M_20("M-20", TestCategory.SECURITY, "بصمة الإصبع", "Parmak İzi", false),
    M_21("M-21", TestCategory.SECURITY, "التعرف على الوجه", "Yüz Tanıma", false),
    M_22("M-22", TestCategory.SENSORS,  "الاهتزاز", "Titreşim", false);

    companion object {
        fun fromId(id: String): TestSpec? = entries.find { it.id == id }
        fun getByCategory(category: TestCategory): List<TestSpec> = entries.filter { it.category == category }
        fun getAutomaticTests(): List<TestSpec> = entries.filter { it.isAutomatic }
        fun getManualTests(): List<TestSpec> = entries.filter { !it.isAutomatic }
    }
}
