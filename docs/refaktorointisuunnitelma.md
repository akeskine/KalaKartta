# KalaKartta – refaktorointisuunnitelma

## Tavoite

Parantaa koodin ylläpidettävyyttä ja testattavuutta vaiheittain niin, että olemassa oleva käyttäjälle näkyvä toiminnallisuus, käyttäjäasetukset ja tietokantadata säilyvät.

Päätavoitteet:

- pienentää suuria Activity- ja Manager-luokkia
- erottaa käyttöliittymä, sovelluslogiikka, tietokanta ja ulkoiset palvelut toisistaan
- vähentää kovakoodattuja asetusten avaimia ja oletusarvoja
- yhtenäistää taustatyöt ja elinkaaren hallinta
- vahvistaa testisuojausta ennen laajoja rakenteellisia muutoksia

## Rajaukset ja turvallisuusperiaatteet

- Refaktorointi tehdään pienissä, palautettavissa kokonaisuuksissa.
- Jokainen vaihe käännetään ja testataan erikseen.
- Käyttäjälle näkyvää toimintaa ei muuteta refaktorointimuutoksissa.
- Olemassa olevia `SharedPreferences`-avaimia ei nimetä uudelleen ilman erillistä migraatiota.
- Tietokantamigraatioita ei yhdistetä käyttöliittymä- tai arkkitehtuurirefaktorointiin.
- Import-, export- ja poistotoimintojen käyttäytyminen varmistetaan ennen niiden muuttamista.
- Käyttöliittymän pilkkominen ja bugikorjaukset pidetään erillisinä muutoksina.
- Yksi vaihe tai looginen kokonaisuus tehdään omana commit-kokonaisuutenaan.

## Nykytila

Projektissa on toimiva jako `data`-, `ui`-, `service`-, `utils`- ja `io`-paketteihin. Room-DAOt, tietomallit ja osa puhtaasta laskentalogiikasta ovat jo kohtuullisesti eroteltuja.

Merkittävimmät rakenteelliset riskikeskittymät ovat:

| Tiedosto | Arvioitu koko | Pääasiallinen ongelma |
|---|---:|---|
| `SettingsManager.kt` | noin 3 300 riviä | Asetusten UI, navigointi, tietokanta, import/export, lajit, sää, puhuva kello ja sessiot samassa luokassa |
| `MainActivity.kt` | noin 2 200 riviä | Kartta, markerit, sessiot, toisto, mittaustyökalu, sijainti, sää ja navigointi samassa Activityssa |
| `MarkerManager.kt` | noin 1 900 riviä | Markerien elinkaari, klusterointi, ikonit, dialogit, media ja poistot |
| `ImportExportManager.kt` | noin 1 100 riviä | Tiedostonvalitsimet, JSON/ZIP, tietokantamuutokset, progress-dialogit ja poistot |
| `WeatherService.kt` | noin 1 000 riviä | Verkkoyhteydet, XML-parsinta, asemavälimuisti, painehistoria ja esitysmuotoilu |
| `FishingHeatmapOverlay.kt` | noin 730 riviä | Heatmapin kyselyt, suodatus, aggregointi, reitit ja piirtäminen |
| `FilterManager.kt` | noin 685 riviä | Suodatusmalli, preference-tallennus ja saalis-, paikka- ja reittisuodatus |

Nykyinen yksikkötestikomento:

```text
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Lähtötilassa yksikkötestit menevät läpi. Testit painottuvat dataan, suodatukseen, laskentaan ja palveluiden puhtaampiin osiin. `SettingsManager`-, `MainActivity`-, `MarkerManager`- ja `ImportExportManager`-luokkien käyttäjäpolkuja ei ole kattavasti testattu.

## Vaihe 0: Lähtötilan dokumentointi ja testiturvaverkko

**Riski:** erittäin matala  
**Hyöty:** erittäin suuri  
**Edellytys:** ei tuotantokoodimuutoksia

Tehtävät:

- aja nykyinen yksikkötestikokonaisuus
- kirjaa käännösvaroitukset ja tunnetut tekniset velat
- dokumentoi tärkeät `SharedPreferences`-avaimet ja niiden oletusarvot
- lisää testit asetusten oletusarvoille ja tallennuksen/lukemisen yhteensopivuudelle
- lisää testit heatmapin käyttörajojen keskeisille yhdistelmille
- lisää testit importin duplikaattien tunnistukselle ja konfliktitiloille
- lisää testit datan poiston ja oletuslajien palautuksen tuloksille, mahdollisuuksien mukaan testitietokannalla

Hyväksymiskriteerit:

- lähtötilan testikomento on dokumentoitu
- asetusten tärkeimmät oletusarvot on katettu testeillä
- testit epäonnistuvat, jos olemassa oleva käyttäytyminen muuttuu tarkoituksetta

Huomio: tarkoitus ei ole rakentaa täydellistä käyttöliittymätestistöä ennen ensimmäistä refaktorointia. Ensin suojataan liiketoimintakäyttäytymisen ja pysyvän datan rajapinnat.

### Vaiheen 0 toteutustilanne

13.9.2026 lisätty testiturvaverkko kattaa seuraavat kokonaisuudet:

- oletuslajien ja kiinnostavien paikkatyyppien identiteetit, järjestyksen ja ikonit
- `FilterManager`-suodattimien kaikkien kenttien tallennus/luku sekä tyhjennys
- Room-tietokannan käyttödatan tyhjennys ja oletusreferenssidatan palautus
- reitti- ja heatmap-kyselyiden aika-, alue- ja nopeusrajat
- aiemmat JSON-yhteensopivuustestit säilyvät osana yksikkötestikokonaisuutta

Varmistetut komennot:

```text
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Molemmat komennot menevät läpi. Instrumentointitestejä ajettiin yhteensä 9 Android 16 -laitteella; uusia testitapauksia näistä on 8.

Vaiheessa tarkoituksella dokumentoidut aukot:

- `SettingsManager`in koko UI-polun oletusarvoja ei testata suoraan, koska asetusten luku on hajautunut Activity-sidonnaiseen UI-koodiin. Tämä on vaiheiden 1–2 tavoite: ensin avaimet ja oletusarvot keskitetään testattavaan rajapintaan.
- Importin duplikaattien konfliktitilat ovat `ImportExportManager`in Activity-sidonnaista sisäistä logiikkaa. Niille tehdään erillinen testattava rajapinta ennen import-logiikan refaktorointia; nykyiset JSON-serialisoinnin ja -yhteensopivuuden testit toimivat turvaverkon perustana.
- Manuaalinen käyttöliittymän smoke-testi jää laite/emulaattorikohtaiseksi tarkistuslistaksi, ei automaattisen vaiheen 0 testiksi.

Käännöksessä raportoidaan edelleen `SettingsManager.kt`:n vanhentuneet `getColor`- ja `startActivityForResult`-kutsut. Ne ovat vaiheeseen 1 kuuluvaa teknistä velkaa, eivätkä tässä vaiheessa aiheuttaneet toiminnallista testivirhettä.

Keskeinen `settings`-preferenssien inventaario ennen keskittämistä:

| Avain | Oletus | Pääasiallinen käyttö |
|---|---:|---|
| `heatmap_enabled` | `false` | Heatmap |
| `fishing_routes_enabled` | `false` | Reitit |
| `heatmap_filter_enabled` / `routes_filter_enabled` | `false` | Suodatuksen käyttö |
| `heatmap_auto_configure` | `true` | Heatmapin pistealue |
| `heatmap_grid_size` | `300.0` m | Heatmap-solun koko |
| `heatmap_min_points` / `heatmap_max_points` | `1` / `50` | Heatmapin piste-/solurajat |
| `max_track_points` / `max_heatmap_cells` | `50000` / `10000` | Laskennan turvarajat |
| `heatmap_remove_transitions` | `false` | Siirtymien poisto |
| `heatmap_remove_transitions_mode` | `0` | Poiston kohde |
| `heatmap_max_speed` | `10.0` | Nopeusraja |
| `heatmap_min_zoom` | `10.0` | Näkyvyysraja |
| `heatmap_reference_latitude` | `64.7` | Metri-/aste-muunnos |
| `routes_fade_enabled` | `true` | Reittien häivytys |
| `routes_fade_start_days` / `routes_fade_full_days` | `365` / `30` | Häivytysrajat |
| `map_source` | `OSM` | Taustakartta |
| `mml_api_key` | `""` | MML-karttojen avain |
| `weather_enabled` | `true` | Säätietojen haku |
| `show_live_session_route` | `true` | Aktiivisen session reitti |
| `location_check_interval` | `10` s | Sijaintipäivitys |
| `min_track_point_interval` / `max_track_point_interval` | `30` / `300` s | Reittipisteiden aikarajat |
| `min_track_point_distance` | `20` m | Reittipisteiden etäisyysraja |
| `show_scale_bar` / `show_measurement_tool` | `false` / `false` | Kartan työkalut |
| `auto_center_on_start` | `true` | Kartan aloituskeskitys |
| `talking_clock_enabled` | `false` | Puhuva kello |
| `talking_clock_interval` | `30` min | Puhuvan kellon väli |
| `fish_icon_scale` / `other_icon_scale` | `1.0` / `1.0` | Markerien koko |

Suodattimet ovat erillisessä `filters`-preferenssissä. Niiden tyhjä oletustila on `FilterManager.Filters()`, ja `weightLengthOperator`-oletus on `OR`. Koordinaatit tallennetaan nykyisessä toteutuksessa `Float`-arvoina; tämä säilytetään yhteensopivuussyistä, kunnes erillinen muutos arvioi tarkkuuden.

## Vaihe 1: Matalan riskin rakenteelliset parannukset

**Riski:** matala  
**Hyöty:** keskisuuri–suuri

Tehtävät:

- keskitetään asetusten nimet ja oletusarvot vakioiksi
- säilytetään vanhat preference-avaimet täsmälleen ennallaan
- erotetaan puhtaita apufunktioita testattaviksi
- poistetaan tarpeettomia täysin kvalifioituja nimiä ja epäjohdonmukaisia importteja
- korvataan vanhentuneet Android-API-kutsut pienissä erillisissä muutoksissa
- korjataan ilmeiset paikalliset oletusarvovirheet omissa committeissaan

Tässä vaiheessa ei vielä vaihdeta `SharedPreferences`-toteutusta DataStoreen eikä muuteta tietokantakerrosta.

Hyväksymiskriteerit:

- sovellus toimii samalla tavalla kuin ennen muutosta
- kaikki aiemmin olemassa olleet preference-avaimet toimivat
- yksikkötestit menevät läpi jokaisen muutoksen jälkeen

## Vaihe 2: Asetusten luku- ja kirjoituslogiikan keskittäminen

**Riski:** matala–keskisuuri  
**Hyöty:** suuri

Luodaan esimerkiksi `AppSettings`- tai `SettingsStore`-tyyppinen rajapinta, joka kapseloi asetusten lukemisen ja kirjoittamisen.

Tavoite:

- `MainActivity`, `SettingsManager`, `MarkerManager`, `FishingHeatmapOverlay` ja palvelut eivät käsittele samoja merkkijonoavaimia itsenäisesti
- oletusarvot määritellään yhdessä paikassa
- asetusten tyypit ja validointi ovat keskitettyjä
- vanhat preference-avaimet säilyvät yhteensopivuuden vuoksi

Toteutusjärjestys:

1. määrittele avaimet ja oletusarvot
2. lisää wrapperi nykyisen `SharedPreferences`-toteutuksen päälle
3. siirrä yksi asetuskokonaisuus kerrallaan wrapperin käyttöön
4. lisää yhteensopivuustestit ennen seuraavaa kokonaisuutta

DataStoreen siirtyminen on erillinen myöhempi hanke, ei tämän vaiheen vaatimus.

## Vaihe 3: SettingsManagerin pilkkominen

**Riski:** keskisuuri  
**Hyöty:** erittäin suuri

Säilytetään aluksi nykyinen `SettingsManager` julkisena koordinaattorina ja siirretään sen sisäiset vastuut erillisiin luokkiin.

Ehdotetut kokonaisuudet:

- `MapSettingsDialog`
- `HeatmapSettingsDialog`
- `GeneralSettingsDialog`
- `TalkingClockSettingsDialog`
- `FishingSessionSettingsDialog`
- `DataTransferDialog`
- `SpeciesSettingsDialog`
- `UserManualDialog`

Ensin siirretään koodi mahdollisimman yksi yhteen. Riippuvuuksien, käyttöliittymätekstien tai navigointikäyttäytymisen laajempaa uudistamista ei tehdä samalla kertaa.

Erityisesti varmistettavat käyttäjäpolut:

- asetusten päävalikosta jokaiseen alavalikkoon siirtyminen ja palaaminen
- dialogien sulkeminen ja uuden dialogin avaaminen
- heatmap- ja reittiasetusten käyttörajojen tarkistus
- puhuvan kellon käyttöoikeuspolku
- import/export-valinta ja paluu Activityyn
- kalalajien palautus oletuksiin
- kalastussession käynnistys ja käynnissä olevan session näkymä

## Vaihe 4: Puhdas logiikka irti käyttöliittymästä

**Riski:** keskisuuri  
**Hyöty:** suuri

Erotetaan ensin sellainen logiikka, jolla ei ole riippuvuutta Androidin View-olioihin:

- asetusten validointi ja normalisointi
- heatmapin käyttörajojen laskenta
- heatmapin koordinaatti- ja ruutulaskenta
- Markdown-ohjeen muunnos ja ankkurien normalisointi
- importin duplikaattien tunnistus
- import/exportin domain-muunnokset
- sessioiden keston ja reittitietojen muotoilu

Näille lisätään yksikkötestit. UI-luokkien tehtäväksi jää tulosten näyttäminen ja käyttäjän toimien välittäminen.

Erikseen tarkistettavat tunnetut epäjohdonmukaisuudet:

- kuvakekoon puuttuvan arvon fallback näyttää olevan eri kuin kommentissa ilmoitettu oletus
- heatmapin käyttörajan tarkistus ja varsinainen heatmap-piirto käyttävät eri referenssileveyspiirin logiikkaa

Mahdolliset bugikorjaukset tehdään omissa muutoksissaan, eivät pelkän koodinsiirron sivuvaikutuksina.

## Vaihe 5: Taustatyöt ja elinkaaren hallinta

**Riski:** keskisuuri–korkea  
**Hyöty:** suuri

Kun vastuut ovat pienempiä ja testit kattavat ydinkäyttäytymisen:

- vähennetään raakaa `Thread { ... }.start()` -mallia
- siirretään taustatyöt hallittuihin coroutine-konteksteihin
- sidotaan dialogien tehtävät dialogin tai Activityn elinkaareen
- varmistetaan, ettei suljettu Activity vastaanota myöhästyneitä UI-päivityksiä
- poistetaan tietokantaoperaatiot UI-säikeeltä
- arvioidaan `allowMainThreadQueries()`-asetuksen poistamista

Tässä vaiheessa ei saa muuttaa samanaikaisesti importin, session tai weatherin toiminnallista logiikkaa ilman erillisiä testejä.

## Vaihe 6: Suurempien koordinaattorien pilkkominen

### MainActivity

**Riski:** keskisuuri–korkea  
**Hyöty:** erittäin suuri

Mahdollisia vastuualueita:

- kartan alustaminen ja karttalähde
- markerien ja overlayden koordinointi
- kalastussession käyttöliittymä
- session toisto
- mittaustyökalu
- sijainti ja käyttöoikeudet
- navigointi

### MarkerManager

**Riski:** korkea  
**Hyöty:** suuri

Mahdollisia osia:

- markerien tila ja kierrätys
- klusterointi
- ikonien luonti ja välimuistit
- tietoikkunat ja dialogit
- media- ja poistotoiminnot

### ImportExportManager

**Riski:** korkea  
**Hyöty:** suuri

Mahdollisia osia:

- tiedostonvalitsimet
- JSON- ja ZIP-formaatit
- importin konfliktit ja duplikaatit
- tietokantaan kirjoittava import-palvelu
- progress- ja tulosviestintä

### WeatherService

**Riski:** keskisuuri–korkea  
**Hyöty:** keskisuuri–suuri

Mahdollisia osia:

- FMI-API-asiakas
- XML-parserit
- sääasemien välimuisti
- painehistoria
- puheeseen ja UI:hin liittyvä esitysmuotoilu

## Riskimatriisi

| Muutos | Riski | Suositus |
|---|---|---|
| Testien lisääminen | erittäin matala | tehdään ensin |
| Vakioiden ja apufunktioiden erottelu | matala | ensimmäisiä tuotantokoodimuutoksia |
| Typed settings-wrapper vanhoilla avaimilla | matala–keskisuuri | tehdään aikaisin |
| `SettingsManager`in UI-osien erottelu | keskisuuri | ensimmäinen suuri refaktorointi |
| Raakojen Threadien muuttaminen | keskisuuri–korkea | vasta myöhemmin |
| `MainActivity`n pilkkominen | keskisuuri–korkea | SettingsManagerin jälkeen |
| `MarkerManager`in muuttaminen | korkea | vasta vahvalla testisuojalla |
| Import/exportin tietokantavirran muuttaminen | korkea | erillinen hanke |
| Tietokantamigraatiot | erittäin korkea | ei yhdistetä tähän suunnitelmaan |

## Työskentelysäännöt jokaiseen vaiheeseen

Ennen muutosta:

- määrittele täsmällinen vastuualue
- tarkista, mitä julkisia kutsuja ja preference-avaimia muutos koskee
- lisää puuttuva käyttäytymistesti, jos muutos on riskialtis

Muutoksen aikana:

- pidä diffi pienenä
- vältä samanaikaista nimeämistä, formatointia ja toiminnallista muutosta
- säilytä julkiset rajapinnat väliaikaisesti adaptereilla tarvittaessa

Muutoksen jälkeen:

- aja `:app:testDebugUnitTest`
- tarkista käännösvaroitukset
- tarkista git-diff manuaalisesti
- testaa muuttunut käyttäjäpolku emulaattorilla tai laitteella
- kirjaa mahdolliset löydökset tähän dokumenttiin tai päätöslokiin

## Päätösloki

Tähän kirjataan myöhemmin tehdyt arkkitehtuuripäätökset, esimerkiksi:

- valitaanko `SettingsStore`-nimeksi `AppSettings`, `SettingsRepository` vai jokin muu
- käytetäänkö jatkossa XML-dialogeja, erillisiä view-luokkia vai ViewModel-pohjaista rakennetta
- milloin raakaa `Thread`-mallia aletaan korvata coroutineilla
- pidetäänkö `SettingsManager` väliaikaisena facade-luokkana vai poistetaanko se lopulta

## Valmis, kun

- asetusten käyttöliittymä ei enää muodosta yhtä monoliittista luokkaa
- asetusten avaimet ja oletusarvot ovat keskitetysti hallittuja
- ydinkäyttäytyminen on yksikkötesteillä suojattu
- tietokantaoperaatiot eivät riipu UI-säikeen sallimisesta
- taustatyöt ovat elinkaariturvallisia
- `MainActivity`, `MarkerManager`, `ImportExportManager` ja `WeatherService` ovat selkeästi rajattuja kokonaisuuksia
- kaikki nykyiset import/export-, kalastus-, kartta- ja asetustoiminnot toimivat edelleen
