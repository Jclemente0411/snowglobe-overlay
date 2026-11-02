package com.snowglobe.overlay

import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF0B172A)
private val Surface = Color(0xFF192C43)
private val Mint = Color(0xFF98EAD8)
private val TextWhite = Color(0xFFF4F8FF)
private val Muted = Color(0xFFB8C8D9)

class StudioActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private var overlay: SnowView? = null
    private var manager: WindowManager? = null
    private var running by mutableStateOf(false)
    private var foreground = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("snowglobe", Context.MODE_PRIVATE)
        window.statusBarColor = android.graphics.Color.rgb(11,23,42)
        window.navigationBarColor = android.graphics.Color.rgb(11,23,42)
        window.decorView.systemUiVisibility = 0
        running = prefs.getBoolean("effectsEnabled",true)
        setContentView(ComposeView(this).apply { setContent { Studio() } })
        if(running && Settings.canDrawOverlays(this)) startEffects()
    }

    private fun sendEffect(action:String) {
        val intent=Intent(this,EffectsService::class.java).setAction(action)
        if(action==EffectsService.STOP) stopService(intent) else startForegroundService(intent)
    }
    private fun startEffects() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        sendEffect(EffectsService.START)
    }
    private fun stopEffects(){sendEffect(EffectsService.STOP)}
    private fun enable(value:Boolean) {
        prefs.edit().putBoolean("effectsEnabled",value).apply()
        running=value
        if(value) startEffects() else stopEffects()
    }
    private fun setBool(key:String,value:Boolean){prefs.edit().putBoolean(key,value).apply();Unit}
    private fun setNum(key:String,value:Int){prefs.edit().putInt(key,value).apply();overlay?.invalidate()}
    private fun preset(name:String) {
        val party=name=="North Pole Party"
        val editor=prefs.edit().putBoolean("snow",true).putBoolean("lights",true)
        editor.putInt("palette",when(name){"Winter Wonderland"->1;"North Pole Party"->3;else->0})
            .putInt("lightDesign",when(name){"Winter Wonderland"->1;"North Pole Party"->2;else->0})
            .putInt("speed",if(party)65 else 40).putInt("density",if(party)75 else 55)
        for(key in listOf("candy","gifts","trees","snowmen","bulbs","stars","confetti")) editor.putBoolean(key,party)
        editor.apply()
        overlay?.invalidate()
    }
    override fun onResume(){super.onResume();foreground=true;if(running && Settings.canDrawOverlays(this))sendEffect(EffectsService.HIDE)}
    override fun onStop(){foreground=false;if(running && Settings.canDrawOverlays(this))sendEffect(EffectsService.SHOW);super.onStop()}
    override fun onDestroy(){super.onDestroy()}

    @Composable private fun Studio() {
        var tab by remember { mutableIntStateOf(0) }
        val labels=listOf("Home","Snow","Lights","Decor")
        val scheme=darkColorScheme(primary=Mint,onPrimary=Night,background=Night,surface=Surface,onSurface=TextWhite,onBackground=TextWhite,secondary=Muted)
        MaterialTheme(colorScheme=scheme,shapes=Shapes(large=RoundedCornerShape(28.dp),medium=RoundedCornerShape(20.dp))) {
            Scaffold(
                containerColor=Night,
                contentWindowInsets=WindowInsets(0,0,0,0),
                topBar={ Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start=24.dp,end=24.dp,top=18.dp,bottom=12.dp)) {
                    Text("SnowGlobe  ❄",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Your personal Christmas studio",color=Muted,style=MaterialTheme.typography.bodyMedium)
                }},
                bottomBar={
                    NavigationBar(containerColor=Surface,windowInsets=NavigationBarDefaults.windowInsets) {
                        labels.forEachIndexed { index,name ->
                            NavigationBarItem(selected=tab==index,onClick={tab=index},
                                icon={Text(listOf("⌂","❄","✦","🎁")[index],fontSize=21.sp)},
                                label={Text(name)})
                        }
                    }
                }
            ) { insets ->
                AnimatedContent(targetState=tab,modifier=Modifier.fillMaxSize().padding(insets),transitionSpec={
                    (androidx.compose.animation.fadeIn(animationSpec=spring()) togetherWith androidx.compose.animation.fadeOut(animationSpec=spring()))
                },label="studio-navigation") { page ->
                    LazyColumn(contentPadding=PaddingValues(start=18.dp,end=18.dp,top=8.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        when(page) {
                            0 -> {
                                item { Panel("Christmas magic","Beautiful effects over your existing wallpaper") {
                                    Text(if(running && Settings.canDrawOverlays(this))"●  Effects ready — press Home to enjoy" else "○  Effects paused or awaiting permission",color=Mint)
                                    Spacer(Modifier.height(18.dp))
                                    Button(onClick={enable(true)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(20.dp)) { Text("Start Christmas magic") }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick={enable(false)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Pause effects") }
                                } }
                                item { Panel("Christmas scenes","Choose your favorite holiday mood") {
                                    listOf("Classic Christmas","Winter Wonderland","North Pole Party").forEach { name ->
                                        FilledTonalButton(onClick={preset(name)},modifier=Modifier.fillMaxWidth().padding(bottom=6.dp),shape=RoundedCornerShape(18.dp)) { Text(name) }
                                    }
                                } }
                                item { Panel("Comfort & performance","Adjust motion for your device") { BoolControl("Gentle motion","gentleMotion",true) } }
                                item { Text("Effects appear over supported apps. Android controls the notification shade and secure lock screen.",color=Muted,style=MaterialTheme.typography.bodySmall) }
                            }
                            1 -> item { Panel("Snowfall studio","Shape your perfect winter") {
                                BoolControl("Falling snow","snow",true)
                                NumControl("Snow speed","speed",50)
                                NumControl("Snowflake size","size",50)
                                NumControl("Snow density","density",55)
                                PickControl("Snowflake design","snowStyle",listOf("Soft snow","Six-point crystal","Icy crystal"),1)
                            } }
                            2 -> item { Panel("Christmas lights","Make your screen glow") {
                                BoolControl("Show Christmas lights","lights",true)
                                BoolControl("Animated twinkle","twinkle",true)
                                PickControl("Strand design","lightDesign",listOf("Festive garland","Draped lights","Retro bulbs"),0)
                                PickControl("Color theme","palette",listOf("Classic Christmas","Winter Wonderland","Golden glow","Candy rainbow"),0)
                                NumControl("Bulb size","lightSize",55)
                            } }
                            3 -> item { Panel("Falling decorations","Mix and match festive favorites") {
                                listOf("Candy canes" to "candy","Wrapped presents" to "gifts","Christmas trees" to "trees","Snowmen" to "snowmen","Christmas bulbs" to "bulbs","Golden stars" to "stars","Holiday confetti" to "confetti").forEach { (name,key) -> BoolControl(name,key,false) }
                            } }
                        }
                    }
                }
            }
        }
    }
    @Composable private fun Panel(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit) {
        Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
    @Composable private fun BoolControl(title:String,key:String,default:Boolean) {
        var checked by remember(key) { mutableStateOf(prefs.getBoolean(key,default)) }
        Row(Modifier.fillMaxWidth().heightIn(min=58.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Switch(checked=checked,onCheckedChange={checked=it;setBool(key,it)})
        }
    }
    @Composable private fun NumControl(title:String,key:String,default:Int) {
        var progress by remember(key) { mutableFloatStateOf(prefs.getInt(key,default).toFloat()) }
        Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(title,style=MaterialTheme.typography.bodyLarge)
                Text("${progress.toInt()}%",color=Mint)
            }
            Slider(value=progress,onValueChange={progress=it;setNum(key,it.toInt())},valueRange=0f..100f)
        }
    }
    @Composable private fun PickControl(title:String,key:String,options:List<String>,default:Int) {
        var selected by remember(key) { mutableIntStateOf(prefs.getInt(key,default).coerceIn(options.indices)) }
        var expanded by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) { Text(options[selected]+"  ▾") }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    options.forEachIndexed { index,option ->
                        DropdownMenuItem(text={Text(option)},onClick={selected=index;setNum(key,index);expanded=false})
                    }
                }
            }
        }
    }
}
