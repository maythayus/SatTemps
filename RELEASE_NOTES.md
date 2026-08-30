# Notes de version

## v1.0.0 — 30 aout 2026

Premiere version de **SatTemps** : heure exacte et meilleure orientation d'antenne
(satellite GNSS, parabole geostationnaire, relais mobile 5G / LTE).

APK : `app/build/outputs/apk/debug/app-debug.apk` (`./gradlew assembleDebug`).

### Nouveautes

**Heure exacte a la milliseconde**
- Horloge `HH:mm:ss.SSS` en heure locale et en UTC.
- Reference de temps hierarchisee : temps GNSS du fix satellite, sinon NTP
  (`time.google.com`, `pool.ntp.org`, `time.cloudflare.com`), sinon horloge du telephone.
- Ecart mesure avec l'horloge du telephone, incertitude estimee, age de la derniere
  synchronisation et resynchronisation manuelle.

**Ciel GNSS et meilleure orientation**
- Vue polaire du ciel (zenith au centre, horizon au bord) orientee au nord vrai grace a la
  boussole corrigee de la declinaison magnetique.
- Satellites GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS et NavIC : azimut, elevation, C/N0,
  utilisation dans le fix ; couleur et taille selon la qualite du signal.
- Meilleure direction du ciel calculee par moyenne vectorielle des azimuts ponderee par le
  signal, avec l'angle de rotation a effectuer.

**Visee parabole geostationnaire**
- Azimut (nord vrai), elevation, inclinaison LNB (skew) et distance oblique calcules depuis la
  position GPS pour 12 satellites courants (Astra 19.2E, Hotbird 13E, Astra 2/3, Eutelsat 5W et
  7W, Nilesat 201, Turksat 4A, Intelsat 20, SES 4, Hispasat 30W, Galaxy 19).
- Selection automatique du satellite le plus haut dans le ciel et masquage de ceux sous l'horizon.

**Reseau mobile 5G / LTE**
- Operateur, technologie du reseau de donnees, cellule servante et cellules voisines.
- Mesures radio : SS-RSRP / RSRP, RSRQ, SINR, bandes (n78, B7...), identifiants NCI / CI / PCI / TAC.
- Mesure directionnelle 360° : echantillonnage de la puissance par secteur de 15° pendant que
  l'utilisateur tourne, avec taux de couverture, meilleur secteur et rotation a effectuer.

### Details techniques

- Kotlin, Jetpack Compose (Material 3), architecture `StateFlow` + `ViewModel`.
- minSdk 26, targetSdk 34, Java 17, AGP 8.5.2, Gradle 8.7.
- Permissions : `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_PHONE_STATE`, `INTERNET`.
- Client SNTP interne, sans dependance reseau tierce.

### Limites connues

- Les cellules voisines dependent du modem et du constructeur : certains telephones ne remontent
  que la cellule servante.
- La direction du relais mobile est une estimation basee sur la puissance mesuree pendant la
  rotation (le corps masque le signal du cote oppose), pas une triangulation.
- La liste des satellites geostationnaires est statique (Europe, Afrique, une partie des Ameriques).
- Android n'expose pas la seconde intercalaire : l'heure suit l'echelle UTC.
- Version non testee sur materiel reel : GPS, boussole et modem 5G ne sont pas simulables sur
  l'environnement de compilation.
