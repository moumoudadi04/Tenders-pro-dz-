package dz.tenderspro.app

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TendersLiveApp() }
    }
}

@Composable
fun TendersLiveApp() {
    val navy = Color(0xFF10243A)
    val teal = Color(0xFF00A896)
    var selectedSource by remember { mutableStateOf("live") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("المناقصات الحقيقية") }

    BackHandler(enabled = selectedSource != "home") {
        if (webView?.canGoBack() == true) webView?.goBack() else selectedSource = "home"
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = teal, background = Color(0xFFF4F7FA))) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF4F7FA))) {
            Column(Modifier.fillMaxWidth().background(navy).padding(18.dp)) {
                Text("Tenders-pro-dz", color = Color.White, fontSize = 23.sp)
                Text("متابعة المناقصات والإعلانات في الجزائر", color = Color(0xFFD5E3EF), fontSize = 13.sp)
            }

            if (selectedSource == "home") {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("مصادر المناقصات", style = MaterialTheme.typography.titleLarge, color = navy)
                    Text("اختر مصدراً لعرض الإعلانات المنشورة فعلياً. قد تتطلب بعض الوثائق التسجيل لدى الجهة الناشرة.", color = Color.DarkGray)
                    SourceButton("الإعلانات الحية — Algeria Tenders", "قائمة إعلانات محدثة وروابط تفاصيلها", teal) {
                        selectedSource = "live"; pageTitle = "الإعلانات الحية"
                    }
                    SourceButton("البوابة الإلكترونية الرسمية للصفقات العمومية", "وزارة المالية — البحث والإعلانات الرسمية", navy) {
                        selectedSource = "official"; pageTitle = "البوابة الرسمية"
                    }
                    SourceButton("BOMOP — النشرة الرسمية لصفقات المتعامل العمومي", "الوكالة الوطنية للنشر والإشهار ANEP", navy) {
                        selectedSource = "bomop"; pageTitle = "BOMOP"
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("ملاحظة: Algeria Tenders منصة تجميع تجارية وليست جهة حكومية. اعتمد على الإعلان الأصلي والبوابة الرسمية للتحقق من الشروط والآجال.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selectedSource = "home" }) { Text("رجوع للمصادر") }
                    Text(pageTitle, color = navy, modifier = Modifier.padding(top = 10.dp))
                    TextButton(onClick = { webView?.reload() }) { Text("تحديث") }
                }
                val url = when (selectedSource) {
                    "official" -> "https://marches-publics.gov.dz/"
                    "bomop" -> "https://bomop.anep.dz/ar/"
                    else -> "https://www.algeriatenders.com/appel-d-offres"
                }
                key(selectedSource) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadUrl(url)
                                webView = this
                            }
                        },
                        update = { webView = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceButton(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = color, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
