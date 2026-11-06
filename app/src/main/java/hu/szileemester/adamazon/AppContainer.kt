package hu.szileemester.adamazon

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import hu.szileemester.adamazon.data.AuthRepository
import hu.szileemester.adamazon.data.BarcodeReader
import hu.szileemester.adamazon.data.GroupSession
import hu.szileemester.adamazon.data.HelpSettings
import hu.szileemester.adamazon.data.ImageCompressor
import hu.szileemester.adamazon.data.RecentIcons
import hu.szileemester.adamazon.data.StickerMaker
import hu.szileemester.adamazon.data.LogoFetcher
import hu.szileemester.adamazon.data.StorePresets
import hu.szileemester.adamazon.data.ThemeSettings
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.DictionaryParser
import hu.szileemester.adamazon.domain.EmojiCatalog
import android.graphics.Paint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container – the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    /** For work that must outlive a screen, e.g. a store deletion started right before navigating back. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val db = FirebaseFirestore.getInstance()

    val auth = AuthRepository(FirebaseAuth.getInstance(), db, appScope)

    private var currentSession: GroupSession? = null

    /** The signed-in user's group. Screens exist only while it is open (see AppRoot). */
    val session: GroupSession
        get() = checkNotNull(currentSession) { "No group session open" }

    /** Opens [groupId]'s session, or keeps the current one if it is the same group. */
    fun openSession(groupId: String): GroupSession =
        currentSession?.takeIf { it.groupId == groupId } ?: GroupSession(db, groupId).also { currentSession = it }

    val theme = ThemeSettings(app)
    val help = HelpSettings(app)
    val images = ImageCompressor(app.contentResolver)
    val logos = LogoFetcher()
    val barcodes = BarcodeReader(app)
    val stickers = StickerMaker(images)
    val recentIcons = RecentIcons(app)

    val categorizer: Categorizer by lazy {
        Categorizer(DictionaryParser.parse(app.assets.open("dictionary.json").bufferedReader().use { it.readText() }))
    }
    val presets by lazy { StorePresets.load(app) }

    /** Every emoji this device's font can draw (minSdk 26 phones lack the newest ones). */
    val emojis: EmojiCatalog by lazy {
        val catalog = EmojiCatalog.parse(app.assets.open("emoji_hu.json").bufferedReader().use { it.readText() })
        val paint = Paint()
        EmojiCatalog(catalog.entries.filter { paint.hasGlyph(it.emoji) })
    }
}
