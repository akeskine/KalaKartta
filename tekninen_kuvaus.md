# KalaKartta – tekninen kuvaus

## Yleiskuvaus

KalaKartta on Kotlinilla toteutettu Android-sovellus saaliiden, kalastuspaikkojen, kalastussessioiden ja kalapäiväkirjamerkintöjen tallentamiseen. Sovellus toimii ensisijaisesti paikallisena sovelluksena: käyttäjän data tallennetaan laitteen Room/SQLite-tietokantaan ja verkkoyhteyttä tarvitaan lähinnä karttalaattoihin sekä säätietojen hakuun.

## Tekniikat ja riippuvuudet

- **Kieli:** Kotlin.
- **Käyttöliittymä:** Androidin XML-layoutit ja Activity-/dialog-pohjaiset näkymät.
- **Kartta:** `osmdroid`; tuettuja karttalähteitä ovat OpenStreetMap, Maanmittauslaitoksen aineistot sekä Traficomin kartta-aineistot.
- **Tietokanta:** Room Persistence Library paikallisen SQLite-tietokannan päällä. Tietokannan nykyinen versio on `23`.
- **Sääpalvelu:** Ilmatieteen laitoksen avoin data `WeatherService.kt`-palvelun kautta.
- **Asynkronisuus:** Kotlin coroutines, Activityn `lifecycleScope` ja `Dispatchers.IO` tietokanta- ja verkkotyölle.
- **Dataformaatit:** JSON sekä useita tiedostoja sisältävä ZIP-vienti ja -tuonti.
- **Arkkitehtuuri:** Perinteinen Android-arkkitehtuuri, jossa Activityt ja dialogit koordinoivat käyttöliittymää, erilliset controller-/manager-luokat hoitavat käyttötapauksia ja Room-DAOt tietokantaoperaatioita.

## Arkkitehtuuri ja tietovirta

`MainActivity` alustaa tietokannan, kartan ja käyttöliittymän controllerit. `SettingsManager` avaa asetusten alavalikot ja käynnistää erilliset Activityt esimerkiksi suodatukselle, yhteenvedolle, kalastussessioille ja kalapäiväkirjalle. Kartan pisteet, sessiot ja päiväkirjasivut luetaan DAO-rajapinnoista; raskas työ tehdään coroutineissa IO-säikeellä ja tulokset välitetään takaisin pääsäikeelle.

Kaikki pysyvä käyttäjädata on paikallista. `AppDatabase` sisältää muun muassa `FishCatch`-, `FishSpecies`-, `PlaceOfInterest`-, `FishingSession`-, `TrackPoint`-, `Media`- ja `FishDiaryPage`-entiteetit sekä sää- ja päivityslokit. Tietokannan migraatiot on määritelty `AppDatabase.kt`-tiedostossa.

## Kalapäiväkirjan toteutus

### Tietomalli ja käyttöliittymä

- `FishDiaryPage.kt` sisältää sivun tunnisteen, alku- ja valinnaisen loppupäivän sekä kentät `location`, `fishingMethod`, `catch` ja `story`.
- `DiaryActivity.kt` näyttää kuukauden kalenterin ja valitun päivän sivut. Päivä kuuluu monipäiväiseen sivuun, jos se osuu alku- ja loppupäivän välille.
- Päiväkirjan päivämäärät käsitellään käyttäjälle Suomen aikavyöhykkeellä (`Europe/Helsinki`).
- Kalenterin, päiväkirjasivujen ja hakutulosten sisältö on yhteisen `ScrollView`-vieritysalueen sisällä molemmissa näyttötiloissa. Sivukortin avaaminen näyttää kentät ja kalenterinäkymässä myös päivään liittyvän median.
- `EditDiaryPageActivity.kt` lisää ja muokkaa sivuja, hallitsee yksi- ja monipäiväisiä merkintöjä, liittää mediaa sekä muodostaa saalis- ja kertomustekstiä valitun aikavälin saaliista ja kalastussessioista.

### Hakutoiminto

`FishDiaryPageDao` tarjoaa `getAll()`- ja tunnistehaun lisäksi Roomin `@RawQuery`-rajapinnat sivutettuun hakuun ja kokonaismäärään. Hakulausekkeet muodostaa `FishDiaryPageSearchQuery.kt`.

- Hakusana pilkotaan välilyöntien kohdalta termeiksi. Kaikkien termien on osuttava, mutta eri termit voivat löytyä eri kentistä.
- Tekstihaku kohdistuu sarakkeisiin `location`, `fishingMethod`, `catch` ja `story`. Haku käyttää kirjainkoon ja suomalaisten ääkkösten normalisointia sekä SQL `LIKE` -osahakua. Media- tai tiedostonimiä ei haeta.
- Muotoa `d.M.yyyy` oleva termi tulkitaan yksittäiseksi päiväksi ja nelinumeroinen termi vuodeksi. Päivämäärähaku käyttää aikavälien päällekkäisyyttä, joten monipäiväinen sivu löytyy myös sen keskeltä haetulla päivällä.
- `FishDiarySearchDictionary.kt` lataa alias- ja yhdyssanaston tiedostosta `app/src/main/assets/fish_diary_search_dictionary.txt`. Tiedostoa voi täydentää ilman hakulogiikan muuttamista muodossa `Näyttönimi|alias1,alias2`. Esimerkiksi `hauki`-haun sanastoon kuuluu `hauenkalastus`.
- Tulokset järjestetään uusimmat ensin (`startDate DESC, id DESC`) ja haetaan 50 sivun erissä `LIMIT`/`OFFSET`-kyselyillä. Ensimmäinen haku suorittaa erillisen `SELECT COUNT(*)` -määräkyselyn.
- **Lataa lisää** näytetään vain, kun viimeisin sivu oli täysi ja kokonaismäärästä on vielä tuloksia jäljellä. Uudet sivut lisätään samaan vieritettävään sisältöön; kaikkia hakutuloksia ei ladata yhdellä kertaa Kotlin-listaksi.
- Hakutila ja hakusana palautetaan Activityn uudelleenluonnissa. Vanhojen hakupyyntöjen myöhästyneet vastaukset ohitetaan pyyntötunnisteen avulla. Muokkaus tai poisto käynnistää aktiivisen haun uudelleen.

Hakutoiminto ei käytä FTS5:ttä. Nykyinen `LIKE`-toteutus ja repossa ylläpidettävä sanasto ovat tarkoituksellinen kevyt ratkaisu; erittäin suurilla sivumäärillä tekstihaku voidaan myöhemmin siirtää FTS-indeksiin.

## Tärkeimmät tiedostot

### Sovelluksen ydin ja kartta

- `MainActivity.kt`: pääikkuna, kartta, päänavigointi ja controllerien elinkaari.
- `MapDisplayController.kt`: karttalähde, kartan näyttöasetukset ja heatmap-/reittinäkyvyys.
- `MarkerManager.kt`: saalis- ja paikkamerkit, klusterointi sekä markereiden päivitys.
- `FishingSessionController.kt` ja `ReplayMapController.kt`: sessioiden tallennuksen ja toiston käyttöliittymälogiikka.

### Data ja DAOt (`fi.anssi.kalakartta.data`)

- `AppDatabase.kt`: Room-tietokanta ja migraatiot.
- `FishCatch.kt`, `FishSpecies.kt`, `PlaceOfInterest.kt`, `FishingSession.kt`, `TrackPoint.kt`, `Media.kt` ja `FishDiaryPage.kt`: tietomallit.
- `FishCatchDao.kt`, `FishingSessionDao.kt`, `MediaDao.kt` ja `FishDiaryPageDao.kt`: tietokantaoperaatiot.
- `DiaryPageJsonMapper.kt`, `JsonService.kt` ja `ImportExportManager.kt`: päiväkirjan ja muun datan JSON/ZIP-siirrot.

### Käyttöliittymä ja käyttötapaukset (`fi.anssi.kalakartta.ui`)

- `DiaryActivity.kt`: kalenterin, päiväkirjasivujen ja haun näkymä.
- `EditDiaryPageActivity.kt`: päiväkirjasivun luonti ja muokkaus.
- `FilterActivity.kt`: kartta- ja yhteenvetonäkymien suodatus.
- `SummaryActivity.kt`: saalistilastot.
- `SettingsManager.kt` ja asetusten erilliset dialogit: sovelluksen asetusten navigointi.
- `CatchManager.kt`, `WeatherController.kt` ja `LocationController.kt`: karttanäkymän keskeiset käyttötapaukset.

### Palvelut ja apuluokat

- `WeatherService.kt`: FMI-sääasemien ja sää- sekä painehistoriatietojen haku.
- `FishingSessionService.kt`: taustalla toimiva kalastussession sijainti- ja reittitallennus.
- `TalkingClockService.kt`: puhuvan kellon foreground-palvelu.
- `FishDiaryPageSearchQuery.kt`: päiväkirjahaun kriteerien jäsennys ja SQL-kyselyjen muodostus.
- `FishDiarySearchDictionary.kt`: repossa ylläpidettävän päiväkirjahakusanaston lataus ja normalisointi.

## Tiedonsiirto ja media

`ImportExportManager` tukee yksittäisten piste-, reitti-, media- ja kalapäiväkirjatiedostojen sekä koko sovelluksen ZIP-varmuuskopion vientiä ja tuontia. Päiväkirjasivut tallennetaan `paivakirja.json`-tiedostoon. Tuonnissa uudet sivut saavat uuden paikallisen tunnisteen, jotta olemassa olevien sivujen tunnisteet eivät törmää.

Media säilytetään erillään tietokannan tekstikentistä. Päiväkirjasivun editori hakee päivämäärään liittyvän median, mutta hakukone ei etsi median nimistä eikä lataa kaikkien hakutulosten mediaa etukäteen.

## Reittien näyttö ja suorituskyky

`FishingHeatmapOverlay` piirtää kartalle kalastussessioiden reittiviivat heat map -näkymän yhteydessä. Näyttöasetukset löytyvät polusta **Kalastetut alueet -> Reittien lisäasetukset**.

- **Häivytä vanhat reittiviivat** suodattaa reittejä niiden iän perusteella. Uudemmat reitit piirretään täydellä peittävyydellä, asetettujen päiväarvojen välissä olevat reitit piirretään asteittain läpikuultavampina ja aloitusrajan ylittäneet reitit jätetään piirtämättä.
- `routesFadeStartDays` määrittää, kuinka vanhoja reittejä vielä piirretään ja mistä kohdasta häivytys alkaa. `routesFadeFullDays` määrittää iän, johon asti reitti on täysin näkyvä. Oletusarvot ovat vastaavasti 365 ja 30 päivää.
- Reittien suorituskykyä voidaan parantaa pienentämällä `routesFadeStartDays`-arvoa, jolloin kartalle piirretään vain tuoreemmat reitit. Lisäksi `FilterManager`in aika- ja aluesuodattimet rajaavat reittipisteitä ennen piirtämistä.
- `HeatmapLimitsChecker` tarkistaa piirrettävien reittipisteiden määrän `max_track_points`-rajaa vasten. Raja-arvoa voi muuttaa kehittäjäasetuksissa, mutta suurempi arvo voi lisätä muistin ja piirtämisen kuormitusta.
- Piirtovaihe tarkistaa reitin rajauslaatikon, ohittaa näkymän ulkopuoliset reitit ja harventaa lähekkäisiä näytön pikseleitä vastaavia reittipisteitä. Näin sama reitti voidaan säilyttää tietokannassa täydellisenä, vaikka kartalla piirretään vain rajattu ja visuaalisesti harvennettu esitys.

## Testaus ja ylläpito

Päiväkirjan puhdas hakulogiikka on testattavissa ilman käyttöliittymää testeillä `FishDiaryPageSearchQueryTest` ja `FishDiaryPageMatcherTest`. Päiväkirjan JSON-muunnosta testataan yhdessä muun import/export-datan kanssa. Yksikkötestit ajetaan komennolla:

```text
.\gradlew.bat :app:testDebugUnitTest
```

Layout-muutoksia tarkistettaessa on huomioitava sekä `app/src/main/res/layout/` että `app/src/main/res/layout-land/`, koska päiväkirjan käyttöä tuetaan molemmissa suunnissa.

## Resurssit

- `app/src/main/res/layout/`: pystynäkymien XML-layoutit.
- `app/src/main/res/layout-land/`: vaakasuuntaisten näkymien XML-layoutit.
- `app/src/main/res/drawable-nodpi/`: kalalajien ja paikkamerkkien kuvakkeet.
- `app/src/main/assets/fish_diary_search_dictionary.txt`: päiväkirjahaun muokattava kalalaji- ja alias-sanasto.
- `app/src/main/assets/kayttoohje.md`: sovelluksen sisäinen käyttöohje.
