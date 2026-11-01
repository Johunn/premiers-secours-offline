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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.kiwix.libzim.Archive
import org.json.JSONArray
import java.io.FileDescriptor
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_OPEN_ZIM = 4102
        private const val ARCHIVE_PREFS = "selected_zim_archive"
        // ARCHIVE_URI_KEY is retained only to migrate installations from the single-archive build.
        private const val ARCHIVE_URI_KEY = "uri"
        private const val ARCHIVE_URIS_KEY = "uris"
        private const val ACTIVE_ARCHIVE_URI_KEY = "active_uri"
        private const val MAX_ARTICLE_BYTES = 4_000_000L
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_INDEXED_ARTICLE_BYTES = 2_000_000L
        private const val MAX_INDEXED_PDF_BYTES = 4_000_000L
        private const val MAX_INDEXED_ARTICLE_TEXT_CHARS = 250_000
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
    private lateinit var archiveSelectionView: TextView
    private lateinit var archivePicker: Spinner
    private lateinit var unifiedStatusView: TextView
    private lateinit var unifiedResultsScreen: ScrollView
    private lateinit var unifiedResultsContainer: LinearLayout
    private lateinit var guideScreen: LinearLayout
    private lateinit var guideWebView: WebView
    private lateinit var guideBackButton: Button
    private lateinit var rootContainer: LinearLayout
    private var selectedArchiveUri: Uri? = null
    private val savedArchiveUris = mutableListOf<Uri>()
    private val archiveOptions = mutableListOf<ArchiveOption>()
    private var sessionOnlyArchiveUri: Uri? = null
    private var archivePickerIsRefreshing = false
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
        PDFBoxResourceLoader.init(applicationContext)
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
            text = "Ajouter une archive ZIM"
            isAllCaps = false
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
        restoreArchiveSelection()
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
            addView(TextView(this@MainActivity).apply {
                text = "Archive ZIM incluse dans la recherche"
                textSize = 14f
                setPadding(0, dp(12), 0, 0)
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            archivePicker = Spinner(this@MainActivity).apply {
                contentDescription = "Choisir l’archive ZIM à inclure avec le guide"
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        if (archivePickerIsRefreshing || archivePicker.selectedItemPosition != position) return
                        val uri = archiveOptions.getOrNull(position)?.uri
                        if (uri != selectedArchiveUri) selectArchive(uri)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            addView(archivePicker, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(Button(this@MainActivity).apply {
                text = "Ajouter une archive ZIM"
                isAllCaps = false
                setOnClickListener { openDocumentPicker() }
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            unifiedSearchButton = Button(this@MainActivity).apply {
                text = "Rechercher dans le guide"
                isAllCaps = false
                setOnClickListener { runUnifiedSearch() }
            }
            addView(unifiedSearchButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            archiveSelectionView = TextView(this@MainActivity).apply {
                text = "Aucune archive ZIM choisie. Le guide reste consultable sans elle."
                textSize = 13f
                setPadding(0, 0, 0, dp(12))
            }
            addView(archiveSelectionView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@MainActivity).apply {
                text = "Choisis une archive dans la liste. Les résultats du guide et de cette archive seront présentés séparément avec leur provenance."
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

    private fun restoreArchiveSelection() {
        val preferences = getSharedPreferences(ARCHIVE_PREFS, MODE_PRIVATE)
        val storedUris = runCatching {
            val array = JSONArray(preferences.getString(ARCHIVE_URIS_KEY, "[]"))
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }?.let {
                    runCatching { Uri.parse(it) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
        val legacyValue = preferences.getString(ARCHIVE_URI_KEY, null)
        val legacyUri = legacyValue?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val candidates = (storedUris + listOfNotNull(legacyUri)).distinct()
        val retainedUris = contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
            .toSet()
        val availableUris = candidates.filter { it in retainedUris }
        val lostArchiveCount = candidates.size - availableUris.size
        savedArchiveUris.clear()
        savedArchiveUris.addAll(availableUris)

        val hasSavedActiveSelection = preferences.contains(ACTIVE_ARCHIVE_URI_KEY)
        val savedActiveValue = preferences.getString(ACTIVE_ARCHIVE_URI_KEY, null)
        val savedActiveUri = savedActiveValue?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val preferredUri = if (hasSavedActiveSelection && savedActiveValue.isNullOrEmpty()) {
            null
        } else {
            savedActiveUri?.takeIf { it in savedArchiveUris }
                ?: legacyUri?.takeIf { it in savedArchiveUris }
                ?: savedArchiveUris.firstOrNull()
        }

        preferences.edit().apply {
            putString(ARCHIVE_URIS_KEY, JSONArray(savedArchiveUris.map(Uri::toString)).toString())
            remove(ARCHIVE_URI_KEY)
            if (preferredUri != null) putString(ACTIVE_ARCHIVE_URI_KEY, preferredUri.toString())
            else if (hasSavedActiveSelection && savedActiveValue.isNullOrEmpty()) {
                putString(ACTIVE_ARCHIVE_URI_KEY, "")
            } else remove(ACTIVE_ARCHIVE_URI_KEY)
        }.apply()
        refreshArchivePicker(preferredUri)
        if (lostArchiveCount > 0) {
            archiveSelectionView.append(
                "\nL’accès à $lostArchiveCount archive(s) a expiré; son index local n’a pas été supprimé."
            )
            statusView.text = "Android n’a plus l’autorisation de lecture d’une archive. Son index local est conservé; ajoute de nouveau l’archive pour la rechercher."
        } else if (preferredUri != null) {
            statusView.text = "Archive retrouvée. Son index local sera réutilisé s’il est déjà prêt."
        }
    }

    private fun refreshArchivePicker(preferredUri: Uri?) {
        archiveOptions.clear()
        archiveOptions += ArchiveOption(uri = null, label = "Guide uniquement")
        savedArchiveUris.forEach { uri ->
            val name = runCatching { queryDisplayName(uri) }.getOrDefault("archive ZIM")
            archiveOptions += ArchiveOption(uri = uri, label = name)
        }
        sessionOnlyArchiveUri?.takeIf { it !in savedArchiveUris }?.let { uri ->
            val name = runCatching { queryDisplayName(uri) }.getOrDefault("archive ZIM")
            archiveOptions += ArchiveOption(uri = uri, label = "$name · cette session")
        }

        selectedArchiveUri = preferredUri?.takeIf { uri -> archiveOptions.any { it.uri == uri } }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            archiveOptions.map { it.label }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        archivePickerIsRefreshing = true
        archivePicker.adapter = adapter
        val selectedPosition = archiveOptions.indexOfFirst { it.uri == selectedArchiveUri }
            .coerceAtLeast(0)
        archivePicker.setSelection(selectedPosition, false)
        archivePicker.post { archivePickerIsRefreshing = false }
        updateArchiveSelectionUi()
    }

    private fun selectArchive(uri: Uri?) {
        selectedArchiveUri = uri
        getSharedPreferences(ARCHIVE_PREFS, MODE_PRIVATE).edit().apply {
            if (uri != null && uri in savedArchiveUris) {
                putString(ACTIVE_ARCHIVE_URI_KEY, uri.toString())
            } else if (uri == null) {
                putString(ACTIVE_ARCHIVE_URI_KEY, "")
            } else {
                remove(ACTIVE_ARCHIVE_URI_KEY)
            }
        }.apply()
        updateArchiveSelectionUi()
    }

    private fun updateArchiveSelectionUi() {
        searchButton.isEnabled = selectedArchiveUri != null
        unifiedSearchButton.text = if (selectedArchiveUri == null) {
            "Rechercher dans le guide seul"
        } else {
            "Rechercher dans le guide et l’archive choisie"
        }
        archiveSelectionView.text = selectedArchiveUri?.let { uri ->
            val name = runCatching { queryDisplayName(uri) }.getOrDefault("archive ZIM")
            if (uri in savedArchiveUris) {
                "Archive choisie : $name. Son index local est conservé séparément."
            } else {
                "Archive choisie : $name. Elle sera disponible jusqu’à la fermeture de l’application."
            }
        } ?: if (savedArchiveUris.isEmpty()) {
            "Aucune archive ZIM choisie. Le guide reste consultable sans elle."
        } else {
            "Recherche dans le guide seul. Choisis une archive enregistrée ci-dessus pour inclure son index."
        }
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
        val hasPersistedReadPermission = try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        } catch (_: SecurityException) {
            false
        }
        if (hasPersistedReadPermission) {
            if (uri !in savedArchiveUris) savedArchiveUris += uri
            sessionOnlyArchiveUri = null
            getSharedPreferences(ARCHIVE_PREFS, MODE_PRIVATE).edit().apply {
                putString(ARCHIVE_URIS_KEY, JSONArray(savedArchiveUris.map(Uri::toString)).toString())
                putString(ACTIVE_ARCHIVE_URI_KEY, uri.toString())
                remove(ARCHIVE_URI_KEY)
            }.apply()
        } else {
            sessionOnlyArchiveUri = uri
        }
        val requestedTitle = articleTitleInput.text.toString().trim()
        refreshArchivePicker(uri)
        val displayName = runCatching { queryDisplayName(uri) }.getOrDefault("archive ZIM")
        chooseButton.isEnabled = false
        statusView.text = if (hasPersistedReadPermission) {
            "Archive ajoutée : $displayName. Vérification et extraction en cours…"
        } else {
            "Archive disponible pour cette session : Android n’a pas conservé son autorisation. Vérification en cours…"
        }
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
            if (zimScreen == null && selectedArchiveUri == null) {
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
        if (zimScreen == null && selectedArchiveUri == null) {
            addUnifiedEmptyMessage("Choisis une archive ZIM sur l’écran d’accueil pour inclure ses articles.")
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
                    mainHandler.post {
                        val progress = "Indexation locale en cours…\n\nEntrées parcourues : 0 / ${archive.allEntryCount}\nDocuments indexés : 0"
                        statusView.text = progress
                        onProgress?.invoke(progress)
                    }
                    // iterByTitle() omits media entries such as PDFs on modern ZIMs.
                    // iterByPath() visits every user entry, including both articles and media.
                    val iterator = archive.iterByPath()
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
                                val mimeType = item.mimetype.orEmpty()
                                val maxBytes = if (mimeType.equals("application/pdf", ignoreCase = true)) {
                                    MAX_INDEXED_PDF_BYTES
                                } else {
                                    MAX_INDEXED_ARTICLE_BYTES
                                }
                                if (item.size in 1..maxBytes &&
                                    (mimeType.startsWith("text/html", ignoreCase = true) ||
                                        mimeType.equals("application/pdf", ignoreCase = true))) {
                                    val body = extractArticleText(mimeType, item.data.data)
                                    if (body != null && body.length >= 40) {
                                        batch += ArticleDocument(
                                            title = searchableTitle(entry.title.orEmpty(), path),
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
                                    append("Documents indexés : $indexed")
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
                    mainHandler.post {
                        val progress = "Indexation locale terminée.\n\nEntrées parcourues : $scanned / ${archive.allEntryCount}\nDocuments indexés : $indexed"
                        statusView.text = progress
                        onProgress?.invoke(progress)
                    }
                    if (indexed == 0) {
                        error("Aucun document HTML ou PDF lisible n’a pu être indexé.")
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
                    appendLine("Documents indexés : ${localIndex.articleCount()}")
                    appendLine("Résultats affichés : ${results.size} (maximum $MAX_SEARCH_RESULTS)")
                    appendLine()
                    if (results.isEmpty()) {
                        append("Aucun résultat pour cette requête.")
                    } else {
                        append("Touchez un résultat ci-dessous pour ouvrir l’article.")
                    }
                }
                return SearchScreen(
                    archiveUri = uri,
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
        val uri = currentSearchScreen?.archiveUri ?: selectedArchiveUri ?: return
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
                val mimeType = item.mimetype.orEmpty()
                val text = extractArticleText(mimeType, item.data.data)
                return ArticlePage(
                    archiveName = displayName,
                    archiveUuid = archive.uuid,
                    title = item.title.orEmpty().ifBlank { entry.title.orEmpty() },
                    path = item.path.orEmpty().ifBlank { path },
                    byteSize = itemSize,
                    mimeType = mimeType,
                    text = text ?: "Aucun texte lisible n’a été extrait de cet article."
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
            text = "Chemin : ${page.path}\nArchive : ${page.archiveName}\nUUID : ${page.archiveUuid}\nType : ${page.mimeType}\nTaille : ${page.byteSize} octets"
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
        val archiveUri: Uri,
        val archiveName: String,
        val archiveUuid: String,
        val summary: String,
        val results: List<ArticleSearchResult>
    )

    private data class ArchiveOption(
        val uri: Uri?,
        val label: String
    )

    private data class ArticlePage(
        val archiveName: String,
        val archiveUuid: String,
        val title: String,
        val path: String,
        val byteSize: Long,
        val mimeType: String,
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
            val mimeType = item.mimetype.orEmpty()
            val plainText = extractArticleText(mimeType, bytes)
            val excerpt = plainText?.take(5_000) ?: "Aucun texte éditorial lisible n’a été extrait de cette entrée."
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
                appendLine("Type MIME : $mimeType")
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

    private fun extractArticleText(mimeType: String, bytes: ByteArray): String? {
        val extracted = when {
            mimeType.startsWith("text/html", ignoreCase = true) -> {
                val html = bytes.toString(Charsets.UTF_8)
                    .replace(HTML_COMMENTS, " ")
                    .replace(NON_CONTENT_BLOCKS, " ")
                android.text.Html.fromHtml(
                    html, android.text.Html.FROM_HTML_MODE_COMPACT
                ).toString()
            }
            mimeType.equals("application/pdf", ignoreCase = true) -> {
                try {
                    PDDocument.load(bytes).use { document ->
                        PDFTextStripper().getText(document)
                    }
                } catch (_: Exception) {
                    return null
                }
            }
            else -> return null
        }
        return extracted
            .replace("\uFFFC", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(MAX_INDEXED_ARTICLE_TEXT_CHARS)
            .takeIf { it.isNotBlank() }
    }

    private fun searchableTitle(title: String, path: String): String =
        title.ifBlank {
            val filename = path.substringAfterLast('/')
            filename.substringBeforeLast('.', filename).replace('_', ' ')
        }

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
