# KalaKartta käyttöohje

KalaKartta on sovellus kalastajille, jotka haluavat pitää tarkkaa kirjaa saaliistaan ja kalapaikoistaan. Sovellus on suunniteltu erityisesti nopeaan ja helppoon käyttöön vesillä ollessa.

KalaKartan keskeisiä periaatteita ovat:
- **Helppous:** Kalapisteiden lisäys onnistuu parilla klikkauksella suoraan kartalta. Puhelimen näytön ollessa pois päältä sovelluksen saa auki ilman lukituksen avaamista.
- **Suodatus:** Voit analysoida saaliitasi monipuolisesti, esimerkiksi vuodenaikojen, kellonajan tai sääolosuhteiden (kuten tuulen suunnan) mukaan.
- **Suorituskyky:** Sovellus on optimoitu kestämään kymmeniä tuhansia pisteitä takeltelematta.
- **Yksityisyys:** Kaikki tiedot tallennetaan vain laitteeseesi. Niitä ei lähetetä palvelimille. Pisteiden jakaminen on aina käyttäjän omassa kontrollissa.
- **Automaatio:** Sovellus hakee säätiedot automaattisesti kalapisteisiin Ilmatieteen laitoksen (FMI) avoimesta datasta.
- **Monipuoliset kartat:** Voit käyttää OpenStreetMapin lisäksi Maanmittauslaitoksen (MML) tarkkoja maasto- ja ilmakuvia sekä Traficomin merikartta-aineistoja.

## 1. Kartan käyttö

Kartan keskellä on aina tähtäin. Voit liikkua kartalla raahaamalla ja zoomata nipistämällä.

Oma sijaintisi näytetään kartalla, jos GPS on päällä. Voit keskittää kartan omaan sijaintiisi vasemman alakulman sijaintipainikkeesta.

Kartan taustakartan voit vaihtaa valikosta (**Valikko -> Taustakartta**). 

KalaKartassa on käytettävissä seuraavat karttapohjat:
- **OpenStreetMap:** Maailmanlaajuinen avoin kartta-aineisto.
- **MML Maastokartta ja Ilmakuva:** Maanmittauslaitoksen tarkat aineistot Suomesta. Käyttö vaatii ilmaisen API-avaimen, jonka voit hankkia Maanmittauslaitoksen asiointipalvelusta.
- **Traficom merikartta:** Traficomin virallinen merikartta-aineisto (Merikarttasarjat). *Huom: Sisältää Liikenne- ja viestintävirasto Traficomin merikartta-aineistoa. Ei navigointikäyttöön.*
- **Traficom veneilykartta:** Traficomin virallinen veneilykartta-aineistoa eräiltä järvialueilta, mm. Puula ja Inari. *Huom: Sisältää Liikenne- ja viestintävirasto Traficomin merikartta-aineistoa. Ei navigointikäyttöön.*

Voit vaihtaa karttapohjan lennossa suoraan karttanäkymästä, jos olet kytkenyt **Karttapohjan pikavalinnan** päälle asetuksista.

## 2. Kalapisteen lisääminen

Lisää kalapiste painamalla kartan oikeassa alakulmassa olevaa **+**-painiketta. Tämä avaa Lisää merkintä-ikkunan:
- **Laji:** Valitse kalalaji kuvakkeesta. Jos lajia ei ole listalla, voit valita "Muu kalalaji" tai lisätä uusia lajeja **Valikko -> Kalalajit -> Muokkaa kalalajeja** -valikosta.
- **Tapahtuma:** Valitse oliko kyseessä saatu kala, karkuutus, varma tärppi, epävarma tärppi vai seurio.
- **Paino ja pituus:** Voit syöttää ne suoraan pikalistaan tai jättää tyhjäksi.
- **Lisää tarkemmat tiedot:** Tästä painikkeesta pääset täyttämään laajemmat tiedot (esim. viehe, syvyys, sää).

Piste tallentuu aina kartan keskipisteen (tähtäimen) kohdalle.

## 3. Muun paikan lisääminen

Voit tallentaa myös muita kiinnostavia kohteita kuten kiviä, matalikkoja, veneenlaskupaikkoja tai laavuja:
1. Paina **+**-painiketta.
2. Valitse alhaalta **"Muu paikka"**.
3. Valitse haluamasi kohdetyyppi (esim. Kivi tai Veneramppi).
4. Voit antaa kohteelle nimen.

## 4. Pisteiden tarkastelu ja muokkaus

Klikkaa kartalla olevaa kuvaketta nähdäksesi sen tiedot.
- **Kalapiste:** Näyttää lajin, painon, pituuden, ajan ja tärkeimmät säätiedot.
- **Muu paikka:** Näyttää tyypin ja nimen.

Klikkaamalla tiedot-ikkunaa aukeaa muokkausnäkymä, jossa voit:
- Muuttaa kaikkia tallennettuja tietoja.
- Poistaa pisteen.

## 5. Tiedon suodatus

Voit suodattaa kartalla näkyviä pisteitä **Valikko -> Tiedon suodatus** -valikosta.

Suodatusmahdollisuuksia ovat mm.:
- **Aika:** Kiinteä aikaväli tai vuosittain toistuva jakso (esim. "kaikki toukokuun saaliit eri vuosilta").
- **Kellonaika:** Esim. vain iltasyönnin saaliit.
- **Kalalaji tai paikan tyyppi.**
- **Sääolosuhteet:** Ilmanpaine, veden lämpötila ja tuulen suunta.
- **Tuulen suunta:** Voit määrittää sektorin (min-max), jolta tuulleesssa saaliit on saatu.
- **Vapaa teksti:** Etsii tekstiä lisätiedoista tai muistiinpanoista.
- **Aluerajaus:** Voit rajata suodatuksen vain tietylle kartta-alueelle. Paina "Rajaa alue kartalta", rajaa haluamasi alue ja vahvista valinta.

Suodatus on voimassa, kunnes se nollataan (Poista suodattimet). Aktiivinen suodatus näkyy kartan yläreunassa tekstinä.

## 6. Sääasetukset ja automaattinen haku

Sovellus hakee oletuksena säätiedot automaattisesti lähimmiltä sääasemilta, kun lisäät kalapisteen. Tämä ominaisuus on myös mahdolista kytkeä pois päältä **Valikko -> Sää -> Sääasetukset** -valikosta.
- **Sääasemat:** Tiedot haetaan usein usealta lähimmältä asemalta (max. 300 km säteeltä kalapisteestä) parhaan tarkkuuden saavuttamiseksi.
- **Automaattinen päivitys:** Voit kytkeä automaattihaun pois päältä sääasetuksista.
- **Puuttuvien tietojen haku:** Voit hakea puuttuvat säätiedot takautuvasti "Päivitä puuttuvat säätiedot" -toiminnolla.

## 7. Kalalajien hallinta

Voit muokata kalalajeja kohdasta **Valikko -> Kalalajit**.
- **Muokkaa lajeja:** Voit vaihtaa lajien järjestystä sovelluksen valintalistoissa ja asettaa rajat "pienelle, ""suurelle" ja "jättiläiselle" kalalle.
- **Omat ikonit:** Voit lisätä lajin eri kokoisille kaloille oman kuvan puhelimesi galleriasta. Kuvan maksimikoko on 1536 x 1024 pikseliä, sen pitää olla png-muotoinen. Kuvan taustan kannattaa olla läpinäkyvä (transparent).
- **Lajin lisäys:** Voit myös lisätä kokonaan uusia kalalajeja ja määritellä niille omat ikonit.
- **Omien kalalajien export** Voit tuoda ja viedä omien kalalajiesi tiedot JSON-muodossa. Tiedoissa viedään myös mahdolliset omat ikonit binäärimuodossa.

## 8. Pisteiden tuonti ja vienti (Export/Import)

Voit viedä ja tuoda pisteitä sekä reittejä JSON-muodossa (**Valikko -> Tiedonsiirto**).
- **Vie pisteet:** Tallentaa kaikki kalapisteet ja muut merkit tiedostoon.
- **Vie suodatetut pisteet:** Jos suodatus on päällä, voit halutessasi viedä vain ne pisteet, jotka näkyvät parhaillaan kartalla.
- **Tuo pisteet:** Lukee pisteet tiedostosta.
- **Vie reitit:** Tallentaa kaikki kalastussessiot ja niiden reittipisteet tiedostoon.
- **Tuo reitit:** Lukee sessiot ja reitit tiedostosta.
- **Poista kaikki reitit:** Tyhjentää kaikki tallennetut reittitiedot sovelluksesta.
- **Duplikaatit:** Tuonnin yhteydessä sovellus tarkistaa päällekkäisyydet. Pisteet katsotaan samoiksi, jos niiden etäisyys on **enintään 2 metriä**. Voit valita ohitetaanko duplikaatit, korvataanko vanhat vai tuodaanko kaikki. Reittien osalta tuodaan toistaiseksi kaikki sessiot uusina.

## 9. Yhteenveto ja reissumuistiinpanot

- **Yhteenveto:** **Valikko -> Yhteenveto** näyttää tilastot saaliistasi valitulta ajalta (esim. kpl-määrät ja suurimmat kalat). Tietoa voidaan suodattaa myös kalastajan mukaan, esim. jos halutaan nähdä erikseen Esa ja Vesan saaliit viikonlopun ajalta. Voit kopioida yhteenvedon tekstimuodossa leikepöydälle (Kopioi teksti -painike) jaettavaksi eteenpäin.
- **Näytä kartalla:** Voit tarkastella yhteenvedon sisältämiä pisteitä suoraan kartalla painamalla karttakuvaketta.
- **Kopioi:** Voit kopioida yhteenvedon tekstin leikepöydälle painamalla kopiointikuvaketta.

## 10. Yleiset asetukset

Yleisistä asetuksista voit:
- Määrittää **oletuskalastajan**, joka asetetaan automaattisesti uusiin saaliisiin. Kalastajatietoa voidaan käytää myöhemmin suodatus- ja yhteenveto-toimintojen rajaamiseen.
- Kytkeä päälle/pois **mittaustyökalut**. Tämän valinnan alta voit valita näytetäänkö kartalla mittakaavajana ja/tai mittaustyökalu-painike.
- Kytkeä päälle/pois **oletuskalastaja** kartan oikeassa alareunassa.
- Valita keskitetäänkö kartta käynnistyksessä automaattisesti omaan sijaintiisi. Tämä on testatusti kätevää vesillä, jos halutaan lisätä kala puhelimen ollessa taskussa näyttö pois päältä.

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

Kalastussessiot mahdollistavat kuljetun reitin tallentamisen automaattisesti taustalla.

- **Tallennuksen aloitus:** **Valikko -> Kalastussessiot -> Aloita tallennus**. Kun tallennus on käynnissä, kartan vasemmassa yläkulmassa vilkkuu punainen REC-pallo. Tallennuksen aloitus on mahdollista vain, jos sijaintipalvelu on puhelmiessa päällä.
- **Tallennuksen lopetus:** **Valikko -> Kalastussessiot -> Aloita tallennus**.
- **Sessioiden tarkastelu:** Pääset selaamaan tallennettuja sessioita kohdasta **Valikko -> Kalastussessiot -> Hae kalastussessiot **.
- **Kalenteri:** Sessiot on järjestetty kalenteriin. Valitse päivä nähdäksesi kyseisen päivän sessiot.
- **Toistaminen:** Klikkaamalla sessiota näet sen keston, pituuden ja reittipisteiden määrän. Voit toistaa session kartalla painamalla **"Toista"**. Toiston aikana voit säätää nopeutta ja kelata reittiä eteen ja taaksepäin tai pysäyttää toiston haluamaasi kohtaan.
- **Vienti ja tuonti:** Sessiot reitteineen voidaan viedä ja tuoda JSON-muodossa **Tiedonsiirto**-asetuksista.

## 14. Kalastetut alueet (Heatmap)

Kalastetut alueet -toiminto (Heatmap) visualisoi kartalla ne alueet, joissa olet viettänyt eniten aikaa kalastussessioiden aikana.

- **Käyttöönotto:** Voit kytkeä ominaisuuden päälle kohdasta **Valikko -> Kalastetut alueet -> Näytä kalastetut alueet**.
- **Pikavalinta:** Voit lisätä kartan yläreunaan painikkeen heatmapin nopeaa kytkemistä varten valitsemalla **"Näytä kalastettujen alueiden pikavalinta"**.
- **Konfigurointi:**
    - **Väri:** Voit vaihtaa heatmapin värin (punainen, violetti, keltainen, vihreä).
    - **Ruudun koko:** Määrittää kuinka tarkasti alueet jaetaan (esim. 100 metriä).
    - **Min/Max vierailukerrat:** Voit säätää, kuinka monta kertaa ruudussa on täytynyt käydä, jotta se näkyy kartalla ja milloin se saavuttaa maksimivärin.
