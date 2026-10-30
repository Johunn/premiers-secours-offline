# Prototype Android de lecture ZIM

Prototype isolé destiné à vérifier qu'une archive ZIM peut être choisie avec le Storage Access Framework (SAF) et lue directement par libkiwix, sans copie préalable.

## Fonctionnement prévu

1. Ouvre le sélecteur Android avec `ACTION_OPEN_DOCUMENT`.
2. Demande un descripteur avec `ContentResolver.openAssetFileDescriptor()`.
3. Ouvre l'archive par `Archive(FileDescriptor, offset, length)` si le fournisseur donne une longueur exploitable. Si la longueur est inconnue, utilise `Archive(FileDescriptor)` uniquement lorsque le décalage vaut zéro.
4. Lit les métadonnées et extrait l'article correspondant au titre exact saisi; si le champ est vide, il extrait l'entrée principale.
5. Affiche le titre, le chemin, le type MIME, un extrait et la provenance.

Si l'accès direct n'est pas possible, le prototype affiche une erreur explicite. Il ne copie, ne déplace et ne modifie aucun fichier ZIM.

## Hors périmètre

- Aucune modification de la PWA.
- Aucun pont JavaScript/Kotlin ni WebView.
- Aucun RAG, index ou modèle IA.
- Aucune garantie de compatibilité avec tous les fournisseurs SAF.
- L'API Java utilisée n'expose pas ici de fermeture native déterministe de l'archive; ce point devra être traité avant un usage prolongé.

## Construction

Ouvrir le dossier `android-zim-probe` dans Android Studio avec un JDK 17, laisser Gradle synchroniser les dépendances, puis lancer la configuration `app` sur un appareil Android.

- Android Gradle Plugin 8.11.1
- Kotlin Android 2.2.0
- Gradle 8.13 (wrapper recommandé)
- Compile SDK 35
- Minimum Android 8.0 (API 26)
- `org.kiwix:libkiwix:2.7.0` (libkiwix 14.2.1 et libzim 9.8.2)

Le workflow reconstruit libzim 9.8.2 avec le correctif SAF et le support Xapian activé (nécessaire aux fonctions de recherche utilisées par libkiwix). Il vérifie ensuite avant assemblage que les symboles libzim requis par libkiwix sont bien exportés. Cette vérification ABI évite de confondre une compilation APK réussie avec un ensemble de bibliothèques chargeables.

Une connexion sera nécessaire à la première synchronisation Gradle pour télécharger les dépendances. L'application n'a pas besoin d'Internet à l'exécution.

## Validation sur le Galaxy S21 FE

1. Installer et lancer le prototype.
2. Saisir si nécessaire le titre exact d'un article, puis choisir une archive ZIM déjà présente sur le téléphone. Si le champ est vide, le prototype extrait la page principale de l'archive.
3. Vérifier que le sélecteur Android la propose sans demander de la copier ou déplacer.
4. Vérifier l'écran « LECTURE RÉUSSIE », les métadonnées, le titre et le chemin d'une entrée, ainsi qu'un extrait lisible.
5. Refaire le test en mode avion après installation.
6. Répéter avec au moins deux archives de types différents.

Un échec SAF ou libkiwix doit être consigné avec son message exact. Ne pas contourner l'échec en copiant plusieurs gigaoctets sans décision explicite.

## Licence

La dépendance `org.kiwix:libkiwix` est publiée sous GPL-3.0 ou ultérieure. Toute distribution de ce prototype ou d'un binaire qui l'intègre doit être examinée au regard des obligations de cette licence, notamment la fourniture du code source correspondant et des mentions requises. Vérifier les licences de toutes les dépendances avant toute distribution publique.
