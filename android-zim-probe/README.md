# Guide hors ligne pour Android

La branche `feature/android-zim-probe` fait évoluer le lecteur ZIM de test vers une application Android regroupant le guide de survie existant et la recherche locale dans une archive ZIM.

## Version 0.9.0

- L'accueil propose une recherche commune dans les fiches du guide et dans l'archive ZIM sélectionnée.
- Les résultats restent regroupés par source, avec leur provenance. Chaque source affiche jusqu'à 10 résultats.
- Toucher une fiche ouvre son détail dans le guide; toucher un article ZIM l'ouvre depuis l'archive.
- Le guide complet, ses filtres, ses illustrations, ses favoris et sa liste de préparation sont chargés depuis des ressources locales intégrées à l'APK.
- La compilation copie les fichiers du guide depuis la PWA à la racine du dépôt et en extrait les données JSON. Les fichiers PWA ne sont pas modifiés.
- La recherche ZIM utilise un index SQLite FTS4 local, conservé dans les données privées Android. Le ZIM original reste à son emplacement et n'est pas copié.
- L'URI SAF de l'archive choisie est mémorisée avec son autorisation de lecture, puis restaurée au redémarrage de l'application. Si Android ne conserve pas cette autorisation, l'application le signale et demande de choisir de nouveau l'archive.
- L'application n'appelle aucun serveur et ne génère pas de conseil médical avec un modèle d'IA.

L'archive Wikivoyage utilisée pour les essais sert à vérifier le lecteur et la recherche. Elle n'est pas une référence médicale. Pour toute fiche de secours, la provenance éditoriale affichée dans le guide reste distincte des articles ZIM.

## Limites actuelles

- Une seule archive ZIM est sélectionnée à la fois.
- La première indexation d'une archive peut prendre plusieurs minutes; les recherches suivantes réutilisent la base locale.
- Les articles ZIM sont affichés en texte nettoyé. Leurs liens internes, images et tableaux ne sont pas reproduits.
- Les liens externes des fiches ouvrent une autre application et peuvent nécessiter du réseau. Les fiches elles-mêmes sont locales.
- La pertinence de la nouvelle recherche commune doit être vérifiée sur téléphone avec des termes venant du guide et du ZIM.
- La restauration de l'archive et la réutilisation de l'index après fermeture de l'application restent à vérifier sur téléphone.

## Construction

Ouvrir `android-zim-probe` dans Android Studio avec Java 17 et Gradle 8.13. La compilation synchronise d'abord les ressources locales du guide. Le workflow GitHub Actions reconstruit libzim 9.8.2 avec le correctif SAF, vérifie les bibliothèques natives et produit l'APK ARM64.

L'application ne demande pas d'accès Internet à l'exécution. La sélection de ZIM utilise le sélecteur de documents Android (SAF) et une autorisation de lecture accordée pour l'archive choisie.

## Vérification sur le Galaxy S21 FE

1. Ouvrir le guide seul et parcourir une fiche; essayer aussi ses filtres, ses favoris et la liste du kit.
2. Depuis l'accueil, rechercher un terme présent dans le guide, par exemple `hémorragie`, sans avoir encore sélectionné de ZIM. Le groupe du guide doit apparaître.
3. Choisir l'archive Wikivoyage avec « Rechercher dans une archive ZIM ».
4. Refaire une recherche commune, par exemple `Bruxelles`, puis `grand-place`. Vérifier que les groupes indiquent leur source, que les résultats ZIM ouvrent les articles, que les fiches ouvrent leur détail et que le retour ramène à la recherche.
5. Fermer puis rouvrir l'application. Vérifier que l'archive reste sélectionnée et que la recherche reprend sans demander de la choisir à nouveau ni reconstruire l'index.
6. Répéter les recherches utiles en mode avion.

Chaque APK CI possède un identifiant de paquet distinct pour coexister avec les versions précédentes. Une nouvelle installation demande de choisir de nouveau l'archive et de reconstruire son index. Elle ne supprime ni l'ancienne application ni ses données. Ne pas désinstaller les anciennes versions pour installer ce build.

## Licences

La dépendance `org.kiwix:libkiwix` est publiée sous GPL-3.0 ou ultérieure. Avant toute distribution publique, vérifier les obligations de cette licence et celles des autres dépendances et ressources intégrées.
