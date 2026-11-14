# neuralcheck-mobile

[English](README.md) · [العربية](README.ar.md) · **Türkçe**

> **Proje durumu: arşivlendi.** NeuralCheck'in geliştirilmesi Kasım 2026'da durduruldu (zaman yetersizliği ve planlanan saha veri toplamanın pratikte neredeyse imkânsız olması nedeniyle). Kod olduğu gibi, garanti ve destek olmaksızın [MIT lisansı](LICENSE) ile yayımlanmıştır. Bu depoda **gizli bilgi yoktur**: Firebase istemci yapılandırması yalnızca `*.example` şablonları olarak sunulur ve yayın imzalama anahtarı hiçbir zaman depoda bulunmadı.

**NeuralCheck**'in (ikinci el akıllı telefonlar için bir tanılama sistemi) yerel Android uygulaması (Kotlin, Jetpack Compose). Çok sayıda otomatik ve manuel donanım testini ve 8 dakikalık bir stres testini çalıştırır, ham bir cihaz parmak izi toplar ve arka uca ([neuralcheck-backend](https://github.com/hasan-oso/neuralcheck-backend)) gönderir; arka uç telefonu derecelendirir ve burada ve web sitesinde ([neuralcheck-website](https://github.com/hasan-oso/neuralcheck-website)) gösterilen bir rapor döndürür.

## Özellikler

- Birçok kategoride otomatik ve manuel testler (pil, işlemci, GPU, depolama, ısı, bellek, sensörler, ekran, ses, kamera, bağlantı, ...).
- Yerel bir C++ kütüphanesiyle (`libstress.so`, yalnızca arm64-v8a) 8 dakikalık çok aşamalı stres testi.
- Müdahale (tamper) göstergeleri, üç tarama modu (genel, onarım öncesi, karşılaştırmalı onarım sonrası) ve QR kodla rapor paylaşımı.
- Dükkân modu: dükkân sahibi bir oturum kodu oluşturur ve müşterinin telefonunu tarar; müşterinin telefonu kodla katılır (Arapça veya Türkçe bir onay ekranıyla).
- Arapça (varsayılan) ve Türkçe arayüz, RTL desteği, koyu ve açık temalar.

## Gereksinimler

JDK 17+ ile Android Studio (birlikte gelen JBR 21 yeterlidir), Android SDK 35, NDK `27.1.12297006`. `minSdk` 28, `compileSdk`/`targetSdk` 35. Bu anlık görüntünün sürümü: **1.0.15** (versionCode 17).

## Kurulum

1. Her ürün çeşidi (flavor) için bir Android uygulaması içeren kendi Firebase projenizi oluşturun ve `google-services.json` dosyasını indirin. Depo yalnızca şablon içerir: her şablonu kopyalayıp doldurun.

   | Flavor | applicationId | oluşturulacak dosya (git tarafından yok sayılır) |
   |---|---|---|
   | `launch` | `net.neuralcheck` | `app/src/launch/google-services.json` (şablon yanında `.example`) |
   | `datacollect` | `net.neuralcheck.datacollect` | `app/src/datacollect/google-services.json` (şablon yanında `.example`) |

2. Release derlemeleri, depo kökündeki `keystore.properties` dosyasıyla imzalanır (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; bkz. `app/build.gradle.kts`). Dosya git tarafından yok sayılır. Debug derlemeleri anahtar gerektirmez.
3. Uygulamayı kendi arka ucunuza bağlamak için [neuralcheck-backend](https://github.com/hasan-oso/neuralcheck-backend) projesini 1. adımdaki Firebase projesine dağıtın.

## Derleme ve test

```bash
./gradlew assembleLaunchDebug
./gradlew cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest   # JVM birim testleri (önce temizleyin, aksi halde Gradle önbellekteki sonuçları sunabilir)
```

JVM testleri C++ kodunu ve JNI'den geçen hiçbir şeyi sınamaz; bunlar gerçek cihaz gerektirir. `:app:externalNativeBuildLaunchDebug` yalnızca yerel kodun derlendiğini doğrular.

## Yapı

```
app/src/main/java/net/neuralcheck/
├── presentation/   Compose ekranları, görünüm modelleri (Hilt), gezinme, tema
├── domain/         kullanım senaryoları, puanlama motoru, onay ve eşleştirme mantığı
├── data/           Room veritabanı, depolar, uzak servisler (Firebase)
├── hardware/       test paketleri (pil, işlemci, GPU, sensörler, ...)
├── service/        ön plan stres testi servisi, toplayıcılar, çalışanlar
└── util/           günlükleme, parmak izi, yardımcılar
app/src/main/cpp/   yerel stres kütüphanesi
app/src/test/       JVM testleri ve arka uç ile web sitesiyle paylaşılan altın örnekler
docs/               sözleşmeler (FV68, ölçüm şartnamesi), mimari notlar, geliştirme notları (Arapça)
tools/guards/       Python koruma betikleri
```

## Üçüncü taraf malzeme

`app/src/main/assets/models/` altında bu proje tarafından üretilmemiş MobileNet V3 TensorFlow Lite model dosyaları bulunur; yeniden dağıtmadan önce özgün lisanslarını (yazım anında Apache-2.0) kontrol edin.

## Lisans

[MIT](LICENSE).

*Not: Türkçe metin bir anadili konuşuru tarafından gözden geçirilmemiştir; düzeltmeler memnuniyetle karşılanır.*
