# Puhuvan kellon TTS-yhteys ja pitkät laulutekstit

Tavallinen kellopuhe ja Androidin TTS-merkkirajaan mahtuvat tekstit käyttävät
edelleen suoraa `TextToSpeech.speak()`-kutsua. Kellon ajastusta, puhesisältöä,
asetuksia, säätietoja ja audio focus -asetuksia ei muuteta.

Vain merkkirajan ylittävä LAULU-teksti välitetään muistista, kun valittuna on
PUHE-64 ja sen suomalainen ääni. `TalkingClockScoreProvider` antaa moottorille
satunnaisella tunnisteella suojatun, vain luettavan `content://`-osoitteen.
`openPipeHelper` välittää UTF-8-tekstin muistista putkeen. Levylle ei kirjoiteta
väliaikaistiedostoja eikä nykyistä FileProvideria muuteta. URI-lukuoikeus annetaan
vain PUHE-64:lle ja se sekä muistissa säilyvä teksti poistetaan toiston päättyessä,
virheessä, keskeytyksessä, jonon korvaamisessa tai palvelun sulkemisessa.

Puhepyynnön yhteysvirhe alustaa TTS-asiakkaan uudelleen ja yrittää samaa pyyntöä
kerran. Alustuksen aikaraja on 10 sekuntia. Syntaksivirheitä ja tunnetusti liian
pitkää tavallista puhetekstiä ei yritetä uudelleen. Vanhan yhteyden callbackit ja
korvatun puhepyynnön myöhäiset tapahtumat ohitetaan. Uusi palvelutoiminto korvaa
odottavan uusintapyynnön. Seuraava normaali kellopuhe voi alustaa yhteyden uudelleen,
vaikka edellisen pyynnön rajattu uusintayritys olisi epäonnistunut.

Tämä käyttää PUHE-64:n olemassa olevaa `fi.anssi.puhe64.tts.scoreUri`-rajapintaa.
Uusia PUHE-64-muutoksia ei tarvita. Jatkonuottia sisältävä TXT tarvitsee moottorin
version, jossa on jatkonuottimerkinnän tuki.
