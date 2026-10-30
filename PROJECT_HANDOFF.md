# Reprise du projet

Dernière mise à jour : 30 octobre 2026.

## Point de reprise

- Dépôt : [Johunn/premiers-secours-offline](https://github.com/Johunn/premiers-secours-offline)
- Branche Android active : `feature/android-zim-probe`
- Dernier commit de code Android : `3d843d7ab643f06d4f32b4fbffdda48b96417392` — résultats activables et ouverture d'article; build CI 41 réussi.
- La PWA du guide de survie reste le produit web existant. Ses fichiers n'ont pas été modifiés dans ce chantier Android.
- Le prototype Android lit une archive ZIM choisie par l'utilisateur et construit un index plein texte FTS4 local.

## Orientation produit envisagée

L'idée évoquée est une expérience Android unique permettant de chercher à la fois dans le guide de premiers secours et dans les articles d'une archive ZIM, avec la source indiquée pour chaque résultat. C'est une orientation à confirmer après les essais; la PWA peut continuer d'exister comme version web. Un assistant conversationnel qui rédige des réponses à partir de ces sources n'est pas inclus dans le prototype actuel.

## État Android

### Dernier APK

- Version : 0.7.0, code 8, build CI numéro 41.
- Identifiant Android : `be.johunn.survie.zimprobe.debug.run41`.
- Fichier joint à l'exécution CI : [APK run 41](https://github.com/Johunn/premiers-secours-offline/actions/runs/41128831129).
- SHA-256 de l'APK : `E997E19009E48F997F6B4381D21F0806598843CAE8CAEA6DD4690982FBBB8AED`.
- La compilation CI, l'alignement et la signature APK ont réussi. Le tap d'un résultat et l'ouverture d'un article restent à vérifier sur téléphone.
- L'artefact GitHub expire le 13 novembre 2026. S'il n'est plus disponible, relancer la compilation sur la branche active.

### Dernier essai rapporté par l'utilisateur

- Archive : `wikivoyage_fr_all_nopic_2026-10.zim`, UUID `1985c9fc-40e3-5546-ebe0-08bfc503d30d`.
- Indexation rapportée : 11 985 articles, environ six minutes la première fois.
- Sur un build précédent, l'utilisateur avait confirmé que la recherche fonctionnait instantanément en mode avion. Son dernier compte rendu ne précise pas le mode réseau.
- Avec le build précédent, `Bruxelles` renvoyait les articles bruxellois en tête et des extraits surlignés lisibles.
- Avec le build précédent, `grand-place` renvoyait encore des résultats trop larges : les mots pouvaient apparaître séparément dans l'article. Le correctif du build 40 conserve les groupes avec trait d'union comme une phrase, par exemple `grand-place` → « grand place ».
- Sur le build 40, `Bruxelles` renvoie Bruxelles en premier et des pages bruxelloises pertinentes; les extraits surlignent correctement le mot recherché.
- Sur le build 40, `grand-place` renvoie des pages où l'expression apparaît effectivement. `Quartier du Centre (Bruxelles)` arrive en premier; la page `Bruxelles` est neuvième, avec des pages d'autres villes ayant elles aussi une Grand-Place entre les deux. Les extraits confirment les correspondances exactes.
- La pertinence de ces deux recherches est donc validée sur téléphone. Le classement reflète les occurrences dans l'archive; une recherche générale `grand-place` peut légitimement retourner les Grand-Places d'autres villes.

### Navigation des résultats (build 41, téléphone à vérifier)

L'utilisateur a signalé que les résultats étaient affichés comme du texte non activable. Le build 41 rend chaque ligne activable, ouvre l'article par son chemin ZIM et affiche le texte nettoyé avec un bouton de retour à la liste. La compilation CI est réussie; l'essai sur téléphone de cette navigation reste à faire. Les liens internes, images et tableaux ne sont pas inclus dans ce premier affichage.

### Prochaine étape

Les recherches `Bruxelles` et `grand-place` sont validées sur le build 40. Première priorité : faire vérifier sur le build 41 le tap sur un résultat, l'ouverture du contenu, le retour à la liste et, si possible, ces gestes en mode avion. Après cette navigation, cadrer l'expérience Android unifiée (guide + archive ZIM); la PWA reste la version web et n'est pas à modifier dans ce chantier.

### Données et installation

Chaque APK CI porte actuellement un identifiant de paquet différent (`.debug.run<numéro>`), pour pouvoir coexister avec les anciens builds. Cela crée un espace de données distinct : chaque nouveau build demande de re-sélectionner le ZIM et de reconstruire l'index. Les applications et index précédents restent sur le téléphone. L'archive ZIM originale n'est ni copiée ni modifiée.

## Repères dans le code

- `android-zim-probe/app/src/main/java/be/johunn/survie/zimprobe/LocalArticleIndex.kt` : index SQLite FTS4, requêtes, extraits et classement.
- `android-zim-probe/app/src/main/java/be/johunn/survie/zimprobe/MainActivity.kt` : sélection SAF de l'archive, extraction des articles et interface de recherche.
- `index.html`, `app.js`, `styles.css` et les autres fichiers PWA à la racine : préserver sans instruction explicite de les modifier.

## Limites à respecter

- Travailler sur `feature/android-zim-probe`; ne pas modifier `main`.
- Ne pas modifier la PWA, fusionner, ouvrir une PR, publier ou déployer sans demande explicite.
- Seul l'utilisateur teste l'application sur son téléphone; distinguer les contrôles de compilation des résultats sur appareil.
- Avant de proposer l'installation d'un nouvel APK, préciser son effet sur les données, les permissions de l'archive et l'index local.
- Ne pas supprimer l'application ou ses données sans demande explicite.

## Sauvegarde et compte rendu

Après toute tâche qui modifie des fichiers, terminer la conversation par un état visible indiquant si les changements sont sauvegardés sur GitHub. Quand les limites du projet le permettent, créer le commit et pousser sur la branche active; indiquer la branche, le commit et les fichiers concernés. Ne déclarer la sauvegarde faite qu'après avoir confirmé que le push a réussi. Signaler séparément l'état de la sauvegarde GitHub, de la compilation CI et des essais sur téléphone.

## Démarrer une nouvelle conversation

Si le dépôt est accessible dans la nouvelle conversation, demander :

> Reprends le projet `Johunn/premiers-secours-offline`. Lis d'abord `AGENTS.md` et `PROJECT_HANDOFF.md` sur `feature/android-zim-probe`, puis continue depuis la prochaine étape indiquée.

Depuis un autre compte ChatGPT, il faudra aussi ouvrir ou fournir le lien du dépôt et disposer de son accès. La fiche GitHub conserve le contexte du projet, mais elle ne transfère pas automatiquement l'historique privé des conversations.
