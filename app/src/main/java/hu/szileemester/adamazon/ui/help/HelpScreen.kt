package hu.szileemester.adamazon.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class HelpPage(val emoji: String, val title: String, val text: String)

private val pages = listOf(
    HelpPage(
        "🛒", "Üdv az Adamazonban!",
        "Közös bevásárlólista a háztartásodnak. Amit felírsz vagy kipipálsz, az a többiek telefonján " +
            "is azonnal megjelenik – internet nélkül is működik, és később szinkronizál.",
    ),
    HelpPage(
        "🏪", "Boltok",
        "A Listák fülön minden bolt egy kártya. A + gombbal vehetsz fel újat (pl. Auchan, DM, Foxpost), " +
            "a logó magától érkezik. A ⇅ gombbal rendezheted őket, a bolt logójára koppintva pedig " +
            "megnézheted vagy megírhatod a leírását, például a nyitvatartást.",
    ),
    HelpPage(
        "✍️", "Tételek felvétele",
        "Írd be lent: „2 kg alma” – a mennyiséget külön felismeri. Vesszővel többet is felvehetsz " +
            "egyszerre: „tej, kenyér, vaj”. Egy több soros listát beillesztve minden sorból külön tétel lesz.",
    ),
    HelpPage(
        "🎤", "Diktálás",
        "Nyomd meg a 🎤 gombot, és mondd: „két kiló alma, tej meg kenyér”. A tételeket a szünet, az " +
            "„és”, a „meg” és a mennyiségek választják el. A piros ■ gombbal fejezed be, utána még " +
            "ellenőrizheted a listát.",
    ),
    HelpPage(
        "🏷️", "Kategóriák és ikonok",
        "Minden tétel magától kategóriát és ikont kap. Ha kijavítod, megjegyzi. A kategóriák sorrendjét " +
            "boltonként a pipálásaidból tanulja meg, így a lista a bolt bejárási sorrendjét követi.",
    ),
    HelpPage(
        "✅", "Pipálás és szerkesztés",
        "A négyzettel kipipálod a tételt, balra húzva törlöd. Koppintásra szerkesztheted: mennyiség " +
            "+/− gombokkal, mértékegység, kategória – vagy áthelyezheted egy másik boltba.",
    ),
    HelpPage(
        "↶", "Visszavonás",
        "Mellényúltál? A lenti ↶ gomb az utolsó öt műveletet vonja vissza: felvételt, pipálást, " +
            "törlést, áthelyezést.",
    ),
    HelpPage(
        "💳", "Hűségkártyák",
        "A Kártyák fülön fotózd le a kártyáidat; a vonalkódot az app felismeri és tisztán újrarajzolja. " +
            "Fizetésnél a képernyő teljes fényerőre vált. A bolthoz kötött kártya a lista fejlécéből is elérhető.",
    ),
    HelpPage(
        "👥", "Közös csoport",
        "A csoportod minden tagja ugyanazokat a boltokat és listákat látja. A tagokat a Beállításokban " +
            "nézheted meg. Ezt a bemutatót is ott találod újra, a Súgó alatt.",
    ),
)

/** The feature tour: one feature per page, swiped sideways; [onClose] ends it at any page. */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(if (last) "Bezárás" else "Kihagyás") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                Page(pages[index])
            }
            Dots(count = pages.size, current = pager.currentPage)
            Button(
                onClick = { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(52.dp),
            ) { Text(if (last) "Kezdjük!" else "Tovább") }
        }
    }
}

@Composable
private fun Page(page: HelpPage) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(page.emoji, fontSize = 64.sp) }
        Spacer(Modifier.height(32.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            page.text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val color by animateColorAsState(
                if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot",
            )
            Box(Modifier.padding(4.dp).size(if (index == current) 10.dp else 8.dp).clip(CircleShape).background(color))
        }
    }
}
