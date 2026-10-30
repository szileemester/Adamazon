package hu.szileemester.adamazon

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import hu.szileemester.adamazon.data.AuthRepository
import hu.szileemester.adamazon.data.CardRepository
import hu.szileemester.adamazon.data.CategoryRepository
import hu.szileemester.adamazon.data.ImageCompressor
import hu.szileemester.adamazon.data.ItemRepository
import hu.szileemester.adamazon.data.LearnedRepository
import hu.szileemester.adamazon.data.LogoFetcher
import hu.szileemester.adamazon.data.StorePresets
import hu.szileemester.adamazon.data.StoreRepository
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.DictionaryParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container – the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    /** For work that must outlive a screen, e.g. a store deletion started right before navigating back. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val db = FirebaseFirestore.getInstance()

    val auth = AuthRepository(FirebaseAuth.getInstance(), db, appScope)
    val categories = CategoryRepository(db)
    val stores = StoreRepository(db)
    val items = ItemRepository(db)
    val learned = LearnedRepository(db)
    val cards = CardRepository(db)

    val images = ImageCompressor(app.contentResolver)
    val logos = LogoFetcher()

    val categorizer: Categorizer by lazy {
        Categorizer(DictionaryParser.parse(app.assets.open("dictionary.json").bufferedReader().use { it.readText() }))
    }
    val presets by lazy { StorePresets.load(app) }
}
