# Spotify Lyrics (Fabric, Minecraft 1.20.1)

Näyttää Spotifyssa soivan kappaleen aikaleimatut sanoitukset (LRCLIB) joko **taivaalla** tai **suoraan edessäsi**.

## Rakennus
1. Asenna JDK 17.
2. `python download_fonts.py`  (lataa 27 fonttia; ilman tätä käytössä on 4 sisäänrakennettua)
3. `gradle wrapper --gradle-version 8.7` (kerran), sitten `./gradlew build`
4. Jar: `build/libs/spotify-lyrics-1.0.0.jar` → `.minecraft/mods/`
   Tarvitset Fabric Loaderin (0.15+) ja Fabric API:n 1.20.1:lle.

## Spotify-yhteys (kerran)
1. https://developer.spotify.com/dashboard → Create app
2. Redirect URI täsmälleen: `http://127.0.0.1:8888/callback`, API: Web API
3. Kopioi **Client ID**
4. Pelissä: avaa asetukset (oletus L) → liitä Client ID → "Yhdistä Spotify" → hyväksy selaimessa

## Käyttö
- Ensimmäisellä käynnistyksellä peli kysyy pikanäppäimen.
- Asetuksissa: sijainti (taivas/edessä), fontti, väri (RGB + hex), koko, joulutila, välkkyvät valot, sana kerrallaan, ajoituksen viive.
- Jos sanat ovat liian aikaisin/myöhään, säädä viivettä (+/- 250 ms).

## Ilman asennuksia: GitHub rakentaa jarin
1. Tee ilmainen GitHub-tili, luo uusi repo ja lataa tämän zipin sisältö sinne (Add file → Upload files; ota .github-kansio mukaan).
2. Avaa repon Actions-välilehti → "Build mod" → odota ~3 min.
3. Lataa valmis jar kohdasta Artifacts.
