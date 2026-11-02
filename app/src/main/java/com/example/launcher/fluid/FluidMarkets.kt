package com.example.launcher.fluid

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.unit.dp
import com.example.launcher.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder

internal data class StockQuote(val symbol: String, val name: String, val price: Double, val currency: String, val change: Double, val points: List<Float>, val time: Long)
internal fun parseStock(raw: String, symbol: String): StockQuote {
    val result = JSONObject(raw).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
    val meta = result.getJSONObject("meta")
    val price = meta.getDouble("regularMarketPrice")
    val previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
    val values = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).optJSONArray("close")
    val points = values?.let { a -> (0 until a.length()).mapNotNull { a.optDouble(it).takeIf(Double::isFinite)?.toFloat() } }.orEmpty()
    return StockQuote(symbol, meta.optString("shortName", symbol), price, meta.optString("currency"), if(previous > 0) (price-previous)/previous*100 else 0.0, points, meta.optLong("regularMarketTime")*1000)
}
private suspend fun quote(symbol: String, range: String, cache: MarketNewsCache): StockQuote = withContext(Dispatchers.IO) {
    val interval = if(range == "1d") "5m" else "1d"
    parseStock(cache.get("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(symbol,"UTF-8")}?range=$range&interval=$interval", ::getHttps), symbol)
}

@Composable internal fun FluidMarkets(state: FluidState, model: FluidModel, modifier: Modifier, settings: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val cache = remember { MarketNewsCache(File(context.filesDir,"market-news")) }
    var range by remember { mutableStateOf("1d") }
    var stocks by remember { mutableStateOf(emptyList<StockQuote>()) }
    var news by remember { mutableStateOf(emptyList<Story>()) }
    var loading by remember { mutableStateOf(true) }
    var stockError by remember { mutableStateOf<String?>(null) }
    var newsError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while(true) { delay(60_000); refresh++ } }
    LaunchedEffect(state.config.stockSymbols, range, refresh) {
        loading = true; stockError = null
        val symbols = state.config.stockSymbols.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().take(8)
        val results = coroutineScope { symbols.map { symbol -> async { try { quote(symbol, range, cache) } catch(e: Exception) { if(e is CancellationException) throw e; null } } }.awaitAll() }
        stocks = results.filterNotNull(); loading = false
        if(stocks.size < symbols.size) stockError = "Some quotes are unavailable. Check your symbols or retry."
    }
    LaunchedEffect(state.config.newsTopics, refresh) {
        newsError = null
        try { val batch = PublicNewsRepository { cache.get(it, ::getHttps) }.topStories(state.config.newsTopics); news = batch.stories; if(batch.failed.isNotEmpty()) newsError = "Some news topics could not update. Try refreshing." }
        catch(e: Exception) { if(e is CancellationException) throw e; newsError = "News unavailable. Check your connection." }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom=110.dp).testTag("fluid-markets"), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FluidHeading("Markets & news", "Your world, at a glance") { GlassIcon("settings", "Market & news settings", onClick=settings) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            GlassButton(state.config.market, glyph="settings", onClick=settings)
            Text("Updates every 10 min",style=F.text(11,muted=true),modifier=Modifier.padding(vertical=14.dp))
        }
        Text("STOCK WATCH", style=F.text(11,true,muted=true))
        if(loading) Text("Updating quotes…", style=F.text(13,muted=true))
        stockError?.let { Text(it,style=F.text(12,muted=true)) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            stocks.forEach { stock ->
                Column(Modifier.width(220.dp).glass(28.dp).clickable(onClick=settings).padding(20.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stock.symbol,style=F.text(17,true)); Text(stock.name,style=F.text(12,muted=true),maxLines=1)
                    Text("${stock.currency} ${"%,.2f".format(stock.price)}",style=F.text(22,true))
                    Text("%+.2f%%".format(stock.change),style=F.text(13,true),color=if(stock.change >= 0) Color(0xFF198874) else Color(0xFFB64076))
                    StockChart(stock.points)
                    Text("As of ${fluidDate(stock.time,"MMM d, HH:mm")} · Yahoo Finance",style=F.text(10,muted=true))
                }
            }
        }
        if(!loading && stocks.isEmpty()) GlassButton("Choose stocks",onClick=settings)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("1D" to "1d","1M" to "1mo","3M" to "3mo","1Y" to "1y").forEach { (label,value) -> TextButton(onClick={range=value}) { Text(label,style=F.text(12,true),color=if(range==value) F.purple else F.muted) } }
        }
        FluidHeading("In the headlines", "Stories picked for you") { GlassIcon("edit","Choose news topics",onClick=settings) }
        newsError?.let { Text(it,style=F.text(12,muted=true)) }
        if(state.config.newsTopics.isEmpty()) Text("Choose topics in settings to build your news feed.",style=F.text(13,muted=true))
        news.take(20).forEach { story ->
            Column(Modifier.fillMaxWidth().glass(28.dp).clickable { model.open(Intent(Intent.ACTION_VIEW, Uri.parse(story.url))) }) {
                StoryImage(story)
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${story.source} · ${NewsCategory.fromKey(story.category)?.label.orEmpty()}",style=F.text(11,muted=true))
                    Text(story.title,style=F.text(17,true)); Text(story.published,style=F.text(10,muted=true))
                }
            }
        }
    }
}
@Composable private fun StockChart(points: List<Float>) {
    val color = F.purple
    if(points.size < 2) { Text("Chart unavailable",style=F.text(11,muted=true)); return }
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val min = points.min(); val span = (points.max()-min).coerceAtLeast(.01f)
        val path = Path()
        points.forEachIndexed { i,p -> val x=i*size.width/(points.size-1); val y=size.height-8.dp.toPx()-(p-min)/span*(size.height-16.dp.toPx()); if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
        drawPath(path,Brush.horizontalGradient(listOf(Color(0xFFB45ACA),color,Color(0xFF539BE9))),style=Stroke(2.dp.toPx()))
        val y=size.height-8.dp.toPx()-(points.last()-min)/span*(size.height-16.dp.toPx())
        drawCircle(color.copy(alpha=.14f),9.dp.toPx(),Offset(size.width,y)); drawCircle(color,4.dp.toPx(),Offset(size.width,y))
    }
}
@Composable private fun StoryImage(story: Story) {
    var bitmap by remember(story.imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(story.imageUrl) {
        val address = story.imageUrl ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                try { connection.connectTimeout=8000; connection.readTimeout=8000
                    connection.inputStream.use { stream -> BitmapFactory.decodeStream(stream,null,BitmapFactory.Options().apply { inSampleSize=2 })?.asImageBitmap() }
                } finally { connection.disconnect() }
            } catch(e: Exception) { if(e is CancellationException) throw e; null }
        }
    }
    if(bitmap != null) Image(bitmap!!,story.title,Modifier.fillMaxWidth().height(170.dp),contentScale=ContentScale.Crop)
    else Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.linearGradient(listOf(F.lilac.copy(alpha=.25f),Color.Transparent))))
}
@Composable internal fun MarketNewsSettings(state: FluidState, model: FluidModel) {
    var symbols by remember(state.config.stockSymbols) { mutableStateOf(state.config.stockSymbols) }
    Text("Stock market",style=F.text(20,true))
    listOf("India" to "RELIANCE.NS,TCS.NS,HDFCBANK.NS", "United States" to "AAPL,MSFT,GOOGL", "United Kingdom" to "HSBA.L,AZN.L,SHEL.L", "Japan" to "7203.T,6758.T,9984.T").forEach { (market,defaults) ->
        GlassButton(if(state.config.market==market) "✓ $market" else market) { model.configure { it.copy(market=market,stockSymbols=defaults) } }
    }
    Text("Any market: enter Yahoo Finance symbols, separated by commas (up to 8). Examples: INFY.NS, AAPL, SAP.DE, 0700.HK.",style=F.text(12,muted=true))
    OutlinedTextField(symbols,{symbols=it},label={Text("Stock symbols")},modifier=Modifier.fillMaxWidth())
    GlassButton("Save stocks",primary=true) {
        val normalized = symbols.split(',').map { it.trim().uppercase() }.filter { it.matches(Regex("[A-Z0-9^=.-]{1,25}")) }.distinct().take(8)
        if(normalized.isEmpty()) model.message("Enter at least one valid symbol") else model.configure { it.copy(stockSymbols=normalized.joinToString(","),market="Custom") }
    }
    Text("News topics",style=F.text(20,true))
    NewsCategory.entries.forEach { category ->
        Row(Modifier.fillMaxWidth().glass(20.dp).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(category.label,style=F.text(14,true))
            Checkbox(category in state.config.newsTopics,{checked -> model.configure { it.copy(newsTopics=if(checked) it.newsTopics+category else it.newsTopics-category) } })
        }
    }
}
