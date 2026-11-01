# Reprise du projet

Dernière mise à jour : 1er novembre 2026.

## Point de reprise

- Dépôt : [Johunn/premiers-secours-offline](https://github.com/Johunn/premiers-secours-offline)
- Branche Android active : `feature/android-zim-probe`
- Dernier APK essayé sur téléphone : version 0.10.0, build CI 45; capture reçue le 1er novembre pour le ZIM Maître Lucas Grammaire.
- Dernière compilation : version 0.11.0, build CI 46; compilation, alignement et signature validés. Essai téléphone à faire.
- Version 0.8.0 / build 42 : l'utilisateur avait aussi confirmé que la recherche commune et les parcours du guide et du ZIM fonctionnaient correctement.
- Commit Android du build 43 : `962a42a` — mémoriser et restaurer l'archive ZIM après redémarrage.
- Commit de la sélection de plusieurs archives : `d87705f`.
- Commit de l'intégration guide + ZIM : `da952fc`.
- La PWA du guide de survie reste le produit web existant. Ses fichiers n'ont pas été modifiés dans ce chantier Android.
- L'application Android lit l'archive ZIM active choisie par l'utilisateur et construit un index plein texte FTS4 local, distinct par UUID d'archive.

## Décision produit

Faire une seule application Android hors ligne avec une recherche commune dans le guide et l'archive ZIM. Présenter les résultats dans deux groupes distincts, chacun avec sa provenance; ne pas fusionner leurs classements. Le guide continue d'être livré comme PWA/web séparément et ses fichiers sont copiés dans l'APK au moment de la compilation. Aucun assistant génératif ni réponse médicale rédigée n'est inclus à cette étape.

L'archive `wikivoyage_fr_all_nopic_2026-10.zim` reste un corpus d'essai du lecteur; elle ne doit pas être présentée comme source médicale.

## État Android

### APK précédent récupéré (build 43)

- Version : 0.9.0, code 10, build CI numéro 43.
- Identifiant Android : `be.johunn.survie.zimprobe.debug.run43`.
- Fichier joint à l'exécution CI : [APK run 43](https://github.com/Johunn/premiers-secours-offline/actions/runs/41139363163).
- SHA-256 de l'APK : `F762A8AF760AB8C87A06F210A9D72F2FC2F430458295A3645EE83A0B6BD65B49`.
- Taille : 68 424 245 octets. L'APK a été extrait de l'artefact CI et son contenu local du guide est valide; le workflow a réussi les contrôles zipalign et apksigner.
- L'artefact GitHub expire le 13 novembre 2026. S'il n'est plus disponible, relancer la compilation sur la branche active.

### APK compilé (build 45)

- Version 0.10.0, code 11; identifiant Android `be.johunn.survie.zimprobe.debug.run45`.
- Fichier : [artefact APK run 45](https://github.com/Johunn/premiers-secours-offline/actions/runs/41146181199/artifacts/13020683012). Le workflow de compilation, zipalign et apksigner est réussi.

### Version intégrée

- Version source : 0.8.0, code 9, commit `da952fc`.
- L'accueil permet une requête commune dans les fiches hors ligne et dans l'archive ZIM sélectionnée; les résultats sont présentés séparément (jusqu'à 10 par source).
- Les fiches et l'interface complètes de la PWA sont intégrées comme ressources locales au build Android, sans modification des fichiers PWA. Les données de recherche du guide sont extraites de `fiches.js` au moment de la compilation.
- Les articles ZIM continuent de s'ouvrir dans le lecteur natif; les fiches de guide ouvrent le détail existant de la PWA intégrée.
- Compilation CI : réussie pour le workflow [build 42](https://github.com/Johunn/premiers-secours-offline/actions/runs/41135050442); bibliothèques natives, APK, alignement et signature vérifiés.
- Essai sur téléphone : l'utilisateur indique que les fonctions testées dans cette version marchent correctement, notamment la recherche `hémorragie` dans le guide et la recherche commune dans le ZIM. Le mode avion n'a pas été précisé explicitement.

### Version 0.9.0

- Version source : 0.9.0, code 10, commit `962a42a`.
- Mémorise l'URI de l'archive seulement quand Android confirme une autorisation SAF de lecture persistante; restaure l'archive au prochain démarrage.
- Si l'autorisation a disparu, efface l'URI mémorisée et indique qu'il faut choisir l'archive à nouveau. L'index FTS4 existant n'est pas supprimé.
- L'APK run 43 a un nouvel identifiant de paquet de test : l'utilisateur a choisi à nouveau l'archive et construit l'index distinct de cette installation; la restauration et la réutilisation après fermeture/réouverture sont confirmées sur téléphone.
- Compilation CI : réussie pour le workflow [build 43](https://github.com/Johunn/premiers-secours-offline/actions/runs/41139363163); bibliothèques natives, APK, alignement et signature vérifiés.
- Essai sur téléphone : réussi pour la restauration de l'archive et la recherche sans réindexation. Le mode avion n'a pas été précisé.

### Version 0.10.0

- Code Android version 11; APK CI run 45, identifiant `be.johunn.survie.zimprobe.debug.run45`.
- Permet de conserver plusieurs URI SAF autorisées, de choisir depuis l'accueil l'archive à inclure dans la recherche commune et d'en ajouter une sans remplacer les autres.
- L'option « Guide uniquement » permet d'exclure le ZIM de la recherche commune. La recherche ZIM utilise une seule archive choisie à la fois; les deux sources restent présentées séparément.
- Le retour d'un résultat ZIM conserve l'URI de l'archive qui a produit ce résultat, même si l'archive active change ensuite.
- Migre l'ancienne préférence à archive unique et conserve les index locaux par UUID.
- Compilation CI : réussie pour le workflow [build 45](https://github.com/Johunn/premiers-secours-offline/actions/runs/41146181199); l'APK est disponible dans l'artefact `android-zim-probe-patched-debug`.
- Essai sur téléphone : en cours. Cette installation a des données séparées de run 43.

### Dernier APK compilé (build 46) — PDF indexés

- Version 0.11.0, code 12; commit `0beeacb`; identifiant Android `be.johunn.survie.zimprobe.debug.run46`.
- Fichier : [artefact APK run 46](https://github.com/Johunn/premiers-secours-offline/actions/runs/41323256680/artifacts/13090132620).
- CI : [run 46 réussi](https://github.com/Johunn/premiers-secours-offline/actions/runs/41323256680), compilation, zipalign et apksigner validés.
- Essai téléphone : en attente; la compilation CI ne vérifie pas l'extraction des PDF sur l'appareil.
- Diagnostic de la recherche « adjectif » : la capture confirme que l'archive sélectionnée est `maitre_lucas_grammaire_fr_2023-05.zim`, UUID `204f92b3-fd36-5e61-9d66-c694392f51ec`, et que son index précédent ne contenait qu'un document.
- L'inspection du ZIM public portant le même UUID trouve 577 entrées de répertoire au total (ce nombre n'est pas le nombre d'articles), dont 13 fichiers `application/pdf`. Le filtre HTML du build 45 ignorait leurs textes; une seule entrée HTML éditoriale admissible explique le compteur 1 affiché. Parmi les PDF figure `files/Adjectif/adjectif corrigé.pdf`.
- Le correctif ajoute l'extraction locale du texte des PDF à l'indexation, à l'ouverture des résultats et à l'outil de lecture d'une entrée, avec [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android). La base d'index passe à la version 2 pour provoquer une reconstruction d'un ancien index. Les entrées HTML sont limitées à 2 Mo et les PDF à 4 Mo; le texte est plafonné à 250 000 caractères par entrée. Les PDF composés uniquement d'images ne sont pas traités par OCR.
- La petite archive affiche maintenant un état de début et de fin d'indexation, même si elle contient moins de 250 entrées.
- Essai à faire sur téléphone : installer run 46 sans désinstaller run 45, ajouter de nouveau le ZIM de grammaire, attendre l'indexation, rechercher `adjectif`, ouvrir un résultat PDF puis revenir aux résultats. Vérifier aussi que le guide reste accessible.
- Run 46 aura le paquet `be.johunn.survie.zimprobe.debug.run46` et un espace de données distinct : il faudra re-sélectionner les archives et reconstruire leurs index. Les données run 45 resteront sur le téléphone tant que l'application run 45 n'est pas désinstallée.

### Derniers essais et diagnostic rapportés

- Build 42 / version 0.8.0 : l'utilisateur confirme que la recherche `hémorragie` dans le guide et la recherche commune dans le guide + ZIM fonctionnent. Il dit que tout fonctionne correctement; il n'a pas spécifié séparément le mode avion.
- Archive : `wikivoyage_fr_all_nopic_2026-10.zim`, UUID `1985c9fc-40e3-5546-ebe0-08bfc503d30d`.
- Indexation rapportée : 11 985 articles, environ six minutes la première fois.
- Sur un build précédent, l'utilisateur avait confirmé que la recherche fonctionnait instantanément en mode avion. Son dernier compte rendu ne précise pas le mode réseau.
- Build 43 / version 0.9.0 : l'utilisateur a sélectionné WikiMed après Wikivoyage; la recherche ne portait alors que sur l'archive sélectionnée, sans choix dans la recherche de l'accueil. Après avoir re-sélectionné Wikivoyage, ses résultats sont revenus sans nouvelle indexation. Les index sont donc réutilisés par archive, mais le build 43 ne mémorise qu'une archive active.
- Build 45 / version 0.10.0 : run45 possède ses propres données; l'utilisateur a choisi le ZIM de grammaire et la recherche « adjectif » a indiqué zéro résultat, avec 1 document indexé. La capture montre l'archive sélectionnée et son UUID; l'hypothèse d'une mauvaise sélection est donc écartée pour cet essai.
- Le ZIM identifié par cet UUID comporte des fichiers PDF de grammaire. L'ancien indexeur ne traitait que le HTML; il ne pouvait donc pas rechercher le texte de ces documents. Les 577 entrées trouvées sont des entrées de répertoire, pas 577 articles éditoriaux.
- Sur le build 45, la progression visible était mise à jour tous les 250 articles parcourus; cela pouvait masquer l'indexation rapide d'un petit ZIM. La version 0.11.0 affiche désormais également l'état initial et final.
- Avec le build précédent, `Bruxelles` renvoyait les articles bruxellois en tête et des extraits surlignés lisibles.
- Avec le build précédent, `grand-place` renvoyait encore des résultats trop larges : les mots pouvaient apparaître séparément dans l'article. Le correctif du build 40 conserve les groupes avec trait d'union comme une phrase, par exemple `grand-place` → « grand place ».
- Sur le build 40, `Bruxelles` renvoie Bruxelles en premier et des pages bruxelloises pertinentes; les extraits surlignent correctement le mot recherché.
- Sur le build 40, `grand-place` renvoie des pages où l'expression apparaît effectivement. `Quartier du Centre (Bruxelles)` arrive en premier; la page `Bruxelles` est neuvième, avec des pages d'autres villes ayant elles aussi une Grand-Place entre les deux. Les extraits confirment les correspondances exactes.
- La pertinence de ces deux recherches est donc validée sur téléphone. Le classement reflète les occurrences dans l'archive; une recherche générale `grand-place` peut légitimement retourner les Grand-Places d'autres villes.

### Navigation des résultats (build 41, validée sur téléphone)

L'utilisateur a signalé que les résultats étaient affichés comme du texte non activable. Le build 41 rend chaque ligne activable, ouvre l'article par son chemin ZIM et affiche le texte nettoyé avec un bouton de retour à la liste. Sur téléphone, l'utilisateur confirme que les résultats s'ouvrent correctement pour `Bruxelles` et `grand-place`, et que le retour fonctionne. Le mode avion n'a pas été précisé pour cet essai. Les liens internes, images et tableaux ne sont pas inclus dans ce premier affichage.

### Prochaine étape

Faire vérifier sur téléphone la recherche `adjectif` dans le ZIM Maître Lucas, l'ouverture d'un résultat PDF et le retour à la recherche. L'installation run46 aura des données isolées : garder run45 installé, re-sélectionner le ZIM de grammaire et laisser reconstruire l'index. Les autres archives devront également être re-sélectionnées/réindexées si elles sont testées dans run46.

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
