@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*

/** A focused daily page: agenda, tasks, shortcuts, and the people the user starred. */
@Composable internal fun FluidPlan(state:FluidState,model:FluidModel,modifier:Modifier,calendarAccess:()->Unit,edit:PageEdit?=null) {
    val openEdit=LocalWidgetEdit.current
    val pageEditor=LocalPageEdit.current
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val widgets=state.config.planWidgets
    val phase=rememberInfiniteTransition(label="Widget wobble").animateFloat(0f,6.2831855f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Wobble")
    SideEffect { edit?.bind(widgets,with(density){16.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().onGloballyPositioned{edit?.viewport=it.boundsInRoot()}.testTag("fluid-plan")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FluidHeading("Plan",fluidDate(state.now,"EEEE, MMM d"))
            widgets.forEachIndexed { index,kind -> key(kind) {
                Box(Modifier.pageEditCard(kind,edit,phase,index).testTag("fluid-widget-${kind.name}")) {
                    Column(Modifier.fillMaxWidth().glass(30.dp,kind==FluidWidget.AGENDA).combinedClickable(onLongClick={if(pageEditor==null)openEdit(kind)},onClick={if(kind==FluidWidget.AGENDA) model.open(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_APP_CALENDAR)) else {}}).padding(20.dp)) {
                        if(kind==FluidWidget.TASKS) Text("Tasks",style=F.text(11,muted=true),modifier=Modifier.padding(bottom=10.dp))
                        WidgetBody(kind,state,model,calendarAccess=calendarAccess)
                    }
                    if(edit!=null && edit.active) Box(Modifier.matchParentSize().pageEditDrag(kind,edit))
                }
            } }
        }
    }
}
