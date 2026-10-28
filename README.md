# SatTemps

*English version: [README.en.md](README.en.md)*

Application Android (Kotlin / Jetpack Compose) qui affiche **l'heure exacte** (heure, minute,
seconde, milliseconde) et **la meilleure orientation** pour capter :

- les satellites GNSS (GPS, Galileo, GLONASS, BeiDou),
- un satellite geostationnaire (parabole : azimut, elevation, inclinaison LNB),
- l'antenne relais mobile 5G NR / 4G LTE la plus forte.

## Fonctionnalites

### Onglet « Heure »
- Horloge a la milliseconde basee sur l'**horloge GNSS atomique** (mesures GNSS brutes
  `GnssClock`, prioritaire), sinon sur le **temps GNSS** du fix satellite, sinon sur
  un serveur **NTP** (`time.google.com`, `pool.ntp.org`, `time.cloudflare.com`), sinon sur
  l'horloge du telephone.
- Affichage de l'ecart mesure avec l'horloge du telephone et de l'incertitude estimee.
- Etat des mesures GNSS brutes (actives, en attente, heure GPS pas encore calculee, non fournies
  par le telephone...), incertitude de l'horloge GNSS et ecart GPS - UTC.
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

## Tests

Les calculs (visee geostationnaire, analyse du ciel GNSS, horloge GNSS brute, encodage NTP, priorite des sources de
temps, mesure directionnelle 360°, azimuts de l'interface) sont couverts par des tests unitaires
JVM, sans emulateur :

```bash
./gradlew test
./gradlew lint
```

Les essais terrain (plusieurs telephones, plusieurs regions, modems differents) restent a faire sur
appareils reels.

## Langues

Tous les textes affiches sont dans `app/src/main/res/values/strings.xml` (francais, langue par
defaut) et `app/src/main/res/values-en/strings.xml` (anglais). Ajouter une langue revient a copier
le fichier dans un dossier `values-xx`.

## Permissions

| Permission | Usage |
|---|---|
| `ACCESS_FINE_LOCATION` | position GPS, etat des satellites, informations de cellule |
| `READ_PHONE_STATE` | technologie reseau et identifiants de cellule |
| `INTERNET` | synchronisation NTP |

## Precision de l'heure

- **GNSS atomique** (Android 11+ et telephone fournissant les mesures brutes) : l'heure GPS est
  calculee depuis l'horloge du recepteur (`timeNanos - fullBiasNanos - biasNanos`), asservie aux
  horloges atomiques des satellites, puis convertie en UTC avec les secondes intercalaires
  (18 s par defaut). Elle est reliee a l'horloge du telephone par `elapsedRealtimeNanos`, sans la
  latence du fix. L'horloge du telephone etant lue a la milliseconde, l'incertitude affichee ne
  descend pas sous ± 1 ms. Si les mesures brutes s'arretent plus de 10 s, l'application revient
  a la source suivante.
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
