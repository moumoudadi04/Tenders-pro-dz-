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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    var apiBase by remember { mutableStateOf(DEFAULT_API) }
    var tenders by remember { mutableStateOf<List<TenderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("اضغط تحديث لجلب أحدث الإعلانات") }
    var query by remember { mutableStateOf("") }
    var wilaya by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var appliedWilaya by remember { mutableStateOf("") }
    var wilayaMenuExpanded by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                val base = apiBase.trim().trimEnd('/')
                val connection = (URL("$base/api/tenders?limit=200").openConnection() as HttpURLConnection)
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val array = root.optJSONArray("items") ?: org.json.JSONArray()
                    buildList {
                        for (i in 0 until array.length()) {
                            val o = array.getJSONObject(i)
                            add(TenderItem(o.optInt("id"), o.optString("title"), o.optString("wilaya"),
                                o.optString("kind"), o.optString("reference"), o.optString("deadline"),
                                o.optString("source_name", o.optString("source")), o.optString("source_url")))
                        }
                    }
                } finally { connection.disconnect() }
            }
            tenders = result
            message = "تم الاتصال بالخادم • ${result.size} إعلان"
        } catch (e: Exception) {
            message = "تعذر الاتصال: ${e.message ?: "تحقق من رابط الخادم"}"
        } finally { loading = false }
    }

    LaunchedEffect(apiBase) {
        while (true) {
            load()
            delay(15 * 60 * 1000L)
        }
    }

    val filtered = tenders.filter {
        (appliedQuery.isBlank() || it.title.contains(appliedQuery, true) || it.reference.contains(appliedQuery, true)) &&
        (appliedWilaya.isBlank() || it.wilaya.contains(appliedWilaya, true))
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = teal, background = Color(0xFFF3F6F9))) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF3F6F9))) {
            Column(Modifier.fillMaxWidth().background(navy).padding(18.dp)) {
                Text("KENDR PRO", color = Color.White, fontSize = 25.sp)
                Text("المناقصات والاستشارات الجزائرية • بحث حسب الولاية", color = Color(0xFFD5E3EF), fontSize = 13.sp)
            }
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(
                    value = apiBase, onValueChange = { apiBase = it },
                    label = { Text("رابط الخادم") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                Text("ابحث عن المناقصات", color = navy, fontSize = 17.sp)

                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("اكتب اسم المناقصة أو رقمها") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = wilaya.ifBlank { "كل الولايات" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اختر الولاية") },
                        trailingIcon = { Text("▼", color = teal) },
                        modifier = Modifier.fillMaxWidth().clickable { wilayaMenuExpanded = true },
                        singleLine = true
                    )
                    DropdownMenu(
                        expanded = wilayaMenuExpanded,
                        onDismissRequest = { wilayaMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("كل الولايات") },
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
                        onClick = { appliedQuery = query.trim(); appliedWilaya = wilaya },
                        modifier = Modifier.weight(1f)
                    ) { Text("بحث") }
                    OutlinedButton(
                        onClick = {
                            query = ""; wilaya = ""
                            appliedQuery = ""; appliedWilaya = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("مسح البحث") }
                }

                Button(
                    onClick = { scope.launch { load() } },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "جارٍ التحديث..." else "تحديث الإعلانات") }

                Text(message, color = if (message.startsWith("تم الاتصال")) teal else Color.DarkGray, fontSize = 12.sp)
                Text("عدد النتائج: ${filtered.size}", color = navy, fontSize = 13.sp)
            }

            if (filtered.isEmpty() && !loading) {
                Text(
                    "لا توجد نتائج مطابقة حالياً. جرّب ولاية أخرى أو امسح البحث.",
                    modifier = Modifier.padding(18.dp), color = Color.Gray
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
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
