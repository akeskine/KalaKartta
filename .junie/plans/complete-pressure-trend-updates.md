---
sessionId: session-260910-133329-wirr
---

# Requirements

### Overview & Goals
Laajennetaan **Päivitä puuttuvat säätiedot** -toiminto täydentämään `FishCatch.pressureTrend`-, `pressureTurningTrend`- ja `pressureSamples`-tiedot niille saalispisteille, joilta paineen trendit puuttuvat. Käytetty asema valitaan samoilla lähimmän aseman fallback-säännöillä kuin nykyisessä säähaussa, ja paineen hetkellinen arvo sekä painehistoria haetaan samalta asemalta.

### Scope
#### In scope
- Lisää `pressureTrend` puuttuvien säätietojen päivityskohteisiin ja näytä nämä pisteet nykyisessä `Päivitettäviä pisteitä`-laskurissa.
- Kokeile saantihetken ilmanpainetta asemilta etäisyysjärjestyksessä ja hae `pressureSamples` samalta asemalta.
- Laske trendi olemassa olevan `FishCatch.calculatePressureTrend()`-logiikan perusteella.
- Laske `pressureTurningTrend` painehistorian −6…0 h ja 0…+6 h puoliskojen trendien erotuksena uusien sääpäivitysten yhteydessä.
- Tallenna uusi turning-trend Roomiin nullable-kenttänä ja vie/tuo se JSON-muodossa.
- Säilytä kaikki muut sääarvot ennallaan, kun piste tarvitsee vain paineeseen liittyvän päivityksen.
- Vie ja tuo `pressureTrend` sekä `pressureSamples.pressure` enintään viiden desimaalin tarkkuudella.

#### Out of scope
- Sovelluksen tietokannan olemassa olevien pisteiden erillinen `pressureTurningTrend`-migraatio- tai massalaskenta; erillisen JSON-viennin kertaluonteinen korjaus käsitellään Step 10:nä.
- Muiden kuin painekehityksen suodatusten tai kehittäjäasetusten käyttöliittymien muuttaminen.
- Muiden automaattisten sääpäivityspolkujen muuttaminen kuin niiden kanssa jaettavan sääasemalogiiikan tarpeelliset sisäiset muutokset.

#### Yritysseurannan lisäys
- Lisää erillinen Room-yritystaulu puuttuvien säätietojen päivityksille ilman aikaperusteista viivettä.
- Näytä viimeisellä päivitysyrityksellä epäonnistuneiden pisteiden määrä nykyisen päivitettävien pisteiden laskurin yhteydessä.
- Valitse ensin pisteet, joilla ei ole epäonnistunutta viimeisintä yritystä, ja vasta sen jälkeen viimeksi epäonnistuneet pisteet.

### Functional Requirements
- Piste on päivitettävä, jos sillä on kelvollinen `caughtAt` ja joko:
  - normaaleja saantihetken säätietoja puuttuu nykyisen ehdon mukaan, tai
  - `pressureTrend == null`, riippumatta siitä, onko `weatherDataCompleteTime` jo asetettu.
- `pressureTurningTrend` lasketaan aina, kun uusi piste tai puuttuvia säätietoja käsiteltävä piste saa painehistorian; puutteellisilla puoliskoilla arvo jää `null`iksi.
- Turning-trendin laskennassa käytetään kummankin puoliskon ensimmäisen ja viimeisen painehavainnon välistä todelliseen aikaeroon perustuvaa hPa/h-muutosnopeutta ja vähennetään ennen-trendi after-trendistä.
- Massapäivityksen on käsiteltävä erikseen nämä tilanteet:
  - uusi piste: haetaan nykyisen normaalin sääpäivityksen tapaan säätiedot sekä paineen näytteet ja lasketaan trendi samaan tallennukseen;
  - vanha piste, jolta `pressure` puuttuu kokonaan: haetaan puuttuva saantihetken paine, `pressureSamples` ja `pressureTrend`, mutta säilytetään kaikki muut jo tallennetut tiedot;
  - vanha piste, jolla `pressure` on olemassa mutta painehistoria/trendi puuttuu: haetaan painehistoria samalta saantihetken paineen tuottavalta asemalta ja täydennetään `pressureSamples` sekä `pressureTrend` ilman muiden sääarvojen korvaamista.
- Painehaku kokeilee lähimmät käytettävissä olevat asemat nykyisen `WeatherService`-logiikan mukaisessa järjestyksessä ja jatkaa seuraavaan asemaan, jos saantihetken paine-/historiatietoa ei saada.
- `pressureSamples` ja saantihetken `pressure` tulevat samasta käytetystä asemasta; asemien tietoja ei yhdistetä painehistorian sisällä.
- Paine-only-päivitys saa muuttaa vain paineeseen liittyviä kenttiä (`pressure`, `pressureSamples`, `pressureTrend`, `pressureTurningTrend`); esimerkiksi lämpötila, tuuli, sade, `weatherStation`, `weatherTime` ja muut kalatiedot säilyvät.
- Jos näytteitä ei saada tai näytteitä on liian vähän trendin laskemiseen, `pressureTrend` jää puuttuvaksi eikä pistettä merkitä onnistuneesti täydennetyksi.
- JSON-viennissä ja -tuonnissa `pressureTrend` sek�� jokaisen `pressureSamples`-alkion paine normalisoidaan enintään viiteen desimaaliin; puuttuvat vanhan JSON-muodon kentät säilyvät yhteensopivina.
- Jokaisesta päivitysyrityksestä säilytetään viimeisin tila catch-kohtaisesti; onnistunut yritys poistaa pisteen epäonnistuneiden joukosta ja epäonnistunut yritys merkitään ilman seuraavan yrityksen aikaviivettä.
- Päivitysrajoitteen täyttyessä valitaan ensin kaikki ei-epäonnistuneet kohteet ja vasta sen jälkeen viimeksi epäonnistuneet kohteet.
- Laskuri näyttää muodossa `Päivitettäviä pisteitä: n / m` lisäksi viimeisellä yrityksellä epäonnistuneiden pisteiden määrän.

# Technical Design

### Current Implementation
- `FishCatch.kt` sisältää jo `pressureTrend`- ja `pressureSamples`-kentät sekä `calculatePressureTrend()`, joka laskee ensimmäisen ja viimeisen näytteen välisen hPa/h-muutoksen.
- `WeatherUpdateActivity.kt` valitsee nykyisin vain pisteet, joilta puuttuu perustason säädata, ja muodostaa päivityksen `copy`-kutsulla kaikista normaaleista sääarvoista. Se ei vielä käsittele painehistoriaa tai trendiä.
- `WeatherService.kt` toteuttaa `fetchNearestStationsSuspend()`-haun enintään viidelle, enintään 300 km:n päässä olevalle asemalle sekä `fetchPressureSamplesSuspend()`-historian haun tietylle `fmisid`-tunnukselle. Nykyinen moniasemahaku palauttaa vain tekstimuotoisen asematiedon.
- `JsonService.kt` vie kentät jo avaimilla `pressureTrend` ja `pressureSamples` ja tuo ne `parseCatches()`/`parsePressureSamples()`-poluissa, mutta ei rajoita painearvojen desimaaleja.
- `AppDatabase.kt`, `FishCatchDao.kt` ja `PressureConverter.kt` tukevat kenttiä jo nykyisellä tietokantaversiolla.

### Key Decisions
- **Painehaun vastuu `WeatherService`-kerrokseen:** lisätään erillinen suspend-haku, joka kapseloi lähimpien asemien läpikäynnin, saantihetken paineen varmennuksen ja saman aseman pressureSamples-haun. `WeatherUpdateActivity` ei parsii `weatherStation`-tekstikenttää.
- **Typed station result:** painehaku palauttaa käytetyn `WeatherStation`/`fmisid`-tiedon yhdessä painearvon ja näytteiden kanssa, jotta historiaa ei haeta eri asemalta kuin saantihetken paine.
- **Selektiivinen tallennus:** normaalia puuttuvan sään päivityshaaraa laajennetaan paineen tiedoilla, mutta trendi-only-pisteelle rakennetaan `FishCatch.copy` vain painekentistä. Nykyinen `fetchWeatherFromMultipleStationsSuspend`-rajapinta säilytetään `EditCatchActivity`-käyttöä varten.
- **JSON-tarkkuus rajataan palvelutasolla:** `JsonService` pyöristää painearvot viiteen desimaaliin sekä kirjoittaessa että luettaessa; Roomin sisäistä listamuunnosta ei muuteta.

### Proposed Changes
1. **`WeatherService.kt`**
   - Lisää painepäivityksen tulosmalli, jossa ovat käytetty asema, saantihetken paine ja `List<PressureSample>`.
   - Lisää suspend-metodi, joka käyttää `fetchNearestStationsSuspend()`-järjestystä, hakee jokaiselta ehdokkaalta saantihetken säähavainnon ja käyttää saman aseman `fmisid`-tunnusta `fetchPressureSamplesSuspend()`-hakuun.
   - Jatka seuraavaan asemaan, jos tarvittavaa painehavaintoa tai käyttökelpoista historiadataa ei saada; pidä sama ±6 tunnin aikaväli kuin `EditCatchActivity.kt`:ssä (`caughtAt - 6 h` ... `min(caughtAt + 6 h, now)`).
   - Pidä nykyisen normaalin moniasemahaun ulkoinen `Triple`-rajapinta yhteensopivana ja jaa tarvittaessa sisäinen asemanvalinnan apulogiikka sen kanssa.

2. **`WeatherUpdateActivity.kt`**
   - Yhtenäistä nykyinen kohdefiltteri niin, että `pressureTrend == null` lisää pisteen päivitettäväksi myös silloin, kun normaali sääpäivitys on jo merkitty valmiiksi.
   - Päivitä `Päivitettäviä pisteitä` -laskuri käyttämään samaa ehtoa kuin varsinainen päivitysajo.
   - Käynnistä painehaku jokaiselle trendittömälle pisteelle ja laske saatu trendi `pressureSamples`-listasta `calculatePressureTrend()`-metodilla.
   - Käsittele uuden tai muuten normaalisti puutteellisen pisteen kohdalla koko sääpäivitys yhtenä `FishCatch`-päivityksenä, johon liitetään paine, näytteet ja trendi.
   - Käsittele vanha täysin paineeton piste paine-only-päivityksenä silloin, kun muu säädata on jo valmis; täydennä vain `pressure`, `pressureSamples` ja `pressureTrend`.
   - Käsittele vanha piste, jolla on jo `pressure` mutta ei painehistoriaa/trendiä, hakemalla näytteet WeatherServicen valitsemalta samalta asemalta ja säilyttämällä kaikki muut kentät ennallaan.
   - Säilytä nykyinen normaali sääpäivitys puuttuville perustiedoille, mutta yhdistä siihen painehaun tulos ilman että tyhjät tai epäonnistuneet painehaut pyyhkivät olemassa olevia arvoja.
   - Käsittele osittainen onnistuminen, tyhjä historiavastaus ja virhe nykyisen onnistumis-/epäonnistumis- ja `WeatherError`-mallin mukaisesti; trendiä ei merkitä valmiiksi ilman laskettavaa tulosta.

3. **`JsonService.kt`**
   - Lisää keskitetty enintään viiden desimaalin `Double`-normalisointi `pressureTrend`-arvolle ja `PressureSample.pressure`-arvolle.
   - Käytä samaa normalisointia `catchesToJson()`-viennissä ja `parseCatches()`/`parsePressureSamples()`-tuonnissa.
   - Säilytä ISO-aikaleimat, tyhjien näytelistojen käsittely ja vanhojen JSON-tiedostojen puuttuvien kenttien oletukset.

### Components & File Structure
- Muokattavat: `app/src/main/java/fi/anssi/kalakartta/data/FishCatch.kt`, `app/src/main/java/fi/anssi/kalakartta/data/AppDatabase.kt`, `app/src/main/java/fi/anssi/kalakartta/data/JsonService.kt`, `app/src/main/java/fi/anssi/kalakartta/ui/CatchManager.kt`, `app/src/main/java/fi/anssi/kalakartta/ui/EditCatchActivity.kt` ja `app/src/main/java/fi/anssi/kalakartta/ui/WeatherUpdateActivity.kt`.
- Testit: `app/src/test/java/fi/anssi/kalakartta/data/JsonCompatibilityTest.kt`, `app/src/test/java/fi/anssi/kalakartta/data/PressureCatchTest.kt` ja `app/src/androidTest/java/fi/anssi/kalakartta/data/FishCatchMigrationTest.kt`.
- Ennalleen: `FishCatch.kt`, `PressureConverter.kt`, `AppDatabase.kt` ja `FishCatchDao.kt`, ellei toteutuksessa tarvitse vain jakaa olemassa olevaa sisäistä apumallia.

### Architecture Diagram
```mermaid
graph TD
    A[WeatherUpdateActivity] -->|missing weather or trend| B[WeatherService]
    B --> C[Nearest stations ordered by distance]
    C --> D[Catch-time pressure]
    D --> E[Same station pressure samples]
    E --> B
    B -->|typed pressure result| A
    A --> F[FishCatchDao update]
    G[JsonService] -->|rounded pressure fields| H[Export and import JSON]
```

# Testing

### Validation Approach
- Aja projektin JVM-yksikkötestit ja varmista, että olemassa olevat trendin laskenta- ja JSON-yhteensopivuustestit säilyvät vihreinä.
- Tarkista kooditasolla, että laskurin ja päivitysajon kohdefiltteri käyttävät samaa ehtoa eivätkä sisällytä pisteitä, joilla sekä perustason sää että `pressureTrend` ovat kunnossa.

### Key Scenarios
- Uusi piste: normaali säähaku täyttää perustiedot, paineen ja painehistorian, ja `pressureTrend` lasketaan samaan `FishCatch`-päivitykseen.
- Vanha piste ilman `pressure`-arvoa: piste näkyy päivitettävissä, paineen fallback-haku suoritetaan, ja vain paineeseen liittyvät puuttuvat kentät täydennetään muun tiedon säilyessä.
- Vanha piste, jolla on `pressure` mutta ei `pressureSamples`-/`pressureTrend`-tietoa: historia haetaan samalta saantihetken paineen asemalta, trendi lasketaan ja muut sääarvot säilyvät.
- Saalispisteeltä puuttuu vain `pressureTrend`: se näkyy laskurissa, pressure-haku kokeilee asemat järjestyksessä, trendi lasketaan näytteistä ja muut sääarvot säilyvät muuttumattomina.
- Lähin asema ei palauta saantihetken painetta tai historiaa: seuraava asema kokeillaan, ja onnistuneessa tapauksessa paine sekä näytteet tulevat samalta asemalta.
- Pisteeltä puuttuu sekä normaaleja säätietoja että trendi: normaali sääpäivitys ja painepäivitys tallentuvat samaan `FishCatch`-päivitykseen.
- Painehistoria on tyhjä tai siinä on vain yksi kelvollinen näyte: trendiä ei aseteta eikä pistettä käsitellä onnistuneesti täydennettynä.
- Viety trendi ja näytteiden paineet sisältävät yli viisi desimaalia: JSON-arvot sisältävät enintään viisi desimaalia ja tuonti palauttaa viiteen desimaaliin normalisoidut `Double`-arvot.
- Vanha JSON ilman `pressureTrend`- ja `pressureSamples`-kenttiä tuodaan edelleen `null`-/tyhjälista-oletuksilla.
- `pressureTurningTrend` lasketaan tunnetusta aineistosta positiiviseksi, negatiiviseksi, nollaksi tai `null`iksi riittämättömillä näytteillä.
- Roomin `22 -> 23` -migraatio lisää uuden nullable-sarakkeen ja jättää vanhojen rivien arvon `null`iksi.

### Test Changes
- Laajenna `JsonCompatibilityTest.kt`: tarkista trendin ja sample-paineiden vienti/tuonti viiden desimaalin rajalla, usean näytteen round-trip sekä vanhan JSON:n yhteensopivuus.
- Laajenna `PressureCatchTest.kt`: varmista, että päivitetystä sample-listasta laskettu nouseva ja laskeva trendi on odotettu ja että liian lyhyt lista palauttaa `null`.
- Lisää tarvittaessa puhdas testattava apumalli/valintafunktio `WeatherService`-asemafallbackille, jotta järjestys, saman aseman käyttö ja tyhjän vastauksen jatkaminen voidaan varmistaa ilman verkkoyhteyttä.

# Delivery Steps

### ✓ Step 1: Add typed pressure-station fallback to WeatherService
`WeatherService` can return catch-time pressure and pressure samples from one station selected by nearest-station fallback.

- Add the pressure update result model and suspend method in `app/src/main/java/fi/anssi/kalakartta/utils/WeatherService.kt`.
- Reuse `fetchNearestStationsSuspend()` ordering and the existing 300 km/active-station filtering.
- Try catch-time pressure and then the ±6-hour pressure history on the same candidate station before moving to the next candidate.
- Keep the existing `fetchWeatherFromMultipleStationsSuspend()` contract usable by `EditCatchActivity.kt`.

### ✓ Step 2: Integrate pressure trend updates into WeatherUpdateActivity
`Päivitä puuttuvat säätiedot` counts and updates new points plus both pressure-missing legacy cases without overwriting unrelated weather fields.

- Update the target predicate in `app/src/main/java/fi/anssi/kalakartta/ui/WeatherUpdateActivity.kt` and use it for both the count and the update loop.
- Handle new or otherwise weather-incomplete points with the existing full weather update and attach the pressure result to the same save.
- Handle old points without `pressure` through a pressure-only save that fills `pressure`, `pressureSamples`, and `pressureTrend`.
- Handle old points with `pressure` but without pressure history by fetching samples from the same selected station and retaining all non-pressure fields.
- Call the new `WeatherService` pressure lookup for trendless points and calculate the value with `FishCatch.calculatePressureTrend()`.
- Combine pressure results with the existing normal weather update for points that also lack other weather values, while preserving current error and progress reporting.

### ✓ Step 3: Normalize JSON pressure precision and regression coverage
Exports and imports preserve pressure trend data with no more than five decimal places and cover the new update contracts with tests.

- Update `app/src/main/java/fi/anssi/kalakartta/data/JsonService.kt` to round `pressureTrend` and sample pressure values on both export and import.
- Extend `JsonCompatibilityTest.kt` with precision, round-trip, and old-format cases.
- Extend `PressureCatchTest.kt` for trend calculation edge cases and run the existing JVM test suite.

### ✓ Step 4: Add persistent weather-update attempt tracking
Each catch has durable last-attempt state for the missing-weather mass update, with no time-based retry delay.

- Add a Room entity, DAO, database registration, and migration for one latest attempt record per catch.
- Store whether the latest attempt succeeded and enough metadata to distinguish the latest result.
- Keep the existing `WeatherError` history separate from the latest attempt state.

### ✓ Step 5: Prioritize and display failed update attempts
The update screen reports the latest failed count and processes non-failed targets before failed targets.

- Use the same target predicate for the total count and update loop, then order targets by latest attempt result before applying the user limit.
- Record success after a complete update and failure for every incomplete or thrown attempt, including pressure-history failures.
- Display `Viime yrityksellä epäonnistuneita n kpl` alongside `Päivitettäviä pisteitä: n / m`.

### ✓ Step 6: Verify retry ordering and failure-state behavior
Tests cover persistence, ordering, count calculation, and the no-delay retry contract.

- Add unit coverage for latest-attempt DAO behavior and non-failed-before-failed ordering.
- Verify that failed points remain eligible immediately but are selected only after eligible points without a failed latest attempt.
- Run the relevant JVM tests and compile checks.

### ✓ Step 7: Add pressure turning trend calculation and persistence
Add `FishCatch.pressureTurningTrend`, calculate it from the two pressure-history halves, and persist it through Room migration.

- Implement before/after endpoint average-change calculation using the existing pressure-sample trend principle.
- Add the nullable Room field and migration with `null` for existing rows.
- Attach the calculated value to new catch weather saves and missing-weather mass-update saves without changing UI or target filtering.

### ✓ Step 8: Extend JSON import/export and regression tests
Preserve `pressureTurningTrend` in JSON and verify calculation, migration, and backward-compatible import behavior.

- Export and import the nullable field while keeping older files valid with a `null` default.
- Add positive, negative, zero, insufficient-data, JSON, and Room migration coverage.
- Run the relevant unit tests and compile checks.

### ✓ Step 9: Correct turning-trend calculation to use endpoint average change
Update the two half-window trend calculations to use each half's first and last samples and their actual elapsed time.

- Calculate endpoint average pressure change rates for the first six and last six samples.
- Keep the existing `pressureTurningTrend` persistence, update paths, and import/export behavior unchanged.
- Add tests proving internal sample values do not affect the endpoint result, then run the focused unit tests and compilation check.

### ✓ Step 10: Recalculate exported pressure turning trends
Update `C:\kehitys\anssi\kalakartta-aineistot\pisteet.json` using the same endpoint average-change calculation as `FishCatch`.

- Recalculate `pressureTurningTrend` for every catch with a non-empty `pressureSamples` list, including catches with an existing value.
- Keep catches with insufficient samples without a calculated value and preserve every other JSON field and formatting.
- Validate the resulting JSON and verify that only the targeted `pressureTurningTrend` fields changed.

### ✓ Step 11: Add pressure-development filters and configurable thresholds
Add filter selectors for `pressureTrend` and `pressureTurningTrend`, with symmetric configurable thresholds in `Yleiset / Kehittäjäasetukset`.

- Use the analyzed defaults `±0.10 hPa/h` for pressure trend and `±0.20 hPa/h` for turning trend.
- Persist the selections with the existing `FilterManager` filter preferences and apply them to `FishCatch` values, excluding catches whose selected value is missing.
- Add both threshold values to the existing developer-tools dialog and include the new filters in the active-filter description.
- Add focused unit coverage for the three-way classification boundaries and run the relevant JVM tests plus compilation.

### ✓ Step 12: Requeue incomplete pressure-history windows
Include old catches in the missing-weather update when their pressure history does not yet reach the expected `+5…+6 h` window.

- Extend the shared update-target predicate in `WeatherUpdateActivity` for catches older than six hours whose pressure samples lack a valid observation in the `+5…+6 h` interval.
- Preserve the existing pressure-only update behavior and let the normal nearest-station fallback refill the history from the catch-time station.
- Add focused unit coverage for complete, incomplete, recent, and missing-history cases, then run the relevant JVM tests and compilation.

### ✓ Step 13: Add a fallback FMI pressure-history request
Allow automatic pressure-history requests to receive and complete observations when FMI returns no data for the initial explicit `timestep=60` parameter.

- Keep the current `timestep=60` request as the first attempt and retry without it when the response is unsuccessful or has no pressure samples.
- When the successful history has sparse three-hour observations, extrapolate a pressure sample into the available `+5…+6 h` window for old catches so the same point is not requeued unnecessarily.
- Keep the existing station fallback and response parsing behavior unchanged.
- Add or update focused coverage for the request variants and run the relevant tests and compilation.
