# KalaKartta: tuonti- ja vientimuodot

Tämä ohje kuvaa KalaKartan käyttämät tiedostomuodot ja erityisesti sen, miten ulkopuolisesta lähteestä tuleva aineisto pitää muuntaa ennen tuontia.

## Tiivistettynä

- KalaKartan oma siirtomuoto on UTF-8-koodattu JSON.
- Koko varmuuskopio ja media siirretään ZIP-tiedostona, jonka sisällä on useita JSON-tiedostoja ja mediatiedostoja.
- Kenttien nimet ovat kirjainkoon suhteen tarkkoja: esimerkiksi `latitude` ja `Latitude` eivät ole sama kenttä.
- Koordinaatit ovat desimaaliasteina WGS84-järjestelmässä. JSON-luvussa käytetään pistettä desimaalierottimena.
- Päivämäärät ja kellonajat ovat ensisijaisesti UTC-aikaa muodossa `yyyy-MM-dd'T'HH:mm:ss'Z'`, esimerkiksi `2026-09-15T12:30:00Z`.
- Tietokenttiin ei saa kirjoittaa yksiköitä, kuten `2500 g`, vaan yksiköt ovat kiinteästi sovitussa kentässä.
- `id`-kentät eivät yleensä säily tuonnissa. Sovellus luo tuoduille tietueille uudet paikalliset tunnisteet.

## Tiedostot ja niiden käyttötarkoitus

| Toiminto | Tiedosto | JSON:n ylin rakenne |
|---|---|---|
| Kalapisteet ja muut paikat | `kalakartta.json` | Objekti, jossa on `catches` ja/tai `places` |
| Kalastussessiot ja reittipisteet | `reitit.json` | Objekti, jossa on `sessions` |
| Kalapäiväkirja | `paivakirja.json` | Objekti, jossa on `diaryPages` |
| Kalalajien asetukset | `kalalajit.json` | Objekti, jossa on `species` |
| Media | ZIP, esimerkiksi `kalakartta-media-2026-09-15.zip` | ZIP:n `media.json` ja `media/`-hakemisto |
| Kaikki tiedot | ZIP, esimerkiksi `kalakartta-kaikki-2026-09-15.zip` | `pisteet.json`, `sessiot.json`, `paivakirja.json`, `kalalajit.json`, `media.json` ja `media/` |

Tuontitoiminnot hyväksyvät tiedostonimen sijaan sisällön perusteella tiedoston. Tiedostopäätteen kannattaa silti olla edellä mainittu, jotta tiedosto on helppo tunnistaa.

## Yleiset muuntosäännöt

### Tekstikoodaus ja JSON

Tallenna tiedosto UTF-8-muodossa. JSON:n pitää olla kelvollista JSONia: merkkijonot ovat lainausmerkeissä, desimaaliluvuissa käytetään pistettä ja viimeisen kentän jälkeen ei kirjoiteta pilkkua.

Hyvä:

```json
{
  "latitude": 61.4981,
  "longitude": 23.7610,
  "weight": 2500
}
```

Huono:

```text
Leveysaste: 61,4981
Paino: 2 500 g
```

Puuttuvan valinnaisen tiedon voi jättää pois tai ilmoittaa JSON-arvona `null`. Tyhjä merkkijono `""` tarkoittaa tekstikentässä tyhjää tietoa. Puuttuvia koordinaatteja ei pidä jättää pois, vaikka sovellus teknisesti sijoittaa silloin pisteen oletuskoordinaattiin (noin `60.0, 24.0`).

### Koordinaatit

- `latitude` = leveysaste, etelä–pohjoinen, välillä `-90..90`.
- `longitude` = pituusaste, länsi–itä, välillä `-180..180`.
- Järjestys on aina leveysaste, pituusaste eli `latitude`, `longitude`.
- Käytä desimaaliasteita, älä asteita/minuutteja/sekunteja tai UTM-koordinaatteja.
- Käytä WGS84-datumia, kuten GPS ja GeoJSON käyttävät.
- Sovellus pyöristää koordinaatit tuonnin yhteydessä viiteen desimaaliin.

Esimerkiksi Helsingin koordinaatti kirjoitetaan:

```json
"latitude": 60.1699,
"longitude": 24.9384
```

### Päivämäärät ja kellonajat

Kalapisteissä, reiteissä ja painenäytteissä käytettävä suositeltu muoto on:

```text
2026-09-15T12:30:00Z
```

Merkintä `Z` tarkoittaa UTC-aikaa. Aikavyöhykkeenä ei pidä käyttää suomalaista muotoa `15.9.2026 15.30` eikä kenttiin pidä lisätä tekstiä `klo`.

Kalapisteiden `caughtAt`- ja `weatherTime`-kentät hyväksyvät yhteensopivuussyistä myös Unix-ajan millisekunteina. Päiväkirjassa päivämäärä on muodossa `2026-09-15T00:00:00Z`. Reittien aikakentissä käytä ISO-muotoa.

| Kenttä | Suositeltu muoto | Huomio |
|---|---|---|
| `caughtAt` | ISO 8601 UTC -merkkijono | Kalapisteen tapahtuma-aika; valinnainen |
| `weatherTime` | ISO 8601 UTC -merkkijono | Säähavainnon aika; valinnainen |
| `pressureSamples[].time` | ISO 8601 UTC -merkkijono | Painenäytteen aika |
| `startedAt`, `endedAt`, `timestamp` | ISO 8601 UTC -merkkijono | Reittien ajat |
| `startDate`, `endDate` | `yyyy-MM-ddT00:00:00Z` | Päiväkirjan päivämäärä, ei kellonaikaa |
| `weatherDataCompleteTime` | Unix-aika millisekunteina | Sovelluksen sisäinen valmistumishetki |
| `media[].pointTime` | Unix-aika millisekunteina | Mediaan liittyvän pisteen aika |

## Kalapisteet ja muut paikat: `kalakartta.json`

Pisteiden tuonti odottaa seuraavanlaista juurta:

```json
{
  "catches": [
    {
      "species": "PIKE",
      "eventType": "CAUGHT_FISH",
      "latitude": 61.4981,
      "longitude": 23.7610,
      "caughtAt": "2026-09-15T12:30:00Z",
      "weight": 2500,
      "length": 82,
      "method": "Heitto",
      "fisherman": "ANSSI",
      "additionalInfo": "Kaislikon reunasta"
    }
  ],
  "places": [
    {
      "typeId": "SHALLOW",
      "latitude": 61.4985,
      "longitude": 23.7620,
      "name": "Matalikko",
      "additionalInfo": "Hyvä kevätpaikka"
    }
  ]
}
```

`catches` ja `places` voivat olla tyhjiä taulukoita, ja vain toinen niistä voidaan sisällyttää tiedostoon. Vanha yhteensopivuusmuoto, jossa juurena on pelkkä taulukko, tulkitaan kalapistetaulukoksi:

```json
[
  {
    "species": "PERCH",
    "latitude": 61.4981,
    "longitude": 23.7610
  }
]
```

### Kalapisteen kentät

Teknisesti parseri antaa monelle puuttuvalle kentälle oletusarvon. Jotta aineisto ei muutu huomaamatta vääräksi, merkitse vähintään `species`, `latitude` ja `longitude` jokaiselle kalapisteelle. `caughtAt` tarvitaan, jos tapahtuma halutaan ajoittaa tai sovelluksen säätietojen ja kuutietojen automaattinen täydentäminen halutaan toimimaan.

| Kenttä | JSON-tyyppi | Pakollisuus ja sisältö |
|---|---|---|
| `species` | merkkijono | Käytännössä pakollinen. Kalalajin tunnus, esimerkiksi `PIKE`; puuttuessa käytetään `UNKNOWN`-arvoa. |
| `eventType` | merkkijono | Valinnainen. Arvot ovat `CAUGHT_FISH`, `LOST_FISH`, `STRIKE_CERTAIN`, `STRIKE_UNCERTAIN` ja `FISH_FOLLOW`. Jos kenttä puuttuu ja `species` on tunnettu, oletus on `CAUGHT_FISH`. |
| `latitude` | numero | Käytännössä pakollinen, desimaaliasteina. |
| `longitude` | numero | Käytännössä pakollinen, desimaaliasteina. |
| `caughtAt` | merkkijono tai kokonaisluku | Valinnainen. Suositus on ISO-aika; kokonaisluku tarkoittaa Unix-aikaa millisekunteina. |
| `weight` | kokonaisluku | Valinnainen, grammoina. Esimerkiksi `2500`, ei `2.5 kg`. |
| `length` | kokonaisluku | Valinnainen, senttimetreinä. |
| `method` | merkkijono | Valinnainen; esimerkiksi `Heitto`, `Uistelu` tai `Pilkkiminen`. |
| `lure` | merkkijono | Valinnainen, vieheen nimi. |
| `lureColor` | merkkijono | Valinnainen, vieheen väri. |
| `strikeDepth` | numero | Valinnainen, tärppisyvyys metreinä. |
| `waterDepth` | numero | Valinnainen, veden syvyys metreinä. |
| `waterTemp` | numero | Valinnainen, veden lämpötila Celsius-asteina. |
| `airTemp` | numero | Valinnainen, ilman lämpötila Celsius-asteina. |
| `cloudiness` | kokonaisluku | Valinnainen, pilvisyys asteikolla `0..8`. |
| `rain` | kokonaisluku | Valinnainen sovelluksen sisäinen sadeasteikko: `0` ei sadetta, `1` tihkusade, `2` normaali sade, `3` rankkasade. |
| `rainHourMm` | numero | Valinnainen, sadanta millimetreinä tunnissa. |
| `windSpeed` | numero | Valinnainen, tuulen nopeus metreinä sekunnissa. |
| `windDirection` | kokonaisluku | Valinnainen, tuulen suunta asteina, yleensä `0..359`. |
| `pressure` | numero | Valinnainen, ilmanpaine hehtopascaleina. |
| `weatherSource` | merkkijono | Valinnainen, esimerkiksi `FMI`. |
| `weatherTime` | merkkijono tai kokonaisluku | Valinnainen säähavainnon aika. |
| `weatherStation` | merkkijono | Valinnainen sääaseman tunnus tai nimi. |
| `additionalInfo` | merkkijono | Valinnainen lisätieto. |
| `originalRef` | merkkijono | Valinnainen lähdejärjestelmän tunnus tai muu viite. |
| `fisherman` | merkkijono | Valinnainen kalastajan nimi. Tuonnissa arvo säilytetään; viennissä se kirjoitetaan yleensä isoilla kirjaimilla. |
| `otherSpecies` | merkkijono | Valinnainen tarkentava lajinimi, käytä erityisesti kun `species` on `OTHER`. |
| `weatherDataCompleteTime` | kokonaisluku | Valinnainen Unix-aika millisekunteina. Tavallisesti tämän voi jättää pois. |
| `pressureTrend` | numero | Valinnainen, ilmanpaineen muutos hPa/h. |
| `pressureTurningTrend` | numero | Valinnainen, ilmanpaineen muutoksen muutos hPa/h. |
| `pressureSamples` | taulukko | Valinnainen. Painenäytteitä muodossa `{ "time": ..., "pressure": ... }`. |
| `moonPhase` | numero | Valinnainen; sovellus täydentää arvon, jos aika on tiedossa. |
| `moonAltitude` | numero | Valinnainen, kuun korkeus asteina; sovellus täydentää arvon, jos aika ja koordinaatit ovat tiedossa. |
| `id` | kokonaisluku | Vapaaehtoinen vientikenttä. Tuonnissa ohitetaan ja korvataan uudella paikallisella tunnisteella. |

Sää- ja kuutietoja ei tarvitse tuottaa itse. Tuonti laskee puuttuvat kuutiedot, jos `caughtAt` ja koordinaatit ovat mukana. Tuonti ei kuitenkaan hae puuttuvia säätietoja suoraan sääpalvelusta, joten ulkopuolisesta lähteestä tulevat sääarvot pitää sisällyttää aineistoon itse, jos ne halutaan säilyttää. Jos tuot vanhaa aineistoa, sää- ja kuutietokentät voi jättää pois.

### Lajitunnukset

`species` ei ole näytössä näkyvä suomenkielinen nimi vaan `FishSpecies`-taulun tunnus. Vakiolajien tunnukset ovat:

| Tunnus | Laji |
|---|---|
| `PERCH` | Ahven |
| `PIKE` | Hauki |
| `ZANDER` | Kuha |
| `TROUT` | Taimen |
| `SALMON` | Lohi |
| `GRAYLING` | Harjus |
| `WHITEFISH` | Siika |
| `RAINBOW` | Kirjolohi |
| `BREAM` | Lahna |
| `IDE` | Säyne |
| `CHAR` | Rautu |
| `BURBOT` | Made |
| `OTHER` | Muu kalalaji |

Jos tuot aineiston ennen kalapisteitä, jossa on omia lajeja, tuo ensin niitä vastaava `kalalajit.json`. Muutoin pisteen lajitunnus ei välttämättä löydy sovelluksen lajilistasta. Puuttuva `species`-kenttä saa arvon `UNKNOWN`; mielivaltainen tuntematon tunnus säilyy sellaisenaan, mutta sille ei välttämättä löydy nimeä tai kuvaketta. Käytä `UNKNOWN`-tunnusta vain, jos haluat tarkoituksella merkitä lajin tuntemattomaksi.

### Muiden paikkojen kentät

| Kenttä | JSON-tyyppi | Pakollisuus ja sisältö |
|---|---|---|
| `typeId` | merkkijono | Käytännössä pakollinen. Suositellut arvot ovat `ROCK`, `VEGETATION`, `SHALLOW`, `DEEP`, `PARKING`, `ACCESS`, `LANDINGSPOT`, `RAMP`, `HARBOUR`, `ACCOMMODATION`, `CAMP`, `CAMPFIRE`, `SHELTER`, `OTHER` ja `PROSPECT`. |
| `latitude` | numero | Käytännössä pakollinen, desimaaliasteina. |
| `longitude` | numero | Käytännössä pakollinen, desimaaliasteina. |
| `name` | merkkijono | Valinnainen nimi. |
| `additionalInfo` | merkkijono | Valinnainen lisätieto. |
| `originalRef` | merkkijono | Valinnainen lähdejärjestelmän tunnus tai muu viite. |
| `id` | kokonaisluku | Vapaaehtoinen vientikenttä; tuonnissa ohitetaan. |

`typeId` on tunnus, ei näytössä näkyvä nimi. Jos käytät omaa tunnusta, piste voidaan silti tuoda, mutta sille ei välttämättä löydy nimeä tai kuvaketta.

## Kalastussessiot ja reitit: `reitit.json`

Rakenne on:

```json
{
  "sessions": [
    {
      "startedAt": "2026-09-15T09:00:00Z",
      "endedAt": "2026-09-15T14:30:00Z",
      "notes": "Aamupäivän kierros",
      "fisherman": "ANSSI",
      "points": [
        {
          "timestamp": "2026-09-15T09:00:00Z",
          "latitude": 61.4981,
          "longitude": 23.7610,
          "speed": 1.4,
          "accuracy": 8.0
        }
      ]
    }
  ]
}
```

| Kenttä | JSON-tyyppi | Pakollisuus ja sisältö |
|---|---|---|
| `sessions` | taulukko | Tuotavan tiedoston juurikenttä. |
| `startedAt` | merkkijono | Käytännössä pakollinen sessiolle. Jos se puuttuu, sovellus voi päätellä sen ensimmäisen reittipisteen ajasta; tyhjä sessio ilman aikaa ei ole käyttökelpoinen. |
| `endedAt` | merkkijono | Valinnainen. Jos puuttuu, sovellus voi päätellä lopetusajan viimeisestä reittipisteestä. |
| `notes` | merkkijono | Valinnainen muistiinpano. |
| `fisherman` | merkkijono | Valinnainen kalastajan nimi. |
| `points` | taulukko | Valinnainen teknisesti, mutta reitti tarvitsee tämän, jos sessiosta halutaan piirtää reitti. |
| `points[].timestamp` | merkkijono | Käytännössä pakollinen reittipisteelle, ISO-aika. |
| `points[].latitude` | numero | Käytännössä pakollinen, WGS84-desimaaliaste. |
| `points[].longitude` | numero | Käytännössä pakollinen, WGS84-desimaaliaste. |
| `points[].speed` | numero | Valinnainen, metriä sekunnissa; puuttuessa `0`. |
| `points[].accuracy` | numero | Valinnainen, GPS-tarkkuus metreinä; puuttuessa `0`. |

Reittien tuonnissa ei tehdä pisteiden kaltaista duplikaattitarkistusta. Jokainen tuotu sessio lisätään uutena sessiona. Jos sama sessio tai reittipiste tuodaan kahdesti, se tulee tietokantaan kahdesti.

## Kalapäiväkirja: `paivakirja.json`

```json
{
  "diaryPages": [
    {
      "startDate": "2026-09-15T00:00:00Z",
      "endDate": "2026-09-16T00:00:00Z",
      "location": "Näsijärvi",
      "fishingMethod": "Heitto",
      "catch": "Kaksi haukea",
      "story": "Tuulinen päivä."
    }
  ]
}
```

`startDate` on sisällöllisesti pakollinen. `endDate` on valinnainen ja tarkoittaa usean päivän merkinnän viimeistä päivää. `location`, `fishingMethod`, `catch` ja `story` voidaan jättää tyhjiksi, mutta niiden pitää olla merkkijonoja, jos ne sisällytetään tiedostoon. `id` on vientikenttä, jota tuonnissa ei säilytetä.

Päiväkirjatuonti lisää sivut nykyisten sivujen joukkoon. Se ei korvaa nykyistä päiväkirjaa eikä tuo päiväkirjasivuihin liittyvää mediaa `paivakirja.json`-tiedostosta.

## Kalalajien asetukset: `kalalajit.json`

```json
{
  "species": [
    {
      "id": "PIKE",
      "name": "Hauki",
      "small_weight": 1000,
      "small_length": 55,
      "large_weight": 3000,
      "large_length": 80,
      "giant_weight": 8000,
      "giant_length": 100,
      "icon_small": "",
      "icon_default": "hauki",
      "icon_large": "",
      "icon_giant": "",
      "favourite_fish": true,
      "sortOrder": 2
    }
  ]
}
```

Jokaisella lajilla pitää olla merkkijonona annettu yksilöllinen `id`. `name` on näytettävä nimi. Painorajat ovat grammoja ja pituusrajat senttimetrejä. Muut numeeriset kentät oletetaan nollaksi, `favourite_fish` oletetaan arvoksi `true` ja `sortOrder` arvoksi `0`, jos ne puuttuvat.

Kuvakekentät ovat joko sovelluksen sisäisen kuvakkeen nimiä, kuten `hauki`, tai mukautetun kuvakkeen tiedostonimiä. Mukautetun PNG-kuvakkeen voi siirtää JSON:ssa Base64-muodossa:

```json
{
  "icon_default": "custom_icon_hauki.png",
  "icon_default_data": "iVBORw0KGgoAAAANSUhEUg..."
}
```

`icon_*_data` tarvitaan vain mukautetulle kuvakkeelle. Sen pitää sisältää koko tiedoston Base64-koodattuna ilman erillistä tiedostopolkua. Käytännössä lajit kannattaa viedä sovelluksesta, jos mukana on omia kuvakkeita.

Huomio: kalalajien tuonti korvaa sovelluksessa olevan lajilistan, jos tiedostosta löytyy lajeja. Jos haluat säilyttää nykyiset lajit, sisällytä ne kaikki tuotavaan tiedostoon.

## Media-ZIP

Media koostuu ZIP-tiedostosta, jonka sisällä on vähintään:

```text
media.json
media/
  kuva-1-....jpg
  video-2-....mp4
```

`media.json`-tiedoston rakenne on:

```json
{
  "version": 1,
  "media": [
    {
      "id": "123",
      "latitude": 61.4981,
      "longitude": 23.7610,
      "pointTime": 1789475400000,
      "originalFileName": "hauki.jpg",
      "fileName": "hauki-550e8400-e29b-41d4-a716-446655440000.jpg",
      "mimeType": "image/jpeg"
    }
  ]
}
```

Jokaisesta `media`-taulukon tietueesta pitää löytyä:

- `id` merkkijonona; sitä käytetään tuonnin duplikaattitarkistuksessa
- `fileName` merkkijonona; ZIP:ssä pitää olla vastaava tiedosto polussa `media/<fileName>`
- `originalFileName` merkkijonona
- `mimeType` merkkijonona, esimerkiksi `image/jpeg`, `image/png`, `audio/mpeg` tai `video/mp4`

`latitude`, `longitude` ja `pointTime` ovat valinnaisia. Ne kannattaa sisällyttää, jos media on tarkoitettu yhdistettäväksi karttapisteeseen. `pointTime` on Unix-aika millisekunteina, ei ISO-merkkijono. `version` on tällä hetkellä arvo `1`; tuonti käyttää ensisijaisesti `media`-taulukkoa.

Mediaa tuotaessa olemassa oleva media ohitetaan, jos sen `id` on jo aiemmin tuotu. Jos `media.json` viittaa puuttuvaan tiedostoon, kyseistä mediaa ei tuoda.

## Koko varmuuskopio: `kalakartta-kaikki-YYYY-MM-DD.zip`

Sovelluksen **Vie kaikki tiedot** -toiminto tuottaa ZIP:n, jossa on nämä osat:

```text
pisteet.json       # catches ja places
sessiot.json       # sessions ja points
paivakirja.json    # vain jos päiväkirjasivuja on olemassa
kalalajit.json     # species
media.json         # media-metatiedot
media/*            # varsinaiset mediatiedostot
```

Tuonti käsittelee osat nimien perusteella. Osia voi siis puuttua, jos tarkoitus on tuoda vain osa aineistosta. Koko varmuuskopion tuonti lisää pisteet, reitit ja päiväkirjasivut nykyisten tietojen joukkoon. Kalalajit korvataan tuotavalla lajilistalla, ja media ohitetaan ulkoisen tunnisteen perusteella.

Nykyinen toteutus ei tallenna ZIP-varmuuskopioon sovelluksen käyttöasetuksia, kuten kartta-, suodatus- tai puhuvan kellon asetuksia. Varmuuskopio sisältää datan ja lajit, ei kaikkia asetuksia.

## Tuonti eri lähdejärjestelmästä

Kun lähdeaineisto on CSV-, Excel-, GPX-, KML- tai jonkin muun järjestelmän muodossa, muunna se yleensä seuraavasti:

1. Muunna jokainen kalasaalis yhdeksi `catches`-taulukon objektiksi.
2. Muunna jokainen muu karttakohde yhdeksi `places`-taulukon objektiksi.
3. Muunna koordinaatit WGS84-desimaaliasteiksi kenttiin `latitude` ja `longitude`.
4. Muunna ajat UTC-muotoon ISO 8601 -merkkijonoiksi.
5. Muunna paino grammoiksi kokonaisluvuksi ja pituus senttimetreiksi kokonaisluvuksi.
6. Muunna lähdejärjestelmän lajinimet KalaKartan `species`-tunnuksiksi. Käytä `OTHER`-tunnusta ja `otherSpecies`-kenttää, jos vastaavaa lajia ei ole.
7. Muunna tapahtumalajit täsmälleen KalaKartan sallituiksi `eventType`-arvoiksi.
8. Jätä tieto pois, jos sitä ei ole; älä arvaa puuttuville koordinaateille tai ajoille arvoja.
9. Tarkista JSON ennen tuontia ja kokeile ensin pienellä tiedostolla.

`id`-kentän voi säilyttää lähdejärjestelmän tunnisteena vain, jos se kirjoitetaan myös `originalRef`-kenttään. Näin lähteen tunnus säilyy näkyvänä, vaikka KalaKartta luo tietueelle oman uuden `id`:n.

## Onko olemassa standardia?

JSON on standardoitu yleinen tiedon esitysmuoto, mutta KalaKartan nykyinen JSON-rakenne on sovelluskohtainen formaatti. Sille ei ole tällä hetkellä erillistä julkista skeemaa, eikä sovellus lue suoraan GeoJSON-, GPX- tai KML-tiedostoja.

Jos tuontitiedostoja tuotetaan ohjelmallisesti, tämän rakenteen pakollisuudet ja tietotyypit kannattaa kuvata erillisellä JSON Schema -skeemalla. JSON Schema helpottaa aineiston automaattista tarkistamista, mutta sen lisääminen ei yksin tee KalaKartasta muiden formaattien lukijaa.

Jos tavoitteena on tulevaisuudessa vaihtaa tietoja useiden paikkatietosovellusten kanssa, järkevä yhdistelmä olisi:

- **GeoJSON (RFC 7946)** kalapisteille ja muille paikkakohteille. Koordinaatit ovat GeoJSON:ssa järjestyksessä `[longitude, latitude]`, mikä poikkeaa KalaKartan JSON-kentistä.
- **GPX 1.1** reiteille ja GPS-jälkien vaihtoon.
- **ISO 8601 / RFC 3339** päivämäärille ja kellonajoille.
- **WGS84** koordinaattijärjestelmäksi.

GeoJSON ei yksin ratkaise kaikkia KalaKartan kenttiä, kuten viehettä, kalastustapahtuman tyyppiä tai sääarvoja. Ne pitäisi tallentaa GeoJSON-ominaisuuksiksi (`properties`) sovittavan oman profiilin mukaisesti. Siksi KalaKartan täydelliseen, häviöttömään varmuuskopiointiin sovelluksen oma JSON/ZIP on tällä hetkellä paras muoto; standardit sopivat paremmin yleiseen paikkatiedon jakamiseen.

## Tarkistuslista ennen tuontia

- Tiedosto on UTF-8-koodattu kelvollinen JSON tai oikea ZIP.
- JSON-kenttien nimet ovat täsmälleen oikein.
- `latitude` ja `longitude` ovat numeroita WGS84-desimaaliasteina.
- Koordinaatit eivät ole vaihtaneet paikkaa: KalaKartta käyttää `latitude`, `longitude`; GeoJSON käyttää `[longitude, latitude]`.
- Ajat ovat ISO-muodossa ja UTC:ssa, paitsi erikseen millisekunteina ilmoitettavat kentät.
- Paino on grammoina ja pituus senttimetreinä, ilman yksikkötekstiä.
- Lajitunnukset ja `eventType`-arvot ovat sallittuja tunnuksia.
- Media-ZIP:ssä jokaiselle `media.json`-tietueelle löytyy tiedosto `media/<fileName>`.
- Tuot ensin pienellä aineistolla ja tarkistat kartalta koordinaatit sekä tietojen määrän.
