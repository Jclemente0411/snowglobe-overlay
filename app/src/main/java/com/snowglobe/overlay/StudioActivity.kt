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
import androidx.compose.runtime.*

/** UI host only. The 0.15 overlay, power and wallpaper lifecycle policies are preserved. */
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
        super.onCreate(savedInstanceState);systemBars(true)
        prefs=getSharedPreferences("snowglobe",Context.MODE_PRIVATE);scenes=SavedScenesStore(this)
        prefs.registerOnSharedPreferenceChangeListener(changes)
        if(!prefs.contains("effectsEnabled"))prefs.edit().putBoolean("effectsEnabled",false).apply()
        // Preserve existing quality selections; new installations start with Auto.
        if(!prefs.contains("qualityMode"))prefs.edit().putInt("qualityMode",3).apply()
        if(prefs.getInt("_settingsSchema",0)<14){
            val migration=prefs.edit().putInt("_settingsSchema",14).putString("selectedScene","Custom")
            if(!prefs.contains("lightMotion"))migration.putInt("lightMotion",if(prefs.getBoolean("twinkle",true))1 else 0)
            migration.apply()
        }
        savedInstanceState?.getString("undoScene")?.let{try{undo=SceneSnapshot.decode(it);undoName=savedInstanceState.getString("undoName","Custom")}catch(_:IllegalArgumentException){}}
        val actions=HolidayActions(::enable,::openPlaySafely,::addQuickTile,::wallpaperPicker,::changeBoolean,::changeNumber,
            ::applyScene,::restoreUndo,::saveScene,::loadScene,::deleteScene,::systemBars)
        setContent{HolidayStudio(prefs,scenes,HolidayUiState(revision,permission,lockApplied,lockCheckAvailable,setupError,setupMessage,undo!=null),actions)}
    }
    private fun systemBars(dark:Boolean){
        val clear=android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle=if(dark)SystemBarStyle.dark(clear)else SystemBarStyle.light(clear,clear),
            navigationBarStyle=if(dark)SystemBarStyle.dark(clear)else SystemBarStyle.light(clear,clear))
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
    private fun saveScene(index:Int,name:String){scenes.save(index,name);revision++}
    private fun deleteScene(index:Int){scenes.delete(index);revision++}
    private fun loadScene(index:Int){
        try{val saved=scenes.read(index);captureUndo();scenes.apply(saved,scenes.name(index))}
        catch(_:IllegalArgumentException){setupError="That saved scene could not be loaded. Save it again."}
    }
    private fun applyScene(scene:String){
        if(SceneCatalog.find(scene)==null){setupError="This scene is not available.";return}
        captureUndo()
        if(!SceneCatalog.apply(prefs,scene))setupError="Could not apply this scene."
    }
}
