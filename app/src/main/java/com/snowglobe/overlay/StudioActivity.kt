package com.snowglobe.overlay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.WallpaperManager
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF081322)
private val PanelColor = Color(0xFF14253A)
private val Raised = Color(0xFF1C344B)
private val Mint = Color(0xFF9DF2DA)
private val White = Color(0xFFF6F9FF)
private val Subtle = Color(0xFFB4C7D9)
private val Gold = Color(0xFFFFD68C)

class StudioActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private var running by mutableStateOf(false)
    private var permissionReady by mutableStateOf(false)
    private var settingsRevision by mutableIntStateOf(0)
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settingsRevision++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("snowglobe", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
        running = prefs.getBoolean("effectsEnabled", true)
        permissionReady = Settings.canDrawOverlays(this)
        window.statusBarColor = android.graphics.Color.rgb(8,19,34)
        window.navigationBarColor = android.graphics.Color.rgb(8,19,34)
        setContentView(androidx.compose.ui.platform.ComposeView(this).apply { setContent { Studio() } })
    }

    private fun send(action: String) {
        val intent = Intent(this, EffectsService::class.java).setAction(action)
        if (action == EffectsService.STOP) stopService(intent)
        else if (action == EffectsService.START) startForegroundService(intent)
        else startService(intent)
    }
    private fun enable(value: Boolean) {
        if (value && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        prefs.edit().putBoolean("effectsEnabled", value).apply()
        running = value
        if (value) {
            send(EffectsService.START)
            send(EffectsService.HIDE)
        } else send(EffectsService.STOP)
    }
    override fun onResume() {
        super.onResume()
        permissionReady = Settings.canDrawOverlays(this)
        running = prefs.getBoolean("effectsEnabled", true)
        if (running && permissionReady) {
            send(EffectsService.START)
            send(EffectsService.HIDE)
        }
    }
    override fun onPause() {
        if (running && permissionReady) send(EffectsService.SHOW)
        super.onPause()
    }
    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(changeListener)
        super.onDestroy()
    }

    private fun openWallpaperPicker() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@StudioActivity, SnowWallpaperService::class.java))
            })
        } catch (_: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun setBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); settingsRevision++ }
    private fun setNum(key: String, value: Int) { prefs.edit().putInt(key, value).apply(); settingsRevision++ }

    private fun preset(name: String) {
        val party = name == "North Pole Party"
        val edit = prefs.edit().putString("selectedScene", name).putBoolean("snow", true).putBoolean("lights", true)
        edit.putInt("palette", when(name) { "Winter Wonderland" -> 1; "North Pole Party" -> 3; else -> 0 })
            .putInt("lightDesign", when(name) { "Winter Wonderland" -> 1; "North Pole Party" -> 2; else -> 0 })
            .putInt("speed", if(party) 65 else 40).putInt("density", if(party) 75 else 55)
        for(key in listOf("candy","gifts","trees","snowmen","bulbs","stars","confetti")) edit.putBoolean(key, party)
        edit.apply()
        settingsRevision++
    }

    @Composable private fun Studio() {
        var page by remember { mutableIntStateOf(0) }
        val pages = listOf("Home", "Snow", "Lights", "Decor")
        val icons = listOf("⌂", "❄", "✦", "✿")
        val scheme = darkColorScheme(primary=Mint, onPrimary=Night, background=Night, surface=PanelColor,
            onSurface=White, onBackground=White, secondary=Gold, onSecondary=Night, surfaceVariant=Raised)
        MaterialTheme(colorScheme=scheme, shapes=Shapes(large=RoundedCornerShape(30.dp), medium=RoundedCornerShape(22.dp))) {
            Scaffold(containerColor=Night, contentWindowInsets=WindowInsets(0,0,0,0),
                topBar={
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=24.dp, vertical=16.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SNOWGLOBE", fontSize=11.sp, letterSpacing=3.sp, color=Mint, fontWeight=FontWeight.Bold)
                            Text(if(page==0) "Christmas Studio" else pages[page] + " Studio",
                                style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold, letterSpacing=(-0.6).sp)
                        }
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Raised),contentAlignment=Alignment.Center) {
                            Text("❄",fontSize=26.sp,color=Mint)
                        }
                    }
                },
                bottomBar={
                    NavigationBar(containerColor=PanelColor, windowInsets=NavigationBarDefaults.windowInsets) {
                        pages.forEachIndexed { index, title ->
                            NavigationBarItem(selected=page==index,onClick={page=index},
                                icon={ Text(icons[index],fontSize=23.sp) },label={ Text(title) },
                                colors=NavigationBarItemDefaults.colors(selectedIconColor=Night,
                                    selectedTextColor=Mint, indicatorColor=Mint, unselectedIconColor=Subtle,
                                    unselectedTextColor=Subtle))
                        }
                    }
                }
            ) { padding ->
                AnimatedContent(targetState=page, modifier=Modifier.fillMaxSize().padding(padding),
                    transitionSpec={ fadeIn(tween(240)) togetherWith fadeOut(tween(150)) },label="studio-pages") { current ->
                    LazyColumn(contentPadding=PaddingValues(start=18.dp,end=18.dp,top=4.dp,bottom=28.dp),
                        verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        when(current) {
                            0 -> {
                                item { Hero() }
                                item { SectionHeader("Find your Christmas mood", "Tap a scene to transform your atmosphere") }
                                item { Scenes() }
                                item { PremiumPanel("Lock screen snowfall", "Set SnowGlobe as a live wallpaper for your lock screen") {
                                    Text("Android controls which screens use a live wallpaper. Choose lock screen when the system offers that option.", color=Subtle, style=MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(onClick={openWallpaperPicker()}, modifier=Modifier.fillMaxWidth()) { Text("Set SnowGlobe live wallpaper") }
                                } }
                                item { SectionHeader("Make it yours", "Fine-tune the magic in just a few taps") }
                                item { QuickModules(onSelect={page=it}) }
                                item { PremiumPanel("Comfort & performance", "A calmer atmosphere when you want it") {
                                    BoolControl("Gentle motion", "gentleMotion", true)
                                } }
                                item { Text("Effects appear over supported apps. Android may restrict secure lock screens and system panels.",
                                    color=Subtle, style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(horizontal=8.dp)) }
                            }
                            1 -> {
                                item { PremiumPanel("Snowfall", "Create the perfect winter atmosphere") {
                                    BoolControl("Falling snow","snow",true)
                                    NumControl("Snow speed","speed",50)
                                    NumControl("Snowflake size","size",50)
                                    NumControl("Snow density","density",55)
                                    PickControl("Snowflake design","snowStyle",listOf("Soft snow","Six-point crystal","Icy crystal"),1)
                                } }
                                item { PremiumPanel("Particle motion", "Give each snowflake and decoration personality") {
                                    PickControl("Travel direction","particleDirection",
                                        listOf("Downward","Diagonal right","Diagonal left","Sideways right","Sideways left"),0)
                                    NumControl("Rotation","particleRotation",35)
                                    NumControl("Wind & sway","particleWind",30)
                                } }
                            }
                            2 -> {
                                item { PremiumPanel("Holiday lights", "A warm glow for every moment") {
                                    BoolControl("Christmas lights","lights",true)
                                    BoolControl("Animated twinkle","twinkle",true)
                                    PickControl("Light strand","lightDesign",listOf("Festive garland","Draped lights","Retro bulbs"),0)
                                    PickControl("Color palette","palette",listOf("Classic Christmas","Winter Wonderland","Golden glow","Candy rainbow"),0)
                                    NumControl("Bulb size","lightSize",55)
                                } }
                            }
                            3 -> {
                                item { PremiumPanel("Falling decorations", "Mix your favorite festive details") {
                                    listOf("Candy canes" to "candy","Wrapped presents" to "gifts",
                                        "Christmas trees" to "trees","Snowmen" to "snowmen",
                                        "Christmas bulbs" to "bulbs","Golden stars" to "stars",
                                        "Holiday confetti" to "confetti").forEach { (label,key) ->
                                        BoolControl(label,key,false)
                                    }
                                } }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun Hero() {
        val animation=rememberInfiniteTransition(label="snow-preview")
        val travel by animation.animateFloat(initialValue=0f,targetValue=1f,
            animationSpec=infiniteRepeatable(tween(9000, easing=androidx.compose.animation.core.LinearEasing),
                RepeatMode.Restart),label="snow-travel")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF24445D),Color(0xFF10283D),Color(0xFF0C1A2D))))
            .padding(22.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(shape=RoundedCornerShape(50),color=if(running && permissionReady) Color(0xFF234C48) else Raised) {
                    Text(if(running && permissionReady)"●  MAGIC ENABLED" else if(!permissionReady)"●  PERMISSION NEEDED" else "○  EFFECTS PAUSED",
                        color=if(running && permissionReady) Mint else Gold,fontSize=11.sp,fontWeight=FontWeight.Bold,
                        letterSpacing=1.sp,modifier=Modifier.padding(horizontal=13.dp,vertical=9.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val points=70
                    for(i in 0 until points) {
                        val x=((i*0.61803398875f)%1f)*size.width
                        val y=(((i*0.38196601125f+travel*(.55f+(i%5)*.14f))%1f))*size.height
                        drawCircle(color=White.copy(alpha=.25f+(i%4)*.13f),
                            radius=1.5f+(i%4)*1.3f,center=Offset(x,y))
                    }
                    drawCircle(color=Mint.copy(alpha=.28f),radius=size.minDimension*.23f,
                        center=Offset(size.width*.52f,size.height*.5f),style=Stroke(width=2.5f))
                }
                Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("❄",fontSize=62.sp,color=White)
                    Text("Let it snow",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                }
            }
            Text("Your screen. Your Christmas magic.",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("Snow, sparkling lights and festive details that follow you across supported apps.",
                color=Subtle,style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            Button(onClick={enable(!running || !permissionReady)},modifier=Modifier.fillMaxWidth().height(56.dp),
                shape=RoundedCornerShape(19.dp)) {
                Text(if(!permissionReady)"Allow display over apps" else if(running)"Pause Christmas magic" else "Start Christmas magic",
                    fontWeight=FontWeight.Bold)
            }
        }
    }

    @Composable private fun SectionHeader(title:String, subtitle:String) {
        Column(Modifier.padding(start=7.dp,end=7.dp,top=6.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(subtitle,color=Subtle,style=MaterialTheme.typography.bodySmall)
        }
    }

    @Composable private fun Scenes() {
        val revision=settingsRevision
        val selected=remember(revision) { prefs.getString("selectedScene","Classic Christmas") ?: "Classic Christmas" }
        val scenes=listOf(
            Triple("Classic Christmas","Timeless red, green & gold","🎄"),
            Triple("Winter Wonderland","Cool tones & peaceful snow","❄"),
            Triple("North Pole Party","Bright, playful holiday fun","🎁"))
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            scenes.forEach { (name, description, emoji) ->
                val active=selected==name
                val background by animateColorAsState(if(active)Color(0xFF254A4C) else PanelColor,label="scene-color")
                Surface(onClick={preset(name)},shape=RoundedCornerShape(23.dp),
                    color=background,border=if(active)BorderStroke(1.dp,Mint.copy(alpha=.7f)) else null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Raised),
                            contentAlignment=Alignment.Center) { Text(emoji,fontSize=28.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(name,fontWeight=FontWeight.SemiBold)
                            Text(description,color=Subtle,style=MaterialTheme.typography.bodySmall)
                        }
                        Text(if(active)"✓" else "›",color=if(active)Mint else Subtle,fontSize=24.sp)
                    }
                }
            }
        }
    }

    @Composable private fun QuickModules(onSelect:(Int)->Unit) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(Triple("Snow","❄",1),Triple("Lights","✦",2),Triple("Decor","🎁",3)).forEach { (label,icon,page) ->
                Surface(onClick={onSelect(page)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp),
                    color=PanelColor) {
                    Column(Modifier.padding(vertical=18.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(icon,fontSize=27.sp,color=Mint)
                        Spacer(Modifier.height(8.dp))
                        Text(label,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    @Composable private fun PremiumPanel(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit) {
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),
            colors=CardDefaults.cardColors(containerColor=PanelColor)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle,color=Subtle,style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }

    @Composable private fun BoolControl(title:String,key:String,default:Boolean) {
        val revision=settingsRevision
        val checked=remember(key,revision) { prefs.getBoolean(key,default) }
        Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Switch(checked=checked,onCheckedChange={setBool(key,it)})
        }
    }
    @Composable private fun NumControl(title:String,key:String,default:Int) {
        val revision=settingsRevision
        val saved=remember(key,revision) { prefs.getInt(key,default) }
        var progress by remember(key) { mutableFloatStateOf(saved.toFloat()) }
        LaunchedEffect(saved) { progress=saved.toFloat() }
        Column(Modifier.fillMaxWidth().padding(vertical=9.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(title,style=MaterialTheme.typography.bodyLarge)
                Text("${progress.toInt()}%",color=Mint,fontWeight=FontWeight.SemiBold)
            }
            Slider(value=progress,onValueChange={progress=it;setNum(key,it.toInt())},valueRange=0f..100f)
        }
    }
    @Composable private fun PickControl(title:String,key:String,options:List<String>,default:Int) {
        val revision=settingsRevision
        val selected=remember(key,revision) { prefs.getInt(key,default).coerceIn(options.indices) }
        var expanded by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(9.dp))
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(17.dp)) {
                    Text(options[selected],modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("⌄",fontSize=20.sp)
                }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    options.forEachIndexed { index,option ->
                        DropdownMenuItem(text={Text(option)},onClick={setNum(key,index);expanded=false})
                    }
                }
            }
        }
    }
}
