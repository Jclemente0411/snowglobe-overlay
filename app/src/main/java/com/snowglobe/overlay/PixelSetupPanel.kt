package com.snowglobe.overlay

import android.os.Build
import android.os.PowerManager
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Public-platform setup facts. Quick tile installation cannot be inferred reliably. */
@Composable internal fun PixelSetupSummary(prefs:SharedPreferences,state:HolidayUiState,onDetails:()->Unit){
    val c=MaterialTheme.colorScheme
    val overlay=PixelSetupStatus.overlay(state.permission,EffectsService.isActive(),
        prefs.getBoolean("effectsEnabled",false),prefs.getString("_serviceState",""))
    Surface(onClick=onDetails,shape=RoundedCornerShape(26.dp),color=c.surface,border=BorderStroke(1.dp,c.outlineVariant.copy(alpha=.5f))){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Pixel setup",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Text("Explore ›",style=MaterialTheme.typography.labelMedium,color=c.primary)
            }
            Text(if(PixelSetupStatus.likelyPixel(Build.MANUFACTURER,Build.MODEL))
                "Made for your "+Build.MODEL else "Designed for Google Pixel · safe to preview on Android",
                style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            SetupStatusLine("Over-app effects",overlay)
            SetupStatusLine("Lock screen",PixelSetupStatus.wallpaper(state.lockCheckAvailable,state.lockApplied))
            SetupStatusLine("Quick Settings","Add a one-tap control")
        }
    }
}

@Composable internal fun PixelSetupGuide(prefs:SharedPreferences,state:HolidayUiState,actions:HolidayActions){
    val c=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Make your Pixel feel like Christmas. These are separate Android features, so setup stays honest and reversible.",
            style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant)
        Text("Once enabled, SnowGlobe checks again after unlocking and may restore effects after a reboot or app update. Pause stays paused. Android can still stop a background service; opening Studio is the manual fallback.",
            style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        SetupStep("01","Snow over your apps",
            PixelSetupStatus.overlay(state.permission,EffectsService.isActive(),
                prefs.getBoolean("effectsEnabled",false),prefs.getString("_serviceState","")),
            "SnowGlobe draws underneath system panels. Some shopping and login screens require Pause.",
            if(!state.permission)"Allow overlay" else if(EffectsService.isActive())"Pause effects" else "Start effects",
            {actions.enable(!EffectsService.isActive())})
        SetupStep("02","Snow on your lock screen",
            PixelSetupStatus.wallpaper(state.lockCheckAvailable,state.lockApplied),
            "Snow over apps cannot cover the secure lock screen. For snow behind the clock, choose SnowGlobe from the live wallpaper picker. This replaces your lock-screen picture, but leaves fingerprint controls to Android.",
            "Open wallpaper picker",actions.wallpaper)
        SetupStep("03","Your control-panel switch",
            PixelSetupStatus.quickTile(),
            "Add SnowGlobe to Quick Settings to pause or resume. Android does not permit ordinary apps to display snow above the notification shade.",
            "Add Quick Settings tile",actions.addTile)
        val manager=LocalContext.current.getSystemService(PowerManager::class.java)
        Surface(color=c.primaryContainer,shape=RoundedCornerShape(20.dp)){
            Column(Modifier.padding(16.dp)){
                Text("Power-aware by design",fontWeight=FontWeight.SemiBold,color=c.onPrimaryContainer)
                Text(if(manager?.isPowerSaveMode==true)
                    "Battery Saver is on. SnowGlobe reduces its animation budget."
                    else "Auto mode adapts the frame target to your display and lowers it for heat or Battery Saver.",
                    style=MaterialTheme.typography.bodySmall,color=c.onPrimaryContainer)
                Text("Battery life still needs measurement on a physical Pixel.",
                    style=MaterialTheme.typography.bodySmall,color=c.onPrimaryContainer)
            }
        }
        Text("Independent app, not affiliated with Google or Samsung. No notification access, app monitoring, or accessibility permission.",
            style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
    }
}
@Composable private fun SetupStatusLine(name:String,status:String){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(name,Modifier.weight(.42f),style=MaterialTheme.typography.labelMedium)
        Text(status,Modifier.weight(.58f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SetupStep(number:String,name:String,status:String,detail:String,actionLabel:String,action:()->Unit){
    val c=MaterialTheme.colorScheme
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=c.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Surface(color=c.primaryContainer,shape=RoundedCornerShape(12.dp)){
                    Text(number,modifier=Modifier.padding(10.dp),style=MaterialTheme.typography.labelLarge,color=c.onPrimaryContainer)
                }
                Text(name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            }
            Text(status,style=MaterialTheme.typography.labelMedium,color=c.primary)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            OutlinedButton(onClick=action,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(actionLabel)}
        }
    }
}