# 📢 Toaster – Servis Bildirim Uygulaması

Bu uygulama belirlediğiniz servisleri düzenli aralıklarla kontrol eder ve **Windows’ta bildirim (toast)**, **Linux’ta masaüstü bildirimi (notify-send)** olarak gösterir.

Örn: Her 10 saniyede bir `/update` endpoint’ini çağırır, güncel bir mesaj varsa başlık–içerik–ikon bilgisiyle size bildirir.

---

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



---

## 📌 Notlar

* Servis endpoint’i JSON dönmeli ve en azından `updated` alanını içermeli.
* Aynı id’li bildirim tekrar edilmez (uygulama en son gösterilen id’yi saklar).
* Servis kapalıysa uygulama hata vermez, sadece log’a yazar ve denemeye devam eder.
* Windows’ta bildirimler **Action Center**’a düşer, Linux’ta `notify-send` kullanılır.






version: "3.8"

services:
  # --- MinIO (S3 storage) ---
  minio:
    image: minio/minio:latest
    container_name: minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"   # S3 API
      - "9001:9001"   # Web console
    environment:
      MINIO_ROOT_USER: minio
      MINIO_ROOT_PASSWORD: minio123
    volumes:
      - minio_data:/data

  # --- Neo4j (Graph DB) ---
  neo4j:
    image: neo4j:5.25
    container_name: neo4j
    ports:
      - "7474:7474"   # HTTP Browser
      - "7687:7687"   # Bolt driver
    environment:
      NEO4J_AUTH: neo4j/test123
    volumes:
      - neo4j_data:/data

  # --- OpenLDAP (Directory server) ---
  ldap:
    image: osixia/openldap:1.5.0
    container_name: ldap
    hostname: ldap
    restart: always
    ports:
      - "389:389"
      - "636:636"
    environment:
      LDAP_ORGANISATION: "MyOrg"
      LDAP_DOMAIN: "myorg.com"
      LDAP_ADMIN_PASSWORD: admin
      LDAP_BASE_DN: "dc=myorg,dc=com"
    volumes:
      - ldap_data:/var/lib/ldap
      - ldap_config:/etc/ldap/slapd.d

  # --- Graylog (Log management) ---
  mongodb:
    image: mongo:6.0
    container_name: mongodb
    volumes:
      - mongo_data:/data/db

  graylog:
    image: graylog/graylog:5.2
    container_name: graylog
    depends_on:
      - mongodb
      - elasticsearch  # assumes you already have ES running in another container
    environment:
      GRAYLOG_PASSWORD_SECRET: "somepasswordpepper"   # must be at least 16 chars
      GRAYLOG_ROOT_PASSWORD_SHA2: "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918" 
      # that's "admin" hashed
      GRAYLOG_HTTP_EXTERNAL_URI: "http://127.0.0.1:9009/"
    entrypoint: >
      sh -c "echo 'Waiting for Elasticsearch...' &&
             sleep 10 &&
             /usr/bin/tini -- wait-for-it elasticsearch:9200 -- /docker-entrypoint.sh"
    ports:
      - "9009:9000"   # Web UI
      - "12201:12201/udp" # GELF input

volumes:
  minio_data:
  neo4j_data:
  ldap_data:
  ldap_config:
  mongo_data: