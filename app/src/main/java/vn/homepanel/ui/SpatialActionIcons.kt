package vn.homepanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.AppStore
import vn.homepanel.R
import vn.homepanel.ha.*

enum class ActionGlyph { POWER, MINUS, PLUS, MORE, LIST, UP, DOWN, STOP, DASHBOARD }

/** Transparent root: only a short name/status pill and individual action circles are drawn. */
@Composable fun SpatialActionIcons(store: AppStore, bindingId: String, onDetails: () -> Unit) {
    val state by store.state.collectAsState()
    val locale=LocalConfiguration.current.locales[0]
    val binding=state.bindings.find { it.id==bindingId } ?: return
    val entity=state.catalog.entities[binding.entityId] ?: return
    val enabled=state.connected && entity.available && entity.id !in state.busy
    val power=compactPowerAction(entity,state.catalog)
    val adjustment=compactAdjustment(entity,state.catalog)
    val status=if (!state.connected) stringResource(R.string.rooms_stale) else if (!entity.available) stringResource(R.string.unavailable) else displayState(entity)
    val name=if(entity.name==binding.label) binding.label else "${binding.label} · ${entity.name}"
    val caption=state.message ?: "$name · $status"
    Column(Modifier.fillMaxSize().padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterVertically)) {
        Text(caption,fontSize=13.sp,lineHeight=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=Color.White,modifier=Modifier.background(Color(0xE6172930),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=5.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
            if (power!=null) {
                val turningOn=power==Control.Power(true) || power is Control.Mode && power.value!="off"
                SpatialIconButton(ActionGlyph.POWER,stringResource(if(turningOn) R.string.power_on else R.string.power_off),enabled,active=entity.available && entity.state!="off") { store.control(entity.id,power) }
            }
            if(adjustment!=null) {
                val label=stringResource(adjustment.label)
                SpatialIconButton(ActionGlyph.MINUS,stringResource(R.string.value_decrease,label),enabled && adjustment.value>adjustment.min) { store.control(entity.id,adjustment.action(false)) }
                val value=if(adjustment.value%1f==0f) adjustment.value.toInt().toString() else "%.1f".format(locale,adjustment.value)
                Text(value+adjustment.suffix,color=Color.White,fontSize=19.sp,modifier=Modifier.background(Color(0xE6172930),RoundedCornerShape(8.dp)).padding(6.dp))
                SpatialIconButton(ActionGlyph.PLUS,stringResource(R.string.value_increase,label),enabled && adjustment.value<adjustment.max) { store.control(entity.id,adjustment.action(true)) }
            }
            if(entity.domain=="cover") {
                listOf(Triple(ActionGlyph.UP,"open_cover",1),Triple(ActionGlyph.STOP,"stop_cover",8),Triple(ActionGlyph.DOWN,"close_cover",2)).forEach { (glyph,service,feature) ->
                    if(entity.supports(feature) && state.catalog.supports("cover",service)) SpatialIconButton(glyph,stringResource(when(glyph) { ActionGlyph.UP->R.string.open;ActionGlyph.DOWN->R.string.close;else->R.string.stop }),enabled) { store.control(entity.id,Control.Cover(service)) }
                }
            }
            SpatialIconButton(ActionGlyph.MORE,stringResource(R.string.all_controls),true,onClick=onDetails)
        }
        if(entity.id in state.busy) LinearProgressIndicator(Modifier.width(80.dp).height(2.dp))
    }
}

@Composable fun SpatialIconButton(glyph: ActionGlyph, label: String, enabled: Boolean=true, active: Boolean=false, onClick: () -> Unit) {
    FilledTonalIconButton(onClick,modifier=Modifier.size(48.dp).semantics { contentDescription=label },enabled=enabled,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=if(active) Color(0xFF92E4C0) else Color(0xEE172930),contentColor=if(active) Color(0xFF0D2424) else Color.White,disabledContainerColor=Color(0xAA172930),disabledContentColor=Color(0xFF73858D))) {
        val color=LocalContentColor.current
        Canvas(Modifier.size(25.dp)) {
            val w=size.width;val h=size.height;val line=Stroke(width=2.2.dp.toPx(),cap=StrokeCap.Round)
            fun segment(x1:Float,y1:Float,x2:Float,y2:Float) = drawLine(color,Offset(w*x1,h*y1),Offset(w*x2,h*y2),line.width,StrokeCap.Round)
            when(glyph) {
                ActionGlyph.POWER -> { drawArc(color,-45f,270f,false,Offset(w*.18f,h*.18f),Size(w*.64f,h*.64f),style=line);segment(.5f,.08f,.5f,.47f) }
                ActionGlyph.MINUS -> segment(.2f,.5f,.8f,.5f)
                ActionGlyph.PLUS -> { segment(.2f,.5f,.8f,.5f);segment(.5f,.2f,.5f,.8f) }
                ActionGlyph.MORE -> listOf(.2f,.5f,.8f).forEach { drawCircle(color,w*.065f,Offset(w*it,h*.5f)) }
                ActionGlyph.LIST -> listOf(.25f,.5f,.75f).forEach { segment(.2f,it,.8f,it) }
                ActionGlyph.DASHBOARD -> { drawRoundRect(color,Offset(w*.12f,h*.18f),Size(w*.76f,h*.64f),androidx.compose.ui.geometry.CornerRadius(w*.1f),style=line);segment(.12f,.4f,.88f,.4f);segment(.45f,.4f,.45f,.82f) }
                ActionGlyph.UP -> { segment(.2f,.62f,.5f,.32f);segment(.5f,.32f,.8f,.62f) }
                ActionGlyph.DOWN -> { segment(.2f,.38f,.5f,.68f);segment(.5f,.68f,.8f,.38f) }
                ActionGlyph.STOP -> drawRect(color,Offset(w*.25f,h*.25f),Size(w*.5f,h*.5f),style=line)
            }
        }
    }
}
