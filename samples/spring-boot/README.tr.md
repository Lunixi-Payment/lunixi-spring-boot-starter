<p align="center"><img src="src/main/resources/static/assets/lunixi.svg" width="240" alt="Lunixi"></p>

# Spring Boot ödeme örneği

1.0.1 kaynakları canonical string para sözleşmesini ve Java SDK 1.0.1 sürümünü kullanır. Public kaynak ve Maven Central yayını ayrı doğrulanır. Yeni kurulum mağaza içinde gömülü form açar. Yönlendirme yalnız backend paymentPageUrl ile yapılır; eski kayıtta adres yoksa yeni ödeme/anahtar açılmadan beklenir. Mevcut denemenin özgün açık mod/gövde/anahtarı korunur.



[English](README.md) · [Rehber](https://docs.lunixi.com/tr/sdks/spring-boot) · [Kaynak](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/tree/main/samples/spring-boot)

**Spring Boot 4.1.2 / Java 17+**, **Lunixi Spring Boot Starter 1.0.1 / Java SDK 1.0.1** ile çalışan MVC uygulaması. Yerel Spring Security JDBC oturumu, parola özeti, CSRF ve sipariş sahipliğini yönlendirmeli/gömülü Lunixi formuna bağlar. Ham imzalı webhook ve doğrulanmış ödeme GET'i; H2 siparişini, olay kaydını ve şifreli kalıcı outbox'ı aynı Spring JDBC transaction içinde günceller.

Açıkça oluşturulan iki deneme kullanıcısının ayrı sunucu tarafı 19,90 TRY siparişi bulunur. Tutar veya müşteri kimliği tarayıcıdan alınmaz, kart verisi toplanmaz. Alternatif yöntemler, çoklu kart, taksit ve sigorta Lunixi form ayarlarındadır. İmzalama, kesin para, çoklu kart tahsilat kanıtı, şifreleme ve güvenli tekrar ortak SDK/Starter tarafından yönetilir.

## Kaynaktan derleme

JDK 17+, JDK sürümünüzle uyumlu Gradle gerekir. Ek kontrol araçları Node ve Python 3 kullanır. Gradle 9.7.1 ile test edilmiştir; sınıfların hedefi Java 17'dir. Örnek, açıkça seçtiğiniz kaynak check-out'larını Gradle composite build ile kullanır; doğrulanmamış Maven Central yayınına dayanmaz.

```bash
git clone https://github.com/Lunixi-Payment/lunixi-spring-boot-starter.git
git clone https://github.com/Lunixi-Payment/lunixi-java.git
cd lunixi-spring-boot-starter/samples/spring-boot
# Önce iki kaynakta da incelediğiniz release commit/tag'ini seçin.
gradle -PlunixiStarterSource=../.. -PlunixiSdkSource=../lunixi-java test installDist browserContract dependencyInventory
```

SDK yolu dahil edilen starter projesinin köküne göre çözülür. Farklı dizin düzeninde iki kaynak parametresine de mutlak yol verin. Örtük check-out veya indirme yapılmaz; bağımlılık koordinatlarını açıkça belirtilen kaynak build'leri karşılar. Uygulama bağımlılıkları `gradle.lockfile` ile sabittir.

## Ayarlar ve çalıştırma

`.env.example` dosyasını bu örneğin Git dışında kalan `.env` dosyasına kopyalayıp kayıtlı TEST bilgilerini doldurun. Bu dosya **kabuk ortam dosyasıdır**; açıkça yüklenir, üst dizinin ortam dosyası okunmaz.

```bash
cp .env.example .env
# API/webhook/veritabanı sırrından türetilmeyen bağımsız anahtar üretir.
python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())'
set -a
. ./.env
set +a
build/install/lunixi-spring-checkout-example/bin/lunixi-spring-checkout-example
```

| Değişken | Gereksinim |
| --- | --- |
| `LUNIXI_MERCHANT_ID` | Kayıtlı üye işyeri kimliği |
| `LUNIXI_ENVIRONMENT` | Gerçek anahtar/webhook ile aynı `TEST` veya `LIVE` |
| `LUNIXI_KEY_ID`, `LUNIXI_PRIVATE_KEY` | Yalnız sunucuda kalan kayıtlı imzalama bilgileri; PEM gerçek satır sonu içerebilir |
| `LUNIXI_WEBHOOK_SECRET` | Üye işyerinin webhook imzalama sırrı |
| `LUNIXI_STORAGE_KEY` | Bağımsız, base64 kodlanmış 32 bayt token/outbox şifreleme anahtarı |
| `LUNIXI_JDBC_URL`, `LUNIXI_JDBC_USER`, `LUNIXI_JDBC_PASSWORD` | Açıkça belirlenen özel H2 adresi ve bilgileri |
| `LUNIXI_PUBLIC_BASE_URL` | Operatörün belirlediği herkese açık HTTPS dönüş kök adresi |
| `LUNIXI_CHECKOUT_MODE` | `embedded` (varsayılan) veya `hosted` |
| `LUNIXI_PREAUTH` | Ön provizyon için `true`; diğer durumda `false` |
| `LUNIXI_LOCALE` | İlk sipariş/form dili: `en` veya `tr` |
| `LUNIXI_INITIALIZE_DISPOSABLE` | Yalnız ayrı TEST örneğini ilk kez kurarken açıkça `true` |
| `DEMO_ALICE_PASSWORD`, `DEMO_BOB_PASSWORD` | Farklı, en az 16 karakterlik açık parolalar |
| `LUNIXI_SECURE_COOKIES` | Varsayılan `true`; yalnız ayrı loopback HTTP geliştirmesinde `false` |

Varsayılan hesap, veritabanı adresi, anahtar veya otomatik tablo kurulumu yoktur. Açık deneme kurucusu yalnız H2/TEST ortamını kabul eder; LIVE ve dış transaction reddedilir. Ledger, kullanıcı/sipariş ve kuyruk tabloları oluşturulur; mali geçmiş silinmez. İlk çalıştırmadan sonra kurulum seçeneğini kapatın. H2 dosyalarını web kökü dışında, şifreleme anahtarını ayrı ve kalıcı yedekte saklayın. Çalışan adaptör ve SQL kapsamı **yalnız H2**'dir; başka üretim veritabanında yerel şema/ledger ve aynı DataSource transaction davranışı ayrıca uygulanıp doğrulanmalıdır.

`http://localhost:8080/login` adresinde `alice` veya `bob` ve belirlediğiniz parolayla oturum açın. Gerçek TEST ödemesi için HTTPS/TLS sunucusu veya tünel kullanın; `LUNIXI_PUBLIC_BASE_URL` değerini güncelleyin ve Lunixi'ye **`https://adresiniz/webhooks/lunixi`** kaydedin. Yayında HTTPS, secure HttpOnly SameSite=Lax çerez, güvenilir host/proxy ve uygulamanızın kimlik politikası gerekir. Hata ekranına gateway yanıtı veya stack trace basılmaz.

## Rotalar ve ödeme yetkisi

| Rota | Yerel sınır |
| --- | --- |
| `GET/POST /login` | Spring Security JDBC parola özeti, CSRF ve oturum kimliği yenileme |
| `POST /logout` | Yerel CSRF korumalı oturum kapatma |
| `GET /orders` | Yalnız mevcut kullanıcının siparişleri |
| `POST /payments/{id}` | Müşteri rolü, CSRF ve güncel sunucu sipariş sahipliği |
| `POST /payments/{id}/return` | Oturumsuz bilgi dönüşü; aynı origin GET adresine sabit 303 |
| `GET /payments/{id}/return` | Oturum ve sipariş sahibi kontrolü; durum/tutar alanları etkisiz |
| `POST /webhooks/lunixi` | Ham imzalı sunucu bildirimi ve kimliği doğrulanmış ödeme GET'i |

Yalnız tam webhook POST'u ve bilgi dönüş POST'u CSRF'den muaftır. Dönüş adaptörü en fazla 64 KiB artı bir ret baytı okur, durum/imza dahil hiçbir form alanını yorumlamaz. Kullanıcı/sipariş sorgusu, veritabanı yazımı veya gateway çağrısı yapmaz; bilinmeyen siparişe de aynı boş 303 yanıtını verir. Cross-site POST sırasında SameSite=Lax çerezi gelmeyebilir; sonraki GET normal oturum ve sahiplik kontrolünü uygular. Tarayıcı dönüşü webhook sırrıyla doğrulanmaz. Ödeme başlatma/giriş/çıkış CSRF korumasını sürdürür.

Gateway sabit `https://api-gateway.lunixi.com` adresidir; gerçek TEST/LIVE erişimini anahtar belirler, uydurma ortam alanı gönderilmez. Hosted mod yalnız backend’in doğrulanmış `paymentPageUrl` değerine gider; token gömülü token’dan farklı olabilir ve adres üretilmez. Embedded mod sabit `https://cdn.lunixi.com/lunixi-checkout.min.js`, doğru kaçışlı tarayıcı tokenı ve resmi object/mount API'sini kullanır. Gateway HTML'i basılmaz. CSP'yi resmi form origin gereksinimleriyle yapılandırın.

## Kalıcılık ve tahsilat

Kısa yerel satır kilitli sipariş rezervasyonu gateway çağrısından önce commit olur. Ortak `CommerceService`, Starter `JdbcLedgerStore` üzerinden kalıcı deneme, tam istek gövdesi ve idempotency anahtarını saklar. Token ve outbox bağımsız anahtarlı ortak `SecretBox` ile kimliği doğrulanmış şifrelidir.

Yanıt kaybında aynı istek/anahtar korunur; belirsiz sonuç otomatik anahtar yenilemez. SDK, gateway'in 24 saatlik önbelleğinden önce temkinli **23 saatlik yeniden gönderim sınırı** uygular. Eski belirsiz kayıt GET/webhook ile uzlaştırılabilir; yeni finansal POST üretemez. İmzalı olay ve eşleşen opak ilk deneme/parmak izi kayıp ilk yanıtı kurtarabilir. Kesin sonuç kanıtı bulunmayan `FAILED` incelemede kalır, yeni anahtar açılmaz.

Webhook, UTF-8 çözmeden önce ham girdiyi 1 MiB ile sınırlar; tekrarlanan/çakışan imza başlıklarını reddeder. V2 ham bayt HMAC, zaman, olay kimliği ve imzalı ortam doğrulanır; ardından kimliği doğrulanmış GET yapılır. Üye işyeri/sipariş/ödeme, para birimi, tam tutar, tahsilat/iade bakiyesi ve çoklu kart ana ödeme toplamı eşleşmelidir. Tek başına imzalı durum veya tarayıcı dönüşü siparişi ödenmiş yapmaz.

Starter ledger ve yerel `JdbcTemplate` aynı DataSource/Spring transaction manager kullanır. Olay tüketimi, ledger, sipariş ve şifreli kuyruk birlikte commit olur. Güncel sipariş/parmak izi/sahip/işyeri/ortam aynı transaction altında kilitlenip yeniden karşılaştırılır. İptal, değişmiş kimlik ve geriye giden bakiye güvenli biçimde reddedilir. Yerel SQL constraint hatası tümünü geri alır; onarım sonrası aynı doğrulanmış olay yeniden uygulanabilir.

Provizyon ve kısmi tahsilat bekler; manuel bekletme tam tahsilatta bile incelemede kalır. Anapara, taksit ek bedeli dahil brüt tahsilat/iade bakiyelerinden ayrıdır. Yalnız tam doğrulanmış tahsilat bir kez şifreli `order.paid` olayı üretir. Mali geçmiş ve anahtarlar otomatik silinmez.

## Uzlaştırma ve commit sonrası işler

İsteğe bağlı, yalnız okuma yapan arka plan uzlaştırmasını açıkça açın:

```bash
export SAMPLE_RECONCILIATION_ENABLED=true
```

Dakikada en fazla 100 rezervasyon GET ile kontrol edilir; `checked_at` sıralaması sonraki kayıtların dışarıda kalmasını önler. Belirsiz/FAILED işlemler bekler, eski finansal POST yeniden gönderilmez. `sample.reconciliation-delay` milisaniye cinsinden gecikmeyi değiştirir.

`PaidOutbox.claim()` mali commit **sonrası** çalışır. Güncel ödenmiş/bekletme durumunu kontrol eder, `ready` kaydını kalıcı `sending` yapar; sabit olay anahtarı ve çözülmüş içeriği döndürür. Kendi teslimat/e-posta işçinizi bağlayın; dış sistemde aynı sabit anahtarla idempotency uygulayın. Yalnız kesin başarıda `acknowledge(key, true)` çağırın. Belirsiz sonuç `uncertain` kalır; `sending` sırasında çökme de uzlaştırma/inceleme bekler. Kör yeniden deneme/sıfırlama yapılmaz. Stok, vergi, kargo veya dış teslimat kuralları uygulamanıza aittir.

SDK'nın alt seviye tahsilat/iade/iptal çağrıları önceden saklanmış idempotency anahtarı ister; bu Java sürümü kalıcı yönetim operasyon defteri sunmaz. Yönetim eklemeden önce uygulamada kalıcı rezervasyon, gerçek işlem kimliği, yetki/CSRF, belirsiz operasyonları sıraya alma, 23 saat sınırı ve kimliği doğrulanmış GET ile bakiye teyidi uygulanıp test edilmelidir. Bu müşteri örneğinde mali yönetim yolu yoktur.

## Kontroller

Aşağıdaki komutları örnek dizininden, yukarıdaki kaynak düzeniyle çalıştırın:

```bash
gradle -PlunixiStarterSource=../.. -PlunixiSdkSource=../lunixi-java test installDist browserContract dependencyInventory
python3 tools/http-smoke.py
python3 tools/audit-dependencies.py
```

Testler gerçek Spring Boot MVC, Spring Security, JDBC ve H2 kullanır. Yalnız gateway HTTP yanıtları kontrollü taşıma yanıtlarıdır; SDK kripto/kanıt/şifreleme ve yerel transaction gerçektir. Rol/sahip/CSRF, örtük DDL olmaması, şifreli token, doğru kaçışlı gömülü form, oturumsuz POST → 303 → korumalı GET, ham imza/tekrar/ortam, ücret/kısmi/FAILED, SQL constraint geri alma ve aynı olayla kurtarma, kayıp yanıt/anahtar, süre sınırı ve commit sonrası belirsizlik test edilir.

Ayrı HTTP smoke kurulu uygulamayı gerçek Tomcat, JDBC hesapları, dosya H2 ve gerçek oturum çerezleriyle çalıştırır; geçerli finansal ödeme POST'u göndermez. `browserContract` gerçek JavaScript dosyasının object/token/mount sözleşmesini yürütür. `audit-dependencies.py` çözümlenen Maven koordinatlarını açık OSV ile kontrol eder; raporda üye işyeri verisi bulunmaz. Canlı Lunixi tahsilatı yapılmamıştır. Yayından önce herkese açık HTTPS üzerinde kayıtlı TEST üye işyeriyle yönlendirmeli/gömülü form ve gerçek webhook kabulünü tamamlayın.

Resmi kaynaklar: [Spring Boot MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [JDBC kimlik doğrulama](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/jdbc.html), [transaction yönetimi](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html).
