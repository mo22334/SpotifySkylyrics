# Spotify Lyrics (Fabric, Minecraft 1.21.11, toimii Lunar Clientissa)

Näyttää Spotifyssa soivan kappaleen aikaleimatut sanoitukset (LRCLIB) joko **taivaalla** tai **suoraan edessäsi**.

## Rakennus (GitHub Actions)
Lataa projekti GitHub-repoon. Actions-välilehti rakentaa jarin (Java 21, Gradle 9.2.1) ja lataa fontit.
Valmis jar löytyy rakennuksen sivun Artifacts-kohdasta.

## Lunar Client
1. Lunar-launcheriin: valitse versioksi 1.21.11 ja Fabric-profiili.
2. Profiilin rataskuvake → Mods → lisää tämä jar.
3. Fabric API tulee Lunarin mukana (ei tarvitse lisätä erikseen).

## Spotify-yhteys (kerran)
1. https://developer.spotify.com/dashboard → Create app
2. Redirect URI täsmälleen: `http://127.0.0.1:8888/callback`, API: Web API
3. Kopioi **Client ID**
4. Pelissä: avaa asetukset (oletus L) → liitä Client ID → "Yhdistä Spotify" → hyväksy selaimessa

## Käyttö
- Ensimmäisellä käynnistyksellä peli kysyy pikanäppäimen.
- Asetuksissa: sijainti (taivas/edessä), fontti, väri (RGB + hex), koko, joulutila, välkkyvät valot, sana kerrallaan, ajoituksen viive.
