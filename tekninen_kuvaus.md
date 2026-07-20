# KalaKartta - Tekninen kuvaus

## Yleiskuvaus
KalaKartta on Android-sovellus, joka on suunniteltu kalastajien avuksi saaliiden ja kalastuspaikkojen dokumentointiin. Sovelluksen avulla käyttäjä voi merkitä kartalle saaliita, kiinnostavia paikkoja (POI) ja seurata kalastusreissujen tietoja. Sovellus tukee myös säätyökalujen käyttöä ja tarjoaa yhteenvetoja saalistilastoista.

## Tekniikat ja kirjastot
- **Kieli:** Kotlin
- **Käyttöliittymä:** Android XML Layouts
- **Karttakirjasto:** [osmdroid](https://github.com/osmdroid/osmdroid) - Karttojen näyttämiseen ja käsittelyyn.
- **Tietokanta:** Room Persistence Library - Paikallinen SQLite-tietokanta tietojen tallentamiseen.
- **Sääpalvelu:** OpenWeatherMap API (säätietojen hakuun).
- **Arkkitehtuuri:** Perinteinen Android-arkkitehtuuri, jossa logiikka on jaettu Manager-luokkiin ja DAO-kerrokseen.

## Tärkeimmät tiedostot ja hakemistorakenne

### Sovelluksen ydin
- `MainActivity.kt`: Sovelluksen pääikkuna, joka hallitsee karttanäkymää, mittakaavaa ja päävalikkoja.
- `app/src/main/AndroidManifest.xml`: Sovelluksen asetukset, oikeudet ja aktiviteettien määrittelyt.

### Tietomallit ja tietokanta (`fi.anssi.kalakartta.data`)
- `AppDatabase.kt`: Room-tietokannan pääluokka.
- `FishCatch.kt`: Saalismerkinnän tietomalli (laji, paino, pituus, koordinaatit, sää jne.).
- `FishSpecies.kt`: Kalalajien määrittelyt.
- `PlaceOfInterest.kt`: Kiinnostavien paikkojen (kivet, tulipaikat, rannat) tietomalli.
- `FishCatchDao.kt`: Rajapinta saaliiden tietokantaoperaatioille.

### Käyttöliittymä ja logiikka (`fi.anssi.kalakartta.ui`)
- `MarkerManager.kt`: Hallitsee kartalla näkyviä merkkejä (saaliit ja POI-pisteet) ja niiden klusterointia.
- `CatchManager.kt`: Hallitsee saaliiden lisäämistä ja muokkaamista.
- `SummaryActivity.kt`: Raportointityökalu, joka laskee ja näyttää tilastoja saaliista tietyllä aikavälillä.
- `FilterActivity.kt`: Mahdollistaa kartalla näkyvien ja yhteenvedossa käytettävien tietojen suodattamisen.
- `TripNotesActivity.kt`: Reissumuistiinpanojen hallinta.

### Apuohjelmat ja integraatiot (`fi.anssi.kalakartta.utils` & `fi.anssi.kalakartta.io`)
- `MMLTileSource.kt`: Maanmittauslaitoksen (MML) kartta-aineistojen (maastokartta, ilmakuva) integraatio.
- `WeatherService.kt`: Rajapinta säätietojen hakuun.
- `ImportExportManager.kt` & `JsonService.kt`: Tietojen vienti ja tuonti JSON-muodossa.

## Tärkeimmät toiminnot
1. **Kartanhallinta:** Tukee OpenStreetMap- ja MML-karttoja. Sisältää dynaamisen mittakaavajanan.
2. **Saaliiden kirjaus:** Mahdollisuus tallentaa laji, koko, paikka, aika, kalastaja ja sääolosuhteet.
3. **Paikkamerkinnät:** POI-pisteiden (esim. matalikot, karkuutukset, nuotiopaikat) tallentaminen eri kuvakkeilla.
4. **Suodatus:** Saaliita voidaan suodattaa lajin, kalastajan tai ajan mukaan.
5. **Tilastot:** Automaattinen yhteenveto saaliiden lukumääristä, keskipainoista ja -pituuksista.
6. **Varmuuskopiointi:** Mahdollisuus viedä ja tuoda saalisdataa JSON-tiedostoina.

## Resurssit
- `app/src/main/res/drawable-nodpi/`: Sisältää kalalajien ja paikkamerkkien kuvakkeet (esim. `ahven.png`, `hauki.png`, `lahna.png`).
- `app/src/main/res/layout/`: Käyttöliittymän XML-määrittelyt.
