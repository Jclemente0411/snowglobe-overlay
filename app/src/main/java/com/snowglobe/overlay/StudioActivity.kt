@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.snowglobe.overlay

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val Night=Color(0xFF081322)
private val Panel=Color(0xFF14253A)
private val Raised=Color(0xFF203B50)
private val Mint=Color(0xFF9DF2DA)
private val White=Color(0xFFF6F9FF)
private val Muted=Color(0xFFB9CBDC)
private val Gold=Color(0xFFFFD68C)

class StudioActivity: ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private var revision by mutableIntStateOf(0)
    private var permission by mutableStateOf(false)
    private var lockApplied by mutableStateOf(false)
    private var lockCheckAvailable by mutableStateOf(false)
    private var setupError by mutableStateOf<String?>(null)
    private val changes=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> revision++ }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { startWhenAllowed() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(8,19,34)))
        prefs=getSharedPreferences("snowglobe",Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(changes)
        if(prefs.getInt("_settingsSchema",0)<14) {
            val migration=prefs.edit().putInt("_settingsSchema",14).putString("selectedScene","Custom")
            if(!prefs.contains("lightMotion"))migration.putInt("lightMotion",if(prefs.getBoolean("twinkle",true))1 else 0)
            migration.apply()
        }
        setContent { Studio() }
    }
    override fun onResume() {
        super.onResume();EffectsService.setStudioVisible(true)
        permission=Settings.canDrawOverlays(this);checkWallpaper();startWhenAllowed();revision++
    }
    override fun onStop() { EffectsService.setStudioVisible(false);super.onStop() }
    override fun onDestroy() { prefs.unregisterOnSharedPreferenceChangeListener(changes);super.onDestroy() }
    private fun startWhenAllowed() {
        if(!prefs.getBoolean("effectsEnabled",true)||!Settings.canDrawOverlays(this))return
        try { if(!EffectsService.isActive())startForegroundService(Intent(this,EffectsService::class.java).setAction(EffectsService.START)) }
        catch(_: RuntimeException) { setupError="Android could not start the effects. Tap Start again while SnowGlobe is open." }
    }
    private fun enable(value: Boolean) {
        setupError=null;prefs.edit().putBoolean("effectsEnabled",value).apply()
        if(!value){stopService(Intent(this,EffectsService::class.java));return}
        if(!Settings.canDrawOverlays(this)) {
            try {startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
            catch(_: RuntimeException){setupError="Open Android Settings, then allow SnowGlobe to display over other apps."}
            return
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startWhenAllowed()
    }
    private fun checkWallpaper() {
        lockApplied=false;lockCheckAvailable=Build.VERSION.SDK_INT>=34
        if(Build.VERSION.SDK_INT>=34)try { lockApplied=WallpaperStatus.isAppliedToLockScreen(this) }
        catch(_: RuntimeException){lockCheckAvailable=false}
    }
    private fun wallpaperPicker() {
        setupError=null
        val specific=Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(this,SnowWallpaperService::class.java))
        try {startActivity(specific)} catch(_: RuntimeException) {
            try {startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))}
            catch(_: RuntimeException){setupError="This device does not expose a live wallpaper picker."}
        }
    }
    private fun changeBoolean(key: String,value: Boolean) {
        val e=prefs.edit().putBoolean(key,value)
        if(key !in setOf("gentleMotion","reduceMotion","wallpaperSnow","wakeSnow"))e.putString("selectedScene","Custom")
        e.apply()
    }
    private fun changeNumber(key: String,value: Int) {
        val e=prefs.edit().putInt(key,value)
        if(key=="lightMotion")e.putBoolean("twinkle",value!=0)
        if(key!="qualityMode")e.putString("selectedScene","Custom")
        e.apply()
    }
    private fun applyScene(scene: String) {
        val party=scene=="North Pole Party";val winter=scene=="Winter Wonderland";val gold=scene=="Golden Evening"
        val e=prefs.edit().putString("selectedScene",scene).putBoolean("snow",true).putBoolean("lights",true)
            .putInt("palette",if(party)3 else if(winter)1 else if(gold)2 else 0)
            .putInt("lightDesign",if(gold)2 else 0).putInt("bulbShape",if(winter)1 else if(gold)2 else 0)
            .putInt("lightMotion",if(party)3 else 1).putBoolean("twinkle",true)
            .putInt("speed",if(party)55 else 32).putInt("size",45).putInt("density",if(party)65 else 42)
            .putInt("snowStyle",1).putInt("particleDirection",0).putInt("particleWind",20).putInt("particleRotation",22)
            .putInt("lightBrightness",75).putInt("lightGlow",45).putInt("lightTempo",25)
            .putBoolean("screenSparkles",party).putInt("sparkleDensity",25).putBoolean("fallingLightBlink",true)
        listOf("candy","gifts","trees","snowmen","bulbs","stars","confetti").forEach { e.putBoolean(it,party) }
        e.apply()
    }
    @Composable private fun Studio() {
        val observed=revision
        val enabled=remember(observed){prefs.getBoolean("effectsEnabled",true)}
        val reduced=remember(observed){prefs.getBoolean("reduceMotion",false)}
        var page by rememberSaveable { mutableIntStateOf(0) }
        val scrollStates=List(4){rememberLazyListState()}
        val pages=listOf("Home","Snow","Lights","Decor")
        val theme=darkColorScheme(primary=Mint,onPrimary=Night,secondary=Gold,onSecondary=Night,
            background=Night,onBackground=White,surface=Panel,onSurface=White,surfaceVariant=Raised,onSurfaceVariant=Muted)
        MaterialExpressiveTheme(colorScheme=theme,motionScheme=MotionScheme.expressive(),
            shapes=Shapes(large=RoundedCornerShape(28.dp),medium=RoundedCornerShape(20.dp))) {
            Scaffold(containerColor=Night,
                contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                topBar={ Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
                    .padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SNOWGLOBE",fontSize=11.sp,letterSpacing=2.sp,color=Mint,fontWeight=FontWeight.Bold)
                        Text(if(page==0)"Christmas Studio" else pages[page]+" Studio",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    }
                    Image(painterResource(R.drawable.snowglobe_icon),null,Modifier.size(46.dp))
                }},
                bottomBar={ NavigationBar(containerColor=Panel,windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal)) {
                    pages.forEachIndexed { index,title -> NavigationBarItem(selected=page==index,onClick={page=index},
                        icon={Text(listOf("\u2302","\u2744","\u2726","\u25C7")[index],fontSize=23.sp)},label={Text(title)},
                        colors=NavigationBarItemDefaults.colors(indicatorColor=Mint,selectedIconColor=Night,selectedTextColor=Mint,
                            unselectedIconColor=Muted,unselectedTextColor=Muted)) }
                }}
            ) { padding ->
                AnimatedContent(targetState=page,modifier=Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    transitionSpec={if(reduced) fadeIn(androidx.compose.animation.core.tween(0)) togetherWith fadeOut(androidx.compose.animation.core.tween(0)) else fadeIn(spring(stiffness=650f)) togetherWith fadeOut(spring(stiffness=850f))},label="studio-navigation") { current ->
                    LazyColumn(state=scrollStates[current],contentPadding=PaddingValues(18.dp,6.dp,18.dp,28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        when(current) {
                            0 -> {
                                item { PreviewCard(enabled) }
                                item { Section("Choose your atmosphere","Coordinated scenes. Every detail stays editable.") }
                                item { Scenes() }
                                item { Section("Make it yours","Snow, glass bulbs and a little Christmas magic.") }
                                item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    pages.drop(1).forEachIndexed { i,name -> FilledTonalButton(onClick={page=i+1},modifier=Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(8.dp)){Text(name)} }
                                } }
                                item { LockScreenPanel() }
                                item { PanelCard("Comfort & performance","Choose the feel, not just the amount.") {
                                    Toggle("Gentle motion","gentleMotion",true,"Less drift and rotation")
                                    Toggle("Reduce motion","reduceMotion",false,"Still particles, steady bulbs and no surprise flashes")
                                    Choice("Animation quality","qualityMode",listOf("Eco - up to 30 fps","Balanced - up to 60 fps","Smooth - up to 120 fps"),1)
                                    Text("Android controls the actual refresh rate. Battery Saver limits rendering to 30 fps.",style=MaterialTheme.typography.bodySmall,color=Muted)
                                } }
                                item { Text("SnowGlobe 0.14.0 beta\nSystem panels and fingerprint controls are not covered or intercepted.",style=MaterialTheme.typography.bodySmall,color=Muted,modifier=Modifier.padding(6.dp)) }
                            }
                            1 -> {
                                item { ScenePreview() }
                                item { PanelCard("Snowfall","Soft layers with individual depth and motion.") {
                                    Toggle("Falling snow","snow",true)
                                    SliderControl("Speed","speed",50);SliderControl("Size","size",50);SliderControl("Amount","density",55)
                                    Choice("Crystal shape","snowStyle",listOf("Soft snow","Six-point crystal","Minimal crystal"),1)
                                } }
                                item { PanelCard("Motion","The same controls move snow and decorations.") {
                                    Choice("Direction","particleDirection",listOf("Down","Down-right","Down-left","Right","Left","Up","Up-right","Up-left"),0)
                                    SliderControl("Rotation","particleRotation",35);SliderControl("Wind & sway","particleWind",30)
                                } }
                            }
                            2 -> {
                                item { ScenePreview() }
                                item { PanelCard("Signature lights","Glass bulbs, gentle curves and a restrained glow.") {
                                    Toggle("Top light strand","lights",true)
                                    Choice("Bulb shape","bulbShape",listOf("Vintage glass","Round globe","Micro fairy light"),0)
                                    Choice("Hanging style","lightDesign",listOf("Soft swags","Deep garland","Straight strand"),0)
                                    Choice("Colors","palette",listOf("Classic Christmas","Winter ice","Warm gold","Candy colors"),0)
                                    Choice("Light animation","lightMotion",listOf("Steady","Soft twinkle","Slow blink","Traveling wave","Alternating"),1)
                                    SliderControl("Bulb size","lightSize",55);SliderControl("Brightness","lightBrightness",75)
                                    var advanced by rememberSaveable {mutableStateOf(false)}
                                    TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide fine tuning" else "Fine tune glow & timing")}
                                    if(advanced){SliderControl("Glow","lightGlow",55);SliderControl("Animation pace","lightTempo",30)}
                                } }
                                item { PanelCard("Lights in motion","Let Christmas lights travel beyond the top edge.") {
                                    Toggle("Falling light bulbs","bulbs",false,"Uses your selected glass shape and palette")
                                    Toggle("Blink falling bulbs","fallingLightBlink",true)
                                    Toggle("Floating fairy lights","screenSparkles",false,"Small lights fade in at random positions, then disappear")
                                    SliderControl("Floating light amount","sparkleDensity",30)
                                    Text("No rapid strobe or full-screen flashes. Reduce motion makes bulbs steady and disables floating flashes.",style=MaterialTheme.typography.bodySmall,color=Muted)
                                } }
                            }
                            3 -> {
                                item { ScenePreview() }
                                item { PanelCard("Holiday details","Choose a few favorites, or celebrate with all of them.") {
                                    listOf("Candy canes" to "candy","Presents" to "gifts","Christmas trees" to "trees","Snowmen" to "snowmen","Glass bulbs" to "bulbs","Golden stars" to "stars","Confetti" to "confetti").forEach{(name,key)->Toggle(name,key,false)}
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
    @Composable private fun PreviewCard(enabled: Boolean) {
        val observed=revision;val active=EffectsService.isActive();val state=prefs.getString("_serviceState","")
        PanelCard("Your Christmas, alive","The preview below uses the actual effects engine.") {
            Text(when { !permission->"Overlay permission needed";state=="error"->"Effects need attention";active->"On - preview while you customize";enabled->"Starting effects...";else->"Paused - preview only" },
                style=MaterialTheme.typography.labelLarge,color=if(active)Mint else Gold)
            Spacer(Modifier.height(12.dp));ScenePreview();Spacer(Modifier.height(16.dp))
            Button(onClick={enable(!active||!permission)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Text(if(!permission)"Allow display over apps" else if(active)"Pause over-app effects" else "Start over-app effects",fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Over-app effects appear when you leave SnowGlobe. Your home wallpaper stays unchanged.",style=MaterialTheme.typography.bodySmall,color=Muted)
            setupError?.let { Text(it,color=Gold,style=MaterialTheme.typography.bodySmall) }
        }
    }
    @Composable private fun ScenePreview() {
        val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
        val scene=remember { SnowView(context,prefs).apply {setPreviewMode(true);contentDescription="Live scene preview. Tap for a small snow flurry."} }
        DisposableEffect(lifecycle,scene) {
            val observer=LifecycleEventObserver { _,_ -> scene.setAnimating(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
            lifecycle.addObserver(observer);scene.setAnimating(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            onDispose {lifecycle.removeObserver(observer);scene.setAnimating(false)}
        }
        AndroidView(factory={scene},modifier=Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(24.dp)))
    }
    @Composable private fun LockScreenPanel() {
        PanelCard("Lock-screen snowfall","Over-app permission does not activate a live wallpaper.") {
            Text(when {lockApplied->"SnowGlobe is selected for the lock screen";lockCheckAvailable->"Not applied to the lock screen";else->"Confirm the destination in Android's wallpaper picker"},color=if(lockApplied)Mint else Gold,style=MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Text("This replaces the selected wallpaper with SnowGlobe's winter scene. In the system picker choose Lock screen, or Home and lock screen when that is the available option.",color=Muted,style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick={wallpaperPicker()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Set lock-screen wallpaper")}
            Toggle("Snow on wallpaper","wallpaperSnow",true);Toggle("Flurry when the screen wakes","wakeSnow",true)
            Text("Wake snowfall does not need a fingerprint tap. Touch flurries work only where Android sends wallpaper touches. Always-on display stays still to save power.",style=MaterialTheme.typography.bodySmall,color=Muted)
            val error=prefs.getString("_wallpaperError",null);if(lockApplied&&error!=null)Text(error,color=Gold)
        }
    }
    @Composable private fun Scenes() {
        val observed=revision;val selected=remember(observed){prefs.getString("selectedScene","Custom")}
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("Classic Christmas" to "Red, green and vintage glass","Winter Wonderland" to "Icy globes and peaceful crystals","Golden Evening" to "Warm fairy lights and gentle snow","North Pole Party" to "Falling bulbs, gifts and floating lights").forEach { (name,description) ->
                val chosen=selected==name;val color by animateColorAsState(if(chosen)Color(0xFF24484C) else Panel,label="scene-selection")
                Surface(onClick={applyScene(name)},modifier=Modifier.fillMaxWidth(),color=color,shape=RoundedCornerShape(22.dp),border=if(chosen)BorderStroke(1.dp,Mint) else null) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.SemiBold);Text(description,style=MaterialTheme.typography.bodySmall,color=Muted)}
                        Text(if(chosen)"\u2713" else "\u203A",fontSize=22.sp,color=Mint)
                    }
                }
            }
        }
    }
    @Composable private fun Section(title: String,detail: String) {
        Column(Modifier.padding(horizontal=6.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(detail,style=MaterialTheme.typography.bodySmall,color=Muted)}
    }
    @Composable private fun PanelCard(title: String,detail: String,content: @Composable ColumnScope.()->Unit) {
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
            Column(Modifier.padding(20.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(5.dp));Text(detail,style=MaterialTheme.typography.bodySmall,color=Muted);Spacer(Modifier.height(16.dp));content()}
        }
    }
    @Composable private fun Toggle(title: String,key: String,default: Boolean,detail: String?=null) {
        val observed=revision;val checked=remember(key,observed){prefs.getBoolean(key,default)}
        Row(Modifier.fillMaxWidth().heightIn(min=60.dp).toggleable(value=checked,role=Role.Switch,onValueChange={changeBoolean(key,it)}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge);if(detail!=null)Text(detail,style=MaterialTheme.typography.bodySmall,color=Muted)}
            Switch(checked=checked,onCheckedChange=null)
        }
    }
    @Composable private fun SliderControl(title: String,key: String,default: Int) {
        val observed=revision;val saved=remember(observed,key){prefs.getInt(key,default).coerceIn(0,100)}
        var dragging by remember {mutableStateOf(false)};var value by remember(key){mutableFloatStateOf(saved.toFloat())}
        LaunchedEffect(saved){if(!dragging)value=saved.toFloat()}
        Column(Modifier.padding(vertical=8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Text("${value.toInt()}%",color=Mint,style=MaterialTheme.typography.labelLarge)
            }
            Slider(value=value,valueRange=0f..100f,onValueChange={dragging=true;value=it},onValueChangeFinished={dragging=false;changeNumber(key,value.toInt())},modifier=Modifier.heightIn(min=48.dp))
        }
    }
    @Composable private fun Choice(title: String,key: String,options: List<String>,default: Int) {
        val observed=revision;val index=remember(observed,key){prefs.getInt(key,default).coerceIn(options.indices)}
        var open by remember {mutableStateOf(false)}
        Column(Modifier.fillMaxWidth().padding(vertical=10.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge);Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(18.dp)) {Text(options[index],Modifier.weight(1f));Text("\u2304",Modifier.padding(start=8.dp))}
                DropdownMenu(expanded=open,onDismissRequest={open=false}) { options.forEachIndexed { i,label->DropdownMenuItem(text={Text(label)},onClick={changeNumber(key,i);open=false}) } }
        }
    }
}
