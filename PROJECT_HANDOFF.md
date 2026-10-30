# Reprise du projet

Dernière mise à jour : 30 octobre 2026.

## Point de reprise

- Dépôt : [Johunn/premiers-secours-offline](https://github.com/Johunn/premiers-secours-offline)
- Branche Android active : `feature/android-zim-probe`
- Commit courant : `2db354ca054f31618a6ffb31ff0f377bbce11ab9` — « Treat hyphenated search terms as phrases »
- La PWA du guide de survie reste le produit web existant. Ses fichiers n'ont pas été modifiés dans ce chantier Android.
- Le prototype Android lit une archive ZIM choisie par l'utilisateur et construit un index plein texte FTS4 local.

## Orientation produit envisagée

L'idée évoquée est une expérience Android unique permettant de chercher à la fois dans le guide de premiers secours et dans les articles d'une archive ZIM, avec la source indiquée pour chaque résultat. C'est une orientation à confirmer après les essais; la PWA peut continuer d'exister comme version web. Un assistant conversationnel qui rédige des réponses à partir de ces sources n'est pas inclus dans le prototype actuel.

## État Android

### Dernier APK

- Version : 0.7.0, code 8, build CI numéro 40.
- Identifiant Android : `be.johunn.survie.zimprobe.debug.run40`.
- Fichier joint à l'exécution CI : [APK run 40](https://github.com/Johunn/premiers-secours-offline/actions/runs/41122230627).
- SHA-256 de l'APK : `7736B66B877226C291FCF2301C8E5442B8F0E19F74E3AA52557BEB4CC7124440`.
- La compilation CI a réussi; l'alignement et la signature APK ont été vérifiés. Ce build n'a pas encore de résultat de recherche confirmé sur le téléphone.
- L'artefact GitHub expire le 13 novembre 2026. S'il n'est plus disponible, relancer la compilation sur la branche active.

### Dernier essai rapporté par l'utilisateur

- Archive : `wikivoyage_fr_all_nopic_2026-10.zim`, UUID `1985c9fc-40e3-5546-ebe0-08bfc503d30d`.
- Indexation rapportée : 11 985 articles, environ six minutes la première fois.
- La recherche hors ligne a répondu instantanément.
- Avec le build précédent, `Bruxelles` renvoyait les articles bruxellois en tête et des extraits surlignés lisibles.
- Avec le build précédent, `grand-place` renvoyait encore des résultats trop larges : les mots pouvaient apparaître séparément dans l'article. Le correctif du build 40 conserve les groupes avec trait d'union comme une phrase, par exemple `grand-place` → « grand place ».

### Prochaine étape

L'utilisateur a indiqué être en train de reconstruire l'index avec le build 40. Attendre ses résultats de recherche, en particulier `grand-place`. Vérifier si les résultats sont désormais centrés sur les articles où l'expression est présente; ne pas présumer que le comportement est validé avant son compte rendu.

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

## Démarrer une nouvelle conversation

Si le dépôt est accessible dans la nouvelle conversation, demander :

> Reprends le projet `Johunn/premiers-secours-offline`. Lis d'abord `AGENTS.md` et `PROJECT_HANDOFF.md` sur `feature/android-zim-probe`, puis continue depuis la prochaine étape indiquée.

Depuis un autre compte ChatGPT, il faudra aussi ouvrir ou fournir le lien du dépôt et disposer de son accès. La fiche GitHub conserve le contexte du projet, mais elle ne transfère pas automatiquement l'historique privé des conversations.
