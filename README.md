# KalaKartta

KalaKartta on Kotlinilla toteutettu Android-sovellus saaliiden, kalapaikkojen, kalastussessioiden, reittien ja kalapäiväkirjan tallentamiseen.

Sovellus toimii ensisijaisesti paikallisesti. Käyttäjän tiedot tallennetaan laitteen Room/SQLite-tietokantaan, ja tietoja voi tuoda ja viedä JSON- sekä ZIP-muodossa.

## Rakentaminen

Tarvitset Android Studion, Android SDK 35:n ja yhteensopivan JDK:n.

Debug-version rakentaminen Windowsissa:

```text
.\gradlew.bat :app:assembleDebug
```

Yksikkötestit:

```text
.\gradlew.bat :app:testDebugUnitTest
```

## Kartat ja aineistot

Sovellus tukee OpenStreetMap-karttaa sekä Maanmittauslaitoksen ja Traficomin kartta-aineistoja. Maanmittauslaitoksen karttojen käyttö vaatii käyttäjän oman API-avaimen, jota ei sisällytetä repositorioon.

Kartta- ja sääpalvelujen aineistot eivät kuulu tämän projektin MIT-lisenssin piiriin:

- OpenStreetMap: ODbL-lisenssi ja lähdemerkintävaatimus.
- Maanmittauslaitos: avoin aineisto, jonka sovellus ilmoittaa CC BY 4.0 -lisenssin mukaisesti.
- Traficom: merikartta- ja veneilykartta-aineistot Traficomin ehtojen mukaisesti. Aineistot eivät ole navigointikäyttöön.
- Ilmatieteen laitos: sää- ja säähavaintodata Ilmatieteen laitoksen avoimen datan ehtojen mukaisesti.

## Suomen merialueen ruudukko

`FinlandSeaService` on erillinen palvelu, joka luokittelee WGS84-koordinaatin
MML-aineistosta johdettuun 500 metrin EPSG:3067-ruutuun. Palvelua ei ole vielä
kytketty käyttöliittymään, karttanäkymään, saalistietoihin tai reittien
tallennukseen. Generaattori, binääriformaatti, validoitu assetti,
visualisointi ja tarkemman ruudukon päivitysohje ovat dokumentissa
[`docs/finland_sea_grid.md`](docs/finland_sea_grid.md).

## Käyttöoikeudet ja rajoitukset

Sovellus käyttää sijaintia nykyisen sijainnin näyttämiseen ja kalastussessioiden reittien tallentamiseen. Käyttöjärjestelmä voi pyytää sijainti-, ilmoitus- ja hälytysten käyttöoikeuksia ominaisuuksien mukaan.

Sovellus ei ole navigointiväline. Käyttäjä vastaa omien tietojensa varmuuskopioinnista ja ulkopuolisten kartta- ja sääpalvelujen käyttöehtojen noudattamisesta.

## Lisenssi

Projektin oma lähdekoodi on MIT-lisensoitu. Katso [LICENSE](LICENSE). Kolmannen osapuolen kirjastot, kuvakkeet, kartta-aineistot ja sääaineistot ovat omien lisenssiensä alaisia.

KalaKartan nimi, logo ja muu brändi eivät kuulu MIT-lisenssiin. Muut projektit ja johdannaiset eivät saa esiintyä KalaKartan virallisina versioina ilman erillistä lupaa.
