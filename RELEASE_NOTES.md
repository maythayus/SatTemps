# Notes de version

## v1.2.0 — 20 octobre 2026

Heure issue directement de l'horloge du recepteur GNSS, asservie aux horloges atomiques des
satellites, et affichage de l'heure corrige.

APK : `dist/SatTemps-v1.2.0-debug.apk` (ou `./gradlew assembleDebug`).

### Nouveautes

- Nouvelle source de temps « GNSS atomique » : l'application lit les mesures GNSS brutes
  (`GnssClock`) et calcule le temps GPS (`timeNanos - fullBiasNanos - biasNanos`), converti en UTC
  avec les secondes intercalaires du recepteur (18 s par defaut).
- L'heure est reliee a l'horloge interne du telephone par `elapsedRealtimeNanos`, sans la latence
  de remontee du fix de position.
- Ordre de priorite : GNSS atomique, puis fix GNSS, puis NTP, puis horloge du telephone. Si les
  mesures brutes s'arretent plus de 10 s, l'application revient a la source suivante.
- Onglet Heure : nom de la source en clair, incertitude de l'horloge GNSS (ns, µs ou ms), ecart
  GPS - UTC et etat des mesures brutes (actives, en attente, non fournies, Android 11 requis).

### Corrections

- L'heure avec millisecondes ne passe plus a la ligne et ne chevauche plus les secondes : sa taille
  s'adapte a la largeur de l'ecran et a la taille de police du telephone.

### Limites

- Mesures brutes : Android 11 minimum et un telephone qui les fournit. Sinon, l'application
  utilise les sources precedentes comme avant.
- L'horloge systeme du telephone n'est pas modifiee (permission reservee aux applications
  systeme) : seule l'heure affichee par SatTemps est corrigee.
- L'horloge du telephone n'etant lue qu'a la milliseconde, l'incertitude totale affichee ne
  descend pas sous ± 1 ms.

### Verification

- 66 tests unitaires (dont le calcul `GnssClock` et la priorite des sources), `lint` et
  compilation verifies. Precision reelle a confirmer sur telephone, a ciel ouvert.

## v1.1.1 — 19 octobre 2026

Version de maintenance : meme fonctionnement que la v1.1.0, publiee depuis la branche principale.

APK : `dist/SatTemps-v1.1.1-debug.apk` (ou `./gradlew assembleDebug`).

### Corrections

- Le numero de version affiche par Android est maintenant `1.1.1` (code de version 3) ; les APK
  precedents annoncaient encore `1.0`.
- Les ameliorations de la v1.1.0 (messages d'erreur, francais/anglais, cadences adaptees a la
  batterie, tests) sont integrees a la branche principale.
- 54 tests unitaires, `lint` et compilation verifies sur la branche principale.

## v1.1.0 — 30 aout 2026

Version de fiabilite : aucune fonction retiree, mais des erreurs enfin expliquees a l'ecran, des
textes traduisibles, une consommation reduite et des calculs couverts par des tests.

APK : `dist/SatTemps-v1.1.0-debug.apk` (ou `./gradlew assembleDebug`).

### Ameliorations

**Messages d'erreur et etats**
- Localisation : service absent, permission refusee (avec acces direct aux reglages en cas de refus
  definitif), GPS desactive, recherche de fix en cours, signal perdu avec l'age du dernier fix.
- Reseau mobile : telephonie indisponible, permission refusee, aucune cellule remontee par le modem,
  echec de lecture.
- Heure : serveur NTP injoignable ou reponse invalide, indicateur de synchronisation en cours,
  reessais espaces progressivement hors couverture.
- Boussole : capteur absent, echec d'enregistrement, besoin de calibrage.
- Mesure 360° : la couverture du tour est affichee et signalee comme insuffisante sous 70 %.

**Langues**
- Tous les textes sont dans `res/values/strings.xml` (francais) et `res/values-en/strings.xml`
  (anglais) ; nombres, dates et heures suivent la langue du telephone.

**Batterie**
- Horloge a 40 ms ecran allume, 200 ms en economie d'energie, 1 s en arriere plan.
- Modem interroge chaque seconde pendant la mesure 360°, toutes les 5 s au repos ; calculs GNSS et
  de visee ralentis hors premier plan.

**Qualite du code**
- 54 tests unitaires JVM : visee geostationnaire, analyse du ciel GNSS, encodage NTP, priorite des
  sources de temps et espacement des reessais, mesure directionnelle 360°, conversions d'azimut.
- Logique de calcul separee des API Android (`SkyAnalysis`, `NtpTimestamp`, `Azimuth`) et KDoc sur
  les classes principales.

### Limites inchangees

Les limites de la v1.0.0 restent valables. Les essais terrain sur plusieurs telephones, plusieurs
regions et differents modems restent a faire sur materiel reel.

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
