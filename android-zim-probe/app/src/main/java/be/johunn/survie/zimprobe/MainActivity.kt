package be.johunn.survie.zimprobe

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Query
import org.kiwix.libzim.Search
import org.kiwix.libzim.Searcher
import org.kiwix.libzim.SearchIterator
import java.io.FileDescriptor
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_OPEN_ZIM = 4102
        private const val MAX_ARTICLE_BYTES = 4_000_000L
        private const val MAX_SEARCH_RESULTS = 10
        private val NON_CONTENT_BLOCKS = Regex("(?is)<(script|style|head|noscript|template)\\b[^>]*>.*?</\\1\\s*>")
        private val HTML_COMMENTS = Regex("(?s)<!--.*?-->")
        init {
            System.loadLibrary("kiwix")
            System.loadLibrary("zim")
            System.loadLibrary("kiwix_wrapper")
            System.loadLibrary("zim_wrapper")
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView
    private lateinit var chooseButton: Button
    private lateinit var articleTitleInput: EditText
    private lateinit var searchQueryInput: EditText
    private lateinit var searchButton: Button
    private var selectedArchiveUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "Prototype de lecture ZIM"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        statusView = TextView(this).apply {
            textSize = 14f
            text = "Aucune archive sélectionnée.\n\nCe prototype tente une lecture directe via le sélecteur Android. Il ne copie pas l’archive."
            setTextIsSelectable(true)
        }
        articleTitleInput = EditText(this).apply {
            hint = "Titre exact de l’article (facultatif)"
            setSingleLine(true)
            textSize = 16f
        }
        searchQueryInput = EditText(this).apply {
            hint = "Rechercher dans l’archive (plein texte)"
            setSingleLine(true)
            textSize = 16f
        }
        searchButton = Button(this).apply {
            text = "Rechercher dans le ZIM"
            isEnabled = false
            setOnClickListener { runFullTextSearch() }
        }
        chooseButton = Button(this).apply {
            text = "Sélectionner une archive ZIM"
            setOnClickListener { openDocumentPicker() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(articleTitleInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(chooseButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(searchQueryInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(searchButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(statusView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_ZIM)
    }

    @Deprecated("Prototype: Activity Result API évitée pour limiter les dépendances.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_ZIM || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Certains fournisseurs ne proposent pas de permission persistante.
        }
        val requestedTitle = articleTitleInput.text.toString().trim()
        selectedArchiveUri = uri
        searchButton.isEnabled = true
        chooseButton.isEnabled = false
        statusView.text = "Archive sélectionnée. Vérification et extraction en cours…"
        executor.execute {
            val result = runCatching { inspectArchive(uri, requestedTitle) }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    "ÉCHEC DE LECTURE\n${error.javaClass.simpleName}: ${error.message ?: "erreur sans détail"}\n\nAucune copie automatique n’a été effectuée."
                }
            )
            mainHandler.post {
                statusView.text = result
                chooseButton.isEnabled = true
            }
        }
    }

    private fun runFullTextSearch() {
        val uri = selectedArchiveUri ?: return
        val queryText = searchQueryInput.text.toString().trim()
        if (queryText.isBlank()) {
            statusView.text = "Saisis un terme à rechercher dans l’archive."
            return
        }
        chooseButton.isEnabled = false
        searchButton.isEnabled = false
        statusView.text = "Recherche plein texte en cours…"
        executor.execute {
            val result = runCatching { searchArchive(uri, queryText) }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    "ÉCHEC DE RECHERCHE\n${error.javaClass.simpleName}: ${error.message ?: "erreur sans détail"}"
                }
            )
            mainHandler.post {
                statusView.text = result
                chooseButton.isEnabled = true
                searchButton.isEnabled = selectedArchiveUri != null
            }
        }
    }

    private fun searchArchive(uri: Uri, queryText: String): String {
        val displayName = queryDisplayName(uri)
        val asset = contentResolver.openAssetFileDescriptor(uri, "r")
            ?: error("Le fournisseur Android n’a pas fourni de descripteur exploitable.")
        asset.use { afd ->
            val archive = openArchive(afd.fileDescriptor, afd.startOffset, afd.length)
            if (!archive.hasFulltextIndex()) {
                archive.dispose()
                error("Cette archive ne contient pas d’index plein texte. La recherche par titre reste distincte.")
            }
            val searcher = try {
                Searcher(archive)
            } catch (error: Throwable) {
                archive.dispose()
                throw error
            }
            var query: Query? = null
            var search: Search? = null
            var iterator: SearchIterator? = null
            try {
                val activeQuery = Query(queryText)
                query = activeQuery
                val activeSearch = searcher.search(activeQuery)
                search = activeSearch
                val estimated = activeSearch.getEstimatedMatches()
                val activeIterator = activeSearch.getResults(0, MAX_SEARCH_RESULTS)
                iterator = activeIterator
                val output = mutableListOf<String>()
                while (activeIterator.hasNext() && output.size < MAX_SEARCH_RESULTS) {
                    val title = activeIterator.getTitle().orEmpty()
                    val path = activeIterator.getPath().orEmpty()
                    val score = activeIterator.getScore()
                    val snippet = activeIterator.getSnippet().orEmpty()
                    output += buildString {
                        appendLine(title.ifBlank { "(titre indisponible)" })
                        appendLine("Chemin : ${path}")
                        appendLine("Score : ${score}")
                        if (snippet.isNotBlank()) appendLine("Extrait : ${snippet}")
                        append("Provenance : ${displayName} / ${path}")
                    }
                    activeIterator.next()
                }
                return buildString {
                    appendLine("RECHERCHE PLEIN TEXTE")
                    appendLine()
                    appendLine("Requête : ${queryText}")
                    appendLine("Archive : ${displayName}")
                    appendLine("UUID : ${archive.uuid}")
                    appendLine("Index plein texte : présent")
                    appendLine("Correspondances estimées : ${estimated}")
                    appendLine("Résultats affichés : ${output.size} (maximum ${MAX_SEARCH_RESULTS})")
                    appendLine()
                    if (output.isEmpty()) {
                        appendLine("Aucun résultat retourné pour cette requête.")
                    } else {
                        output.forEachIndexed { index, item ->
                            appendLine("${index + 1}. ${item}")
                            appendLine()
                        }
                    }
                }
            } finally {
                runCatching { iterator?.dispose() }
                runCatching { search?.dispose() }
                runCatching { query?.dispose() }
                runCatching { searcher.dispose() }
                runCatching { archive.dispose() }
            }
        }
    }

    private fun inspectArchive(uri: Uri, requestedTitle: String): String {
        val displayName = queryDisplayName(uri)
        val asset = contentResolver.openAssetFileDescriptor(uri, "r")
            ?: error("Le fournisseur Android n’a pas fourni de descripteur exploitable.")
        asset.use { afd ->
            val archive = openArchive(afd.fileDescriptor, afd.startOffset, afd.length)
            val archiveTitle = safeMetadata(archive, "Title")
            val archiveName = safeMetadata(archive, "Name")
            val language = safeMetadata(archive, "Language")
            val uuid = runCatching { archive.uuid }.getOrNull().orEmpty()
            val selectedEntry = if (requestedTitle.isBlank()) archive.mainEntry
                else archive.getEntryByTitle(requestedTitle)
            val entry = requireNotNull(selectedEntry) {
                if (requestedTitle.isBlank()) "L’archive ne déclare aucune entrée principale."
                else "Aucun article ne porte exactement le titre « $requestedTitle »."
            }
            val item = entry.getItem(true)
            val itemSize = item.size
            require(itemSize in 1..MAX_ARTICLE_BYTES) {
                "L’article sélectionné fait ${itemSize} octets. Limite du prototype : ${MAX_ARTICLE_BYTES} octets."
            }
            val bytes = item.data.data
            val html = bytes.toString(Charsets.UTF_8)
            val contentHtml = html
                .replace(HTML_COMMENTS, " ")
                .replace(NON_CONTENT_BLOCKS, " ")
            val plainText = android.text.Html.fromHtml(
                contentHtml, android.text.Html.FROM_HTML_MODE_COMPACT
            ).toString()
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex(" *\\n *"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
            val excerpt = plainText.take(5_000).ifBlank {
                "Aucun texte éditorial lisible n’a été extrait de cette entrée HTML."
            }
            return buildString {
                appendLine("LECTURE RÉUSSIE")
                appendLine()
                appendLine("Fichier choisi : ${displayName}")
                appendLine("URI SAF : ${uri}")
                appendLine()
                appendLine("ARCHIVE")
                appendLine("Titre : ${archiveTitle}")
                appendLine("Nom : ${archiveName}")
                appendLine("Langue principale : ${language}")
                appendLine("UUID : ${uuid}")
                appendLine("Taille déclarée : ${afd.length} octets")
                appendLine("Décalage du contenu : ${afd.startOffset} octets")
                appendLine()
                appendLine("ARTICLE EXTRAIT")
                appendLine("Titre recherché : ${requestedTitle.ifBlank { "(page principale de l’archive)" }}")
                appendLine("Titre extrait : ${item.title}")
                appendLine("Chemin : ${item.path}")
                appendLine("Type MIME : ${item.mimetype}")
                appendLine("Taille : ${itemSize} octets")
                appendLine()
                appendLine("EXTRAIT DU CONTENU")
                appendLine(excerpt)
                appendLine()
                appendLine("PROVENANCE : archive « ${displayName} », UUID ${uuid}, entrée « ${item.path} ».")
            }
        }
    }

    private fun openArchive(fd: FileDescriptor, offset: Long, length: Long): Archive =
        when {
            length > 0L -> Archive(fd, offset, length)
            offset == 0L -> Archive(fd)
            else -> error(
                "Le fournisseur SAF indique un décalage de ${offset} octets sans longueur. " +
                    "Lecture directe impossible sans risquer de lire le mauvais segment. Aucun fichier n’a été copié."
            )
        }

    private fun safeMetadata(archive: Archive, key: String): String =
        runCatching { archive.getMetadata(key) }.getOrNull().orEmpty()

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: uri.lastPathSegment.orEmpty()
            }
        }
        return uri.lastPathSegment ?: "nom indisponible"
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
