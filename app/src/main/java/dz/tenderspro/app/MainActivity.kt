package dz.tenderspro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// يتم اختيار خادم KENDR PRO تلقائياً دون مطالبة المستخدم بإدخال الرابط.
private const val DEFAULT_API = "https://kendr-pro-api.onrender.com"

private val ALGERIAN_WILAYAS = listOf(
    "أدرار", "الشلف", "الأغواط", "أم البواقي", "باتنة", "بجاية", "بسكرة", "بشار",
    "البليدة", "البويرة", "تمنراست", "تبسة", "تلمسان", "تيارت", "تيزي وزو", "الجزائر",
    "الجلفة", "جيجل", "سطيف", "سعيدة", "سكيكدة", "سيدي بلعباس", "عنابة", "قالمة",
    "قسنطينة", "المدية", "مستغانم", "المسيلة", "معسكر", "ورقلة", "وهران", "البيض",
    "إليزي", "برج بوعريريج", "بومرداس", "الطارف", "تندوف", "تيسمسيلت", "الوادي",
    "خنشلة", "سوق أهراس", "تيبازة", "ميلة", "عين الدفلى", "النعامة", "عين تموشنت",
    "غرداية", "غليزان", "تيميمون", "برج باجي مختار", "أولاد جلال", "بني عباس",
    "عين صالح", "عين قزام", "تقرت", "جانت", "المغير", "المنيعة"
)

data class TenderItem(
    val id: Int, val title: String, val wilaya: String, val kind: String,
    val reference: String, val deadline: String, val source: String, val url: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KEndrProApp() }
    }
}

@Composable
fun KEndrProApp() {
    val navy = Color(0xFF10243A)
    val teal = Color(0xFF00A896)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var tenders by remember { mutableStateOf<List<TenderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("جارٍ الاتصال بخادم KENDR PRO تلقائياً...") }
    var query by remember { mutableStateOf("") }
    var wilaya by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var appliedWilaya by remember { mutableStateOf("") }
    var wilayaMenuExpanded by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                val connection = (URL("$DEFAULT_API/api/tenders?limit=500").openConnection() as HttpURLConnection)
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.setRequestProperty("Accept", "application/json")
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val array = root.optJSONArray("items") ?: org.json.JSONArray()
                    buildList {
                        for (i in 0 until array.length()) {
                            val o = array.getJSONObject(i)
                            add(TenderItem(
                                o.optInt("id"), o.optString("title"), o.optString("wilaya"),
                                o.optString("kind"), o.optString("reference"), o.optString("deadline"),
                                o.optString("source_name", o.optString("source")), o.optString("source_url")
                            ))
                        }
                    }
                } finally { connection.disconnect() }
            }
            tenders = result
            message = "متصل بالخادم تلقائياً • تم تحميل ${result.size} إعلان"
        } catch (e: Exception) {
            message = "تعذر الاتصال بالخادم تلقائياً: ${e.message ?: "حاول التحديث لاحقاً"}"
        } finally { loading = false }
    }

    LaunchedEffect(Unit) {
        load()
        while (true) {
            delay(15 * 60 * 1000L)
            load()
        }
    }

    val filtered = tenders.filter { item ->
        val matchesQuery = appliedQuery.isBlank() ||
            item.title.contains(appliedQuery, true) ||
            item.reference.contains(appliedQuery, true) ||
            item.source.contains(appliedQuery, true)
        val matchesWilaya = appliedWilaya.isBlank() ||
            item.wilaya.contains(appliedWilaya, true) ||
            (appliedWilaya == "الجزائر" && item.wilaya.contains("العاصمة", true))
        matchesQuery && matchesWilaya
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = teal, background = Color(0xFFF3F6F9))) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF3F6F9))) {
            Column(Modifier.fillMaxWidth().background(navy).padding(18.dp)) {
                Text("KENDR PRO", color = Color.White, fontSize = 25.sp)
                Text("المناقصات والاستشارات الجزائرية • جميع الولايات", color = Color(0xFFD5E3EF), fontSize = 13.sp)
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("ابحث عن المناقصات والاستشارات", color = navy, fontSize = 17.sp)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("اسم المناقصة أو رقمها أو المؤسسة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        appliedQuery = query.trim()
                        appliedWilaya = wilaya
                    })
                )

                Box(Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { wilayaMenuExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9AA8B5))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("الولاية", color = Color.Gray, fontSize = 11.sp)
                                Text(wilaya.ifBlank { "كل الولايات" }, color = navy, fontSize = 15.sp)
                            }
                            Text("▼", color = teal, fontSize = 16.sp)
                        }
                    }
                    DropdownMenu(
                        expanded = wilayaMenuExpanded,
                        onDismissRequest = { wilayaMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 360.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("كل الولايات", color = navy) },
                            onClick = { wilaya = ""; wilayaMenuExpanded = false }
                        )
                        ALGERIAN_WILAYAS.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { wilaya = name; wilayaMenuExpanded = false }
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            appliedQuery = query.trim()
                            appliedWilaya = wilaya
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("بحث") }
                    OutlinedButton(
                        onClick = {
                            query = ""
                            wilaya = ""
                            appliedQuery = ""
                            appliedWilaya = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("إلغاء الفلترة") }
                }

                Button(
                    onClick = { scope.launch { load() } },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "جارٍ تحميل الإعلانات..." else "تحديث الإعلانات") }

                Text(message, color = if (message.startsWith("متصل")) teal else Color.DarkGray, fontSize = 12.sp)
                Text("عدد النتائج: ${filtered.size} من ${tenders.size}", color = navy, fontSize = 13.sp)
            }

            if (filtered.isEmpty() && !loading) {
                Text(
                    if (tenders.isEmpty()) "لا توجد إعلانات محملة حالياً. اضغط تحديث الإعلانات." else "لا توجد نتائج مطابقة. جرّب كلمة أخرى أو اختر كل الولايات.",
                    modifier = Modifier.padding(18.dp), color = Color.Gray
                )
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(filtered) { item ->
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, color = navy)
                            Spacer(Modifier.height(6.dp))
                            Text("الولاية: ${item.wilaya.ifBlank { "غير محددة" }}  •  النوع: ${item.kind}", color = Color.DarkGray)
                            Text("المرجع: ${item.reference.ifBlank { "غير متوفر" }}", color = Color.Gray, fontSize = 12.sp)
                            if (item.deadline.isNotBlank() && item.deadline != "null") Text("آخر أجل: ${item.deadline}", color = Color(0xFFB45309))
                            if (item.url.startsWith("https://")) TextButton(onClick = { uri.openUri(item.url) }) { Text("فتح الإعلان الأصلي") }
                        }
                    }
                }
            }
        }
    }
}
