@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.snowglobe.overlay

import android.os.Build
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class HolidayUiState(val revision:Int,val permission:Boolean,val lockApplied:Boolean,
    val lockCheckAvailable:Boolean,val error:String?,val message:String?,val canUndo:Boolean)
internal class HolidayActions(
    val enable:(Boolean)->Unit,val pauseAndOpenPlay:()->Unit,val addTile:()->Unit,val wallpaper:()->Unit,
    val setBoolean:(String,Boolean)->Unit,val setNumber:(String,Int)->Unit,val preset:(String)->Unit,
    val undo:()->Unit,val save:(Int,String)->Unit,val load:(Int)->Unit,val delete:(Int)->Unit,
    val systemBars:(Boolean)->Unit,val keyboardSettings:()->Unit,val keyboardPicker:()->Unit)

@Composable internal fun HolidayStudio(prefs:SharedPreferences,scenes:SavedScenesStore,state:HolidayUiState,actions:HolidayActions){
    val context=LocalContext.current
    val appearance=remember(context){HolidayAppearanceStore(context)}
    var theme by remember{mutableStateOf(appearance.theme())}
    var page by rememberSaveable{mutableIntStateOf(0)}
    var sheet by rememberSaveable{mutableStateOf<String?>(null)}
    val scrollStates=List(4){rememberLazyListState()}
    val reduced=remember(state.revision){prefs.getBoolean("reduceMotion",false)}
    val pixel=theme==HolidayThemeTokens.PIXEL&&Build.VERSION.SDK_INT>=31
    val palette=remember(theme){HolidayThemeTokens.palette(theme)}
    val dark=if(pixel)isSystemInDarkTheme()else palette.dark
    val colors=if(pixel){if(dark)dynamicDarkColorScheme(context)else dynamicLightColorScheme(context)}else holidayColors(palette)
    LaunchedEffect(dark){actions.systemBars(dark)}
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(state.error,state.message){(state.error?:state.message)?.let{snackbar.showSnackbar(it)}}
    MaterialExpressiveTheme(colorScheme=colors,motionScheme=MotionScheme.expressive(),
        shapes=Shapes(small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(22.dp),large=RoundedCornerShape(30.dp))){
        val pages=listOf("Home","Snow","Lights","Decor")
        val enter=MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit=MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        Scaffold(containerColor=colors.background,contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            snackbarHost={SnackbarHost(snackbar)},
            topBar={
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
                    .padding(horizontal=22.dp,vertical=8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Image(painterResource(R.drawable.snowglobe_icon),null,Modifier.size(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SNOWGLOBE",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,letterSpacing=2.sp,
                            color=colors.primary,fontWeight=FontWeight.Bold)
                        IconButton(onClick={sheet="appearance"}){Icon(painterResource(R.drawable.ic_studio_palette),"Appearance",Modifier.size(24.dp))}
                        IconButton(onClick={sheet="settings"}){Icon(painterResource(R.drawable.ic_studio_settings),"Settings and safety",Modifier.size(24.dp))}
                    }
                    Text(if(page==0)"Christmas Studio"else pages[page]+" Studio",style=MaterialTheme.typography.headlineMedium,
                        fontWeight=FontWeight.SemiBold,modifier=Modifier.semantics{heading()})
                }
            },
            bottomBar={NavigationBar(containerColor=colors.surface,windowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal)){
                val icons=listOf(R.drawable.ic_nav_home,R.drawable.ic_nav_snow,R.drawable.ic_nav_lights,R.drawable.ic_nav_decor)
                pages.forEachIndexed{index,title->NavigationBarItem(selected=page==index,onClick={page=index},
                    icon={Icon(painterResource(icons[index]),null,Modifier.size(24.dp))},label={Text(title)},
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=colors.onPrimary,indicatorColor=colors.primary,
                        selectedTextColor=colors.primary,unselectedIconColor=colors.onSurfaceVariant,unselectedTextColor=colors.onSurfaceVariant))}
            }}
        ){padding->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter){
                AnimatedContent(targetState=page,modifier=Modifier.widthIn(max=720.dp).fillMaxSize(),label="holiday-pages",
                    transitionSpec={if(reduced)fadeIn(tween(0)) togetherWith fadeOut(tween(0))else fadeIn(enter) togetherWith fadeOut(exit)}){current->
                    LazyColumn(state=scrollStates[current],contentPadding=PaddingValues(18.dp,8.dp,18.dp,28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                        when(current){
                            0->{
                                item{HolidayHero(prefs,state,actions,previewActive=sheet==null)}
                                item{PixelSetupSummary(prefs,state,onDetails={sheet="setup"})}
                                item{HolidayCard("Christmas RGB keyboard","An optional SnowGlobe keyboard with festive lights. Enable it in Android settings, then select it as your keyboard."){
                                    OutlinedButton(onClick=actions.keyboardSettings,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("1. Enable Christmas keyboard")}
                                    OutlinedButton(onClick=actions.keyboardPicker,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("2. Choose Christmas keyboard")}
                                    Text("Typing stays on your device. This beta keyboard does not yet include Gboard-style suggestions, swipe typing or multilingual autocorrect. Switch back to Gboard at any time.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }
                                item{HolidayCard("Snow visibility","Make flakes subtle or bright without changing how many fall."){
                                    HolidaySlider(prefs,state,actions,"Snow visibility","snowVisibility",100)
                                    Text("0% hides flakes; 100% makes them brightest. Snow amount and speed are in the Snow tab.",
                                        style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }}
                                item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){HolidayHeading("Christmas magic");Text("Five complete looks, one tap.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                                    TextButton(onClick={sheet="saved"}){Text("My scenes")}
                                }}
                                item{HolidayScenes(prefs,state,actions)}
                                item{HolidayCard("Touch-friendly mode","Top-edge effects are the default for better tap compatibility."){
                                    HolidayToggle(prefs,state,actions,"Effects near the top only","edgeOnly",true,
                                        "Keep the falling effects in the top quarter of the screen.")
                                    Text("Full-screen effects can block taps in other apps, including checkout. Keep top-only on for safer use; Pause before payment or login screens.",
                                        style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }}
                                if(state.canUndo)item{TextButton(onClick=actions.undo){Text("Undo last scene")}}
                                item{Surface(onClick={sheet="appearance"},color=colors.primaryContainer,shape=RoundedCornerShape(24.dp)){
                                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                                        HolidaySprig(Modifier.size(48.dp),colors.onPrimaryContainer)
                                        Column(Modifier.weight(1f)){Text("Dress up your Studio",fontWeight=FontWeight.SemiBold,color=colors.onPrimaryContainer)
                                            Text(themeLabel(theme)+" · change appearance",style=MaterialTheme.typography.bodySmall,color=colors.onPrimaryContainer)}
                                        Text("\u203A",color=colors.onPrimaryContainer,fontSize=24.sp)
                                    }
                                }}
                                item{HolidayCard("Magic beyond the app","The wallpaper and over-app effects are separate."){
                                    Text(if(state.lockApplied)"SnowGlobe is selected for your lock screen."else "Bring the same scene to your lock screen through Android's wallpaper picker.",style=MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(onClick={sheet="settings"},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Wallpaper & controls")}
                                }}
                                item{Text("SnowGlobe ${BuildConfig.VERSION_NAME} beta · all features unlocked\nDesigned for Pixel. Independent, not affiliated with Google or Samsung.",
                                    style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,modifier=Modifier.padding(horizontal=6.dp))}
                            }
                            1->{
                                item{HolidayPreview(prefs,sheet==null,150)}
                                item{HolidayCard("Make snow visible","Adjust for light or dark app backgrounds without affecting touch security."){
                                    HolidaySlider(prefs,state,actions,"Snow visibility","snowVisibility",100)
                                    HolidayChoice(prefs,state,actions,"Snow shade","snowShade",
                                        listOf("Bright white","Soft silver","Dark charcoal"),0)
                                    Text("Bright snow is easier to see on dark screens. Charcoal snow is easier to see on light screens. Zero visibility hides the snow but not other enabled decorations.",
                                        style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }}
                                item{HolidayCard("Snowfall","Choose your perfect winter flurry."){
                                    HolidayToggle(prefs,state,actions,"Falling snow","snow",true)
                                    HolidaySlider(prefs,state,actions,"Speed","speed",50);HolidaySlider(prefs,state,actions,"Size","size",50);HolidaySlider(prefs,state,actions,"Amount","density",55)
                                    HolidayChoice(prefs,state,actions,"Crystal shape","snowStyle",listOf("Soft snow","Six-point crystal","Minimal crystal"),1)
                                }}
                                item{HolidayCard("A little movement","These controls also move falling decorations."){
                                    HolidayChoice(prefs,state,actions,"Direction","particleDirection",listOf("Down","Down-right","Down-left","Right","Left","Up","Up-right","Up-left"),0)
                                    HolidaySlider(prefs,state,actions,"Rotation","particleRotation",35);HolidaySlider(prefs,state,actions,"Wind & sway","particleWind",30)
                                }}
                            }
                            2->{
                                item{HolidayPreview(prefs,sheet==null,150)}
                                item{HolidayCard("Signature lights","Dress your screen in a warm Christmas glow."){
                                    HolidayToggle(prefs,state,actions,"Top light strand","lights",true)
                                    HolidayChoice(prefs,state,actions,"Bulb shape","bulbShape",listOf("Vintage glass","Round globe","Micro fairy light"),0)
                                    HolidayChoice(prefs,state,actions,"Hanging style","lightDesign",listOf("Soft swags","Deep garland","Straight strand"),0)
                                    HolidayChoice(prefs,state,actions,"Colors","palette",listOf("Classic Christmas","Winter ice","Warm gold","Candy colors"),0)
                                    HolidayChoice(prefs,state,actions,"Light animation","lightMotion",listOf("Steady","Soft twinkle","Slow blink","Traveling wave","Alternating","Vintage incandescent"),1)
                                    HolidaySlider(prefs,state,actions,"Bulb size","lightSize",55);HolidaySlider(prefs,state,actions,"Brightness","lightBrightness",75)
                                    var fine by rememberSaveable{mutableStateOf(false)}
                                    TextButton(onClick={fine=!fine}){Text(if(fine)"Hide fine tuning"else "Fine tune glow & timing")}
                                    if(fine){HolidaySlider(prefs,state,actions,"Glow","lightGlow",55);HolidaySlider(prefs,state,actions,"Animation pace","lightTempo",30)}
                                }}
                                item{HolidayCard("A little extra sparkle","Small, playful lights beyond the top edge."){
                                    HolidayToggle(prefs,state,actions,"Falling light bulbs","bulbs",false)
                                    HolidayToggle(prefs,state,actions,"Blink falling bulbs","fallingLightBlink",true)
                                    HolidayToggle(prefs,state,actions,"Floating fairy lights","screenSparkles",false,"Fade in and out at different positions")
                                    HolidaySlider(prefs,state,actions,"Floating light amount","sparkleDensity",30)
                                    Text("Reduce motion makes bulbs steady and disables floating flashes.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }}
                            }
                            3->{
                                item{HolidayPreview(prefs,sheet==null,150)}
                                item{HolidayCard("Holiday details","Little things that make it feel like Christmas."){
                                    listOf("Candy canes" to "candy","Presents" to "gifts","Christmas trees" to "trees","Snowmen" to "snowmen","Glass bulbs" to "bulbs","Golden stars" to "stars","Confetti" to "confetti").forEach{(title,key)->HolidayToggle(prefs,state,actions,title,key,false)}
                                }}
                            }
                        }
                    }
                }
            }
        }
        sheet?.let{open->
            ModalBottomSheet(onDismissRequest={sheet=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
                containerColor=colors.background,contentColor=colors.onBackground){
                LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding(),contentPadding=PaddingValues(20.dp,0.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(when(open){"appearance"->"Studio appearance";"saved"->"My Christmas scenes";"setup"->"Set up your Pixel";else->"Settings & safety"},
                            modifier=Modifier.weight(1f).semantics{heading()},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        TextButton(onClick={sheet=null}){Text("Done")}
                    }}
                    when(open){
                        "appearance"->{
                            item{Text("A different look for the app itself. Your snow, lights, wallpaper, saved scenes and Pause stay exactly as they are.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
                            val ids=listOf(HolidayThemeTokens.CLASSIC,HolidayThemeTokens.EVERGREEN,HolidayThemeTokens.CANDLELIGHT,HolidayThemeTokens.PIXEL)
                            ids.forEach{id->item{ThemeOption(id,theme==id){appearance.select(id);theme=id}}}
                        }
                        "saved"->{item{SavedHolidayScenes(scenes,state,actions)}}
                        "setup"->{item{PixelSetupGuide(prefs,state,actions)}}
                        else->{
                            item{OutlinedButton(onClick={sheet="setup"},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Pixel setup guide")}}
                            item{HolidayCard("Safe taps & quick control","Pause before shopping or protected screens."){
                                HolidayToggle(prefs,state,actions,"Top-edge-only effects","edgeOnly",true,
                                    "A smaller overlay leaves most of the screen uncovered. Checkout may still require Pause.")
                                OutlinedButton(onClick=actions.pauseAndOpenPlay,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Pause & open Google Play")}
                                OutlinedButton(onClick=actions.addTile,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Add Quick Settings control")}
                                Text("Enabled effects automatically recheck after unlock. Pause always stays paused. If Android stops the service, use Start or the Quick Settings tile. Protected apps may still reject taps until Pause; snow cannot cover the notification shade.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }}
                            item{HolidayCard("Lock-screen snowfall","Selected separately through Android."){
                                Text(when{!state.lockCheckAvailable->"Check the selection in Android's wallpaper picker.";state.lockApplied->"Selected for the lock screen.";else->"Not yet selected for the lock screen."},color=colors.primary)
                                Spacer(Modifier.height(8.dp))
                                Text("Over-app snow cannot appear on the secure lock screen. For effects behind the clock, select SnowGlobe as the live wallpaper. This replaces your lock-screen picture; choose Lock screen or both when offered.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                OutlinedButton(onClick=actions.wallpaper,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Set lock-screen wallpaper")}
                                HolidayToggle(prefs,state,actions,"Snow on wallpaper","wallpaperSnow",true)
                                HolidayToggle(prefs,state,actions,"Flurry when the screen wakes","wakeSnow",true)
                                Text("No fingerprint interception. Wallpaper touches depend on Android. Always-on display stays still.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }}
                            item{HolidayCard("Comfort & performance","Keep the atmosphere comfortable."){
                                HolidayToggle(prefs,state,actions,"Gentle motion","gentleMotion",true,"Less drift and rotation")
                                HolidayToggle(prefs,state,actions,"Reduce motion","reduceMotion",false,"Still particles, steady lights, no surprise flashes")
                                HolidayChoice(prefs,state,actions,"Animation quality","qualityMode",listOf("Eco - up to 30 fps","Balanced - up to 60 fps","Smooth - up to 120 fps","Auto - adapts to your screen"),3)
                                Text("Auto is recommended. It selects a conservative 30 or 60 fps target from your display; Battery Saver and heat lower the limit further. Smooth is an upper limit, not a guarantee.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }}
                            item{Text("All beta features are unlocked. No checkout or paywall is active.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                        }
                    }
                }
            }
        }
    }
}

private fun holidayColors(p:HolidayThemeTokens.Palette):ColorScheme{
    val base=if(p.dark)darkColorScheme()else lightColorScheme()
    return base.copy(primary=Color(p.primary),onPrimary=Color(p.onPrimary),primaryContainer=Color(p.container),onPrimaryContainer=Color(p.onContainer),
        secondary=Color(p.secondary),onSecondary=Color(p.onSecondary),secondaryContainer=Color(p.raised),onSecondaryContainer=Color(p.text),
        tertiary=Color(p.secondary),onTertiary=Color(p.onSecondary),tertiaryContainer=Color(p.container),onTertiaryContainer=Color(p.onContainer),
        background=Color(p.background),onBackground=Color(p.text),surface=Color(p.surface),onSurface=Color(p.text),
        surfaceVariant=Color(p.raised),onSurfaceVariant=Color(p.muted),outline=Color(p.outline),outlineVariant=Color(p.outline).copy(alpha=.45f),
        surfaceTint=Color(p.primary),surfaceContainer=Color(p.surface),surfaceContainerLow=Color(p.background),surfaceContainerHigh=Color(p.raised),surfaceContainerHighest=Color(p.raised))
}
private fun themeLabel(id:String)=when(id){HolidayThemeTokens.EVERGREEN->"Evergreen Lodge";HolidayThemeTokens.CANDLELIGHT->"Candlelight";HolidayThemeTokens.PIXEL->"Pixel colors";else->"Classic Christmas"}

@Composable private fun ThemeOption(id:String,selected:Boolean,onSelect:()->Unit){
    val p=HolidayThemeTokens.palette(id);val c=MaterialTheme.colorScheme
    Surface(color=if(selected)c.primaryContainer else c.surface,shape=RoundedCornerShape(24.dp),
        border=if(selected)BorderStroke(2.dp,c.primary)else BorderStroke(1.dp,c.outlineVariant)){
        Row(Modifier.fillMaxWidth().selectable(selected=selected,role=Role.RadioButton,onClick=onSelect)
            .semantics{contentDescription="Use ${themeLabel(id)} theme"}.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Color(p.background)),contentAlignment=Alignment.Center){
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){listOf(p.primary,p.secondary,p.raised).forEach{Box(Modifier.size(11.dp).clip(CircleShape).background(Color(it)))}}
            }
            Column(Modifier.weight(1f)){
                Text(themeLabel(id),fontWeight=FontWeight.SemiBold)
                Text(when(id){HolidayThemeTokens.EVERGREEN->"Pine, woodland and antique gold";HolidayThemeTokens.CANDLELIGHT->"Warm cream, cranberry and paper";HolidayThemeTokens.PIXEL->"Your device's Material You colors";else->"Velvet cranberry and Christmas gold"},style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            }
            RadioButton(selected=selected,onClick=null)
        }
    }
}

@Composable private fun HolidayHero(prefs:SharedPreferences,state:HolidayUiState,actions:HolidayActions,previewActive:Boolean){
    val c=MaterialTheme.colorScheme;val active=EffectsService.isActive()
    val service=remember(state.revision){prefs.getString("_serviceState","")}
    Surface(shape=RoundedCornerShape(30.dp),color=c.surface,border=BorderStroke(1.dp,c.outlineVariant)){
        Column(Modifier.background(Brush.verticalGradient(listOf(c.surface,c.primaryContainer.copy(alpha=.32f)))).padding(20.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("YOUR HOLIDAY, YOUR WAY",style=MaterialTheme.typography.labelSmall,letterSpacing=1.6.sp,color=c.primary)
                    Text("Make room for magic.",fontFamily=FontFamily.Serif,fontWeight=FontWeight.Medium,fontSize=27.sp,lineHeight=32.sp)
                }
                HolidaySprig(Modifier.size(48.dp),c.secondary)
            }
            Text("Current scene: "+(prefs.getString("selectedScene","Custom")?:"Custom"),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            Spacer(Modifier.height(14.dp));HolidayPreview(prefs,previewActive,174)
            Spacer(Modifier.height(12.dp))
            Text(when{!state.permission->"Overlay permission needed";service=="error"->"Effects need attention";active->"Over-app effects on";else->"Paused · preview only"},
                style=MaterialTheme.typography.labelLarge,color=if(service=="error")c.error else c.primary)
            Spacer(Modifier.height(10.dp))
            Button(onClick={actions.enable(!active||!state.permission)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)){
                Text(if(!state.permission)"Allow display over apps"else if(active)"Pause over-app effects"else "Start over-app effects",fontWeight=FontWeight.Bold)
            }
            TextButton(onClick=actions.pauseAndOpenPlay,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Pause & open Google Play")}
        }
    }
}

@Composable private fun HolidayPreview(prefs:SharedPreferences,active:Boolean,height:Int){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val view=remember(context){SnowView(context,prefs).apply{setPreviewMode(true);contentDescription="Live effect preview. Tap for a snow flurry."}}
    DisposableEffect(lifecycle,view,active){
        val observer=LifecycleEventObserver{_,_->view.setAnimating(active&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
        lifecycle.addObserver(observer);view.setAnimating(active&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose{lifecycle.removeObserver(observer);view.setAnimating(false)}
    }
    Surface(shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Box{
            AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(height.dp))
            Surface(color=Color(0xD90B1826),shape=RoundedCornerShape(10.dp),modifier=Modifier.align(Alignment.BottomStart).padding(10.dp)){
                Text("LIVE PREVIEW",fontSize=10.sp,letterSpacing=1.sp,color=Color(0xFFFFF0DB),modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp))
            }
        }
    }
}

@Composable private fun HolidayScenes(prefs:SharedPreferences,state:HolidayUiState,actions:HolidayActions){
    val c=MaterialTheme.colorScheme
    val chosen=remember(state.revision){prefs.getString("selectedScene","Custom")}
    val recipes=remember{SceneCatalog.presets()}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
        items(recipes.size){index->
            val recipe=recipes[index]
            val selected=chosen==recipe.name
            Surface(onClick={actions.preset(recipe.name)},
                modifier=Modifier.width(222.dp).semantics{contentDescription="Apply "+recipe.name+" scene"},
                shape=RoundedCornerShape(24.dp),color=if(selected)c.primaryContainer else c.surface,
                border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)c.primary else c.outlineVariant)){
                Column{
                    HolidayArtwork(recipe.illustration,Modifier.fillMaxWidth().height(122.dp))
                    Column(Modifier.padding(16.dp)){
                        Text(recipe.name,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(recipe.description,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,minLines=2)
                        Spacer(Modifier.height(10.dp))
                        Text(if(selected)"Selected ✓"else "Apply scene",style=MaterialTheme.typography.labelMedium,color=c.primary)
                    }
                }
            }
        }
    }
}

@Composable private fun SavedHolidayScenes(scenes:SavedScenesStore,state:HolidayUiState,actions:HolidayActions){
    var editing by rememberSaveable{mutableStateOf<Int?>(null)}
    var name by rememberSaveable{mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Keep three favorite combinations on this phone. Loading changes visuals only, never Pause or power settings.",style=MaterialTheme.typography.bodyMedium)
        for(index in 0..2){
            val exists=scenes.exists(index)
            HolidayCard(if(exists)scenes.name(index)else "Your scene ${index+1}",if(exists)"Saved on this phone"else "An empty spot for your favorite look"){
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OutlinedButton(enabled=exists,onClick={actions.load(index)}){Text("Load")}
                    TextButton(onClick={name=if(exists)scenes.name(index)else "My Christmas ${index+1}";editing=index}){Text(if(exists)"Replace"else "Save current")}
                }
            }
        }
        if(state.canUndo)TextButton(onClick=actions.undo){Text("Undo last scene")}
    }
    editing?.let{index->AlertDialog(onDismissRequest={editing=null},title={Text("Save your Christmas")},
        text={Column{OutlinedTextField(value=name,onValueChange={name=it.take(32)},label={Text("Scene name")},singleLine=true)
            if(scenes.exists(index))TextButton(onClick={actions.delete(index);editing=null}){Text("Delete saved scene")}}},
        confirmButton={TextButton(onClick={actions.save(index,name);editing=null}){Text("Save")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}})}
}

@Composable private fun HolidayHeading(title:String){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.semantics{heading()})}
@Composable private fun HolidayCard(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){
    val c=MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=c.surface),border=BorderStroke(1.dp,c.outlineVariant.copy(alpha=.4f))){
        Column(Modifier.padding(20.dp)){HolidayHeading(title);Spacer(Modifier.height(5.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant);Spacer(Modifier.height(14.dp));content()}
    }
}
@Composable private fun HolidayToggle(prefs:SharedPreferences,state:HolidayUiState,actions:HolidayActions,title:String,key:String,default:Boolean,detail:String?=null){
    val checked=remember(key,state.revision){prefs.getBoolean(key,default)}
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).toggleable(value=checked,role=Role.Switch,onValueChange={actions.setBoolean(key,it)}).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge);if(detail!=null)Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked=checked,onCheckedChange=null)
    }
}
@Composable private fun HolidaySlider(prefs:SharedPreferences,state:HolidayUiState,actions:HolidayActions,title:String,key:String,default:Int){
    val saved=remember(key,state.revision){prefs.getInt(key,default).coerceIn(0,100)}
    var value by remember(key){mutableFloatStateOf(saved.toFloat())};var dragging by remember{mutableStateOf(false)}
    LaunchedEffect(saved){if(!dragging)value=saved.toFloat()}
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer){Text("${value.toInt()}%",color=MaterialTheme.colorScheme.onPrimaryContainer,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp))}
        }
        Slider(value=value,valueRange=0f..100f,onValueChange={dragging=true;value=it},onValueChangeFinished={dragging=false;actions.setNumber(key,value.toInt())},
            modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription=title})
    }
}
@Composable private fun HolidayChoice(prefs:SharedPreferences,state:HolidayUiState,actions:HolidayActions,title:String,key:String,options:List<String>,default:Int){
    val index=remember(key,state.revision){prefs.getInt(key,default).coerceIn(options.indices)}
    var open by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(vertical=10.dp)){
        Text(title,style=MaterialTheme.typography.bodyLarge);Spacer(Modifier.height(8.dp))
        Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)){
            Text(options[index],Modifier.weight(1f));Text("\u2304",Modifier.padding(start=8.dp))}
            DropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEachIndexed{i,label->DropdownMenuItem(text={Text(label)},onClick={actions.setNumber(key,i);open=false})}}
        }
    }
}
