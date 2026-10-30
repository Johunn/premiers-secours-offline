package be.johunn.survie.zimprobe

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.kiwix.libzim.Archive
import java.io.FileDescriptor
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_OPEN_ZIM = 4102
        private const val MAX_ARTICLE_BYTES = 4_000_000L
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_INDEXED_ARTICLE_BYTES = 2_000_000L
        private const val INDEX_BATCH_SIZE = 10
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
    private lateinit var searchControls: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var homeScreen: ScrollView
    private lateinit var unifiedQueryInput: EditText
    private lateinit var unifiedSearchButton: Button
    private lateinit var unifiedStatusView: TextView
    private lateinit var unifiedResultsScreen: ScrollView
    private lateinit var unifiedResultsContainer: LinearLayout
    private lateinit var guideScreen: LinearLayout
    private lateinit var guideWebView: WebView
    private lateinit var guideBackButton: Button
    private lateinit var rootContainer: LinearLayout
    private var selectedArchiveUri: Uri? = null
    private var currentSearchScreen: SearchScreen? = null
    private var showingArticle = false
    private var openingArticle = false
    private var guideLoaded = false
    private var guidePageReady = false
    private var pendingGuideFicheId: String? = null
    private var guideReturnToSearch = false
    private var activeTopLevelScreen = TopLevelScreen.HOME
    private val guideContentIndex by lazy { GuideContentIndex(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "Bibliothèque ZIM"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        statusView = TextView(this).apply {
            textSize = 14f
            text = "Aucune archive sélectionnée. Le ZIM est lu depuis son emplacement et n’est pas copié."
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
        searchControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(articleTitleInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(chooseButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(searchQueryInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(searchButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(Button(this@MainActivity).apply {
                text = "← Accueil"
                isAllCaps = false
                setOnClickListener { showHome() }
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(searchControls, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(statusView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(resultsContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scrollView = ScrollView(this).apply { addView(content) }

        homeScreen = createHomeScreen()
        unifiedResultsScreen = createUnifiedResultsScreen()
        guideWebView = createGuideWebView()
        guideScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            guideBackButton = Button(this@MainActivity).apply {
                text = "← Accueil"
                isAllCaps = false
                setOnClickListener { returnFromGuide() }
            }
            addView(guideBackButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(guideWebView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(homeScreen, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(guideScreen, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(unifiedResultsScreen, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(rootContainer)
        showHome()
    }

    private fun createHomeScreen(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            addView(TextView(this@MainActivity).apply {
                text = "Guide hors ligne"
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply {
                text = "Fiches de préparation et bibliothèque ZIM, réunies dans une seule application. Les contenus intégrés restent disponibles sans réseau."
                textSize = 16f
                setPadding(0, 0, 0, dp(20))
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            unifiedQueryInput = EditText(this@MainActivity).apply {
                hint = "Rechercher dans le guide et le ZIM"
                setSingleLine(true)
                textSize = 16f
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                        runUnifiedSearch()
                        true
                    } else false
                }
            }
            addView(unifiedQueryInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            unifiedSearchButton = Button(this@MainActivity).apply {
                text = "Rechercher dans les deux sources"
                isAllCaps = false
                setOnClickListener { runUnifiedSearch() }
            }
            addView(unifiedSearchButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply {
                text = "Les résultats du guide et de l’archive seront présentés séparément avec leur provenance."
                textSize = 13f
                setPadding(0, 0, 0, dp(12))
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(Button(this@MainActivity).apply {
                text = "Ouvrir le guide de premiers secours"
                isAllCaps = false
                setOnClickListener {
                    guideReturnToSearch = false
                    showGuide()
                }
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(Button(this@MainActivity).apply {
                text = "Rechercher dans une archive ZIM"
                isAllCaps = false
                setOnClickListener { showZimLibrary() }
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(Button(this@MainActivity).apply {
                text = "Appeler le 112"
                isAllCaps = false
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                }
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply {
                text = "L’appel au 112 exige un réseau téléphonique. Les fiches hors ligne ne remplacent pas les secours."
                textSize = 13f
                setPadding(0, dp(8), 0, 0)
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return ScrollView(this).apply { addView(content) }
    }

    private fun createUnifiedResultsScreen(): ScrollView {
        unifiedStatusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        }
        unifiedResultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            addView(Button(this@MainActivity).apply {
                text = "← Accueil"
                isAllCaps = false
                setOnClickListener { showHome() }
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply {
                text = "Résultats de recherche"
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(unifiedStatusView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(unifiedResultsContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return ScrollView(this).apply { addView(content) }
    }

    private fun createGuideWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = handleGuideNavigation(request?.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handleGuideNavigation(url?.let { Uri.parse(it) })

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                guidePageReady = true
                // Keep the web guide's content and interactions; expose only a small local
                // entry point so a unified search can open its existing fiche dialog.
                val integrationScript =
                    "document.querySelector('#install')?.remove();" +
                        "document.querySelector('#refresh-app')?.remove();" +
                        "window.openBundledGuideFiche=function(id){" +
                        "const fiche=window.FICHES?.find(item=>item.id===id);if(!fiche)return;" +
                        "const fav=document.querySelector('#favorites-toggle');" +
                        "if(fav?.classList.contains('active'))fav.click();" +
                        "const all=[...document.querySelectorAll('#filters button')].find(b=>b.textContent==='Tout');" +
                        "if(all&&!all.classList.contains('selected'))all.click();" +
                        "const dialog=document.querySelector('#detail');if(dialog?.open)dialog.close();" +
                        "const input=document.querySelector('#search');if(!input)return;" +
                        "input.value=fiche.title;input.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "requestAnimationFrame(()=>{const card=[...document.querySelectorAll('.fiche')].find(c=>c.querySelector('h3')?.textContent===fiche.title);card?.click();});" +
                        "};"
                val pendingId = pendingGuideFicheId
                pendingGuideFicheId = null
                val openPendingFiche = pendingId?.let { id ->
                    "window.openBundledGuideFiche?.(${org.json.JSONObject.quote(id)});"
                }.orEmpty()
                view?.evaluateJavascript(integrationScript + openPendingFiche, null)
            }
        }
    }

    private fun handleGuideNavigation(uri: Uri?): Boolean {
        uri ?: return true
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme == "file" && uri.path.orEmpty().startsWith("/android_asset/guide/")) {
            return false
        }
        if (scheme == "tel" || scheme == "geo" || scheme == "https" || scheme == "http") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            return true
        }
        // Keep navigation inside the bundled guide; do not let it reach arbitrary local files.
        return true
    }

    private fun showHome() {
        activeTopLevelScreen = TopLevelScreen.HOME
        showingArticle = false
        openingArticle = false
        homeScreen.visibility = android.view.View.VISIBLE
        guideScreen.visibility = android.view.View.GONE
        scrollView.visibility = android.view.View.GONE
        unifiedResultsScreen.visibility = android.view.View.GONE
    }

    private fun showGuide() {
        activeTopLevelScreen = TopLevelScreen.GUIDE
        homeScreen.visibility = android.view.View.GONE
        guideScreen.visibility = android.view.View.VISIBLE
        scrollView.visibility = android.view.View.GONE
        unifiedResultsScreen.visibility = android.view.View.GONE
        if (!guideLoaded) {
            guideLoaded = true
            guideWebView.loadUrl("file:///android_asset/guide/index.html")
        }
    }

    private fun showZimLibrary() {
        activeTopLevelScreen = TopLevelScreen.ZIM
        homeScreen.visibility = android.view.View.GONE
        guideScreen.visibility = android.view.View.GONE
        scrollView.visibility = android.view.View.VISIBLE
        unifiedResultsScreen.visibility = android.view.View.GONE
    }

    private fun showUnifiedResults() {
        activeTopLevelScreen = TopLevelScreen.RESULTS
        homeScreen.visibility = android.view.View.GONE
        guideScreen.visibility = android.view.View.GONE
        scrollView.visibility = android.view.View.GONE
        unifiedResultsScreen.visibility = android.view.View.VISIBLE
    }

    private fun openGuideFicheInWebView(id: String) {
        guideWebView.evaluateJavascript(
            "window.openBundledGuideFiche?.(${org.json.JSONObject.quote(id)});",
            null
        )
    }

    private fun openGuideFiche(id: String) {
        guideReturnToSearch = true
        guideBackButton.text = "← Résultats"
        pendingGuideFicheId = id
        showGuide()
        if (guidePageReady) {
            pendingGuideFicheId = null
            openGuideFicheInWebView(id)
        }
    }

    private enum class TopLevelScreen { HOME, GUIDE, ZIM, RESULTS }

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
            val result = runCatching { searchArchive(uri, queryText) }
            mainHandler.post {
                result.fold(
                    onSuccess = { screen -> showSearchResults(screen) },
                    onFailure = { error ->
                        resultsContainer.removeAllViews()
                        statusView.text = "ÉCHEC DE RECHERCHE\n${error.javaClass.simpleName}: ${error.message ?: "erreur sans détail"}"
                    }
                )
                chooseButton.isEnabled = true
                searchButton.isEnabled = selectedArchiveUri != null
            }
        }
    }

    private fun runUnifiedSearch() {
        val queryText = unifiedQueryInput.text.toString().trim()
        if (queryText.isBlank()) {
            unifiedStatusView.text = "Saisis un mot ou une expression à rechercher."
            unifiedResultsContainer.removeAllViews()
            showUnifiedResults()
            return
        }
        val archiveUri = selectedArchiveUri
        unifiedSearchButton.isEnabled = false
        unifiedResultsContainer.removeAllViews()
        unifiedStatusView.text = if (archiveUri == null) {
            "Recherche dans le guide. Sélectionne une archive ZIM pour inclure sa bibliothèque."
        } else {
            "Recherche locale dans le guide et l’archive ZIM…"
        }
        showUnifiedResults()

        executor.execute {
            val guideResults = runCatching { guideContentIndex.search(queryText, MAX_SEARCH_RESULTS) }
            val zimResults = archiveUri?.let { uri ->
                runCatching {
                    searchArchive(uri, queryText) { progress ->
                        unifiedStatusView.text = "Indexation ou recherche dans le ZIM\n$progress"
                    }
                }
            }
            mainHandler.post {
                showUnifiedSearchResults(
                    queryText = queryText,
                    guideResults = guideResults.getOrDefault(emptyList()),
                    guideError = guideResults.exceptionOrNull()?.message,
                    zimScreen = zimResults?.getOrNull(),
                    zimError = zimResults?.exceptionOrNull()?.message
                )
                unifiedSearchButton.isEnabled = true
            }
        }
    }

    private fun showUnifiedSearchResults(
        queryText: String,
        guideResults: List<GuideSearchResult>,
        guideError: String?,
        zimScreen: SearchScreen?,
        zimError: String?
    ) {
        showUnifiedResults()
        unifiedResultsContainer.removeAllViews()
        unifiedStatusView.text = buildString {
            appendLine("Requête : $queryText")
            if (selectedArchiveUri == null) {
                append("Aucune archive ZIM sélectionnée. Les résultats affichés viennent du guide.")
            } else if (zimScreen != null) {
                appendLine("Guide : ${guideResults.size} résultat(s)")
                append("ZIM : ${zimScreen.archiveName} · ${zimScreen.results.size} résultat(s) · index local prêt (${zimScreen.archiveUuid})")
            } else {
                append("Recherche dans le ZIM impossible : ${zimError ?: "erreur inconnue"}")
            }
            if (guideError != null) appendLine("\nRecherche dans le guide impossible : $guideError")
        }

        addUnifiedSectionHeader("GUIDE DE PREMIERS SECOURS · ${guideResults.size}")
        if (guideResults.isEmpty()) {
            addUnifiedEmptyMessage("Aucune fiche du guide ne correspond à cette recherche.")
        } else {
            guideResults.forEach { result ->
                addUnifiedResultRow(
                    title = result.fiche.title,
                    snippet = result.snippet,
                    provenance = "Source : Guide hors ligne · ${result.fiche.category}",
                    onClick = { openGuideFiche(result.fiche.id) }
                )
            }
        }

        addUnifiedSectionHeader("ARCHIVE ZIM · ${zimScreen?.results?.size ?: 0}")
        if (selectedArchiveUri == null) {
            addUnifiedEmptyMessage("Choisis une archive dans « Rechercher dans une archive ZIM » pour inclure ses articles.")
        } else if (zimScreen == null) {
            addUnifiedEmptyMessage("Les résultats ZIM ne sont pas disponibles : ${zimError ?: "erreur inconnue"}")
        } else if (zimScreen.results.isEmpty()) {
            addUnifiedEmptyMessage("Aucun article ZIM ne correspond à cette recherche.")
        } else {
            zimScreen.results.forEach { result ->
                addUnifiedResultRow(
                    title = result.title,
                    snippet = result.snippet.replace("⟦", "").replace("⟧", ""),
                    provenance = "Source : ${zimScreen.archiveName} · ${result.path}",
                    onClick = {
                        currentSearchScreen = zimScreen
                        showZimLibrary()
                        openSearchResult(result)
                    }
                )
            }
        }
        unifiedResultsScreen.post { unifiedResultsScreen.scrollTo(0, 0) }
    }

    private fun addUnifiedSectionHeader(text: String) {
        unifiedResultsContainer.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(6))
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun addUnifiedEmptyMessage(text: String) {
        unifiedResultsContainer.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun addUnifiedResultRow(
        title: String,
        snippet: String,
        provenance: String,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val background = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)) {
                setBackgroundResource(background.resourceId)
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = snippet
            textSize = 14f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(TextView(this).apply {
            text = provenance
            textSize = 12f
            setPadding(0, dp(3), 0, 0)
        })
        unifiedResultsContainer.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        unifiedResultsContainer.addView(android.view.View(this).apply {
            setBackgroundColor(0x33000000)
        }, ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun returnFromGuide() {
        guideWebView.evaluateJavascript(
            "(()=>{const dialog=document.querySelector('#detail');if(dialog?.open)dialog.close();})();",
            null
        )
        if (guideReturnToSearch) showUnifiedResults() else showHome()
        guideReturnToSearch = false
        guideBackButton.text = "← Accueil"
    }

    private fun searchArchive(
        uri: Uri,
        queryText: String,
        onProgress: ((String) -> Unit)? = null
    ): SearchScreen {
        val displayName = queryDisplayName(uri)
        val asset = contentResolver.openAssetFileDescriptor(uri, "r")
            ?: error("Le fournisseur Android n’a pas fourni de descripteur exploitable.")
        asset.use { afd ->
            val archive = openArchive(afd.fileDescriptor, afd.startOffset, afd.length)
            val archiveUuid = archive.uuid
            val localIndex = LocalArticleIndex(this, archiveUuid)
            try {
                if (!localIndex.isReady()) {
                    localIndex.beginRebuild()
                    // iterByTitle() yields editorial articles on both legacy A/ and modern namespace ZIMs.
                    val iterator = archive.iterByTitle()
                    var scanned = 0
                    var indexed = 0
                    val batch = mutableListOf<ArticleDocument>()
                    try {
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            scanned++
                            val path = entry.path.orEmpty()
                            if (!entry.isRedirect()) {
                                val item = entry.getItem(true)
                                if (item.size in 1..MAX_INDEXED_ARTICLE_BYTES &&
                                    item.mimetype.orEmpty().startsWith("text/html", ignoreCase = true)
                                ) {
                                    val html = item.data.data.toString(Charsets.UTF_8)
                                    val articleHtml = html
                                        .replace(HTML_COMMENTS, " ")
                                        .replace(NON_CONTENT_BLOCKS, " ")
                                    val body = android.text.Html.fromHtml(
                                        articleHtml, android.text.Html.FROM_HTML_MODE_COMPACT
                                    ).toString()
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                    if (body.length >= 40) {
                                        batch += ArticleDocument(
                                            title = entry.title.orEmpty(),
                                            path = path,
                                            body = body
                                        )
                                    }
                                }
                            }

                            if (batch.size >= INDEX_BATCH_SIZE) {
                                localIndex.addArticles(batch)
                                indexed += batch.size
                                batch.clear()
                            }
                            if (scanned % 250 == 0) {
                                val progress = buildString {
                                    appendLine("Indexation locale en cours…")
                                    appendLine()
                                    appendLine("Entrées parcourues : $scanned / ${archive.allEntryCount}")
                                    append("Articles indexés : $indexed")
                                }
                                mainHandler.post {
                                    statusView.text = progress
                                    onProgress?.invoke(progress)
                                }
                            }
                        }
                        if (batch.isNotEmpty()) {
                            localIndex.addArticles(batch)
                            indexed += batch.size
                            batch.clear()
                        }
                    } finally {
                        iterator.dispose()
                    }
                    if (indexed == 0) {
                        error("Aucun article HTML éditorial n’a pu être indexé. Articles déclarés par l’archive : ${archive.articleCount}.")
                    }
                    localIndex.finishRebuild(indexed)
                }

                val results = localIndex.search(queryText, MAX_SEARCH_RESULTS)
                val summary = buildString {
                    appendLine("RECHERCHE LOCALE")
                    appendLine()
                    appendLine("Requête : $queryText")
                    appendLine("Archive : $displayName")
                    appendLine("UUID : $archiveUuid")
                    appendLine("Index : SQLite FTS4 local, conservé sur le téléphone")
                    appendLine("Articles indexés : ${localIndex.articleCount()}")
                    appendLine("Résultats affichés : ${results.size} (maximum $MAX_SEARCH_RESULTS)")
                    appendLine()
                    if (results.isEmpty()) {
                        append("Aucun résultat pour cette requête.")
                    } else {
                        append("Touchez un résultat ci-dessous pour ouvrir l’article.")
                    }
                }
                return SearchScreen(
                    archiveName = displayName,
                    archiveUuid = archiveUuid,
                    summary = summary,
                    results = results
                )
            } finally {
                localIndex.close()
                archive.dispose()
            }
        }
    }

    private fun showSearchResults(screen: SearchScreen) {
        currentSearchScreen = screen
        showingArticle = false
        openingArticle = false
        searchControls.visibility = android.view.View.VISIBLE
        statusView.visibility = android.view.View.VISIBLE
        statusView.text = screen.summary
        resultsContainer.removeAllViews()

        if (screen.results.isEmpty()) {
            resultsContainer.addView(TextView(this).apply {
                text = "Aucun résultat pour cette requête."
                textSize = 16f
                setPadding(0, dp(12), 0, dp(12))
            })
        } else {
            screen.results.forEachIndexed { index, result ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    val background = TypedValue()
                    if (theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)) {
                        setBackgroundResource(background.resourceId)
                    }
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    contentDescription = "Ouvrir l’article ${result.title}"
                    setOnClickListener { openSearchResult(result) }
                }
                row.addView(TextView(this).apply {
                    text = "${index + 1}. ${result.title}"
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                })
                row.addView(TextView(this).apply {
                    text = "Chemin : ${result.path}\nExtrait :"
                    textSize = 14f
                    setPadding(0, dp(4), 0, 0)
                })
                row.addView(TextView(this).apply {
                    text = highlightSnippet(result.snippet)
                    textSize = 14f
                })
                row.addView(TextView(this).apply {
                    text = "Provenance : ${screen.archiveName} / ${result.path}"
                    textSize = 12f
                    setPadding(0, dp(4), 0, 0)
                })
                resultsContainer.addView(
                    row,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (index < screen.results.lastIndex) {
                    resultsContainer.addView(android.view.View(this).apply {
                        setBackgroundColor(0x33000000)
                    }, ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                }
            }
        }
        scrollView.post { scrollView.scrollTo(0, 0) }
    }

    private fun highlightSnippet(markedText: String): CharSequence {
        val formatted = SpannableStringBuilder()
        var position = 0
        while (position < markedText.length) {
            val start = markedText.indexOf('⟦', position)
            if (start < 0) {
                formatted.append(markedText.substring(position))
                break
            }
            formatted.append(markedText.substring(position, start))
            val end = markedText.indexOf('⟧', start + 1)
            if (end < 0) {
                formatted.append(markedText.substring(start + 1))
                break
            }
            val spanStart = formatted.length
            formatted.append(markedText.substring(start + 1, end))
            if (formatted.length > spanStart) {
                formatted.setSpan(
                    StyleSpan(Typeface.BOLD),
                    spanStart,
                    formatted.length,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            position = end + 1
        }
        return formatted
    }

    private fun openSearchResult(result: ArticleSearchResult) {
        if (openingArticle) return
        val uri = selectedArchiveUri ?: return
        openingArticle = true
        chooseButton.isEnabled = false
        searchButton.isEnabled = false
        statusView.visibility = android.view.View.VISIBLE
        statusView.text = "Ouverture de « ${result.title} » depuis l’archive…"
        executor.execute {
            val page = runCatching { readSearchResult(uri, result.path) }
            mainHandler.post {
                page.fold(
                    onSuccess = { showArticle(it) },
                    onFailure = { error ->
                        openingArticle = false
                        statusView.visibility = android.view.View.VISIBLE
                        statusView.text = "ÉCHEC D’OUVERTURE DE L’ARTICLE\n${error.javaClass.simpleName}: ${error.message ?: "erreur sans détail"}"
                    }
                )
                chooseButton.isEnabled = true
                searchButton.isEnabled = selectedArchiveUri != null
            }
        }
    }

    private fun readSearchResult(uri: Uri, path: String): ArticlePage {
        val displayName = queryDisplayName(uri)
        val asset = contentResolver.openAssetFileDescriptor(uri, "r")
            ?: error("Le fournisseur Android n’a pas fourni de descripteur exploitable.")
        asset.use { afd ->
            val archive = openArchive(afd.fileDescriptor, afd.startOffset, afd.length)
            try {
                val entry = archive.getEntryByPath(path)
                val item = entry.getItem(true)
                val itemSize = item.size
                require(itemSize in 1..MAX_ARTICLE_BYTES) {
                    "L’article sélectionné fait ${itemSize} octets. Limite du prototype : ${MAX_ARTICLE_BYTES} octets."
                }
                val html = item.data.data.toString(Charsets.UTF_8)
                val contentHtml = html
                    .replace(HTML_COMMENTS, " ")
                    .replace(NON_CONTENT_BLOCKS, " ")
                val text = android.text.Html.fromHtml(
                    contentHtml, android.text.Html.FROM_HTML_MODE_COMPACT
                ).toString()
                    .replace("\uFFFC", " ")
                    .replace(Regex("[ \\t]+"), " ")
                    .replace(Regex(" *\\n *"), "\n")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
                return ArticlePage(
                    archiveName = displayName,
                    archiveUuid = archive.uuid,
                    title = item.title.orEmpty().ifBlank { entry.title.orEmpty() },
                    path = item.path.orEmpty().ifBlank { path },
                    byteSize = itemSize,
                    text = text.ifBlank { "Aucun texte lisible n’a été extrait de cet article." }
                )
            } finally {
                archive.dispose()
            }
        }
    }

    private fun showArticle(page: ArticlePage) {
        showingArticle = true
        openingArticle = false
        searchControls.visibility = android.view.View.GONE
        statusView.visibility = android.view.View.GONE
        resultsContainer.removeAllViews()
        resultsContainer.addView(Button(this).apply {
            text = "← Retour aux résultats"
            isAllCaps = false
            setOnClickListener { currentSearchScreen?.let(::showSearchResults) }
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        resultsContainer.addView(TextView(this).apply {
            text = page.title
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        })
        resultsContainer.addView(TextView(this).apply {
            text = "Chemin : ${page.path}\nArchive : ${page.archiveName}\nUUID : ${page.archiveUuid}\nTaille : ${page.byteSize} octets"
            textSize = 13f
            setTextIsSelectable(true)
        })
        resultsContainer.addView(TextView(this).apply {
            text = page.text
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, dp(24))
        })
        scrollView.post { scrollView.scrollTo(0, 0) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Prototype: article view has an explicit return-to-results button.")
    override fun onBackPressed() {
        val screen = currentSearchScreen
        if (showingArticle && screen != null) showSearchResults(screen)
        else when (activeTopLevelScreen) {
            TopLevelScreen.GUIDE -> returnFromGuide()
            TopLevelScreen.ZIM, TopLevelScreen.RESULTS -> showHome()
            TopLevelScreen.HOME -> super.onBackPressed()
        }
    }

    private data class SearchScreen(
        val archiveName: String,
        val archiveUuid: String,
        val summary: String,
        val results: List<ArticleSearchResult>
    )

    private data class ArticlePage(
        val archiveName: String,
        val archiveUuid: String,
        val title: String,
        val path: String,
        val byteSize: Long,
        val text: String
    )

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
        if (::guideWebView.isInitialized) guideWebView.destroy()
        super.onDestroy()
    }
}
