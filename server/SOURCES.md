# Spam ve arayan kimliği kaynakları

Uygulama birincil olarak kendi sunucumuzu (`better-call-soul/server`) kullanır. Ayar
"Bulut spam ve arayan kimliği" açıkken, rehberde olmayan numarayı buluta sorar. İstemci
(`SpamClient`) birincil sunucunun yanında yedek sunucuları da sırayla dener; biri cevap
vermezse diğerine geçer. Yedekler `bcs.apiBackupUrls` Gradle özelliğine virgülle yazılır
ve bizim API biçimimizle (`/api/lookup`, `/api/report`) aynı olmalıdır.

## Doğrudan yedek (uyumlu)
Bunlar bizim sunucumuzun kopyalarıdır, aynı sözleşmeyi konuşur, anahtar gerektirmez:
- Aynı kodun ikinci bir Vercel dağıtımı (örneğin farklı bölge: fra1 + hnd1).
- Kendi sunucunda (VPS) çalışan bir kopya.

## Dış kaynaklar (uyumlu DEĞİL, adaptör gerekir)
Hiçbiri bizim biçimimizi konuşmaz; kullanmak için sunucumuza, isteği/yanıtı çeviren bir
adaptör yazmak gerekir. Çoğu API anahtarı ister ve ücretlidir.

- **FCC şikayet verisi** (opendata.fcc.gov, Socrata): Açık ve ücretsiz, ama yalnızca
  ABD/Kanada numaraları ve "şikayet" verisi; Türkiye için işe yaramaz.
  Örnek proje: https://github.com/DrCaiola/spam-call-lookup
- **Twilio Lookup**: Ücretli, numara başına. https://www.twilio.com/en-us/user-authentication-identity/lookup
- **IPQualityScore Phone Lookup**: Ücretsiz katman + ücretli. https://www.ipqualityscore.com/free-phone-number-lookup
- **SkipCalls spam-check API**: ABD odaklı, ücretsiz katman iddiası doğrulanmadı.
  https://skipcalls.com/blog/free-spam-check-api

## Türkiye için not
Truecaller, Getcontact ve TürkCaller kullanıcı bildirimine dayalı veritabanları işletir,
ama HERKESE AÇIK bir sorgu API'si sunmazlar; yedek olarak bağlanamayız. Gerçekçi yol kendi
kalabalık kaynaklı veritabanımızı büyütmek; dış kaynak bir yedek değil, olsa olsa ileride bir
zenginleştirme.
