# SatTemps

Application Android (Kotlin / Jetpack Compose) qui affiche **l'heure exacte** (heure, minute,
seconde, milliseconde) et **la meilleure orientation** pour capter :

- les satellites GNSS (GPS, Galileo, GLONASS, BeiDou),
- un satellite geostationnaire (parabole : azimut, elevation, inclinaison LNB),
- l'antenne relais mobile 5G NR / 4G LTE la plus forte.

## Fonctionnalites

### Onglet « Heure »
- Horloge a la milliseconde basee sur le **temps GNSS** du fix satellite (prioritaire), sinon sur
  un serveur **NTP** (`time.google.com`, `pool.ntp.org`, `time.cloudflare.com`), sinon sur
  l'horloge du telephone.
- Affichage de l'ecart mesure avec l'horloge du telephone et de l'incertitude estimee.
- Heure locale + UTC, position GPS (latitude, longitude, altitude, precision).

### Onglet « Ciel GPS »
- Vue polaire du ciel : chaque satellite est place selon son azimut et son elevation, la couleur
  et la taille indiquent la qualite du signal (C/N0 en dB-Hz).
- La vue tourne avec le telephone (boussole corrigee de la declinaison magnetique : nord vrai).
- Calcul de la **meilleure direction** = moyenne vectorielle des azimuts ponderee par le signal,
  avec l'angle de rotation a effectuer.

### Onglet « Parabole »
- A partir de la position GPS, calcul de la visee vers une douzaine de satellites
  geostationnaires courants (Astra 19.2E, Hotbird 13E, Eutelsat 5W/7W, Nilesat, Turksat...).
- Azimut (nord vrai), elevation, inclinaison LNB (skew), distance oblique, satellite le plus haut.

### Onglet « Reseau »
- Operateur, technologie (5G NR / LTE / UMTS / GSM), cellule servante et cellules voisines :
  puissance (SS-RSRP / RSRP), qualite (RSRQ), SINR, bandes, identifiants (NCI, PCI, TAC).
- **Mesure directionnelle 360°** : demarrez la mesure et tournez lentement sur vous meme ;
  l'application echantillonne la puissance du signal par secteur de 15° et indique la direction
  du relais le mieux capte, ainsi que la rotation a effectuer.

## Compilation

Prerequis : JDK 17 et le SDK Android (plateforme 34, build-tools 34).

```bash
# Renseigner le chemin du SDK
echo "sdk.dir=/chemin/vers/Android/sdk" > local.properties

./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

Installation sur un telephone connecte en USB (debogage USB active) :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Usage |
|---|---|
| `ACCESS_FINE_LOCATION` | position GPS, etat des satellites, informations de cellule |
| `READ_PHONE_STATE` | technologie reseau et identifiants de cellule |
| `INTERNET` | synchronisation NTP |

## Precision de l'heure

- **GNSS** : le fix GPS porte un horodatage UTC issu des horloges atomiques des satellites. On le
  compare a l'horloge monotone du telephone (`elapsedRealtimeNanos`) pour retirer la latence de
  remontee du fix ; precision typique de quelques dizaines de millisecondes.
- **NTP** : algorithme SNTP classique, l'incertitude affichee vaut la moitie du temps aller-retour.
- Android ne donne pas acces a la seconde intercalaire ; l'heure affichee suit l'echelle UTC.

## Limites connues

- Les informations de cellule voisine dependent du modem et du constructeur ; certains telephones
  ne remontent que la cellule servante.
- La direction de l'antenne relais est deduite de la puissance mesuree pendant la rotation
  (le corps de l'utilisateur masque le signal du cote oppose) : c'est une estimation, pas une
  triangulation.
- La liste des satellites geostationnaires est statique et couvre l'Europe, l'Afrique et une
  partie des Ameriques.
