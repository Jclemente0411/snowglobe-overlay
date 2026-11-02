@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.snowglobe.overlay

import android.Manifest
import android.app.StatusBarManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
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

class StudioActivity:ComponentActivity(){
    private lateinit var prefs:SharedPreferences
    private lateinit var scenes:SavedScenesStore
    private val studioToken=Any()
    private var revision by mutableIntStateOf(0)
    private var permission by mutableStateOf(false)
    private var lockApplied by mutableStateOf(false)
    private var lockCheckAvailable by mutableStateOf(false)
    private var setupError by mutableStateOf<String?>(null)
    private var setupMessage by mutableStateOf<String?>(null)
    private var undo by mutableStateOf<SceneSnapshot?>(null)
    private var undoName="Custom"
    private val changes=SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){startWhenAllowed()}

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(8,19,34)))
        prefs=getSharedPreferences("snowglobe",Context.MODE_PRIVATE);scenes=SavedScenesStore(this)
        prefs.registerOnSharedPreferenceChangeListener(changes)
        if(!prefs.contains("effectsEnabled"))prefs.edit().putBoolean("effectsEnabled",false).apply()
        if(prefs.getInt("_settingsSchema",0)<14){
            val migration=prefs.edit().putInt("_settingsSchema",14).putString("selectedScene","Custom")
            if(!prefs.contains("lightMotion"))migration.putInt("lightMotion",if(prefs.getBoolean("twinkle",true))1 else 0)
            migration.apply()
        }
        savedInstanceState?.getString("undoScene")?.let{try{undo=SceneSnapshot.decode(it);undoName=savedInstanceState.getString("undoName","Custom")}catch(_:IllegalArgumentException){}}
        setContent{Studio()}
    }
    override fun onStart(){super.onStart();EffectsService.studioStarted(studioToken)}
    override fun onResume(){super.onResume();permission=Settings.canDrawOverlays(this);checkWallpaper();startWhenAllowed();revision++}
    override fun onStop(){EffectsService.studioStopped(studioToken);super.onStop()}
    override fun onDestroy(){EffectsService.studioStopped(studioToken);prefs.unregisterOnSharedPreferenceChangeListener(changes);super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){undo?.let{out.putString("undoScene",it.encode());out.putString("undoName",undoName)};super.onSaveInstanceState(out)}
    private fun startWhenAllowed(){
        if(!prefs.getBoolean("effectsEnabled",false)||!Settings.canDrawOverlays(this))return
        try{if(!EffectsService.isActive())startForegroundService(Intent(this,EffectsService::class.java).setAction(EffectsService.START))}
        catch(_:RuntimeException){setupError="Android could not start effects. Tap Start while SnowGlobe is open.";prefs.edit().putString("_serviceState","error").apply()}
    }
    private fun enable(value:Boolean){
        setupError=null
        if(!value){EffectsService.pauseNow(this);revision++;return}
        prefs.edit().putBoolean("effectsEnabled",true).apply()
        if(!Settings.canDrawOverlays(this)){
            try{startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
            catch(_:RuntimeException){setupError="Open Android Settings and allow SnowGlobe to display over other apps."}
            return
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startWhenAllowed()
    }
    private fun openPlaySafely(){
        EffectsService.pauseNow(this);revision++
        try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=$packageName")).setPackage("com.android.vending"))}
        catch(_:RuntimeException){setupMessage="Effects are paused. Open Google Play normally; resume SnowGlobe when finished."}
    }
    private fun addQuickTile(){
        setupError=null
        if(Build.VERSION.SDK_INT>=33){
            try{getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this,SnowGlobeTileService::class.java),"SnowGlobe",AndroidIcon.createWithResource(this,R.drawable.ic_stat_snowflake),mainExecutor
            ){result->setupMessage=when(result){
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED->"SnowGlobe is available in Quick Settings. Tap it to start or pause effects."
                else->"You can add SnowGlobe using the edit button in Quick Settings."
            }}}
            catch(_:RuntimeException){setupMessage="Open Quick Settings, tap Edit, then add SnowGlobe."}
        }else setupMessage="Swipe down twice, tap Edit, then drag SnowGlobe into Quick Settings."
    }
    private fun checkWallpaper(){
        lockApplied=false;lockCheckAvailable=Build.VERSION.SDK_INT>=34
        if(Build.VERSION.SDK_INT>=34)try{lockApplied=WallpaperStatus.isAppliedToLockScreen(this)}catch(_:RuntimeException){lockCheckAvailable=false}
    }
    private fun wallpaperPicker(){
        setupError=null
        val specific=Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(this,SnowWallpaperService::class.java))
        try{startActivity(specific)}catch(_:RuntimeException){
            try{startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))}
            catch(_:RuntimeException){setupError="This device does not expose a live wallpaper picker."}
        }
    }
    private fun changeBoolean(key:String,value:Boolean){
        val e=prefs.edit().putBoolean(key,value)
        if(key !in setOf("gentleMotion","reduceMotion","wallpaperSnow","wakeSnow","pixelColors"))e.putString("selectedScene","Custom")
        e.apply()
    }
    private fun changeNumber(key:String,value:Int){
        val e=prefs.edit().putInt(key,value)
        if(key=="lightMotion")e.putBoolean("twinkle",value!=0)
        if(key!="qualityMode")e.putString("selectedScene","Custom")
        e.apply()
    }
    private fun captureUndo(){undo=scenes.capture();undoName=prefs.getString("selectedScene","Custom")?:"Custom"}
    private fun restoreUndo(){undo?.let{scenes.apply(it,undoName)};undo=null;setupMessage="Previous visual settings restored. Playback and safety settings were not changed."}
    private fun applyScene(scene:String){
        captureUndo()
        val party=scene=="North Pole Party";val winter=scene=="Winter Wonderland";val gold=scene=="Golden Evening"
        val e=prefs.edit().putString("selectedScene",scene).putBoolean("snow",true).putBoolean("lights",true)
            .putInt("palette",if(party)3 else if(winter)1 else if(gold)2 else 0)
            .putInt("lightDesign",if(gold)2 else 0).putInt("bulbShape",if(winter)1 else if(gold)2 else 0)
            .putInt("lightMotion",if(party)3 else 1).putBoolean("twinkle",true)
            .putInt("speed",if(party)55 else 32).putInt("size",45).putInt("density",if(party)65 else 42)
            .putInt("snowStyle",1).putInt("particleDirection",0).putInt("particleWind",20).putInt("particleRotation",22)
            .putInt("lightBrightness",75).putInt("lightGlow",45).putInt("lightTempo",25)
            .putBoolean("screenSparkles",party).putInt("sparkleDensity",25).putBoolean("fallingLightBlink",true)
        listOf("candy","gifts","trees","snowmen","bulbs","stars","confetti").forEach{e.putBoolean(it,party)};e.apply()
    }
    @Composable private fun Studio(){
        val observed=revision
        val enabled=remember(observed){prefs.getBoolean("effectsEnabled",false)}
        val reduced=remember(observed){prefs.getBoolean("reduceMotion",false)}
        val dynamic=remember(observed){prefs.getBoolean("pixelColors",false)}
        var page by rememberSaveable{mutableIntStateOf(0)}
        val scrollStates=List(4){rememberLazyListState()};val pages=listOf("Home","Snow","Lights","Decor")
        val colors=if(dynamic&&Build.VERSION.SDK_INT>=31)dynamicDarkColorScheme(this) else darkColorScheme(
            primary=Mint,onPrimary=Night,secondary=Gold,onSecondary=Night,background=Night,onBackground=White,
            surface=Panel,onSurface=White,surfaceVariant=Raised,onSurfaceVariant=Muted)
        MaterialExpressiveTheme(colorScheme=colors,motionScheme=MotionScheme.expressive(),shapes=Shapes(large=RoundedCornerShape(28.dp),medium=RoundedCornerShape(20.dp))){
            Scaffold(containerColor=MaterialTheme.colorScheme.background,
                contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                topBar={Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
                    .padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("SNOWGLOBE",fontSize=11.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                        Text(if(page==0)"Christmas Studio" else pages[page]+" Studio",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    }
                    Image(painterResource(R.drawable.snowglobe_icon),null,Modifier.size(46.dp))
                }},
                bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal)){
                    val icons=listOf(R.drawable.ic_nav_home,R.drawable.ic_nav_snow,R.drawable.ic_nav_lights,R.drawable.ic_nav_decor)
                    pages.forEachIndexed{index,title->NavigationBarItem(selected=page==index,onClick={page=index},
                        icon={Icon(painterResource(icons[index]),null,Modifier.size(24.dp))},label={Text(title)})}
                }}
            ){padding->
                AnimatedContent(targetState=page,modifier=Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    transitionSpec={if(reduced)fadeIn(androidx.compose.animation.core.tween(0)) togetherWith fadeOut(androidx.compose.animation.core.tween(0))
                        else fadeIn(spring(stiffness=650f)) togetherWith fadeOut(spring(stiffness=850f))},label="studio-navigation"){current->
                    LazyColumn(state=scrollStates[current],contentPadding=PaddingValues(18.dp,6.dp,18.dp,28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                        when(current){
                            0->{
                                item{PreviewCard(enabled)}
                                item{ControlPanel()}
                                item{Section("Choose your atmosphere","Coordinated scenes. Every detail stays editable.")}
                                item{Scenes()}
                                item{SavedScenesPanel()}
                                item{Section("Make it yours","Snow, glass bulbs and a little Christmas magic.")}
                                item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                    pages.drop(1).forEachIndexed{i,name->FilledTonalButton(onClick={page=i+1},modifier=Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(8.dp)){Text(name)}}
                                }}
                                item{LockScreenPanel()}
                                item{PanelCard("Comfort & performance","Automatically gentler when your phone is warm."){
                                    Toggle("Gentle motion","gentleMotion",true,"Less drift and rotation")
                                    Toggle("Reduce motion","reduceMotion",false,"Still particles, steady bulbs and no surprise flashes")
                                    Choice("Animation quality","qualityMode",listOf("Eco - up to 30 fps","Balanced - up to 60 fps","Smooth - up to 120 fps"),1)
                                    Text("Previews are capped at 30 fps. Battery Saver and heat lower the rendering budget. Actual refresh rate depends on Android and your display.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(Build.VERSION.SDK_INT>=31)Toggle("Match Pixel colors","pixelColors",false,"Use your device's Material You palette in the Studio")
                                }}
                                item{Text("SnowGlobe ${BuildConfig.VERSION_NAME} beta • all features unlocked\nDesigned for Pixel. Independent app; not affiliated with Google or Samsung.\nSystem panels and fingerprint controls are not covered or intercepted.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(6.dp))}
                            }
                            1->{
                                item{ScenePreview()}
                                item{PanelCard("Snowfall","Soft layers with individual depth and motion."){
                                    Toggle("Falling snow","snow",true)
                                    SliderControl("Speed","speed",50);SliderControl("Size","size",50);SliderControl("Amount","density",55)
                                    Choice("Crystal shape","snowStyle",listOf("Soft snow","Six-point crystal","Minimal crystal"),1)
                                }}
                                item{PanelCard("Motion","The same controls move snow and decorations."){
                                    Choice("Direction","particleDirection",listOf("Down","Down-right","Down-left","Right","Left","Up","Up-right","Up-left"),0)
                                    SliderControl("Rotation","particleRotation",35);SliderControl("Wind & sway","particleWind",30)
                                }}
                            }
                            2->{
                                item{ScenePreview()}
                                item{PanelCard("Signature lights","Glass bulbs, gentle curves and a restrained glow."){
                                    Toggle("Top light strand","lights",true)
                                    Choice("Bulb shape","bulbShape",listOf("Vintage glass","Round globe","Micro fairy light"),0)
                                    Choice("Hanging style","lightDesign",listOf("Soft swags","Deep garland","Straight strand"),0)
                                    Choice("Colors","palette",listOf("Classic Christmas","Winter ice","Warm gold","Candy colors"),0)
                                    Choice("Light animation","lightMotion",listOf("Steady","Soft twinkle","Slow blink","Traveling wave","Alternating"),1)
                                    SliderControl("Bulb size","lightSize",55);SliderControl("Brightness","lightBrightness",75)
                                    var advanced by rememberSaveable{mutableStateOf(false)}
                                    TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide fine tuning" else "Fine tune glow & timing")}
                                    if(advanced){SliderControl("Glow","lightGlow",55);SliderControl("Animation pace","lightTempo",30)}
                                }}
                                item{PanelCard("Lights in motion","Let Christmas lights travel beyond the top edge."){
                                    Toggle("Falling light bulbs","bulbs",false,"Uses your selected glass shape and palette")
                                    Toggle("Blink falling bulbs","fallingLightBlink",true)
                                    Toggle("Floating fairy lights","screenSparkles",false,"Small lights fade in at random positions, then disappear")
                                    SliderControl("Floating light amount","sparkleDensity",30)
                                    Text("No rapid strobe or full-screen flashes. Reduce motion makes bulbs steady and disables floating flashes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }}
                            }
                            3->{
                                item{ScenePreview()}
                                item{PanelCard("Holiday details","Choose a few favorites, or celebrate with all of them."){
                                    listOf("Candy canes" to "candy","Presents" to "gifts","Christmas trees" to "trees","Snowmen" to "snowmen","Glass bulbs" to "bulbs","Golden stars" to "stars","Confetti" to "confetti").forEach{(name,key)->Toggle(name,key,false)}
                                }}
                            }
                        }
                    }
                }
            }
        }
    }
    @Composable private fun PreviewCard(enabled:Boolean){
        val observed=revision;val active=EffectsService.isActive();val state=prefs.getString("_serviceState","")
        PanelCard("Your Christmas, alive","The preview uses the same effects engine as your wallpaper."){
            Text(when{!permission->"Overlay permission needed";state=="error"->"Effects need attention";active->"Enabled - preview while you customize";enabled->"Ready to start";else->"Paused - preview only"},
                style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp));ScenePreview();Spacer(Modifier.height(16.dp))
            Button(onClick={enable(!active||!permission)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){
                Text(if(!permission)"Allow display over apps" else if(active)"Pause over-app effects" else "Start over-app effects",fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Pausing removes the overlay window completely. It stays paused until you start it again. Lock-screen wallpaper is separate.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            setupError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    }
    @Composable private fun ControlPanel(){
        PanelCard("Quick control & safe taps","Keep shopping and protected screens working normally."){
            Text("Some apps reject taps whenever any overlay is present. Pause SnowGlobe first; we do not bypass their protection or monitor which apps you use.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick={openPlaySafely()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Pause & open Google Play")}
            OutlinedButton(onClick={addQuickTile()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Add Quick Settings control")}
            Text("The tile controls effects. Snow cannot be placed over the notification shade using Android's normal overlay API.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            setupMessage?.let{Spacer(Modifier.height(8.dp));Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
    @Composable private fun ScenePreview(){
        val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
        val scene=remember{SnowView(context,prefs).apply{setPreviewMode(true);contentDescription="Live scene preview. Tap for a small snow flurry."}}
        DisposableEffect(lifecycle,scene){
            val observer=LifecycleEventObserver{_,_->scene.setAnimating(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
            lifecycle.addObserver(observer);scene.setAnimating(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            onDispose{lifecycle.removeObserver(observer);scene.setAnimating(false)}
        }
        AndroidView(factory={scene},modifier=Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp)))
    }
    @Composable private fun LockScreenPanel(){
        val observed=revision
        PanelCard("Lock-screen snowfall","A separate, system-selected live wallpaper."){
            Text(when{!lockCheckAvailable->"Check the selection in Android's wallpaper picker";lockApplied->"SnowGlobe is selected for the lock screen";else->"Not yet selected for the lock screen"},color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text("This replaces the selected wallpaper with SnowGlobe's scene. In Android's picker choose Lock screen or Home and lock screens when offered. Enabling the over-app effect alone does not set a wallpaper.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick={wallpaperPicker()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Set lock-screen wallpaper")}
            Toggle("Snow on wallpaper","wallpaperSnow",true);Toggle("Flurry when the screen wakes","wakeSnow",true)
            Text("Wake snowfall does not need a fingerprint tap. Touch flurries work only where Android sends wallpaper touches. Always-on display stays still to save power.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            prefs.getString("_wallpaperError",null)?.let{if(lockApplied)Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    @Composable private fun Scenes(){
        val observed=revision;val selected=remember(observed){prefs.getString("selectedScene","Custom")}
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            listOf("Classic Christmas" to "Red, green and vintage glass","Winter Wonderland" to "Icy globes and peaceful crystals","Golden Evening" to "Warm fairy lights and gentle snow","North Pole Party" to "Falling bulbs, gifts and floating lights").forEach{(name,description)->
                val chosen=selected==name
                val color by animateColorAsState(if(chosen)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,label="scene-selection")
                Surface(onClick={applyScene(name)},modifier=Modifier.fillMaxWidth(),color=color,shape=RoundedCornerShape(22.dp),border=if(chosen)BorderStroke(1.dp,MaterialTheme.colorScheme.primary)else null){
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.SemiBold);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        Text(if(chosen)"\u2713" else "\u203A",fontSize=22.sp,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if(undo!=null)TextButton(onClick={restoreUndo()}){Text("Undo last scene")}
        }
    }
    @Composable private fun SavedScenesPanel(){
        val observed=revision
        var expanded by rememberSaveable{mutableStateOf(false)}
        var editing by rememberSaveable{mutableStateOf<Int?>(null)}
        var name by rememberSaveable{mutableStateOf("")}
        PanelCard("My scenes","Three favorites, saved privately on this phone."){
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide saved scenes" else "Save or load a scene")}
            if(expanded)for(index in 0..2){
                val exists=scenes.exists(index)
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp)){
                    Text(if(exists)scenes.name(index) else "Empty slot ${index+1}",fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(enabled=exists,onClick={try{val snapshot=scenes.read(index);captureUndo();scenes.apply(snapshot,scenes.name(index))}catch(_:IllegalArgumentException){setupError="That saved scene could not be loaded. Save it again."}}){Text("Load")}
                        TextButton(onClick={name=if(exists)scenes.name(index)else "My scene ${index+1}";editing=index}){Text(if(exists)"Replace" else "Save current")}
                    }
                }
            }
            Text("Loading a scene changes visuals only. It never restarts paused effects or changes your power settings.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        editing?.let{slot->AlertDialog(onDismissRequest={editing=null},title={Text("Save current look")},text={
            Column{OutlinedTextField(value=name,onValueChange={name=it.take(32)},label={Text("Scene name")},singleLine=true)
                if(scenes.exists(slot))TextButton(onClick={scenes.delete(slot);revision++;editing=null}){Text("Delete saved scene")}}
        },confirmButton={TextButton(onClick={scenes.save(slot,name);revision++;editing=null}){Text("Save")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}})}
    }
    @Composable private fun Section(title:String,detail:String){Column(Modifier.padding(horizontal=6.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    @Composable private fun PanelCard(title:String,detail:String,content:@Composable ColumnScope.()->Unit){
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
            Column(Modifier.padding(20.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(5.dp));Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(16.dp));content()}
        }
    }
    @Composable private fun Toggle(title:String,key:String,default:Boolean,detail:String?=null){
        val observed=revision;val checked=remember(key,observed){prefs.getBoolean(key,default)}
        Row(Modifier.fillMaxWidth().heightIn(min=60.dp).toggleable(value=checked,role=Role.Switch,onValueChange={changeBoolean(key,it)}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge);if(detail!=null)Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Switch(checked=checked,onCheckedChange=null)
        }
    }
    @Composable private fun SliderControl(title:String,key:String,default:Int){
        val observed=revision;val saved=remember(observed,key){prefs.getInt(key,default).coerceIn(0,100)}
        var dragging by remember{mutableStateOf(false)};var value by remember(key){mutableFloatStateOf(saved.toFloat())}
        LaunchedEffect(saved){if(!dragging)value=saved.toFloat()}
        Column(Modifier.padding(vertical=8.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Text("${value.toInt()}%",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
            }
            Slider(value=value,valueRange=0f..100f,onValueChange={dragging=true;value=it},onValueChangeFinished={dragging=false;changeNumber(key,value.toInt())},modifier=Modifier.heightIn(min=48.dp))
        }
    }
    @Composable private fun Choice(title:String,key:String,options:List<String>,default:Int){
        val observed=revision;val index=remember(observed,key){prefs.getInt(key,default).coerceIn(options.indices)}
        var open by remember{mutableStateOf(false)}
        Column(Modifier.fillMaxWidth().padding(vertical=10.dp)){
            Text(title,style=MaterialTheme.typography.bodyLarge);Spacer(Modifier.height(8.dp))
            Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(18.dp)){
                Text(options[index],Modifier.weight(1f));Text("\u2304",Modifier.padding(start=8.dp))}
                DropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEachIndexed{i,label->DropdownMenuItem(text={Text(label)},onClick={changeNumber(key,i);open=false})}}
            }
        }
    }
}
