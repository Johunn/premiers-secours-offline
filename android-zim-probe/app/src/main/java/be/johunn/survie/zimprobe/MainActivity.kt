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
import java.io.FileDescriptor
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_OPEN_ZIM = 4102
        private const val MAX_ARTICLE_BYTES = 4_000_000L
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
            val item = selectedEntry.getItem(true)
            val itemSize = item.size
            require(itemSize in 1..MAX_ARTICLE_BYTES) {
                "L’article sélectionné fait ${itemSize} octets. Limite du prototype : ${MAX_ARTICLE_BYTES} octets."
            }
            val bytes = item.data.data
            val html = bytes.toString(Charsets.UTF_8)
            val plainText = android.text.Html.fromHtml(
                html, android.text.Html.FROM_HTML_MODE_COMPACT
            ).toString().replace(Regex("\\n{3,}"), "\n\n").trim()
            val excerpt = plainText.take(5_000).ifBlank { html.take(1_500) }
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
