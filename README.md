# 📢 Toaster – Servis Bildirim Uygulaması

Bu uygulama belirlediğiniz servisleri düzenli aralıklarla kontrol eder ve **Windows’ta bildirim (toast)**, **Linux’ta masaüstü bildirimi (notify-send)** olarak gösterir.

Örn: Her 10 saniyede bir `/update` endpoint’ini çağırır, güncel bir mesaj varsa başlık–içerik–ikon bilgisiyle size bildirir.

---

## 🚀 Kurulum

1. Sistemin Windows 10+ olduğundan ve Java 17+ kurulu olduğundan emin olun.

   ```bash
   java -version
   ```
2. Maven ile projeyi paketleme (toaster klasörünün içerisinde):

   ```bash
   rm -f dependency-reduced-pom.xml
   rm -rf target
   mvn clean package
   ```
3. Çalıştır:

   ```bash
   java -Dconfig="services.json" -jar target/windows-notifier-1.0-all.jar
   ```


## ⚙️ services.json Yapısı

`services.json` dosyası, uygulamanın hangi servisleri izleyeceğini tanımlar.

Örnek:

```json
{
  "defaultPollIntervalSec": 10,
  "stateFile": "%APPDATA%/Toaster/state.json",
  "services": [
    {
      "name": "örnek-servis-1",
      "request": {
        "url": "http://localhost:5000/update?service=alpha",
        "method": "GET",
        "timeoutMs": 5000
      },
      "pollIntervalSec": 10,
      "parse": {
        "updatedField": "updated",
        "updatedIsTrue": true,
        "dataPath": "data",
        "idField": "id",
        "titleField": "title",
        "contentField": "content",
        "linkField": "link",
        "iconField": "icon"
      },
      "iconOverride": null,
      "enabled": true
    }
  ]
}
```

---

## 📝 Açıklamalar

* **defaultPollIntervalSec**: Varsayılan sorgulama aralığı (saniye).
* **stateFile**: Hangi bildirimlerin daha önce görüldüğünü saklayan dosya yolu.
* **services**: İzlenecek servis listesi.

Her servis için:

* **name**: Servise vereceğiniz isim (loglarda görünecek).
* **request.url**: Servisin sorgulanacak endpoint’i.
* **request.method**: `GET` veya `POST`.
* **request.timeoutMs**: Zaman aşımı (milisaniye).
* **pollIntervalSec**: Bu servisin özel kontrol aralığı (default’tan farklı olabilir).
* **parse**: Servisin cevabındaki alanlar.

  * `updatedField`: Güncelleme var mı yok mu bilgisinin olduğu alan.
  * `dataPath`: Güncelleme objesinin yolu.
  * `idField`, `titleField`, `contentField`, `linkField`, `iconField`: Bildirim için kullanılacak alan isimleri.
* **iconOverride**: Buraya URL ya da dosya yolu yazılırsa, servis ne dönerse dönsün bu ikon kullanılır.
* **enabled**: Servis aktif mi? (`true`/`false`)

---

## 📌 Notlar

* Servis endpoint’i JSON dönmeli ve en azından `updated` alanını içermeli.
* Aynı id’li bildirim tekrar edilmez (uygulama en son gösterilen id’yi saklar).
* Servis kapalıysa uygulama hata vermez, sadece log’a yazar ve denemeye devam eder.
* Windows’ta bildirimler **Action Center**’a düşer, Linux’ta `notify-send` kullanılır.
