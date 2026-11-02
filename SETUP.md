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

## 3. Csoportok és felhasználók

Minden felhasználó egy csoportba (háztartásba) tartozik, és csak a csoportja boltjait, listáit, kártyáit és kategóriáit látja. Ezt a Firestore szabályok kényszerítik ki. Mindent a Firebase konzolban, kézzel állítasz be, az app újratelepítése nélkül.

**Új csoport:** Firestore → `groups` gyűjtemény → **Dokumentum hozzáadása**
- Dokumentumazonosító: beszédes, rövid, ékezet nélküli név, pl. `kovacs-csalad`. Később nem nevezhető át, de sehol nem látszik.
- Mező: `name` (string), ez jelenik meg az appban, pl. `Kovács család`. **Átnevezés:** csak ezt a mezőt kell átírni, és az app azonnal az új nevet mutatja.

Az új csoport üresen, az alap kategóriákkal indul.

**Új felhasználó:** Firestore → `allowedUsers` gyűjtemény → **Dokumentum hozzáadása**
- Dokumentumazonosító: a Google-fiók e-mail címe **kisbetűvel**, pl. `szileemester@gmail.com`
- Mező: `displayName` (string), tetszőleges név, a csoport taglistájában látszik
- Mező: `groupId` (string), a csoport dokumentumazonosítója, pl. `kovacs-csalad`

Ha a `groupId` hiányzik, a felhasználó a „Nincs csoporthoz rendelve” képernyőt látja. Amint beírod, az app magától továbblép. A felhasználót a Play Console belső tesztelői közé is fel kell venni (5. lépés).

**Áthelyezés másik csoportba:** írd át a `groupId` mezőt, és az app azonnal átvált. Csak a személy költözik: a boltok és listák a régi csoportnál maradnak.

**Kizárás:** töröld az `allowedUsers` dokumentumot, és a hozzáférés azonnal megszűnik.

**A csoportok előtti adatok** (egyszeri): a régi, legfelső szintű `categories`, `stores`, `learned` és `cards` gyűjteményeket az a csoport veszi át, amelynek a dokumentumában `legacyImport` (boolean) `true`. Az app az első induláskor átmásolja őket, majd beállítja a `migratedAt` mezőt. A régi gyűjtemények csak olvashatók maradnak. Ha az átmásolt adatok rendben vannak, törölhetők a konzolban.

## 4. Kipróbálás

Android Studio → **Open** → `D:\Claude\Adamazon` → futtatás emulátoron vagy USB-n csatlakoztatott telefonon.
Az első belépéskor az app létrehozza az alapkategóriákat.

## 5. Google Play – belső tesztelés

1. Feltöltési kulcs létrehozása (egyszer; a jelszót **jegyezd fel biztonságos helyre**, a fájlról legyen mentés).
   A titkok helye a projektben a `.secrets\` mappa, amit a git soha nem követ:
   ```bash
   "C:\Program Files\Android\Android Studio\jbr\bin\keytool" -genkeypair -v -keystore D:\Claude\Adamazon\.secrets\adamazon-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Hozd létre a `D:\Claude\Adamazon\.secrets\keystore.properties` fájlt (az útvonal a projekt gyökeréhez képest értendő):
   ```
   storeFile=.secrets/adamazon-upload.jks
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
   Az oldal helye: Play Console → **Alkalmazásvédelem** → Google Play Áruház-védelem → Play alkalmazásaláírás.
   Ugyanezt a SHA-1-et vedd fel az API-kulcs korlátozásai közé is:
   <https://console.cloud.google.com/apis/credentials> → **Android key (auto created by Firebase)** →
   Application restrictions → Android apps → **Add** (csomagnév + SHA-1) → Save.
   Ha ez kimarad, bejelentkezéskor ez a hiba jelenik meg: *„Requests from this Android client application … are blocked”*.
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
