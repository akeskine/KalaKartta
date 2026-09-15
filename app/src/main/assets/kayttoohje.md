# KalaKartta käyttöohje

KalaKartta on sovellus kalastajille, jotka haluavat pitää tarkkaa kirjaa saaliistaan ja kalapaikoistaan. Sovellus on suunniteltu erityisesti nopeaan ja helppoon käyttöön vesillä ollessa.

KalaKartan keskeisiä periaatteita ovat:
- **Helppous:** Kalapisteiden lisäys onnistuu parilla klikkauksella suoraan kartalta. Puhelimen näytön ollessa pois päältä sovelluksen saa auki ilman lukituksen avaamista.
- **Suodatus:** Voit analysoida saaliitasi monipuolisesti, esimerkiksi vuodenaikojen, kellonajan tai sääolosuhteiden (kuten tuulen suunnan) mukaan.
- **Suorituskyky:** Sovellus on optimoitu kestämään kymmeniä tuhansia pisteitä ja tuhansia tallennettuja kalastussessioita/reittejä takeltelematta.
- **Yksityisyys:** Kaikki tiedot tallennetaan vain laitteeseesi. Niitä ei lähetetä palvelimille. Pisteiden jakaminen on aina käyttäjän omassa kontrollissa.
- **Automaatio:** Sovellus hakee säätiedot automaattisesti kalapisteisiin Ilmatieteen laitoksen (FMI) avoimesta datasta.
- **Monipuoliset kartat:** Voit käyttää OpenStreetMapin lisäksi Maanmittauslaitoksen (MML) tarkkoja maasto- ja ilmakuvia sekä Traficomin merikartta-aineistoja.

## 1. Kartan käyttö

Kartan keskellä on aina tähtäin. Voit liikkua kartalla raahaamalla ja zoomata nipistämällä.<br><br>

Oma sijaintisi näytetään kartalla, jos GPS on päällä. Voit keskittää kartan omaan sijaintiisi vasemman alakulman sijaintipainikkeesta.<br><br>

Kartan taustakartan voit vaihtaa valikosta (**Valikko -> Taustakartta**).<br><br> 

KalaKartassa on käytettävissä seuraavat karttapohjat:
- **OpenStreetMap:** Maailmanlaajuinen avoin kartta-aineisto.
- **MML Maastokartta ja Ilmakuva:** Maanmittauslaitoksen tarkat aineistot Suomesta. Käyttö vaatii ilmaisen API-avaimen, jonka voit hankkia Maanmittauslaitoksen asiointipalvelusta.
- **Traficom merikartta:** Traficomin virallinen merikartta-aineisto (Merikarttasarjat). *Huom: Sisältää Liikenne- ja viestintävirasto Traficomin merikartta-aineistoa. Ei navigointikäyttöön.*
- **Traficom veneilykartta:** Traficomin virallinen veneilykartta-aineisto eräiltä järvialueilta, mm. Puula ja Inari. *Huom: Sisältää Liikenne- ja viestintävirasto Traficomin merikartta-aineistoa. Ei navigointikäyttöön.*

Voit vaihtaa karttapohjan lennossa suoraan karttanäkymästä, jos olet kytkenyt **Karttapohjan pikavalinnan** päälle asetuksista.

## 2. Kalapisteen lisääminen

Lisää kalapiste painamalla kartan oikeassa alakulmassa olevaa **+**-painiketta. Tämä avaa Lisää merkintä -ikkunan:
- **Laji:** Valitse kalalaji kuvakkeesta. Jos lajia ei ole listalla, voit valita "Muu kalalaji" tai lisätä uusia lajeja **Valikko -> Kalalajit -> Muokkaa kalalajeja** -valikosta.
- **Tapahtuma:** Valitse oliko kyseessä saatu kala, karkuutus, varma tärppi, epävarma tärppi vai seurio.
- **Paino ja pituus:** Voit syöttää ne suoraan pikalistaan tai jättää tyhjäksi.
- **Lisää tarkemmat tiedot:** Tästä painikkeesta pääset täyttämään laajemmat tiedot (esim. viehe, syvyys, sää).

Piste tallentuu aina kartan keskipisteen (tähtäimen) kohdalle.

## 3. Muun paikan lisääminen

Voit tallentaa myös muita kiinnostavia kohteita kuten kiviä, matalikkoja, veneenlaskupaikkoja tai laavuja:<br><br>

1. Paina **+**-painiketta.<br><br>
2. Valitse alhaalta **"Muu paikka"**.<br><br>
3. Valitse haluamasi kohdetyyppi (esim. Kivi tai Veneramppi).<br><br>
4. Voit antaa kohteelle nimen.<br><br>

## 4. Pisteiden tarkastelu ja muokkaus

Klikkaa kartalla olevaa kuvaketta nähdäksesi sen tiedot.
- **Kalapiste:** Näyttää lajin, painon, pituuden, ajan ja tärkeimmät säätiedot.
- **Muu paikka:** Näyttää tyypin ja nimen.

Klikkaamalla tiedot-ikkunaa aukeaa muokkausnäkymä, jossa voit:
- Muuttaa kaikkia tallennettuja tietoja.
- Poistaa pisteen.
- **Media:** Voit liittää pisteeseen kuvia, äänitteitä ja videoita. Media voidaan myös yhdistää automaattisesti, jos se on tallennettu 5 metrin etäisyydellä ja 1 sekunnin aikaikkunalla pisteen tallennushetkestä. Liitetyt mediatiedostot näkyvät pisteen tiedot -dialogissa.

## 5. Tiedon suodatus

Voit suodattaa kartalla näkyviä pisteitä **Valikko -> Tiedon suodatus** -valikosta.<br><br>

Suodatusmahdollisuuksia ovat mm.:
- **Aika:** Kiinteä aikaväli tai vuosittain toistuva jakso (esim. "kaikki toukokuun saaliit eri vuosilta").
- **Kellonaika:** Esim. vain iltasyönnin saaliit.
- **Kalalaji tai paikan tyyppi.**
- **Sääolosuhteet:** Ilmanpaine, veden lämpötila ja tuulen suunta.
- **Tuulen suunta:** Voit määrittää sektorin (asteet min-max) 
- **Vapaa teksti:** Etsii tekstiä lisätiedoista tai muistiinpanoista.
- **Aluerajaus:** Voit rajata suodatuksen vain tietylle kartta-alueelle. Paina "Rajaa alue kartalta", rajaa haluamasi alue ja vahvista valinta.

Suodatus on voimassa, kunnes se nollataan (Poista suodattimet). Aktiivinen suodatus näkyy kartan yläreunassa tekstinä.

## 6. Sääasetukset ja automaattinen haku

Sovellus hakee oletuksena säätiedot automaattisesti lähimmiltä sääasemilta, kun lisäät kalapisteen. Tämä ominaisuus on myös mahdollista kytkeä pois päältä **Valikko -> Sää -> Sääasetukset** -valikosta.
- **Sääasemat:** Tiedot haetaan usein usealta lähimmältä asemalta (max. 300 km säteeltä kalapisteestä) parhaan tarkkuuden saavuttamiseksi.
- **Automaattinen päivitys:** Voit kytkeä automaattihaun pois päältä sääasetuksista.
- **Puuttuvien tietojen haku:** Voit hakea puuttuvat säätiedot takautuvasti "Päivitä puuttuvat säätiedot" -toiminnolla.

## 7. Kalalajien hallinta

Voit muokata kalalajeja kohdasta **Valikko -> Kalalajit**.
- **Muokkaa lajeja:** Voit vaihtaa lajien järjestystä sovelluksen valintalistoissa ja asettaa rajat "pienelle", "suurelle" ja "jättiläiselle" kalalle.
- **Omat ikonit:** Voit lisätä lajin eri kokoisille kaloille oman kuvan puhelimesi galleriasta. Kuvan maksimikoko on 1536 x 1024 pikseliä, sen pitää olla png-muotoinen. Kuvan taustan kannattaa olla läpinäkyvä (transparent).
- **Lajin lisäys:** Voit myös lisätä kokonaan uusia kalalajeja ja määritellä niille omat ikonit.
- **Omien kalalajien export** Voit tuoda ja viedä omien kalalajiesi tiedot JSON-muodossa. Tiedoissa viedään myös mahdolliset omat ikonit binäärimuodossa.

## 8. Pisteiden tuonti ja vienti (Export/Import)

Voit viedä ja tuoda pisteitä, reittejä, mediaa ja kalapäiväkirjasivuja JSON-muodossa (**Valikko -> Tiedonsiirto**).
- **Vie kaikki tiedot:** Tallentaa sovelluksen kaiken datan (pisteet, reitit, kalapäiväkirjasivut, mediatiedostojen linkitykset ja asetukset) yhteen tiedostoon.
- **Tuo kaikki tiedot:** Palauttaa kaikki sovelluksen tiedot, myös kalapäiväkirjasivut, tiedostosta.
- **Poista kaikki tiedot:** Tyhjentää kaikki sovelluksen tiedot, mukaan lukien pisteet, reitit, mediat, kalapäiväkirjan ja asetukset.
- **Vie kalapisteet ja muut pisteet:** Tallentaa kaikki kalapisteet ja muut merkit tiedostoon.
- **Vie suodatetut pisteet:** Jos suodatus on päällä, voit halutessasi viedä vain ne pisteet, jotka näkyvät parhaillaan kartalla.
- **Tuo kalapisteet ja muut pisteet:** Lukee pisteet tiedostosta.
- **Vie kalastussessiot:** Tallentaa kaikki kalastussessiot ja niiden reittipisteet tiedostoon.
- **Tuo kalastussessiot:** Lukee sessiot ja reitit tiedostosta.
- **Poista kalastussessiot:** Tyhjentää kaikki tallennetut reittitiedot sovelluksesta.
- **Duplikaatit:** Tuonnin yhteydessä sovellus tarkistaa päällekkäisyydet. Pisteet katsotaan samoiksi, jos niiden etäisyys on **enintään 2 metriä**. Voit valita ohitetaanko duplikaatit, korvataanko vanhat vai tuodaanko kaikki. Reittien osalta tuodaan toistaiseksi kaikki sessiot uusina.
- **Infopallo (i):** Tiedonsiirto-valikon yläreunassa on infopainike, josta näet yksityiskohtaiset tilastot sovelluksen sisältämästä datasta (sessiot, pisteet, päiväkirjasivut, mediatiedostot) ja käytetystä tallennustilasta.

## 9. Yhteenveto ja reissumuistiinpanot

- **Yhteenveto:** **Valikko -> Yhteenveto** näyttää tilastot saaliistasi valitulta ajalta (esim. kpl-määrät ja suurimmat kalat). Tietoa voidaan suodattaa myös kalastajan mukaan, esim. jos halutaan nähdä erikseen Esan ja Vesan saaliit viikonlopun ajalta. Voit kopioida yhteenvedon tekstimuodossa leikepöydälle (Kopioi teksti -painike) jaettavaksi eteenpäin.
- **Näytä kartalla:** Voit tarkastella yhteenvedon sisältämiä pisteitä suoraan kartalla painamalla karttakuvaketta.
- **Kopioi:** Voit kopioida yhteenvedon tekstin leikepöydälle painamalla kopiointikuvaketta.

## 10. Yleiset asetukset

Yleisistä asetuksista voit:
- Määrittää **oletuskalastajan**, joka asetetaan automaattisesti uusiin saaliisiin. Kalastajatietoa voidaan käytää myöhemmin suodatus- ja yhteenveto-toimintojen rajaamiseen.
- Kytkeä päälle/pois **mittaustyökalut**. Tämän valinnan alta voit valita näytetäänkö kartalla mittakaavajana ja/tai mittaustyökalu-painike.
- Kytkeä päälle/pois **oletuskalastaja** kartan oikeassa alareunassa.
- Valita keskitetäänkö kartta käynnistyksessä automaattisesti omaan sijaintiisi. Tämä on testatusti kätevää vesillä, jos halutaan lisätä kala puhelimen ollessa taskussa näyttö pois päältä.
- Määrittää puhuvan kellon asetukset sekä käynnistää ja pysäyttää sen.

## 11. Karttapohjan pikavalinta

Pikavalinnan avulla voit vaihtaa karttapohjaa nopeasti suoraan karttanäkymästä ilman asetuksiin menemistä.

- Mene kohtaan **Valikko -> Taustakartta**. 
- Laita ruksi kohtaan **"Näytä karttapohjan pikavalinta"**.
- Kartan vasempaan yläreunaan ilmestyy karttakuvake (mittauspainikkeen alapuolelle). Painiketta klikkaamalla karttapohja vaihtuu seuraavaan valittuun vaihtoehtoon.
- Voit itse päättää mitkä karttapohjat ovat mukana pikavalinnan kierrossa: rasti haluamasi kartat **"Pikavalinta"**-sarakkeesta Taustakartta-asetuksissa.
- Huom: MML:n kartat ovat mukana pikavalinnassa vain, jos olet asettanut toimivan API-avaimen.

## 12. Mittaustyökalu

Mittaustyökalulla voit mitata etäisyyksiä ja reittien pituuksia suoraan kartalta. Työkalu on käytettävissä, kun se on kytketty päälle Yleisistä asetuksista Mittaustyökalut-sivulta.
- **Aloitus:** Paina kartan oikeassa yläreunassa olevaa mittaustyökalu-painiketta (nuppineula-ikoni). Kartan keskellä (tähtäimen kohdalla) on tällöin aloituspiste.
- **Reittipisteiden lisäys:** Siirrä karttaa niin, että tähtäin on haluamassasi kohdassa ja paina mittauspainiketta lyhyesti. Pisteeseen tulee punainen nuppineula ja näet etäisyyden edellisestä pisteestä sekä koko reitin pituuden ruudun yläreunassa.
- **Pisteen poisto:** Voit poistaa viimeisimmän lisätyn pisteen mittauspainikkeen alapuolella olevalla "Undo"-painikkeella.
- **Nollaus:** Voit nollata mittauksen painamalla mittauspainiketta pitkään (n. 1 sekunti). Kaikki mittauspisteet ja reitti häviävät kartalta.

## 13. Kalastussessiot

Kalastussessiot mahdollistavat kuljetun reitin tallentamisen automaattisesti taustalla ja reittitiedon keräämisen.

- **Tallennuksen aloitus:** **Valikko -> Kalastussessiot -> Aloita tallennus**. Kun tallennus on käynnissä, kartan vasemmassa yläkulmassa näkyy REC-teksti. Tallennuksen aloitus on mahdollista vain, jos sijaintipalvelu on puhelimessa päällä.
- **Tallennuksen lopetus:** **Valikko -> Kalastussessiot -> Lopeta tallennus**.
- **Sessioiden tarkastelu:** Pääset selaamaan tallennettuja sessioita kohdasta **Valikko -> Kalastussessiot -> Hae kalastussessiot**.
- **Kalenteri:** Sessiot on järjestetty kalenteriin. Valitse päivä nähdäksesi kyseisen päivän sessiot.
- **Toistaminen:** Klikkaamalla sessiota näet sen keston, pituuden ja reittipisteiden määrän. Voit toistaa session kartalla painamalla **"Toista"**. Toiston käynnistys on manuaalinen. Toiston aikana voit säätää nopeutta (jopa 2880x), kelata reittiä eteen ja taaksepäin tai pysäyttää toiston haluamaasi kohtaan.
- **Muokkaus ja trimmaus:** Voit muokata session tietoja, kuten **kalastajan nimeä**, session editorissa. Trimmaustoiminnolla voit poistaa session alusta tai lopusta turhia siirtymiä (esim. siirtymä rannasta kalapaikalle).
- **Tallennusvälit:** Voit säätää reittipisteiden tallennusvälejä omassa dialogissaan. Sovellus pidentää tallennusväliä automaattisesti, jos pysyt paikallaan (etäisyys < 20 m) akun säästämiseksi.
- **Vienti ja tuonti:** Sessiot reitteineen voidaan viedä ja tuoda JSON-muodossa **Tiedonsiirto**-asetuksista.

## 14. Kalastetut alueet (Heat map)

Kalastetut alueet -toiminto (Heat map) visualisoi kartalla ne alueet, joissa olet viettänyt eniten aikaa kalastussessioiden aikana. Tämän toiminnon avulla voit verrata saamiasi saaliita alueella viettämääsi aikaan tai nähdä helposti, mitkä osat vesistöä ansaitsisivat vielä lisää tutkimista.

- **Käyttöönotto:** Voit kytkeä ominaisuuden päälle kohdasta **Valikko -> Kalastetut alueet -> Näytä kalastetut alueet**.
- **Näytä kalastetut alueet**: Tällä valinnalla tehdään kalastettujen alueiden heat map kartalla näkyväksi. 
- **Näytä kalastussessioiden reitit**: Tällä valinnalla tehdään kalastussessioiden reitit kartalla näkyväksi. 
- **Suodatus:** Voit valita, että kartalle valitut suodatusehdot pätevät myös heat mapiin ja/tai reittipisteisiin.
- **Pikavalinta:** Voit lisätä kartan yläreunaan painikkeen heat mapin nopeaa kytkemistä varten valitsemalla **"Näytä kalastettujen alueiden pikavalinta"**.
- **Konfigurointi:**
    - **Väri:** Voit vaihtaa heat mapin värin klikkaamalla väripalkkia (vaihtoehdot punainen, violetti, vihreä).<br><br>
    - **Ruudun koko:** Määrittää kuinka tarkasti alueet jaetaan (vaihtoehdot 50, 100, 300 ja 1000 metriä). Oletus on 300 metriä.<br><br>
    - **Laskentatapa:** Voit vaihtaa ruudun käyntikertojen laskentatapaa. Ruudun käyntikerrat lasketaan joko ruutuun osuvien reittipisteiden määrästä tai siitä, monenko kalastussession reittipisteitä ruudussa on. Säätämällä reittipisteiden määrän minimi- ja maksimiarvoja ruutua kohden voit määrittää, montako käyntiä ruudussa tietty heat mapin värisävy vaatii.<br><br>
    - **Nopeussuodatus:** Voit suodattaa heat mapia reittipisteen nopeuden perusteella.<br><br>
    - **Siirtymäpisteet:** Voit valita, poistetaanko siirtymien (suuret nopeudet) kohdalta vain heat map -ruudut vai myös reittipisteet kartalta. Voit myös valita, kuinka suuri nopeus reittipisteessä tekee reittipisteestä siirtymäpisteeksi tulkittavan.<br><br>
- **Reittien lisäasetukset:** Avaa **Kalastetut alueet -> Reittien lisäasetukset** ja ota käyttöön **Häivytä vanhat reittiviivat**. Asetus näyttää uusimmat reitit täysin näkyvinä, häivyttää vanhempia reittejä vähitellen ja jättää aloitusrajan ylittäneet vanhat reitit piirtämättä. Asetuksen alla olevat arvot ilmoitetaan päivinä: vasemmanpuoleinen arvo kertoo, minkä iän jälkeen häivytys alkaa, ja oikeanpuoleinen, mihin asti reitti näkyy täysin.
- **Suorituskyky:** Jos kartan reittiviivojen piirtäminen hidastaa sovellusta, pienennä Reittien lisäasetuksissa häivytyksen aloitusrajaa. Tällöin kartalle piirretään vain uudemmat reitit ja vanhat jätetään kokonaan pois. Tarvittaessa rajaa dataa myös **Tiedon suodatus** -asetuksen aika- tai aluerajauksella. Sovellus rajoittaa lisäksi näytettävien heat map -ruutujen ja reittipisteiden enimmäismäärää; näitä rajoja voi muuttaa **Kehittäjäasetukset**-valikosta.

## 15. Puhuva kello

Puhuva kello on toiminnallisuus, joka kertoo kellonajan ja muita tietoja ääneen, jotta sinun ei tarvitse katsoa puhelinta kalastuksen aikana. Toiminto vaatii käyttäjältä luvan lähettää ilmoituksia.<br><br>

- **Käyttöönotto:** Pääset puhuvan kellon asetuksiin ja statussivulle **Valikko -> Yleiset -> Puhuva kello**.
- **Toiminnot:** Sovellus voi ilmoittaa kellonajan lisäksi auringon nousu- ja laskuajat, sääennusteen sekä akun varauksen.
- **Asetukset:** Voit määrittää kellonajan ilmoitusvälin, auringon nousu- ja laskuajoista ilmoittamisen, sääennusteiden asetukset sekä vakiopuhuttelun. Voit myös valita, onko kello automaattisesti päällä kalastussession ollessa käynnissä.
- **Äänenhallinta:** Sovellus vaimentaa automaattisesti muun musiikin tai puheen ilmoituksen ajaksi.
- **Status:** Dialogin otsikon alla näet kellon nykyisen tilan ja seuraavan ilmoitusajan.

## 16. Kalapäiväkirja

Kalapäiväkirja löytyy kohdasta **Valikko -> Kalapäiväkirja**. Se on erillinen muistiinpano-osio saalismerkinnöistä ja kalastussessioista koostuville reissukuvauksille.

- **Kalenterin käyttö:** Kalenterissa päivä, jolla on vähintään yksi päiväkirjasivu, merkitään erillisellä ilmaisimella. Vaihda kuukautta nuolipainikkeilla tai napauttamalla kuukausi- ja vuositekstiä. Valitse päivä nähdäksesi sitä koskevat sivut. Koko näkymää voi vierittää myös vaakasuuntaisessa näyttötilassa.
- **Uusi sivu:** Valitse **Lisää päiväkirjasivu**, valitse päivämäärä ja syötä paikka, kalastustapa, saalis ja kertomus. Valitsemalla **Usean päivän merkintä** voit antaa sivulle myös loppupäivämäärän.
- **Tekstin generointi:** **Saalis**-kentän **Generoi** muodostaa yhteenvedon valitun aikavälin saaliista. **Kertomus**-kentän **Generoi** muodostaa yhteenvedon samalla aikavälillä tallennetuista kalastussessioista. Voit muokata muodostettua tekstiä ennen tallentamista.
- **Media:** Päiväkirjasivulle voi liittää kuvia, äänitteitä ja videoita. Kalenterinäkymässä kortin avaaminen näyttää sivun tiedot ja siihen liittyvän median.
- **Sivujen käsittely:** Avaa sivu napauttamalla sen laatikkoa. Jos samalle päivälle osuu useita sivuja, otsikossa näytetään sivunumero, esimerkiksi `14.9.2026 (sivu 2) - Laru`. Sivun valikosta voit muokata tai poistaa sivun.
- **Haku:** Kirjoita hakusana kenttään **Hae päiväkirjasivuista** ja paina **Hae** tai näppäimistön hakupainiketta. Haku kohdistuu päivämäärään, paikkaan, kalastustapaan, saaliiseen ja kertomukseen. Haku ei käynnisty jokaisella näppäimenpainalluksella.
- **Hakusäännöt:** Haku ei huomioi kirjainkokoa ja löytää osittaisia osumia. Useampi sana tarkoittaa, että jokaisen sanan pitää löytyä jostain haettavasta kentästä. Päivämäärän voi antaa muodossa `15.9.2026` tai pelkkänä vuotena, kuten `2026`; monipäiväinen sivu löytyy, jos päivä osuu sen aikavälille. Kalalajisanasto tunnistaa myös yleisiä taivutusmuotoja ja yhdyssanoja, joten esimerkiksi `hauki` löytää tekstin `hauenkalastus`.

Kalapäiväkirjasivut voidaan viedä ja tuoda JSON-muodossa **Valikko -> Tiedonsiirto** -valikon **Vie kalapäiväkirja**- ja **Tuo kalapäiväkirja**-toiminnoilla.
