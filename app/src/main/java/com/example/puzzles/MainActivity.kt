package com.example.puzzles

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.puzzles.ui.theme.PuzzlesTheme
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.util.*
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PuzzlesTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen(
                            onSudokuClick = { navController.navigate("sudoku") },
                            onCrosswordClick = { navController.navigate("crossword") },
                            onSudokuSamariClick = { navController.navigate("sudoku_samari") }
                        )
                    }
                    composable("sudoku") {
                        SudokuScreen(onBack = { navController.popBackStack() })
                    }
                    composable("crossword") {
                        CrosswordScreen(onBack = { navController.popBackStack() })
                    }
                    composable("sudoku_samari") {
                        SudokuSamariScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onSudokuClick: () -> Unit, onCrosswordClick: () -> Unit, onSudokuSamariClick: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Puzzles Scraper",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            Button(
                onClick = onSudokuClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Samurai Sudoku (Capture)")
            }
            Button(
                onClick = onSudokuSamariClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Sudoku Samari (Space)")
            }
            Button(
                onClick = onCrosswordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Daily Crosswords")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("Ready") }
    var isProcessing by remember { mutableStateOf(false) }
    var isPageLoaded by remember { mutableStateOf(false) }

    val sudokuUrl = "https://www.samurai-sudoku.com/"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Samurai Sudoku") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showSudokuCaptureDialog(context) { startDate, count ->
                                    scope.launch {
                                        runSudokuCaptureFlow(webView, context, startDate, count, { isProcessing = it }) { msg -> statusText = msg }
                                    }
                                }
                            },
                            enabled = isPageLoaded && !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Capture Puzzles")
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val file = File(context.cacheDir, "sudoku_puzzles.pdf")
                                    if (file.exists()) {
                                        printPdf(file, context)
                                    } else {
                                        statusText = "No PDF found. Capture first."
                                    }
                                }
                            },
                            enabled = isPageLoaded && !isProcessing
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print All")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val file = File(context.cacheDir, "sudoku_puzzles.pdf")
                                    if (file.exists()) {
                                        emailPdf(file, context)
                                    } else {
                                        statusText = "No PDF found. Capture first."
                                    }
                                }
                            },
                            enabled = isPageLoaded && !isProcessing
                        ) {
                            Icon(Icons.Default.Email, contentDescription = "Email All")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Status: $statusText", style = MaterialTheme.typography.bodySmall)
                }
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                if (request?.isForMainFrame == true) return null
                                val url = request?.url?.toString()?.lowercase() ?: return null
                                val blockedDomains = setOf("doubleclick.net", "googlesyndication.com", "taboola.com")
                                if (blockedDomains.any { url.contains(it) }) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(buildSudokuAntiAdScript(), null)
                                statusText = "Page Loaded"
                                isPageLoaded = true
                            }
                        }
                        loadUrl(sudokuUrl)
                        webView = this
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuSamariScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("Ready") }
    var isProcessing by remember { mutableStateOf(false) }
    var isPageLoaded by remember { mutableStateOf(false) }
    var monthText by remember { mutableStateOf("") }

    val listingUrl = "http://www.sudoku-space.com/samurai-sudoku/"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sudoku Samari (Space)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = monthText,
                            onValueChange = { monthText = it },
                            label = { Text("Month (e.g. 7)") },
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing
                        )
                        Button(
                            onClick = {
                                val m = monthText.toIntOrNull()
                                if (m != null && m in 1..12) {
                                    confirmAndProcessSudokuSpacePdfs(webView, context, scope, m, true, { isProcessing = it }) { msg -> statusText = msg }
                                } else {
                                    statusText = "Enter valid month (1-12)"
                                }
                            },
                            enabled = isPageLoaded && !isProcessing
                        ) {
                            Text("Submit")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Status: $statusText", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    val m = monthText.toIntOrNull()
                                    if (m != null && m in 1..12) {
                                        confirmAndProcessSudokuSpacePdfs(webView, context, scope, m, true, { isProcessing = it }) { msg -> statusText = msg }
                                    }
                                },
                                enabled = isPageLoaded && !isProcessing
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print All")
                            }
                            IconButton(
                                onClick = {
                                    val m = monthText.toIntOrNull()
                                    if (m != null && m in 1..12) {
                                        confirmAndProcessSudokuSpacePdfs(webView, context, scope, m, false, { isProcessing = it }) { msg -> statusText = msg }
                                    }
                                },
                                enabled = isPageLoaded && !isProcessing
                            ) {
                                Icon(Icons.Default.Email, contentDescription = "Email All")
                            }
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                statusText = "Page Loaded"
                                isPageLoaded = true
                            }
                        }
                        loadUrl(listingUrl)
                        webView = this
                    }
                }
            )
        }
    }
}

private fun confirmAndProcessSudokuSpacePdfs(webView: WebView?, context: Context, scope: CoroutineScope, month: Int, isPrint: Boolean, onProcessing: (Boolean) -> Unit, onStatus: (String) -> Unit) {
    if (webView == null) return
    onProcessing(true)

    val js = """
(function() {
  const m = $month;
  const rx = new RegExp("/samurai-sudoku/pdf/(?:0?" + m + ")-\\d{1,2}-\\d{4}/", 'i');
  const anchors = Array.from(document.querySelectorAll("a[href*='/samurai-sudoku/pdf/']"));
  const seen = new Set();
  const urls = [];
  for (const a of anchors) {
    try {
      const href = new URL(a.getAttribute('href'), document.baseURI).href;
      if (rx.test(href) && !seen.has(href)) {
        seen.add(href);
        urls.push(href);
      }
    } catch (e) {}
  }
  return JSON.stringify(urls);
})();
    """.trimIndent()

    onStatus("Scanning for PDF links...")
    webView.evaluateJavascript(js) { result ->
        try {
            val cleanedJson = result.removeSurrounding("\"").replace("\\\"", "\"")
            val urlsArray = JSONArray(cleanedJson)
            val urls = mutableListOf<String>()
            for (i in 0 until urlsArray.length()) {
                urls.add(urlsArray.getString(i))
            }

            if (urls.isEmpty()) {
                onStatus("No PDF links found for month $month")
                onProcessing(false)
                return@evaluateJavascript
            }

            val title = if (isPrint) "Download & Print" else "Download & Email"
            val message = "Found ${urls.size} puzzles. Do you want to download, merge, and ${if (isPrint) "print" else "email"} them all?"

            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes") { _, _ ->
                    scope.launch {
                        try {
                            downloadAndMergePdfs(urls, context, isPrint, onStatus)
                        } finally {
                            onProcessing(false)
                        }
                    }
                }
                .setNegativeButton("No") { _, _ ->
                    onProcessing(false)
                }
                .setOnCancelListener {
                    onProcessing(false)
                }
                .show()

        } catch (e: Exception) {
            onStatus("Error scanning links: ${e.message}")
            onProcessing(false)
        }
    }
}

private fun buildSudokuAntiAdScript(): String {
    return """
        (() => {
          const junk = ['header', 'footer', '.ads', '.ad-container', '#top-nav', 'h1', 'p', 'form', '.don'];
          junk.forEach(s => {
            document.querySelectorAll(s).forEach(el => el.style.display = 'none');
          });
          const css = `
            .ads,.adsbygoogle,.ad-container,.ad-container-desktop,.adslot,.adbanner,.advert,.advertisement,
            .ad-banner,.adbox,.ad-wrapper,.sponsored,.sponsor,[data-ad],[data-ads] {
              display:none!important;visibility:hidden!important;max-height:0!important;height:0!important;
            }`;
          const s = document.createElement('style');
          s.appendChild(document.createTextNode(css));
          (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
}

private fun showSudokuCaptureDialog(context: Context, onConfirm: (Date, Int) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(context, { _, year, month, day ->
        val selectedDate = Calendar.getInstance().apply {
            set(year, month, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val input = EditText(context).apply {
            setText("5")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        AlertDialog.Builder(context)
            .setTitle("Number of Puzzles")
            .setView(input)
            .setPositiveButton("Capture") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: 5
                onConfirm(selectedDate, count)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
}

private suspend fun runSudokuCaptureFlow(webView: WebView?, context: Context, startDate: Date, count: Int, onProcessing: (Boolean) -> Unit, onStatus: (String) -> Unit) {
    if (webView == null) return
    onProcessing(true)

    val today = Calendar.getInstance().apply { 
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val startMillis = startDate.time
    val diffDays = ((today - startMillis) / (1000 * 60 * 60 * 24)).toInt()
    var increment = if (diffDays < 0) 0 else diffDays

    val bitmaps = mutableListOf<Bitmap>()

    try {
        for (i in 0 until count) {
            onStatus("Capturing ${i + 1}/$count...")
            
            val script = """
                (function() {
                    const sel = document.getElementById('ai');
                    if (sel) {
                        sel.selectedIndex = $increment;
                        sel.dispatchEvent(new Event('change'));
                        return true;
                    }
                    return false;
                })();
            """.trimIndent()
            
            withContext(Dispatchers.Main) { webView.evaluateJavascript(script, null) }
            delay(3500) // Give time for the puzzle to change and render

            val captureResult = withContext(Dispatchers.Main) {
                // Ensure software layer for capture
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                
                val js = """
                    (function() {
                        const junk = ['header', 'footer', '.ads', '.ad-container', '#top-nav', 'h1', 'p', 'form', '.don'];
                        junk.forEach(s => {
                            document.querySelectorAll(s).forEach(el => el.style.display = 'none');
                        });
                        
                        let puzzle = document.querySelector('svg') || document.querySelector('canvas') || document.getElementById('puzzle') || document.querySelector('table.grid');
                        if (!puzzle) {
                            let maxArea = 0;
                            let largest = document.body;
                            document.querySelectorAll('div, table').forEach(el => {
                                let r = el.getBoundingClientRect();
                                let area = r.width * r.height;
                                if (area > maxArea && r.width < window.innerWidth * 0.99) {
                                    maxArea = area;
                                    largest = el;
                                }
                            });
                            puzzle = largest;
                        }
                        puzzle.scrollIntoView({block: 'start', inline: 'center'});
                        window.scrollTo(0, 0);
                        
                        const rect = puzzle.getBoundingClientRect();
                        return JSON.stringify({
                            x: rect.left,
                            y: rect.top,
                            width: rect.width,
                            height: rect.height,
                            scale: window.devicePixelRatio
                        });
                    })();
                """.trimIndent()
                
                val resultJson = suspendCancellableCoroutine<String?> { cont ->
                    webView.evaluateJavascript(js) { res -> cont.resume(res) }
                }
                
                if (resultJson != null && resultJson != "null") {
                    val json = JSONObject(resultJson.removeSurrounding("\"").replace("\\\"", "\""))
                    val x = (json.getDouble("x") * json.getDouble("scale")).toInt()
                    val y = (json.getDouble("y") * json.getDouble("scale")).toInt()
                    val w = (json.getDouble("width") * json.getDouble("scale")).toInt()
                    val h = (json.getDouble("height") * json.getDouble("scale")).toInt()

                    val fullBitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(fullBitmap)
                    webView.draw(canvas)
                    
                    val cropped = try {
                        Bitmap.createBitmap(fullBitmap, 
                            x.coerceIn(0, fullBitmap.width - 1), 
                            y.coerceIn(0, fullBitmap.height - 1), 
                            w.coerceAtMost(fullBitmap.width - x), 
                            h.coerceAtMost(fullBitmap.height - y))
                    } catch (e: Exception) {
                        fullBitmap
                    }
                    if (cropped != fullBitmap) fullBitmap.recycle()
                    cropped
                } else {
                    null
                }
            }

            if (captureResult != null) {
                bitmaps.add(captureResult)
            }
            
            increment++
        }

        if (bitmaps.isNotEmpty()) {
            onStatus("Merging into PDF...")
            saveBitmapsToPdf(bitmaps, context, "sudoku_puzzles.pdf")
            onStatus("Done! PDF ready.")
        } else {
            onStatus("No puzzles captured.")
        }

    } catch (e: Exception) {
        onStatus("Error: ${e.message}")
    } finally {
        onProcessing(false)
    }
}

private suspend fun saveBitmapsToPdf(bitmaps: List<Bitmap>, context: Context, fileName: String) {
    withContext(Dispatchers.IO) {
        val document = PDDocument()
        try {
            for (bitmap in bitmaps) {
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                
                val pdImage = LosslessFactory.createFromImage(document, bitmap)
                PDPageContentStream(document, page).use { contentStream ->
                    val pageWidth = PDRectangle.LETTER.width
                    val pageHeight = PDRectangle.LETTER.height
                    val imgWidth = pdImage.width.toFloat()
                    val imgHeight = pdImage.height.toFloat()
                    
                    val scale = Math.min(pageWidth / imgWidth, (pageHeight - 50) / imgHeight) * 0.95f
                    val drawWidth = imgWidth * scale
                    val drawHeight = imgHeight * scale
                    val x = (pageWidth - drawWidth) / 2
                    val y = (pageHeight - drawHeight) / 2
                    
                    contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                }
                bitmap.recycle()
            }
            val file = File(context.cacheDir, fileName)
            document.save(file)
        } finally {
            document.close()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrosswordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var monthLinks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expanded by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Ready") }
    var isMonthPageLoaded by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val indexUrl = "https://freedailycrosswords.com/printable-crossword-puzzles/"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Crosswords") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Control Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expanded && !isProcessing,
                            onExpandedChange = { if (!isProcessing) expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            TextField(
                                value = selectedMonth ?: "Select Month",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { if (!isProcessing) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(),
                                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                enabled = !isProcessing
                            )
                            ExposedDropdownMenu(
                                expanded = expanded && !isProcessing,
                                onDismissRequest = { expanded = false }
                            ) {
                                monthLinks.keys.forEach { month ->
                                    DropdownMenuItem(
                                        text = { Text(month) },
                                        onClick = {
                                            selectedMonth = month
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                selectedMonth?.let { month ->
                                    monthLinks[month]?.let { url ->
                                        statusText = "Loading $month..."
                                        isMonthPageLoaded = false
                                        webView?.loadUrl(url)
                                    }
                                }
                            },
                            enabled = selectedMonth != null && !isProcessing
                        ) {
                            Text("Process")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Status: $statusText", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    confirmAndProcessPdfs(webView, context, scope, true, { isProcessing = it }) { msg -> statusText = msg }
                                },
                                enabled = isMonthPageLoaded && !isProcessing
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print All")
                            }
                            IconButton(
                                onClick = {
                                    confirmAndProcessPdfs(webView, context, scope, false, { isProcessing = it }) { msg -> statusText = msg }
                                },
                                enabled = isMonthPageLoaded && !isProcessing
                            ) {
                                Icon(Icons.Default.Email, contentDescription = "Email All")
                            }
                        }
                    }
                }
            }

            // WebView
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        val defaultUA = settings.userAgentString
                        settings.userAgentString = defaultUA.replace("; wv", "").replace("Version/4.0 ", "")
                        
                        webViewClient = object : WebViewClient() {
                            private val blockedDomains = setOf(
                                "doubleclick.net", "googlesyndication.com", "googletagservices.com",
                                "googletagmanager.com", "adservice.google.com", "taboola.com",
                                "outbrain.com", "criteo.com", "amazon-adsystem.com", "scorecardresearch.com"
                            )
                            private val blockedPathFragments = arrayOf("/adsbygoogle.", "/adserver/", "/advert", "/adscript")

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                if (request?.isForMainFrame == true) return null
                                val url = request?.url?.toString()?.lowercase() ?: return null
                                val host = request.url.host?.lowercase() ?: ""
                                
                                val isBlocked = blockedDomains.any { host.contains(it) } || 
                                              blockedPathFragments.any { url.contains(it) }
                                
                                if (isBlocked) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                statusText = "Page Loaded"
                                view?.evaluateJavascript(buildAntiAdScript(), null)
                                
                                val isIndex = url?.trimEnd('/') == indexUrl.trimEnd('/')
                                if (isIndex) {
                                    isMonthPageLoaded = false
                                    statusText = "Scanning for months..."
                                    view?.evaluateJavascript(buildMonthScrapeScript()) { result ->
                                        val cleanedResult = result.removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"")
                                        val links = mutableMapOf<String, String>()
                                        cleanedResult.lines().forEach { line ->
                                            val parts = line.split("|")
                                            if (parts.size >= 2) {
                                                links[parts[0].trim()] = parts[1].trim()
                                            }
                                        }
                                        monthLinks = links
                                        statusText = if (links.isEmpty()) "No months found" else "Found ${links.size} months"
                                    }
                                } else if (url != null) {
                                    isMonthPageLoaded = true
                                }
                            }
                        }
                        loadUrl(indexUrl)
                        webView = this
                    }
                }
            )
        }
    }
}

private fun buildAntiAdScript(): String {
    return """
        (() => {
          try { window.open = () => null; window.alert = () => {}; window.confirm = () => false; window.prompt = () => null; } catch {}
          const css = `
            .ads,.adsbygoogle,.ad-container,.ad-container-desktop,.adslot,.adbanner,.advert,.advertisement,
            .ad-banner,.adbox,.ad-wrapper,.sponsored,.sponsor,[data-ad],[data-ads],
            iframe[src*="adsbygoogle"],iframe[src*="doubleclick"],iframe[src*="googlesyndication"],
            iframe[src*="taboola"],iframe[src*="outbrain"] {
              display:none!important;visibility:hidden!important;max-height:0!important;height:0!important;
            }`;
          try {
            const s = document.createElement('style');
            s.appendChild(document.createTextNode(css));
            (document.head || document.documentElement).appendChild(s);
          } catch {}
        })();
    """.trimIndent()
}

private fun buildMonthScrapeScript(): String {
    return """
        (() => {
          const months = ['january','february','march','april','may','june',
                          'july','august','september','october','november','december'];
          const anchors = Array.from(document.querySelectorAll('a[href]'));
          const currentYear = new Date().getFullYear().toString();
          const lastYear = (new Date().getFullYear() - 1).toString();
          const best = {};

          for (const a of anchors) {
            const href = (a.href || '').trim();
            const lo = href.toLowerCase();
            if (!href.startsWith('http')) continue;
            for (const m of months) {
              if (lo.includes('/' + m + '-') || lo.includes('/' + m + '/') || lo.includes(m + '-')) {
                const prev = best[m];
                const score = h => h.includes(currentYear) ? 2 : h.includes(lastYear) ? 1 : 0;
                if (!prev || score(href) > score(prev) ||
                   (score(href) === score(prev) && href.length < prev.length)) {
                  best[m] = href;
                }
              }
            }
          }

          const cap = s => s.charAt(0).toUpperCase() + s.slice(1);
          return Object.entries(best).map(([m,u]) => cap(m) + '|' + u).join('\n');
        })();
    """.trimIndent()
}

private fun confirmAndProcessPdfs(webView: WebView?, context: Context, scope: CoroutineScope, isPrint: Boolean, onProcessing: (Boolean) -> Unit, onStatus: (String) -> Unit) {
    if (webView == null) return
    onProcessing(true)

    val pdfScript = """
        (function(){
          const as = Array.from(document.querySelectorAll('a[href]'));
          const seen = new Set();
          const out = [];
          for (const a of as) {
            const href = (a.href || '').trim();
            const lo = href.toLowerCase();
            if (!href) continue;
            if (lo.includes('.pdf') && !lo.includes('solution')) {
              if (!seen.has(href)) { out.push(href); seen.add(href); }
            }
          }
          return JSON.stringify(out);
        })();
    """.trimIndent()

    onStatus("Scanning for PDFs...")
    webView.evaluateJavascript(pdfScript) { result ->
        try {
            val cleanedJson = result.removeSurrounding("\"").replace("\\\"", "\"")
            val urlsArray = JSONArray(cleanedJson)
            val urls = mutableListOf<String>()
            for (i in 0 until urlsArray.length()) {
                urls.add(urlsArray.getString(i))
            }

            if (urls.isEmpty()) {
                onStatus("No PDF links found")
                onProcessing(false)
                return@evaluateJavascript
            }

            val title = if (isPrint) "Download & Print" else "Download & Email"
            val message = "Found ${urls.size} puzzles. Do you want to download, merge, and ${if (isPrint) "print" else "email"} them all?"

            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes") { _, _ ->
                    scope.launch {
                        try {
                            downloadAndMergePdfs(urls, context, isPrint, onStatus)
                        } finally {
                            onProcessing(false)
                        }
                    }
                }
                .setNegativeButton("No") { _, _ ->
                    onProcessing(false)
                }
                .setOnCancelListener {
                    onProcessing(false)
                }
                .show()

        } catch (e: Exception) {
            onStatus("Error scanning links: ${e.message}")
            onProcessing(false)
        }
    }
}

private suspend fun downloadAndMergePdfs(urls: List<String>, context: Context, isPrint: Boolean, onStatus: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "temp_pdfs")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }

        val downloadedFiles = mutableListOf<File>()

        for ((index, url) in urls.withIndex()) {
            withContext(Dispatchers.Main) { onStatus("Downloading ${index + 1}/${urls.size}...") }
            try {
                val file = File(tempDir, "puzzle_$index.pdf")
                URL(url).openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                downloadedFiles.add(file)
            } catch (e: Exception) {
                // Skip failed downloads
            }
        }

        if (downloadedFiles.isEmpty()) {
            withContext(Dispatchers.Main) { onStatus("Download failed") }
            return@withContext
        }

        withContext(Dispatchers.Main) { onStatus("Merging PDFs...") }
        val mergedFile = File(context.cacheDir, "puzzles_${System.currentTimeMillis()}.pdf")
        try {
            val merger = PDFMergerUtility()
            merger.destinationFileName = mergedFile.absolutePath
            
            for (file in downloadedFiles) {
                merger.addSource(file)
            }
            
            // Object-level merge is best for digital quality. 
            // We use setupMainMemoryOnly() to avoid storage permission issues during merge.
            merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly())

            withContext(Dispatchers.Main) {
                if (isPrint) {
                    onStatus("Opening print dialog...")
                    printPdf(mergedFile, context)
                } else {
                    onStatus("Opening sharing dialog...")
                    emailPdf(mergedFile, context)
                }
                // Clean up temp downloaded files
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onStatus("Merge error: ${e.message}") }
            if (mergedFile.exists()) { mergedFile.delete() }
        }
    }
}

private fun printPdf(file: File, context: Context) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val printAttributes = PrintAttributes.Builder()
        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
        .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
        .build()

    val printAdapter = object : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }

            var pageCount = PrintDocumentInfo.PAGE_COUNT_UNKNOWN
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                // Fallback to unknown if renderer fails
            }

            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(pages: Array<out android.print.PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                return
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }

        override fun onFinish() {
            super.onFinish()
            if (file.exists()) { file.delete() }
        }
    }
    printManager.print("Puzzles", printAdapter, printAttributes)
}

private fun emailPdf(file: File, context: Context) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.example.puzzles.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Puzzles")
            putExtra(Intent.EXTRA_TEXT, "Attached are the merged puzzles.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Chooser helps ensure the user can select their email app
        context.startActivity(Intent.createChooser(intent, "Send Email..."))
    } catch (e: Exception) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage("Could not share PDF: ${e.message}")
            .show()
    }
}
