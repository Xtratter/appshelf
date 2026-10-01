package io.github.xtratter.appshelf

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        /** Открытый экран — чтобы после установки APK обновить список. */
        var current: java.lang.ref.WeakReference<MainActivity>? = null

        private const val REQ_SAVE = 1
        private const val REQ_AUTOSAVE = 2
        private const val REQ_OPEN = 3
        private const val REQ_LINKS = 4
        private const val REQ_APK_FOLDER = 5
    }

    private lateinit var prefs: Prefs
    private lateinit var list: ListView
    private lateinit var topBar: View
    private lateinit var topScrim: View
    private lateinit var searchBox: View
    private lateinit var searchField: EditText
    private lateinit var summary: LinearLayout
    private lateinit var chipsBox: LinearLayout
    private lateinit var chipsScroll: View

    /** Открыт поиск: сводка и фильтры скрыты, найденное — сразу под строкой поиска. */
    private val searching get() = searchBox.visibility == View.VISIBLE
    private val adapter = Adapter()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Все установленные приложения (null — ещё загружаются). */
    private var installed: List<AppInfo>? = null
    private var installedPkgs = emptySet<String>()
    /** Открытый сохранённый список — режим восстановления. */
    private var restore: Snapshot? = null
    private var missingOnly = false
    private var filter: Source? = null
    /** Выбор нескольких приложений (долгое нажатие на строку): отмеченные пакеты. */
    private val selected = LinkedHashSet<String>()
    private val selecting get() = selected.isNotEmpty()
    private lateinit var selBar: LinearLayout
    private lateinit var selTitle: TextView
    private lateinit var selExclude: TextView
    /** Строки, видимые сейчас (для «Все»). */
    private var shownApps: List<AppInfo> = emptyList()

    /** Фильтр «Обновления»: приложения, для которых на GitHub есть версия новее. */
    private var onlyUpdates = false

    /** Фильтр «APK без ссылки»: приложения из APK-файлов, для которых нет ни своей ссылки, ни ссылки из каталога. */
    private var noLink = false
    private var query = ""
    private var pendingFormat = Format.JSON
    private val labels = HashMap<String, String>()
    private var insetBottom = 0

    private fun dp(v: Float) = Ui.dp(this, v).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Haptics.init(this)
        // тема может смениться и без нас: «как в системе» при переключении тёмного режима
        if (!Ui.isCurrent(this, prefs.theme())) Ui.apply(this, prefs.theme())
        setTheme(if (Ui.light) R.style.AppTheme_Light else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        Ui.forgetDialogs()
        setupWindow()
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.list)
        topBar = findViewById(R.id.topBar)
        topScrim = findViewById(R.id.topScrim)
        searchBox = findViewById(R.id.searchBox)
        searchField = findViewById(R.id.searchField)
        val barFill = Ui.withAlpha(Ui.mix(Ui.base, Ui.surface, 0.6f), 0.9f)
        Ui.liquidRoot = window.decorView
        if (Ui.liquid && Build.VERSION.SDK_INT >= 33) {
            // «жидкое стекло»: список виден сквозь панель, изгибаясь у кромки
            val bar = findViewById<View>(R.id.bar)
            val tint = Ui.withAlpha(Ui.base, Ui.barTintAlpha)
            bar.background = LiquidBackdrop(bar, listOf(list), 32f, tint)
            searchBox.background = LiquidBackdrop(searchBox, listOf(list), 26f, tint)
            // мягкая тень: стекло «висит» над списком; контейнер панели не должен обрезать её по своим отступам —
            // иначе тень видна только прямоугольником в уголках у круглых концов панели
            bar.elevation = Ui.dp(this, 8f)
            searchBox.elevation = Ui.dp(this, 8f)
            (topBar as? ViewGroup)?.apply { clipToPadding = false; clipChildren = false }
            (topBar.parent as? ViewGroup)?.clipChildren = false
        } else {
            findViewById<View>(R.id.bar).background = GlassDrawable(this, 32f, barFill)
            searchBox.background = GlassDrawable(this, 26f, barFill)
        }
        topScrim.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Ui.withAlpha(Ui.base, 0.94f), Ui.withAlpha(Ui.base, 0.7f), Ui.withAlpha(Ui.base, 0f)))

        applyThemeToViews()
        setupTopBar()
        buildHeader()
        setupInsets()
        list.adapter = adapter
        list.setOnItemClickListener { parent, view, pos, _ ->
            val r = parent.getItemAtPosition(pos) as? Row ?: return@setOnItemClickListener
            if (selecting) { toggleSelected(r.app.pkg); return@setOnItemClickListener }
            Motion.from(view)   // карточка вытечет из строки
            Haptics.play(Haptics.Kind.TAP)
            DetailsDialog.show(this, r, sourceText(r.app))
        }
        // долгое нажатие — выбор нескольких приложений (не в режиме восстановления)
        list.setOnItemLongClickListener { parent, _, pos, _ ->
            val r = parent.getItemAtPosition(pos) as? Row ?: return@setOnItemLongClickListener false
            if (restore != null) return@setOnItemLongClickListener false
            toggleSelected(r.app.pkg)
            true
        }
        buildSelectionBar()
        render()
    }

    override fun onResume() {
        super.onResume()
        current = java.lang.ref.WeakReference(this)
        // вернулись из настроек с разрешением на установку — продолжаем отложенную установку APK
        ApkInstaller.resume(this)
        Motion.Tilt.start(this)   // блик на стекле следует за наклоном телефона
    }

    override fun onPause() {
        Motion.Tilt.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    /** Приложение установлено — перечитать список (в режиме восстановления может закончиться восстановление). */
    fun onInstalled() { if (!isDestroyed) reload() }

    override fun onStart() {
        super.onStart()
        reload()
        refreshLinks()
        // будильник WebDAV мог пропасть (остановка приложения); пропущенная отправка — догоняем
        Sync.ensure(this)
    }

    // ---------- окно и панель ----------

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.setBackgroundDrawable(if (Ui.aurora) AuroraDrawable() else android.graphics.drawable.ColorDrawable(Ui.base))
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            (if (Ui.light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    /** Цвета из разметки — под текущую тему; в светлой теме значки строки состояния тёмные. */
    private fun applyThemeToViews() {
        if (Build.VERSION.SDK_INT >= 30) {
            val light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (Ui.light) light else 0, light)
        }
        val tint = android.content.res.ColorStateList.valueOf(Ui.TEXT)
        for (id in intArrayOf(R.id.btnSearch, R.id.btnMore, R.id.btnSearchClose))
            findViewById<ImageButton>(id).imageTintList = tint
        findViewById<android.widget.ImageView>(R.id.searchIcon).imageTintList = tint
        findViewById<TextView>(R.id.title).setTextColor(Ui.TEXT)
        searchField.setTextColor(Ui.TEXT)
        searchField.setHintTextColor(Ui.TEXT3)
    }

    @Suppress("DEPRECATION")
    private fun setupInsets() {
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, ins ->
            val top: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = bars.top; insetBottom = bars.bottom
                topBar.setPadding(dp(12f) + bars.left, bars.top + dp(8f), dp(12f) + bars.right, 0)
                list.setPadding(bars.left, list.paddingTop, bars.right, list.paddingBottom)
            } else {
                top = ins.systemWindowInsetTop; insetBottom = ins.systemWindowInsetBottom
                topBar.setPadding(dp(12f), top + dp(8f), dp(12f), 0)
            }
            updateListPadding()
            ins
        }
        // список начинается под плавающей панелью и прокручивается под неё
        topBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateListPadding() }
    }

    private fun updateListPadding() {
        val scrimH = topBar.height + dp(28f)
        if (topScrim.layoutParams.height != scrimH) topScrim.post {
            topScrim.layoutParams = topScrim.layoutParams.apply { height = scrimH }
        }
        val top = topBar.height + dp(10f)
        val bottom = insetBottom + dp(16f) + (if (::selBar.isInitialized && selBar.visibility == View.VISIBLE) selBar.height + dp(12f) else 0) +
            (if (::qBar.isInitialized && qBar.visibility == View.VISIBLE) qBar.height + dp(12f) else 0)
        if (list.paddingTop != top || list.paddingBottom != bottom)
            list.post { list.setPadding(list.paddingLeft, top, list.paddingRight, bottom) }
    }

    private fun setupTopBar() {
        // вся панель, кроме кнопки ⋮, — поиск приложений: название, пустое место и лупа (тема — в меню ⋮)
        val toggleSearch = View.OnClickListener { showSearch(searchBox.visibility != View.VISIBLE) }
        findViewById<View>(R.id.bar).apply {
            setOnClickListener(toggleSearch)
            foreground = Ui.ripple(this@MainActivity, 32f)
        }
        findViewById<TextView>(R.id.title).setOnClickListener(toggleSearch)
        findViewById<View>(R.id.btnSearch).setOnClickListener { showSearch(searchBox.visibility != View.VISIBLE) }
        findViewById<View>(R.id.btnSearchClose).setOnClickListener { showSearch(false) }
        findViewById<View>(R.id.btnMore).setOnClickListener { Motion.from(it); showMenu(it) }
        // щелчок вибрацией на кнопках панели
        for (id in intArrayOf(R.id.bar, R.id.title, R.id.btnSearch, R.id.btnSearchClose, R.id.btnMore))
            Haptics.onClick(findViewById(id))
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty().trim(); render() }
        })
    }

    private fun showSearch(show: Boolean) {
        val imm = getSystemService(InputMethodManager::class.java)
        // строка поиска вытекает из кнопки-лупы и стекает обратно («жидкое стекло»)
        val lens = findViewById<View>(R.id.btnSearch)
        /** Сводка и фильтры на время поиска прячутся (сам заголовок списка при этом сжимается до нуля). */
        fun layoutFor(searchOn: Boolean) {
            summary.visibility = if (searchOn) View.GONE else View.VISIBLE
            chipsScroll.visibility = summary.visibility
            render()
            list.setSelection(0)
        }
        if (show) {
            Motion.openSearch(searchBox, lens) { layoutFor(true) }
            searchField.requestFocus()
            imm.showSoftInput(searchField, 0)
        } else {
            imm.hideSoftInputFromWindow(searchField.windowToken, 0)
            Motion.closeSearch(searchBox, lens) {
                searchField.setText("")
                layoutFor(false)
            }
        }
    }

    private fun buildHeader() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(this@MainActivity, 28f)
            setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        }
        box.addView(summary, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(12f); rightMargin = dp(12f); bottomMargin = dp(12f)
        })
        chipsBox = LinearLayout(this).apply { setPadding(dp(12f), 0, dp(12f), 0) }
        chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsBox)
        }
        box.addView(chipsScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
        list.addHeaderView(box, null, false)
    }

    // ---------- данные ----------

    private fun reload() {
        io.execute {
            val apps = Apps.load(this)
            History.record(this, apps)   // журнал «Что изменилось»: новые и пропавшие приложения
            main.post {
                if (isDestroyed) return@post
                val missingBefore = missingCount()
                installed = apps
                installedPkgs = apps.mapTo(HashSet()) { it.pkg }
                labels.clear()
                Icons.forgetMissing()
                render()
                autosave()
                // обновления с GitHub (раз в 6 часов на репозиторий) — в фоне
                val app = applicationContext
                Thread { if (Updates.check(app, apps)) main.post { refresh() } }.start()
                // последнее недостающее приложение установлено — предлагаем вернуться к своему списку
                if (queueWaiting) queueReturned()
                else if (missingBefore != null && missingBefore > 0 && missingCount() == 0) restoredDialog()
            }
        }
    }

    // ---------- резервные копии APK ----------

    /** Какие копии APK есть (пакет → файл) — чтобы при восстановлении ставить из копии. */
    private var backups: Map<String, String> = emptyMap()

    fun backupFor(pkg: String): String? = backups[pkg]

    /** Установленные сейчас приложения (пусто — ещё читаются). */
    fun installedApps(): List<AppInfo> = installed.orEmpty()

    /** Перечитать, какие копии есть (в фоне), и перерисовать список. */
    fun refreshBackups() {
        val app = applicationContext
        Thread {
            val b = runCatching { ApkBackup.index(app) }.getOrDefault(emptyMap())
            main.post { if (!isDestroyed) { backups = b; render() } }
        }.start()
    }

    /** Выбрать папку на телефоне для копий APK. */
    fun pickApkFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_APK_FOLDER)
    }

    // ---------- установить все недостающие по очереди ----------

    private var queue: List<AppInfo> = emptyList()
    private var qIndex = 0
    private var qDone = 0
    /** Открыли магазин / ссылку и ждём возвращения, чтобы проверить, установилось ли. */
    private var queueWaiting = false
    private lateinit var qBar: LinearLayout
    private lateinit var qTitle: TextView
    private lateinit var qSub: TextView
    private lateinit var qRetry: TextView

    private fun startQueue() {
        val snap = restore ?: return
        queue = ListFile.sorted(visible(snap.apps).filter { it.pkg !in installedPkgs })
        if (queue.isEmpty()) return
        qIndex = 0; qDone = 0
        if (!::qBar.isInitialized) buildQueueBar()
        openCurrent()
    }

    /** Открыть, откуда ставить текущее: своя ссылка или из каталога, иначе магазин. */
    private fun openCurrent() {
        val a = queue.getOrNull(qIndex) ?: return finishQueue()
        if (a.pkg in installedPkgs) { qIndex++; return openCurrent() }   // уже поставили вручную
        showQueueBar(waitingFor = true)
        queueWaiting = true
        val link = LinkStore.forApp(this, a.pkg).firstOrNull()?.first
        val backup = backupFor(a.pkg)
        // своя ссылка или из каталога → резервная копия → магазин
        when {
            link != null -> LinkStore.open(this, link, a.pkg, a.label)
            backup != null -> ApkInstaller.fromBackup(this, a.pkg, a.label, backup)
            else -> Store.open(this, a)
        }
    }

    /** Вернулись в AppShelf (список перечитан): установилось — дальше, нет — предложить «ещё раз» или «пропустить». */
    private fun queueReturned() {
        queueWaiting = false
        val a = queue.getOrNull(qIndex) ?: return finishQueue()
        if (a.pkg in installedPkgs) {
            qDone++; qIndex++
            Haptics.play(Haptics.Kind.SUCCESS)
            if (qIndex >= queue.size) return finishQueue()
            showQueueBar(waitingFor = false, justInstalled = a)
            main.postDelayed({ if (::qBar.isInitialized && qBar.visibility == View.VISIBLE && !queueWaiting) openCurrent() }, 900)
        } else showQueueBar(waitingFor = false, notInstalled = a)
    }

    private fun finishQueue() {
        val total = queue.size
        stopQueue()
        if (total > 0) Toast.makeText(this, getString(R.string.q_done, qDone, total), Toast.LENGTH_LONG).show()
        render()
    }

    private fun stopQueue() {
        queueWaiting = false
        queue = emptyList()
        if (::qBar.isInitialized) { qBar.visibility = View.GONE; updateListPadding() }
    }

    /** Стеклянная панель снизу: «Установка 3 из 31 — Telegram» и «Ещё раз / Пропустить / Стоп». */
    private fun buildQueueBar() {
        val root = findViewById<android.widget.FrameLayout>(R.id.root)
        qBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(14f))
            background = GlassDrawable(this@MainActivity, 28f, Ui.withAlpha(Ui.mix(Ui.base, Ui.surface, 0.6f), 0.92f))
            elevation = Ui.dp(this@MainActivity, 8f)
            visibility = View.GONE
            isClickable = true
        }
        qTitle = text(16f, Ui.TEXT, Ui.medium)
        qSub = text(13.5f, Ui.TEXT2).apply { setPadding(0, dp(2f), 0, 0) }
        qBar.addView(qTitle)
        qBar.addView(qSub)
        val row = LinearLayout(this).apply { isBaselineAligned = false }
        fun act(label: String, filled: Boolean, block: () -> Unit) = button(label, filled, block).also {
            row.addView(it, LinearLayout.LayoutParams(0, dp(42f), 1f).apply { if (row.childCount > 0) leftMargin = dp(8f) })
        }
        qRetry = act(getString(R.string.q_retry), true) { openCurrent() }
        act(getString(R.string.q_skip), false) { qIndex++; openCurrent() }
        act(getString(R.string.q_stop), false) { finishQueue() }
        qBar.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        root.addView(qBar, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(12f); rightMargin = dp(12f); bottomMargin = insetBottom + dp(12f)
        })
        qBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateListPadding() }
    }

    private fun showQueueBar(waitingFor: Boolean, justInstalled: AppInfo? = null, notInstalled: AppInfo? = null) {
        val a = queue.getOrNull(qIndex)
        qTitle.text = getString(R.string.q_progress, (qIndex + 1).coerceAtMost(queue.size), queue.size)
        qSub.setTextColor(if (notInstalled != null) Ui.WARN else Ui.TEXT2)
        qSub.text = when {
            justInstalled != null -> getString(R.string.q_installed_next, justInstalled.label, a?.label.orEmpty())
            notInstalled != null -> getString(R.string.q_not_installed, notInstalled.label)
            else -> a?.let { getString(R.string.q_installing, it.label) }.orEmpty()
        }
        qRetry.text = getString(if (notInstalled != null) R.string.q_retry else R.string.q_open)
        val lp = qBar.layoutParams as android.widget.FrameLayout.LayoutParams
        if (lp.bottomMargin != insetBottom + dp(12f)) { lp.bottomMargin = insetBottom + dp(12f); qBar.layoutParams = lp }
        if (qBar.visibility != View.VISIBLE) { qBar.visibility = View.VISIBLE; updateListPadding() }
    }

    // ---------- выбор нескольких приложений ----------

    private fun toggleSelected(pkg: String) {
        if (!selected.remove(pkg)) selected += pkg
        Haptics.play(Haptics.Kind.TICK)
        render()
    }

    private fun clearSelection() {
        selected.clear()
        render()
    }

    /** Стеклянная панель снизу: «Выбрано: N» и действия с отмеченными приложениями. */
    private fun buildSelectionBar() {
        val root = findViewById<android.widget.FrameLayout>(R.id.root)
        selBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(14f))
            background = GlassDrawable(this@MainActivity, 28f, Ui.withAlpha(Ui.mix(Ui.base, Ui.surface, 0.6f), 0.92f))
            elevation = Ui.dp(this@MainActivity, 8f)
            visibility = View.GONE
            isClickable = true   // касания не проходят к списку под панелью
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        selTitle = text(16f, Ui.TEXT, Ui.medium)
        head.addView(selTitle, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply {
            text = "✕"; textSize = 18f; setTextColor(Ui.TEXT2); gravity = Gravity.CENTER
            background = Ui.ripple(this@MainActivity, 100f)
            setOnClickListener { clearSelection() }
            Haptics.onClick(this)
        }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        selBar.addView(head)
        val row = LinearLayout(this).apply { isBaselineAligned = false }
        fun act(label: String, filled: Boolean, block: () -> Unit) = button(label, filled, block).also {
            row.addView(it, LinearLayout.LayoutParams(0, dp(42f), 1f).apply { if (row.childCount > 0) leftMargin = dp(8f) })
        }
        act(getString(R.string.sel_all), false) {
            selected.addAll(shownApps.map { it.pkg }); render()
        }
        selExclude = act(getString(R.string.sel_exclude), false) {
            val excl = prefs.excluded
            // все отмеченные уже убраны — возвращаем, иначе убираем
            setExcluded(if (selected.all { it in excl }) excl - selected else excl + selected)
        }
        act(getString(R.string.sel_share), false) { shareSelected() }
        act(getString(R.string.sel_uninstall), true) { uninstallSelected() }
        selBar.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
        root.addView(selBar, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(12f); rightMargin = dp(12f); bottomMargin = insetBottom + dp(12f)
        })
        selBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateListPadding() }
    }

    /** Показать или спрятать панель выбора и обновить её надписи. */
    private fun updateSelectionBar() {
        if (!::selBar.isInitialized) return
        val show = selecting && restore == null
        if (show) {
            selTitle.text = getString(R.string.sel_count, selected.size)
            selExclude.text = getString(if (selected.all { it in prefs.excluded }) R.string.sel_include else R.string.sel_exclude)
            val lp = selBar.layoutParams as android.widget.FrameLayout.LayoutParams
            if (lp.bottomMargin != insetBottom + dp(12f)) { lp.bottomMargin = insetBottom + dp(12f); selBar.layoutParams = lp }
        }
        if ((selBar.visibility == View.VISIBLE) != show) {
            selBar.visibility = if (show) View.VISIBLE else View.GONE
            updateListPadding()
        }
    }

    private fun selectedApps() = installed.orEmpty().filter { it.pkg in selected }

    /** Поделиться отмеченными: текстом, со ссылками и заметками. */
    private fun shareSelected() {
        val apps = ListFile.sorted(selectedApps())
        if (apps.isEmpty()) return
        val text = apps.joinToString("\n") { a ->
            val links = LinkStore.get(this, a.pkg).joinToString(" ") { it.url }
            val note = LinkStore.note(this, a.pkg)
            "• ${a.label} — ${a.pkg} — ${sourceText(a)}" + (if (links.isNotEmpty()) " — $links" else "") +
                (if (note.isNotEmpty()) " — $note" else "")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_list)))
    }

    /** Удалить отмеченные: одно подтверждение здесь, дальше Android спрашивает про каждое по очереди. */
    private fun uninstallSelected() {
        val apps = ListFile.sorted(selectedApps()).filter { !it.system && it.pkg != packageName }
        if (apps.isEmpty()) { Toast.makeText(this, R.string.sel_nothing_to_remove, Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.sel_uninstall_title, apps.size, apps.size))
            .setMessage(apps.joinToString(", ") { it.label } + "\n\n" + getString(R.string.sel_uninstall_text))
            .setPositiveButton(R.string.sel_uninstall) { _, _ ->
                ApkInstaller.uninstallAll(this, apps.map { it.pkg to it.label })
                clearSelection()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }

    private fun needsLink(a: AppInfo) = a.source == Source.APK && LinkStore.forApp(this, a.pkg).isEmpty()

    /** Обновить список и сводку (например, после изменения ссылок). */
    fun refresh() { if (!isDestroyed) render() }

    /** В фоне: каталог ссылок (раз в день или [catalogAge]) и свои ссылки с WebDAV; потом — перерисовать. */
    private fun refreshLinks(catalogAge: Long = 24 * 60 * 60 * 1000L, forceDav: Boolean = false) {
        val app = applicationContext
        Thread {
            val a = runCatching { LinkStore.refreshCatalog(app, catalogAge) }.getOrDefault(false)
            val p = Prefs(app)
            val b = if (p.davUrl.isNotEmpty() && (forceDav || p.linksDirty || System.currentTimeMillis() - p.linksSynced > 6 * 60 * 60 * 1000L))
                runCatching { LinkStore.syncDav(app); true }.getOrDefault(false) else false
            if (a || b) main.post { refresh() }
        }.start()
    }

    /** Сколько приложений из открытого списка не установлено; null — список не открыт. */
    private fun missingCount(): Int? = restore?.let { s -> visible(s.apps).count { it.pkg !in installedPkgs } }

    private fun closeRestore() {
        stopQueue()
        restore = null; missingOnly = false; filter = null; noLink = false; onlyUpdates = false
        list.setSelection(0)
        render()
    }

    private fun restoredDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restored_title)
            .setMessage(getString(R.string.restored_text, current()?.size ?: 0))
            .setPositiveButton(R.string.back_to_mine) { _, _ -> closeRestore() }
            .setNegativeButton(R.string.stay, null)
            .show().also { Ui.glassDialog(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // «назад»: сначала закрыть поиск, потом выйти из восстановления, потом — из приложения
        when {
            selecting -> clearSelection()
            searchBox.visibility == View.VISIBLE -> showSearch(false)
            restore != null -> closeRestore()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    /** Приложения, которые мы показываем и сохраняем: без системных, если они скрыты. */
    private fun visible(apps: List<AppInfo>) = if (prefs.showSystem) apps else apps.filter { !it.system }

    private fun current(): List<AppInfo>? = restore?.apps?.let { visible(it) } ?: installed?.let { visible(it) }

    private fun render() {
        Liquid.version++
        renderSummary()
        val apps = current() ?: run { adapter.update(emptyList()); renderChips(emptyList()); return }
        renderChips(apps)
        val restoring = restore != null
        var shown = apps
        // при поиске фильтры не видны — ищем среди всех
        if (!searching) {
            filter?.let { f -> shown = shown.filter { it.source == f } }
            if (!restoring && noLink) shown = shown.filter { needsLink(it) }
            if (!restoring && onlyUpdates) shown = shown.filter { Updates.available(this, it) != null }
        }
        if (restoring && missingOnly && !searching) shown = shown.filter { it.pkg !in installedPkgs }
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            // ищем и по названию, и по пакету, и по своим заметкам
            shown = shown.filter { q in it.label.lowercase() || q in it.pkg.lowercase() || q in LinkStore.note(this, it.pkg).lowercase() }
        }
        shownApps = if (restoring) emptyList() else shown
        // отмеченные, которых больше нет (удалили), из выбора убираем
        installed?.let { all -> val have = all.mapTo(HashSet()) { it.pkg }; selected.retainAll(have) }
        updateSelectionBar()
        val items = ArrayList<Any>()
        val excl = prefs.excluded
        var section = ""
        for (a in ListFile.sorted(shown)) {
            val s = ListFile.section(a.label)
            if (s != section) { section = s; items += s }
            val missing = restoring && a.pkg !in installedPkgs
            items += Row(a, sourceText(a), dateText(a.firstInstall), if (restoring) !missing else null,
                excluded = !restoring && a.pkg in excl, selected = a.pkg in selected,
                update = if (restoring) null else Updates.available(this, a)?.let { Updates.numbers(it.tag).joinToString(".").ifEmpty { it.tag } },
                link = if (missing) LinkStore.forApp(this, a.pkg).firstOrNull()?.let { getString(Links.kind(it.first.url).title) }
                    ?: backupFor(a.pkg)?.let { getString(R.string.bk_row) } else null,
                note = LinkStore.note(this, a.pkg))
        }
        if (items.isEmpty()) items += getString(
            if (restoring && missingOnly && query.isEmpty() && filter == null) R.string.all_installed else R.string.nothing_found)
        adapter.update(items)
    }

    /** Источник словами: «Google Play», «APK · через Telegram», название чужого установщика. */
    fun sourceText(a: AppInfo): String = when (a.source) {
        Source.APK -> if (a.initiator.isNotEmpty()) getString(R.string.src_apk_via, label(a.initiator)) else getString(R.string.src_apk)
        Source.OTHER -> label(a.installer)
        else -> getString(a.source.title)
    }

    private fun label(pkg: String) = labels.getOrPut(pkg) { Apps.labelOf(this, pkg) }

    private val dayFmt by lazy { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    private val timeFmt by lazy { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    fun dateText(ms: Long) = if (ms <= 0) "" else dayFmt.format(Date(ms))

    // ---------- шапка: сводка и фильтры ----------

    private fun text(size: Float, color: Int, face: android.graphics.Typeface = Ui.regular) = TextView(this).apply {
        textSize = size; setTextColor(color); typeface = face; setLineSpacing(0f, 1.1f)
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Ui.medium
        gravity = Gravity.CENTER
        setPadding(dp(14f), 0, dp(14f), 0)
        // одна строка: если не влезает — шрифт уменьшается, а не переносится и обрезается
        maxLines = 1
        setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        setTextColor(if (filled) Ui.ON_ACCENT else Ui.primary)
        background = if (filled) Ui.pill(this@MainActivity, Ui.primary)
        else Ui.pill(this@MainActivity, Ui.withAlpha(Ui.primary, 0.12f), Ui.withAlpha(Ui.primary, 0.35f))
        foreground = Ui.ripple(this@MainActivity, 100f)
        setOnClickListener { Motion.from(it); onClick() }   // окно, которое откроет кнопка, вытечет из неё
        Haptics.onClick(this)
    }

    private fun renderSummary() {
        summary.removeAllViews()
        val snap = restore
        val apps = current()
        if (apps == null) {
            summary.addView(text(15f, Ui.TEXT2).apply { setText(R.string.loading) })
            return
        }
        val n = apps.size
        val big = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
        if (snap == null) {
            big.addView(text(44f, Ui.primary, Ui.bold).apply { text = n.toString() })
            big.addView(text(18f, Ui.TEXT2, Ui.medium).apply {
                text = resources.getQuantityString(R.plurals.apps, n)
                setPadding(dp(8f), 0, 0, dp(8f))
            })
            summary.addView(big)
            // самые частые источники одной строкой
            val bySource = apps.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }
            summary.addView(text(14f, Ui.TEXT2).apply {
                // неразрывные пробелы: «F-Droid 14» не разрывается на две строки
                text = bySource.take(4).joinToString(" · ") { (getString(it.key.title) + " " + it.value).replace(' ', '\u00A0') } +
                    if (bySource.size > 4) " · " + getString(R.string.more_sources, bySource.size - 4) else ""
                setPadding(0, dp(2f), 0, dp(12f))
            })
            val excl = prefs.excluded
            val nExcl = apps.count { it.pkg in excl }
            if (nExcl > 0) summary.addView(text(13.5f, Ui.primary, Ui.medium).apply {
                text = getString(R.string.excluded_line, nExcl)
                setPadding(0, 0, 0, dp(10f))
                setOnClickListener { selectApps() }
            })
            val saved = prefs.lastSaved
            summary.addView(text(13.5f, if (saved > 0) Ui.TEXT3 else Ui.hintText).apply {
                text = if (saved > 0) getString(R.string.saved_at, timeFmt.format(Date(saved)), prefs.lastSavedName)
                else getString(R.string.never_saved)
                if (saved <= 0) {
                    background = GlassDrawable(this@MainActivity, 16f, Ui.hintFill)
                    setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(if (syncLine() != null) 6f else 14f) })
            syncLine()?.let { (line, bad) ->
                summary.addView(text(13.5f, if (bad) Ui.WARN else Ui.TEXT3).apply {
                    text = line
                    setOnClickListener { SyncDialog.show(this@MainActivity) }
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14f) })
            }
            // без выравнивания по тексту: у кнопки с уменьшенным шрифтом базовая линия ниже, и её бы сдвинуло и обрезало
            summary.addView(button(getString(R.string.save_restore), true) { SaveDialog.show(this) },
                LinearLayout.LayoutParams(-1, dp(44f)))
        } else {
            val missing = apps.count { it.pkg !in installedPkgs }
            summary.addView(text(13f, Ui.primary, Ui.medium).apply { setText(R.string.restore_title) })
            big.addView(text(40f, if (missing > 0) Ui.WARN else Ui.OK, Ui.bold).apply { text = missing.toString() })
            big.addView(text(17f, Ui.TEXT2, Ui.medium).apply {
                text = getString(R.string.restore_missing, n)
                setPadding(dp(8f), 0, 0, dp(7f))
            })
            summary.addView(big)
            summary.addView(text(13.5f, Ui.TEXT3).apply {
                text = listOfNotNull(
                    snap.created.takeIf { it > 0 }?.let { getString(R.string.restore_from, dayFmt.format(Date(it))) },
                    snap.device.takeIf { it.isNotEmpty() },
                ).joinToString(" · ").ifEmpty { getString(R.string.restore_hint) }
                setPadding(0, dp(2f), 0, dp(14f))
            })
            // без выравнивания по тексту: у кнопки с уменьшенным шрифтом базовая линия ниже, и её бы сдвинуло и обрезало
            if (missing == 0) {
                // всё восстановлено — одна понятная кнопка обратно
                summary.addView(button(getString(R.string.back_to_mine), true) { closeRestore() }, LinearLayout.LayoutParams(-1, dp(44f)))
                return
            }
            // главное — поставить всё недостающее по очереди
            summary.addView(button(getString(R.string.q_start, missing), true) { startQueue() },
                LinearLayout.LayoutParams(-1, dp(44f)).apply { bottomMargin = dp(10f) })
            val buttons = LinearLayout(this).apply { isBaselineAligned = false }
            buttons.addView(button(getString(if (missingOnly) R.string.show_all else R.string.show_missing), false) {
                missingOnly = !missingOnly; render()
            }, LinearLayout.LayoutParams(0, dp(44f), 1f))
            buttons.addView(button(getString(R.string.close_list), false) { closeRestore() },
                LinearLayout.LayoutParams(0, dp(44f), 1f).apply { leftMargin = dp(10f) })
            summary.addView(buttons)
        }
    }

    /** Строка про WebDAV в сводке: ошибка последней отправки или когда следующая; null — расписания нет. */
    fun syncLine(): Pair<String, Boolean>? {
        if (prefs.syncLast > 0 && !prefs.syncOk && prefs.davUrl.isNotEmpty())
            return getString(R.string.dav_line_fail, timeFmt.format(Date(prefs.syncLast)), prefs.syncMsg) to true
        if (prefs.syncPending && Sync.enabled(prefs)) return getString(R.string.dav_line_pending) to false
        val next = prefs.syncNext.takeIf { Sync.enabled(prefs) && it > 0 } ?: return null
        return getString(R.string.dav_line_next, nextFmt.format(Date(next))) to false
    }

    private val nextFmt by lazy { SimpleDateFormat("EEE, d MMM, HH:mm", Locale.getDefault()) }

    fun refreshSummary() { if (!isDestroyed) renderSummary() }

    /** Открыть список (из файла или с сервера) в режиме восстановления. */
    fun showSnapshot(s: Snapshot) {
        // ссылки из списка — к своим; и свежий каталог / ссылки с сервера, раз уж настраиваем телефон
        LinkStore.import(this, s.links)
        refreshLinks(catalogAge = 60 * 60 * 1000L, forceDav = true)
        refreshBackups()   // для каких недостающих есть резервные копии APK
        restore = s
        missingOnly = s.apps.any { it.pkg !in installedPkgs }
        filter = null
        list.setSelection(0)
        render()
    }

    private fun renderChips(apps: List<AppInfo>) {
        chipsBox.removeAllViews()
        if (apps.isEmpty()) return
        val counts = apps.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }
        if (filter != null && counts.none { it.key == filter }) filter = null
        fun chip(label: String, sel: Boolean, dot: Int?, onClick: () -> Unit) = chipsBox.addView(TextView(this).apply {
            text = label
            textSize = 13.5f
            typeface = Ui.medium
            gravity = Gravity.CENTER
            setPadding(dp(if (dot != null) 12f else 16f), 0, dp(16f), 0)
            setTextColor(if (sel) Ui.ON_ACCENT else Ui.TEXT)
            background = if (sel) Ui.pill(this@MainActivity, Ui.primary) else Ui.pill(this@MainActivity, Ui.card, Ui.ink(0x33))
            foreground = Ui.ripple(this@MainActivity, 100f)
            if (dot != null) {
                val d = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(dot); setSize(dp(8f), dp(8f))
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
                compoundDrawablePadding = dp(8f)
            }
            setOnClickListener { onClick() }
            Haptics.onClick(this, Haptics.Kind.TICK)
        }, LinearLayout.LayoutParams(-2, dp(36f)).apply { rightMargin = dp(8f) })
        chip(getString(R.string.all) + " · " + apps.size, filter == null && !noLink && !onlyUpdates, null) {
            filter = null; noLink = false; onlyUpdates = false; render()
        }
        // обновления с GitHub — первым делом, если есть
        val upd = if (restore == null) apps.count { Updates.available(this, it) != null } else 0
        if (upd > 0 || onlyUpdates) chip(getString(R.string.upd_chip) + " · " + upd, onlyUpdates, Ui.primary) {
            onlyUpdates = !onlyUpdates; render()
        }
        for ((src, count) in counts) {
            chip(getString(src.title) + " · " + count, filter == src, src.color) {
                filter = if (filter == src) null else src; render()
            }
        }
        // приложения из APK, для которых неизвестно, где их потом взять
        val without = if (restore == null) apps.count { needsLink(it) } else 0
        if (without > 0 || noLink) chip(getString(R.string.no_link_chip) + " · " + without, noLink, null) {
            noLink = !noLink; render()
        }
    }

    // ---------- сохранение ----------

    private fun snapshot(): Snapshot? = installed?.let { Apps.snapshot(this, it, prefs) }

    fun isExcluded(pkg: String) = pkg in prefs.excluded

    fun toggleExcluded(pkg: String) {
        val excl = prefs.excluded
        setExcluded(if (pkg in excl) excl - pkg else excl + pkg)
    }

    /** Включить или исключить приложение из сохраняемого списка; файл автосохранения сразу обновляется. */
    fun setExcluded(pkgs: Set<String>) {
        prefs.excluded = pkgs
        render()
        autosave()
    }

    /** Выбор приложений для списка; [then] — что сделать после «Готово» (например, вернуться к сохранению). */
    /** Сколько приложений войдёт в список и сколько всего; null — ещё загружаются. */
    fun includedCount(): Pair<Int, Int>? {
        val all = installed?.let { visible(it) } ?: return null
        val excl = prefs.excluded
        return all.count { it.pkg !in excl } to all.size
    }

    /** Файл автосохранения; null — выключено. */
    fun autosaveName(): String? = prefs.autosaveUri.takeIf { it.isNotEmpty() }?.let { fileName(Uri.parse(it)) }

    fun selectApps(then: (() -> Unit)? = null) {
        val apps = installed?.let { ListFile.sorted(visible(it)) } ?: return
        SelectDialog.show(this, apps, prefs.excluded) { excl -> setExcluded(excl); then?.invoke() }
    }

    private fun sourceName(s: Source) = getString(s.title)

    /** Сохранить в файл: выбор формата, затем системный выбор места. */
    fun saveToFile() {
        val formats = Format.entries
        if (installed == null) return
        val names = arrayOf(getString(R.string.fmt_json), getString(R.string.fmt_md), getString(R.string.fmt_csv))
        AlertDialog.Builder(this)
            .setTitle(R.string.save_file)
            .setItems(names) { _, i ->
                pendingFormat = formats[i]
                val day = ListFile.date(System.currentTimeMillis())
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = pendingFormat.mime
                    putExtra(Intent.EXTRA_TITLE, "AppShelf-$day.${pendingFormat.ext}")
                }, REQ_SAVE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }

    /** Свои ссылки в формате каталога (sources.json) — чтобы перенести их в репозиторий каталога. */
    fun exportLinks() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Format.JSON.mime
            putExtra(Intent.EXTRA_TITLE, "sources.json")
        }, REQ_LINKS)
    }

    fun share() {
        val s = snapshot() ?: return
        val text = ListFile.write(s, Format.MARKDOWN, ::sourceName)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AppShelf — " + ListFile.date(s.created))
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_list)))
    }

    /** Записать список в файл [uri] в фоне; [done] получает ошибку или null. */
    private fun writeTo(uri: Uri, f: Format, done: (Exception?) -> Unit) {
        val s = snapshot() ?: return done(IllegalStateException("not loaded"))
        io.execute {
            val err = try {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(ListFile.write(s, f, ::sourceName).toByteArray()) }
                null
            } catch (e: Exception) {
                e
            }
            main.post {
                if (err == null) {
                    prefs.lastSaved = s.created
                    prefs.lastSavedName = fileName(uri)
                    if (!isDestroyed) renderSummary()
                }
                done(err)
            }
        }
    }

    private fun fileName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    } catch (e: Exception) {
        uri.lastPathSegment.orEmpty()
    }

    /** Автосохранение: при каждом запуске список заново записывается в выбранный файл. */
    private fun autosave() {
        val u = prefs.autosaveUri.takeIf { it.isNotEmpty() } ?: return
        writeTo(Uri.parse(u), Format.JSON) { err ->
            if (err != null) {
                prefs.autosaveUri = ""
                Toast.makeText(this, R.string.autosave_failed, Toast.LENGTH_LONG).show()
                if (!isDestroyed) renderSummary()
            }
        }
    }

    fun autosaveDialog() {
        val on = prefs.autosaveUri.isNotEmpty()
        val b = AlertDialog.Builder(this).setTitle(R.string.autosave)
        if (on) {
            b.setMessage(getString(R.string.autosave_is_on, fileName(Uri.parse(prefs.autosaveUri))))
                .setPositiveButton(R.string.autosave_disable) { _, _ ->
                    try {
                        contentResolver.releasePersistableUriPermission(Uri.parse(prefs.autosaveUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    } catch (e: Exception) {}
                    prefs.autosaveUri = ""
                    renderSummary()
                }
                .setNeutralButton(R.string.autosave_other) { _, _ -> pickAutosave() }
        } else {
            b.setMessage(R.string.autosave_explain).setPositiveButton(R.string.autosave_choose) { _, _ -> pickAutosave() }
        }
        b.setNegativeButton(android.R.string.cancel, null).show().also { Ui.glassDialog(it) }
    }

    private fun pickAutosave() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Format.JSON.mime
            putExtra(Intent.EXTRA_TITLE, "AppShelf.json")
        }, REQ_AUTOSAVE)
    }

    fun openList() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/csv", "text/comma-separated-values",
                "text/plain", "application/octet-stream"))
        }, REQ_OPEN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_SAVE -> writeTo(uri, pendingFormat) { err ->
                Haptics.play(if (err == null) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                Toast.makeText(this, if (err == null) getString(R.string.saved_to, fileName(uri))
                else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
            }
            REQ_AUTOSAVE -> {
                try {
                    contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // провайдер не даёт постоянный доступ — запишем хотя бы сейчас
                }
                prefs.autosaveUri = uri.toString()
                writeTo(uri, Format.JSON) { err ->
                    Toast.makeText(this, if (err == null) getString(R.string.autosave_enabled, fileName(uri))
                    else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
                    if (err != null) prefs.autosaveUri = ""
                    renderSummary()
                }
            }
            REQ_APK_FOLDER -> {
                try {
                    contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: SecurityException) {}
                prefs.apkFolderUri = uri.toString()
                prefs.apkDest = ApkBackup.Dest.FOLDER.name
                ApkBackupDialog.show(this)
            }
            REQ_LINKS -> io.execute {
                val err = try {
                    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(LinkStore.catalogExport(this).toByteArray()) }
                    null
                } catch (e: Exception) {
                    e
                }
                main.post {
                    Toast.makeText(this, if (err == null) getString(R.string.saved_to, fileName(uri))
                    else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
                }
            }
            REQ_OPEN -> io.execute {
                val result = try {
                    val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    ListFile.read(text)
                } catch (e: Exception) {
                    null
                }
                main.post {
                    if (result == null || result.apps.isEmpty()) {
                        Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
                        return@post
                    }
                    showSnapshot(result)
                }
            }
        }
    }

    // ---------- меню ----------

    /** Меню ⋮ — стеклянное окно под кнопкой (системное всплывающее меню стеклом не сделать). */
    private fun showMenu(anchor: View) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
        }
        val dialog = AlertDialog.Builder(this).setView(box).create()
        fun item(title: Int, checked: Boolean? = null, action: () -> Unit) = box.addView(TextView(this).apply {
            setText(title)
            textSize = 16f
            setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52f)
            setPadding(dp(18f), 0, dp(18f), 0)
            background = Ui.ripple(this@MainActivity, 16f)
            if (checked != null) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                    if (checked) getDrawable(android.R.drawable.checkbox_on_background) else getDrawable(android.R.drawable.checkbox_off_background), null)
                compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (checked) Ui.primary else Ui.TEXT3)
            }
            setOnClickListener { Ui.chain(dialog) { action() } }
        }, LinearLayout.LayoutParams(-1, -2))
        item(R.string.hi_title) { HistoryDialog.show(this) }
        item(R.string.catalog_title) { CatalogDialog.show(this) }
        item(R.string.show_system, prefs.showSystem) { prefs.showSystem = !prefs.showSystem; render() }
        item(R.string.theme) { themeDialog() }
        item(R.string.haptics) { hapticsDialog() }
        item(R.string.about) { about() }
        dialog.show()
        Ui.glassDialog(dialog)
        dialog.window?.apply {
            val at = IntArray(2)
            anchor.getLocationOnScreen(at)
            setGravity(Gravity.TOP or Gravity.END)
            attributes = attributes.apply {
                width = dp(270f)
                x = dp(10f)
                y = at[1] + anchor.height - dp(4f)
            }
            setDimAmount(0.15f)
        }
    }

    /** Тема: цвета (список) и галочка «Жидкое стекло» — эффект поверх любой темы, с настройкой прозрачности. */
    private fun themeDialog() {
        val themes = Theme.entries
        val tint = android.content.res.ColorStateList.valueOf(Ui.primary)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(6f), dp(20f), 0)
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.theme)
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton(R.string.close, null)
            .apply { if (Ui.liquid) setNeutralButton(R.string.glass_clarity_btn) { _, _ -> clarityDialog() } }
            .create()
        val group = android.widget.RadioGroup(this)
        for (t in themes) group.addView(android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            setText(t.title); textSize = 16f; setTextColor(Ui.TEXT); buttonTintList = tint
            minHeight = dp(48f)
            isChecked = t == prefs.theme()
            setOnClickListener { dialog.dismiss(); if (t != prefs.theme()) changeTheme(t) }
        })
        box.addView(group)
        box.addView(View(this).apply { setBackgroundColor(Ui.ink(0x22)) },
            LinearLayout.LayoutParams(-1, dp(1f)).apply { topMargin = dp(8f); bottomMargin = dp(8f) })
        val works = Liquid.works
        box.addView(android.widget.CheckBox(this).apply {
            setText(R.string.th_liquid); textSize = 16f; setTextColor(Ui.TEXT); buttonTintList = tint
            isChecked = works && prefs.liquidGlass
            isEnabled = works
            setOnCheckedChangeListener { _, on ->
                prefs.liquidGlass = on
                dialog.dismiss()
                Ui.apply(this@MainActivity, prefs.theme())
                recreate()
            }
        })
        box.addView(TextView(this).apply {
            setText(if (works) R.string.liquid_sub else R.string.liquid_unavailable)
            textSize = 13f; setTextColor(Ui.TEXT3); setLineSpacing(0f, 1.1f)
            setPadding(dp(32f), 0, 0, dp(8f))
        })
        dialog.show()
        Ui.glassDialog(dialog)
    }

    /** Вибрация: сила отклика или «Выключена»; при выборе сразу проигрывается пример. */
    private fun hapticsDialog() {
        val levels = Haptics.Level.entries
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(6f), dp(20f), 0)
        }
        val tint = android.content.res.ColorStateList.valueOf(Ui.primary)
        val group = android.widget.RadioGroup(this)
        for (l in levels) group.addView(android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            setText(l.title); textSize = 16f; setTextColor(Ui.TEXT); buttonTintList = tint
            minHeight = dp(48f)
            isChecked = l == Haptics.level()
            isEnabled = Haptics.available() || l == Haptics.Level.OFF
            setOnClickListener {
                Haptics.setLevel(this@MainActivity, l)
                // пример: щелчок и «открытие окна»
                Haptics.play(Haptics.Kind.TAP)
                main.postDelayed({ Haptics.play(Haptics.Kind.OPEN) }, 220)
            }
        })
        box.addView(group)
        // как уровень работает на этом телефоне: у эффектов производителя сила одна — уровень меняет, на что откликаться
        val how = TextView(this)
        fun explain() {
            how.text = if (!Haptics.available()) getString(R.string.hap_none) else getString(when (Haptics.effective()) {
                Haptics.Engine.EFFECTS -> R.string.hap_how_effects
                Haptics.Engine.PRIMITIVES -> R.string.hap_how_primitives
                else -> if (Haptics.amplitude()) R.string.hap_how_simple else R.string.hap_how_simple_fixed
            })
        }
        explain()
        // способ вибрации — с пометкой, что поддерживает этот телефон; при выборе — пример
        if (Haptics.available()) {
            box.addView(TextView(this).apply {
                setText(R.string.hap_engine); textSize = 14f; setTextColor(Ui.primary); typeface = Ui.medium
                setPadding(0, dp(12f), 0, dp(2f))
            })
            val engines = android.widget.RadioGroup(this)
            for (e in Haptics.Engine.entries) engines.addView(android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                val ok = Haptics.supports(e)
                text = getString(e.title) + if (ok) "" else " — " + getString(R.string.hap_unsupported)
                textSize = 15f; setTextColor(if (ok) Ui.TEXT else Ui.TEXT3); buttonTintList = tint
                minHeight = dp(44f)
                isEnabled = ok
                isChecked = e == Haptics.engineChoice()
                setOnClickListener {
                    Haptics.setEngine(this@MainActivity, e)
                    explain()
                    Haptics.play(Haptics.Kind.TAP)
                    main.postDelayed({ Haptics.play(Haptics.Kind.OPEN) }, 220)
                }
            })
            box.addView(engines)
        }
        box.addView(how.apply {
            textSize = 13f; setTextColor(Ui.TEXT3); setLineSpacing(0f, 1.1f)
            setPadding(0, dp(6f), 0, dp(4f))
        })
        AlertDialog.Builder(this).setTitle(R.string.haptics).setView(box)
            .setPositiveButton(R.string.done, null)
            .show().also { Ui.glassDialog(it) }
    }

    /** Прозрачность стекла: ползунок «матовое — прозрачное»; само окно меняется сразу, экран — при закрытии. */
    private fun clarityDialog() {
        val start = prefs.glassClarity
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(8f), dp(24f), 0)
        }
        val value = TextView(this).apply { textSize = 14f; setTextColor(Ui.TEXT2) }
        box.addView(TextView(this).apply {
            setText(R.string.glass_clarity_text); textSize = 14f; setTextColor(Ui.TEXT2); setLineSpacing(0f, 1.1f)
        })
        val seek = android.widget.SeekBar(this).apply {
            max = 100
            progress = start
            progressTintList = android.content.res.ColorStateList.valueOf(Ui.primary)
            thumbTintList = progressTintList
        }
        box.addView(seek, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })
        box.addView(LinearLayout(this).apply {
            addView(TextView(this@MainActivity).apply { setText(R.string.glass_matte); textSize = 13f; setTextColor(Ui.TEXT3) },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply { setText(R.string.glass_clear); textSize = 13f; setTextColor(Ui.TEXT3) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
        value.setPadding(0, dp(10f), 0, 0)
        box.addView(value)
        fun showValue(p: Int) { value.text = getString(R.string.glass_clarity_value, p) }
        showValue(start)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.glass_clarity)
            .setView(box)
            .setPositiveButton(R.string.done, null)
            .setNeutralButton(R.string.catalog_default) { _, _ -> }
            .create()
        /** Применить: заново размыть снимок экрана и перерисовать стекло этого окна. */
        // размытие считается в фоне из готового снимка; пока считается — новые значения копятся, берётся последнее
        var blurring = false
        var blurAgain = false
        fun reblur() {
            if (blurring) { blurAgain = true; return }
            blurring = true
            val app = applicationContext
            Thread {
                val bmp = Ui.reblurredSnapshot(app)
                main.post {
                    blurring = false
                    if (bmp != null && dialog.isShowing) {
                        Ui.snapshot = bmp
                        dialog.window?.decorView?.invalidate()
                    }
                    if (blurAgain) { blurAgain = false; reblur() }
                }
            }.start()
        }
        /** Применить сразу: заливка окна — мгновенно, размытие того, что за ним, — догоняет в фоне. */
        fun apply(p: Int) {
            prefs.glassClarity = p
            Ui.setClarity(p / 100f)
            dialog.window?.setBackgroundDrawable(GlassDrawable(this, 28f, Ui.dialogBlurColor()))
            reblur()
        }
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                showValue(p)
                if (fromUser && p % 5 == 0) Haptics.play(Haptics.Kind.TICK)
                if (fromUser) apply(p)
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })
        // при закрытии — пересоздать экран, чтобы панель и карточки взяли новую прозрачность
        dialog.setOnDismissListener { if (prefs.glassClarity != start) recreate() }
        dialog.show()
        Ui.glassDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { seek.progress = 50; apply(50) }
    }

    /** Пересоздаём экран с новыми цветами. */
    private fun changeTheme(t: Theme) {
        prefs.theme = t.name
        Ui.apply(this, t)
        recreate()
    }

    private fun about() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val tv = TextView(this).apply {
            text = android.text.Html.fromHtml(getString(R.string.about_text, version), android.text.Html.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(Ui.primary)
            setTextColor(Ui.TEXT2)
            setPadding(dp(24f), dp(8f), dp(24f), 0)
            textSize = 15f
        }
        AlertDialog.Builder(this).setTitle(R.string.app_name).setView(tv)
            .setPositiveButton(android.R.string.ok, null).show().also { Ui.glassDialog(it) }
    }

    // ---------- список ----------

    private inner class Adapter : BaseAdapter() {
        private var items: List<Any> = emptyList()

        fun update(newItems: List<Any>) { items = newItems; notifyDataSetChanged() }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (items[position] is Row) 1 else 0
        override fun isEnabled(position: Int) = items[position] is Row

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]
            if (item is Row) {
                val v = convertView as? AppItemView ?: AppItemView(this@MainActivity)
                v.row = item
                v.alpha = if (item.excluded) 0.5f else 1f   // не входит в сохраняемый список
                return v
            }
            val v = convertView as? TextView ?: TextView(this@MainActivity).apply {
                textSize = 15f
                typeface = Ui.bold
                setTextColor(Ui.primary)
                setPadding(dp(28f), dp(14f), dp(28f), dp(4f))
            }
            v.text = item as String
            return v
        }
    }
}
