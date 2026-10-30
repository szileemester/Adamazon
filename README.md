# Adamazon

Közös, Google Keep-szerű bevásárlólista Androidra, engedélyezett Google-fiókoknak.

- **Listák**: üzletenként egy lista (boltok, csomagautomaták), Keep-szerű rács, húzással rendezhető.
- **Elemek**: gyors bevitel („2 kg paradicsom”), automatikus kategória és emoji, kipipált elemek összecsukva.
- **Tanulás**: a kézi kategória-javításokat megjegyzi; a kategóriák sorrendjét üzletenként a pipálás sorrendjéből tanulja.
- **Kártyák**: hűségkártyák képként, teljes képernyőn, maximális fényerővel.
- **Valós idő + offline**: Firebase Firestore.

Beállítás: [SETUP.md](SETUP.md).

## Felépítés

| Mappa | Tartalom |
|---|---|
| `app/src/main/java/.../domain` | Tiszta Kotlin logika (mennyiség-felismerés, kategorizálás, sorrendtanulás) – egységtesztelt |
| `app/src/main/java/.../data` | Firestore repositoryk, bejelentkezés, képtömörítés, logóletöltés |
| `app/src/main/java/.../ui` | Jetpack Compose képernyők |
| `app/src/main/assets` | Termékszótár (`dictionary.json`), üzlet-presetek (`store_presets.json`) |
| `firestore.rules`, `firestore-tests/` | Biztonsági szabályok és tesztjeik |
