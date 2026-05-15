package com.example.puzzles

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
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
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.*
import java.net.URL

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
                            onCrosswordClick = { navController.navigate("crossword") }
                        )
                    }
                    composable("sudoku") {
                        SudokuScreen(onBack = { navController.popBackStack() })
                    }
                    composable("crossword") {
                        CrosswordScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onSudokuClick: () -> Unit, onCrosswordClick: () -> Unit) {
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
                Text("Samurai Sudoku")
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

@Composable
fun SudokuScreen(onBack: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sudoku Screen Placeholder")
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("Back to Menu")
            }
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
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            TextField(
                                value = selectedMonth ?: "Select Month",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(),
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
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
                            enabled = selectedMonth != null
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
                                    confirmAndProcessPdfs(webView, context, scope, true) { msg -> statusText = msg }
                                },
                                enabled = isMonthPageLoaded
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print All")
                            }
                            IconButton(
                                onClick = {
                                    confirmAndProcessPdfs(webView, context, scope, false) { msg -> statusText = msg }
                                },
                                enabled = isMonthPageLoaded
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

private fun confirmAndProcessPdfs(webView: WebView?, context: Context, scope: CoroutineScope, isPrint: Boolean, onStatus: (String) -> Unit) {
    if (webView == null) return

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
                return@evaluateJavascript
            }

            val title = if (isPrint) "Download & Print" else "Download & Email"
            val message = "Found ${urls.size} puzzles. Do you want to download, merge, and ${if (isPrint) "print" else "email"} them all?"

            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes") { _, _ ->
                    scope.launch {
                        downloadAndMergePdfs(urls, context, isPrint, onStatus)
                    }
                }
                .setNegativeButton("No", null)
                .show()

        } catch (e: Exception) {
            onStatus("Error scanning links: ${e.message}")
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
        val mergedFile = File(context.cacheDir, "puzzles.pdf")
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
                    onStatus("Printing...")
                    printPdf(mergedFile, context)
                } else {
                    onStatus("Sharing...")
                    emailPdf(mergedFile, context)
                }
                // Clean up temp files
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onStatus("Merge error: ${e.message}") }
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
            val info = android.print.PrintDocumentInfo.Builder("puzzles.pdf")
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(pages: Array<out android.print.PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
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
            putExtra(Intent.EXTRA_SUBJECT, "Crossword Puzzles")
            putExtra(Intent.EXTRA_TEXT, "Attached are the merged crossword puzzles.")
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
