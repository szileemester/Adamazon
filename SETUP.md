# Adamazon – beállítási útmutató

Ezeket a lépéseket neked kell elvégezned, mert a saját Google-fiókodhoz kötődnek. A sorrend számít.

## 1. Firebase projekt (kb. 10 perc)

1. Nyisd meg a <https://console.firebase.google.com> oldalt → **Projekt létrehozása** → név: `adamazon` (a Google Analytics kikapcsolható).
2. **Android-alkalmazás hozzáadása**:
   - Csomagnév: `hu.szileemester.adamazon`
   - Becenév: Adamazon
   - **SHA-1 (debug)**: `C9:FF:73:0B:0A:77:C1:9D:B2:4D:2E:78:A3:EC:29:6C:64:55:96:B7`
     (ez a gépeden lévő debug kulcsé, az Android Studióból futtatott apphoz kell)
3. Töltsd le a `google-services.json` fájlt, és másold az `app/` mappába (`app/google-services.json`).
   - A fájl **nincs a gitben** (`.gitignore`), így nem kerül fel GitHubra. Új gépen vagy friss klónozás után
     innen kell újra letölteni: Projektbeállítások (fogaskerék) → Általános → Saját alkalmazások → **google-services.json**.
     Nélküle a build hibával leáll („File google-services.json is missing”).
4. **Authentication** → Kezdés → Bejelentkezési módok → **Google** → Engedélyezés → támogatási e-mail: a saját címed → Mentés.
   - Ezután töltsd le újra a `google-services.json`-t (most már benne lesz a webes kliensazonosító, `"client_type": 3`), és cseréld le ismét.
5. **Firestore Database** → Adatbázis létrehozása → hely: `eur3 (europe-west)` → **éles módban** (production mode) indítsd.

## 2. Biztonsági szabályok telepítése

A projekt mappájában (`D:\Claude\Adamazon`):

```bash
npm install -g firebase-tools
```

```bash
firebase login
```

```bash
firebase use --add
```
(válaszd ki az előbb létrehozott projektet, alias: `default`; a választás a helyi `.firebaserc` fájlba kerül, ez sincs a gitben)

```bash
firebase deploy --only firestore:rules
```

## 3. Engedélyezett felhasználók

Firestore → **Gyűjtemény indítása** → azonosító: `allowedUsers`
- Dokumentumazonosító: a Google-fiók e-mail címe **kisbetűvel**, pl. `szileemester@gmail.com`
- Mező: `displayName` (string) – tetszőleges név

Ismételd meg a második felhasználóval. Később ugyanígy vehetsz fel újakat, az app újratelepítése nélkül.

## 4. Kipróbálás

Android Studio → **Open** → `D:\Claude\Adamazon` → futtatás emulátoron vagy USB-n csatlakoztatott telefonon.
Az első belépéskor az app létrehozza az alapkategóriákat.

## 5. Google Play – belső tesztelés

1. Feltöltési kulcs létrehozása (egyszer; a jelszót **jegyezd fel biztonságos helyre**, a fájlról legyen mentés):
   ```bash
   "C:\Program Files\Android\Android Studio\jbr\bin\keytool" -genkeypair -v -keystore D:\Claude\adamazon-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Hozd létre a `D:\Claude\Adamazon\keystore.properties` fájlt (git nem követi):
   ```
   storeFile=../adamazon-upload.jks
   storePassword=<a jelszavad>
   keyAlias=upload
   keyPassword=<a jelszavad>
   ```
3. AAB készítése:
   ```bash
   gradlew bundleRelease
   ```
   Eredmény: `app/build/outputs/bundle/release/app-release.aab`
4. <https://play.google.com/console> → fejlesztői fiók regisztrálása (egyszeri 25 USD, személyazonosság-ellenőrzés).
5. **Alkalmazás létrehozása** → név: Adamazon, ingyenes app.
6. **Tesztelés → Belső tesztelés** → Tesztelők: e-mail lista a két címmel → **Új kiadás** → töltsd fel az AAB-t.
   A Play App Signing-et fogadd el (alapértelmezett).
7. **Fontos:** Play Console → Beállítás → **Alkalmazásaláírás** → másold ki az *alkalmazásaláíró kulcs* **SHA-1** ujjlenyomatát,
   és add hozzá a Firebase-ben: Projektbeállítások → Android-app → **Ujjlenyomat hozzáadása**.
   Enélkül a Play-ről telepített appban nem működik a Google-bejelentkezés.
8. A Play Console kérheti az *Alkalmazás tartalma* részt (adatvédelmi irányelvek, adatbiztonság). Rövid válasz:
   az app Google-fiók e-mail címet és a felhasználó által beírt listaelemeket/képeket tárol a Firebase-ben,
   harmadik félnek nem adja át, a fiók törlése kérésre lehetséges.
9. A tesztelési oldalon kapott **meghívó linket** küldd el a másik felhasználónak: elfogadás után a Play Áruházból telepíthető, és onnan frissül.

## Fejlesztői parancsok

| Mit | Parancs |
|---|---|
| Egységtesztek | `gradlew testDebugUnitTest` |
| Biztonsági szabályok tesztje (emulátorral) | `cd firestore-tests && npm install && npm test` |
| Debug APK | `gradlew assembleDebug` |

A parancssori Gradle-hez a `JAVA_HOME` legyen `C:\Program Files\Android\Android Studio\jbr`.
