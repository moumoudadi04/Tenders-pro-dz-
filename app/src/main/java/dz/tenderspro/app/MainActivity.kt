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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Tender(val title:String,val organization:String,val wilaya:String,val kind:String,val date:String)
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { TendersApp() } }
}
@Composable fun TendersApp() {
 val navy=Color(0xFF10243A); val teal=Color(0xFF00A896)
 var query by remember { mutableStateOf("") }; var selected by remember { mutableStateOf("الكل") }
 val sample=listOf(
  Tender("أشغال تهيئة وصيانة","بلدية عين كرشة","أم البواقي","مناقصة","17/11/2026"),
  Tender("اقتناء لوازم ومعدات مكتبية","مؤسسة عمومية","سطيف","استشارة","16/11/2026"),
  Tender("أشغال ترميم وصيانة","مديرية التجهيزات العمومية","قسنطينة","مناقصة","15/11/2026")
 )
 val filtered=sample.filter { (query.isBlank() || it.title.contains(query,true) || it.organization.contains(query,true) || it.wilaya.contains(query,true)) && (selected=="الكل" || it.kind==selected) }
 MaterialTheme(colorScheme=darkColorScheme(primary=teal,background=Color(0xFFF4F7FA),surface=Color.White,onSurface=navy)) {
  Column(Modifier.fillMaxSize().background(Color(0xFFF4F7FA))) {
   Column(Modifier.fillMaxWidth().background(navy).padding(20.dp)) {
    Text("Tenders-pro-dz",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
    Text("بوابتك لمتابعة المناقصات والاستشارات في الجزائر",color=Color(0xFFD5E3EF),fontSize=13.sp)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("ابحث عن إعلان أو مؤسسة أو ولاية")},singleLine=true,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,unfocusedBorderColor=Color.Transparent,focusedBorderColor=teal))
   }
   Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("الكل","مناقصة","استشارة").forEach { label ->
     FilterChip(selected==label,{selected=label},label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=teal,selectedLabelColor=Color.White))
    }
   }
   Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
    Text("الإعلانات",fontSize=20.sp,fontWeight=FontWeight.Bold,color=navy)
    Text("${filtered.size} نتائج",color=Color.Gray,fontSize=12.sp)
   }
   LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    items(filtered) { item ->
     Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(2.dp)) {
      Column(Modifier.padding(16.dp)) {
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(item.kind,color=teal,fontWeight=FontWeight.Bold,fontSize=12.sp)
        Text(item.date,color=Color.Gray,fontSize=12.sp)
       }
       Spacer(Modifier.height(8.dp))
       Text(item.title,color=navy,fontWeight=FontWeight.Bold,fontSize=17.sp,textAlign=TextAlign.Start)
       Spacer(Modifier.height(6.dp))
       Text(item.organization,color=Color.DarkGray,fontSize=14.sp)
       Text("الولاية: ${item.wilaya}",color=Color.Gray,fontSize=12.sp)
      }
     }
    }
    if(filtered.isEmpty()) item { Text("لا توجد نتائج مطابقة",Modifier.fillMaxWidth().padding(24.dp),textAlign=TextAlign.Center,color=Color.Gray) }
   }
   Text("نسخة أولية — البيانات المعروضة تجريبية وليست إعلانات رسمية",Modifier.fillMaxWidth().padding(8.dp),fontSize=10.sp,color=Color.Gray,textAlign=TextAlign.Center)
  }
 }
}
