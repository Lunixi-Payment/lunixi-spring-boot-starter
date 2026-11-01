<p align="center"><picture><source media="(prefers-color-scheme: dark)" srcset=".github/assets/lunixi-logo-dark.svg"><img src=".github/assets/lunixi-logo.svg" alt="Lunixi" width="240"></picture></p>

# Lunixi Spring Boot Starter

1.0.1, canonical minor-unit string para sözleşmesini kullanır. Java SDK 1.0.1 public kaynak commit’i ve indirilen arşivin SHA-256 özeti doğrulanmış pin ile sabitlenir; starter ve SDK 1.0.1 Maven Central yayınları ayrıca doğrulandı. Yeni kurulum mağaza içinde gömülü form açar. Yönlendirme yalnız backend paymentPageUrl ile yapılır; eski kayıtta adres yoksa yeni ödeme/anahtar açılmadan beklenir. Mevcut denemenin özgün açık mod/gövde/anahtarı korunur.



[English](README.md) · [Rehber](https://docs.lunixi.com/tr/sdks/spring-boot) · [Ödeme örneği](samples/spring-boot/README.tr.md) · [Sürüm geçmişi](CHANGELOG.md) · [Güvenlik](SECURITY.md)

**1.0.1**, **Java SDK 1.0.1** istemcisini **Spring Boot 4.1.2 / Java 17+** uygulamasına bağlar. Otomatik bean yapılandırması, ham merchant webhook adaptörü ve isteğe bağlı transaction kullanan JDBC ledger sağlar. Spring/SDK bağımlılıkları vardır; sıfır bağımlılık iddiası yoktur.

Kütüphane HTTP rotası açmaz, otomatik tablo kurmaz, müşteri kimliği/sipariş sahipliği çözmez ve kalıcı tahsilat/iade/iptal kuyruğu sunmaz. Çalışan MVC örneği ayrı uygulamadır; kendi auth/CSRF/sipariş yaşam döngüsü vardır. [Java ödeme/Commerce rehberi](https://github.com/Lunixi-Payment/lunixi-java/blob/main/docs/commerce.tr.md).

## Maven Central üzerinden kurulum

Maven Central’da yayımlanan **[`com.lunixi:lunixi-spring-boot-starter:1.0.1`](https://central.sonatype.com/artifact/com.lunixi/lunixi-spring-boot-starter/1.0.1)** koordinatını kullanın. Yayımlanmış **[`com.lunixi:lunixi-java-sdk:1.0.1`](https://central.sonatype.com/artifact/com.lunixi/lunixi-java-sdk/1.0.1)** bağımlılığı geçişli olarak çözülür.

Gradle:

```groovy
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-spring-boot-starter:1.0.1' }
```

Maven:

```xml
<dependency>
  <groupId>com.lunixi</groupId>
  <artifactId>lunixi-spring-boot-starter</artifactId>
  <version>1.0.1</version>
</dependency>
```

Maven standart Central deposunu kullanır. Yayımlanmış bağımlılığı kullanan uygulamada kaynak bootstrap, composite build veya `mavenLocal()` gerekmez.

### Geliştirme için kaynak, composite build ve yerel Maven

Starter'ın kendisini derlemek veya değiştirmek için incelenmiş kaynağı ve açık SDK bootstrap adımını kullanın:

```sh
git clone --depth 1 --branch v1.0.1 https://github.com/Lunixi-Payment/lunixi-spring-boot-starter.git
cd lunixi-spring-boot-starter
python3 tools/bootstrap-sdk.py
gradle build
```

JDK 17+, Gradle 9.7.1 ve Python 3.9+ gerekir. `tools/sdk-source.json` SDK deposunu, tam commit'i, sürümü ve SHA256 özetini sabitler. Araç yalnız bu değişmez HTTPS arşivini indirir; özeti, arşiv yolu/bağlantı güvenliğini kontrol edip `.sdk-cache/lunixi-java` altına atomik kurar. Her varsayılan Gradle açılışı arşivi ve tüm kaynak dosyalarını çevrimdışı yeniden doğrular; eksik, ek veya değiştirilmiş dosya reddedilir. Hareketli branch/latest kullanılmaz; Gradle örtük SDK indirmez. Önce bootstrap açıkça çalıştırılır. Manifest yalnız incelenmiş SDK yükseltmesinde değiştirilmelidir; eksik commit/özet indirmeden önce hata verir. Açık yerel kaynak override’ında da SDK sürümü `lunixiSdkVersion` ile eşleşmelidir.

Geliştirme uygulamasında Starter'ı incelenmiş kaynakla değiştirmek için `settings.gradle` dosyası:

```groovy
includeBuild('/absolute/path/to/lunixi-spring-boot-starter')
```

Uygulamanın `build.gradle` dosyası:

```groovy
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-spring-boot-starter:1.0.1' }
```

Starter'ın doğrulanmış kaynak SDK'sı composite build ile bağımlılığı sağlar. Yerel Maven yolu gerekiyorsa önce SDK'yı, sonra Starter'ı kurun:

```sh
gradle -p .sdk-cache/lunixi-java publishToMavenLocal -PlunixiUnsigned
gradle publishToMavenLocal -PlunixiUnsigned
```

Yalnız bu yerel geliştirme kurulumunu kullanan Gradle tüketicisinde `mavenLocal()` eklenir; Maven kendi yerel deposunu otomatik okur. İmzasız geliştirme komutları uzak kayıt deposuna yayın yapmaz. `-PlunixiSdkSource=/absolute/reviewed-sdk` yalnız açık geliştirme override'ıdır; değişmez kaynak doğrulamasını bilerek atlar ve kaynağı geliştirici inceler. CI/sürüm kabulü varsayılan doğrulanmış public pin'i kullanır. [Doğrulama ayrıntıları](docs/verification.md).

## Korumalı sunucu ayarları

```yaml
lunixi:
  base-url: https://api-gateway.lunixi.com
  allowed-origins:
    - https://api-gateway.lunixi.com
  key-id: ${LUNIXI_KEY_ID}
  private-key: ${LUNIXI_PRIVATE_KEY}
  environment: TEST
  timeout: 30s
  webhook-secret: ${LUNIXI_WEBHOOK_SECRET}
  storage-key: ${LUNIXI_STORAGE_KEY}
  jdbc-ledger-enabled: true
  table-prefix: lunixi_
```

Gateway tam HTTPS origin ve açık operatör allowlist'i ister; userinfo/path/query/fragment kabul edilmez. Anahtarlar yalnız sunucuda kalır. Lunixi'ye yalnız açık Ed25519 anahtarı yüklenir. Varsayılan TEST ve 30 saniyedir; mevcut denemeler için eski ortam/profil saklanır. Webhook secret ayrı uç sırrıdır. Storage key, bağımsız rastgele 32 bayt anahtarın base64 değeridir; DB/web kökü dışında ayrıca yedeklenir. JDBC ledger varsayılan kapalıdır, açılırsa gerçek DataSource gerekir. Tablo öneki sıkı doğrulanır.

Merchant kimliğini sunucudaki `OrderSnapshot` içine kayıtlı siparişten verin; starter müşteri/mağaza sahipliğini tarayıcıdan çıkarmaz. İlgili özel bean'ler varsayılan `LunixiClient`, `TokenStore`, `HttpTransport`, `LedgerStore` ve diğer yapılandırmaları değiştirebilir. Starter'da ağ tekrarları sıfırdır. Yapılandırmanın metin tanısı anahtar değerlerini gizler.

## Açık şema kurulumu ve transaction

JDBC ledger tam aynı DataSource'un Spring transaction yöneticisini ister. H2, PostgreSQL ve MySQL/MariaDB için SQL yolları vardır; yerel kabul H2 ve MariaDB 11 üzerinde yapılır. Diğer sürücü/diyalektleri üretim şemanızla ayrıca doğrulayın. MySQL/MariaDB yerel sipariş ve eklenti tablolarının tamamı InnoDB olmalıdır. Açık `install()` yalnız kendi tablolarını InnoDB oluşturur; mevcut yerel motorları değiştirmez.

```java
// Yalnız operatörün açık migration/yönetim adımı; aktif transaction dışında.
ledger.install();
```

Yalnız `<prefix>attempts/events` kurulur; sipariş/stok/history/bildirim kuyruğu uygulamanızındır. `CommerceService.reconcile` callback'i aynı DataSource/bağlantıyla yerel yazmayı yapmalıdır. Dış/iç içe transaction ve transaction dışına kaçmış kayıt erişimi reddedilir. Rezervasyon ağdan önce commit etmelidir; `CommerceService.start` çağrısını geniş bir `@Transactional` controller bloğuna sarmayın.

Olay ID/özeti kalıcıdır; aynı teslimat bir kez uygulanır, aynı ID/farklı gövde reddedilir. Olay ve yerel yazma birlikte geri alınır. Olaysız tekrarlanan GET kanıtı için uygulamanın yerel işlemi ayrıca idempotent olmalıdır. Mevcut tutar/kimlikleri kilit altında yeniden denetleyin; fraud/manual teslim blokelerini koruyun. Bildirimleri transaction'da kuyruğa alın, commit sonrası gönderin. Finansal işlem içinde e-posta/sevkiyat başlatmayın. Geçmişi, anahtarları ve olay kimliklerini saklama süresi boyunca koruyun.

## Webhook, form ve finansal yetki

`MerchantWebhooks.verify(byte[],Map<String,List<String>>)` JSON'dan önce 1 MiB sınırı, UTF-8 ve yinelenen imza başlıklarını denetleyip strict merchant zarfını doğrular. Proxy ham baytları korumalıdır. Rezervasyonu yalnız kayıtlı bağ veya sıkı ayrıştırılmış imzalı adayla bulun. Commerce strict `payment.*` ve tam ham gövde özeti ister, kimlik doğrulamalı ödeme/çoklu kart GET'i alır. Tarayıcı alanları ödeme kanıtı değildir. Yalnız yerel commit sonrası 2xx verin; hatada yeniden denenebilir başarısız yanıt dönün.

Ön provizyon, kısmi tahsilat, eksik çoklu kart, belirsiz/FAILED veya yerel teslim blokesi yeni anahtar/teslim yetkisi vermez. SDK oluşturma rezervasyonu ve 23 saat tekrar sınırını yönetir; **kalıcı tahsilat/iade/iptal kuyruğu değildir**. Bu işlemlerin ID, gövde, zaman ve anahtarını uygulama kalıcılaştırmalı; belirsizlikte farklı ID'yi engellemeli, güvenli tekrar sınırı ve GET bakiye teyidi uygulamalıdır. Örnek uygulama finansal yönetim HTTP ucu açmaz.

Yönlendirmeli form pay.lunixi.com, gömülü form resmî Lunixi object/token/mount sözleşmesiyle açılır. Yöntemler, taksit, yemek kartı, alışveriş kredisi, sigorta ve çoklu kart Lunixi paneli/formundan yönetilir; kart numarası/CVV sunucuya alınmaz. Yönlendirmeli/çoklu kart/3DS dönüşü POST'tur. Yalnız sınırlı bilgilendirici POST, alanları yok sayıp sahiplik denetimli aynı-site GET'e 303 dönebilir; checkout/yönetici auth/CSRF koruması korunur. Çalışan örnek rota/çerez/CSP ayrıntılarını gösterir.

## Kabul kontrolleri

```sh
python3 tools/bootstrap-sdk.py
python3 -m unittest discover -s tools/tests -v
gradle --no-daemon -I tools/verification.init.gradle clean test spotbugsMain resolvedAuditDependencies jar sourcesJar javadocJar generatePomFileForStarterPublication
python3 tools/check-artifacts.py
python3 tools/audit-dependencies.py
python3 tools/check-secrets.py
```

Gerçek MariaDB testi için `LUNIXI_TEST_DB_FILE`, localhost üzerindeki ayrı `lunixi_` şemaya ait host/port/database/username/password JSON dosyasını göstermelidir. Canlı bilgi kullanmayın. Test yalnız kendisinin benzersiz önekli InnoDB tablolarını açıp kaldırır. CI ayrı MariaDB kurar ve testin çalışmasını zorunlu tutar; açık fixture yoksa yalnız bu entegrasyon testi atlanır. H2 rollback/kilit/olay testleri gerçek Spring JDBC kullanır. Örneğin MVC/HTTP/tarayıcı/bağımlılık testleri ayrıdır. Canlı ödeme veya Marketplace onayı iddiası yoktur.

Lisans: [MIT](LICENSE). [Katkı](CONTRIBUTING.md).


## Maven Central yayın paketi

Mevcut imzalama anahtarını GPG agent üzerinden açan geliştirici şu komutu çalıştırabilir:

```bash
gradle centralBundle -Psigning.gnupg.keyName=YOUR_PUBLIC_KEY_FINGERPRINT
```

Çıktı: `build/central/lunixi-spring-boot-starter-1.0.1-bundle.zip`. Maven dizin yapısında kütüphane, kaynak kod, Javadoc, POM, dosya özetleri ve ayrık imzalar bulunur. `-PlunixiUnsigned` yalnızca yerel paket yapısını incelemek içindir; Central’a yayımlanabilecek imzalı paket üretmez. Dosyanın yüklenmesi ve kayıt servisinden erişilebilir olması ayrıca doğrulanır.
