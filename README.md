# Guide de survie — repères hors ligne

PWA mobile en français pour la préparation aux urgences en Belgique. Fiches préchargées, consultation hors ligne, recherche locale, favoris et listes sauvegardés sur cet appareil.

## Contenu et limites
Les fiches sont des résumés prudents de sources officielles; elles ne remplacent ni une formation, ni les services d’urgence, ni les consignes des autorités. En danger immédiat : 112. En cas d’intoxication : Centre Antipoisons 070 245 245; pour une détresse vitale, 112.

Chaque fiche affiche sa source et la date de vérification éditoriale. Cette date ne garantit pas que la recommandation soit toujours la plus récente. Une copie hors ligne ne reçoit pas les changements des autorités; reconnectez-vous périodiquement pour charger la version publiée et consultez les liens source.

Organic Maps est une application séparée. Le bouton ouvre une recherche/coordonnée via une URL géographique; l’ouverture dépend du système et de l’application installée. Les cartes téléchargées aident à se repérer mais ne garantissent ni l’exactitude des points d’intérêt ni la praticabilité d’un itinéraire.

## Publication GitHub Pages
1. Ouvrir **Settings → Pages** du dépôt.
2. Dans **Build and deployment**, choisir **Deploy from a branch**.
3. Sélectionner **feature/pwa-survie-offline**, dossier **/(root)**, puis **Save**.
4. Attendre l’URL Pages affichée dans cette section. Ouvrir cette URL sur Android avec Chrome, charger une fois toutes les ressources, puis menu ⋮ → **Installer l’application** ou **Ajouter à l’écran d’accueil**.
5. Pour valider le hors-ligne, ouvrir l’application après l’installation, puis couper les données/Wi-Fi et relancer. Garder une copie installée et tester avant un déplacement.

Pages peut prendre quelques minutes à publier. L’adresse est normalement https://johunn.github.io/premiers-secours-offline/, sous réserve de la configuration effective du compte.

## Développement
Fichiers statiques sans dépendances ni compilation. Héberger via HTTP(S); les service workers ne fonctionnent pas depuis une adresse file.
