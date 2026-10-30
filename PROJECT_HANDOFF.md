# Reprise du projet

Dernière mise à jour : 30 octobre 2026.

## Point de reprise

- Dépôt : [Johunn/premiers-secours-offline](https://github.com/Johunn/premiers-secours-offline)
- Branche Android active : `feature/android-zim-probe`
- Dernier APK testé : version 0.8.0, build CI 42; l'utilisateur rapporte que la recherche commune et les parcours du guide et du ZIM fonctionnent correctement.
- Code source en cours : version 0.9.0, mémorisation et restauration de l'archive ZIM après redémarrage; compilation CI et essai téléphone restent à faire.
- Commit Android de l'intégration 0.8.0 : `da952fc` — application unique avec recherche commune guide + ZIM et résultats séparés par source.
- La PWA du guide de survie reste le produit web existant. Ses fichiers n'ont pas été modifiés dans ce chantier Android.
- L'application Android lit une archive ZIM choisie par l'utilisateur et construit un index plein texte FTS4 local.

## Décision produit

Faire une seule application Android hors ligne avec une recherche commune dans le guide et l'archive ZIM. Présenter les résultats dans deux groupes distincts, chacun avec sa provenance; ne pas fusionner leurs classements. Le guide continue d'être livré comme PWA/web séparément et ses fichiers sont copiés dans l'APK au moment de la compilation. Aucun assistant génératif ni réponse médicale rédigée n'est inclus à cette étape.

L'archive `wikivoyage_fr_all_nopic_2026-10.zim` reste un corpus d'essai du lecteur; elle ne doit pas être présentée comme source médicale.

## État Android

### Dernier APK

- Version : 0.8.0, code 9, build CI numéro 42.
- Identifiant Android : `be.johunn.survie.zimprobe.debug.run42`.
- Fichier joint à l'exécution CI : [APK run 42](https://github.com/Johunn/premiers-secours-offline/actions/runs/41135050442).
- SHA-256 de l'APK : `720F8ED7571D89A041845BC9A3FDD447C9B12E7021F7B20DA73B62E4D79BC208`.
- Taille : 68 424 245 octets. L'APK extrait de l'artefact CI a été lu et haché localement; le workflow a aussi réussi les contrôles zipalign et apksigner.
- L'artefact GitHub expire le 13 novembre 2026. S'il n'est plus disponible, relancer la compilation sur la branche active.

### Version intégrée

- Version source : 0.8.0, code 9, commit `da952fc`.
- L'accueil permet une requête commune dans les fiches hors ligne et dans l'archive ZIM sélectionnée; les résultats sont présentés séparément (jusqu'à 10 par source).
- Les fiches et l'interface complètes de la PWA sont intégrées comme ressources locales au build Android, sans modification des fichiers PWA. Les données de recherche du guide sont extraites de `fiches.js` au moment de la compilation.
- Les articles ZIM continuent de s'ouvrir dans le lecteur natif; les fiches de guide ouvrent le détail existant de la PWA intégrée.
- Compilation CI : réussie pour le workflow [build 42](https://github.com/Johunn/premiers-secours-offline/actions/runs/41135050442); bibliothèques natives, APK, alignement et signature vérifiés.
- Essai sur téléphone : l'utilisateur indique que les fonctions testées dans cette version marchent correctement, notamment la recherche `hémorragie` dans le guide et la recherche commune dans le ZIM. Le mode avion n'a pas été précisé explicitement.

### Version 0.9.0 en préparation

- Version source : 0.9.0, code 10.
- Mémorise l'URI de l'archive seulement quand Android confirme une autorisation SAF de lecture persistante; restaure l'archive au prochain démarrage.
- Si l'autorisation a disparu, efface l'URI mémorisée et indique qu'il faut choisir l'archive à nouveau. L'index FTS4 existant n'est pas supprimé.
- Le prochain APK CI aura un nouvel identifiant de paquet de test : l'utilisateur devra choisir à nouveau l'archive et construire un index distinct dans cette installation; la restauration sera ensuite vérifiée après fermeture/réouverture de la même installation.
- Compilation CI : en attente de la poussée du code.
- Essai sur téléphone : à faire après publication de l'APK.

### Dernier essai rapporté par l'utilisateur

- Build 42 / version 0.8.0 : l'utilisateur confirme que la recherche `hémorragie` dans le guide et la recherche commune dans le guide + ZIM fonctionnent. Il dit que tout fonctionne correctement; il n'a pas spécifié séparément le mode avion.
- Archive : `wikivoyage_fr_all_nopic_2026-10.zim`, UUID `1985c9fc-40e3-5546-ebe0-08bfc503d30d`.
- Indexation rapportée : 11 985 articles, environ six minutes la première fois.
- Sur un build précédent, l'utilisateur avait confirmé que la recherche fonctionnait instantanément en mode avion. Son dernier compte rendu ne précise pas le mode réseau.
- Avec le build précédent, `Bruxelles` renvoyait les articles bruxellois en tête et des extraits surlignés lisibles.
- Avec le build précédent, `grand-place` renvoyait encore des résultats trop larges : les mots pouvaient apparaître séparément dans l'article. Le correctif du build 40 conserve les groupes avec trait d'union comme une phrase, par exemple `grand-place` → « grand place ».
- Sur le build 40, `Bruxelles` renvoie Bruxelles en premier et des pages bruxelloises pertinentes; les extraits surlignent correctement le mot recherché.
- Sur le build 40, `grand-place` renvoie des pages où l'expression apparaît effectivement. `Quartier du Centre (Bruxelles)` arrive en premier; la page `Bruxelles` est neuvième, avec des pages d'autres villes ayant elles aussi une Grand-Place entre les deux. Les extraits confirment les correspondances exactes.
- La pertinence de ces deux recherches est donc validée sur téléphone. Le classement reflète les occurrences dans l'archive; une recherche générale `grand-place` peut légitimement retourner les Grand-Places d'autres villes.

### Navigation des résultats (build 41, validée sur téléphone)

L'utilisateur a signalé que les résultats étaient affichés comme du texte non activable. Le build 41 rend chaque ligne activable, ouvre l'article par son chemin ZIM et affiche le texte nettoyé avec un bouton de retour à la liste. Sur téléphone, l'utilisateur confirme que les résultats s'ouvrent correctement pour `Bruxelles` et `grand-place`, et que le retour fonctionne. Le mode avion n'a pas été précisé pour cet essai. Les liens internes, images et tableaux ne sont pas inclus dans ce premier affichage.

### Prochaine étape

Le build 42 (0.8.0) a été rapporté comme fonctionnel pour l'intégration du guide et du ZIM. Après compilation du build 43 (0.9.0), faire vérifier sur le Galaxy : choisir l'archive, lancer une recherche pour créer l'index, fermer puis rouvrir l'application, vérifier que l'archive est restaurée et refaire une recherche sans re-sélection ni réindexation. Le test explicite en mode avion n'a pas été confirmé.

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
