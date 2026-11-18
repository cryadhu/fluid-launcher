package com.example.launcher.fluid

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*

/** A focused daily page: agenda, tasks, shortcuts, and the people the user starred. */
@Composable internal fun FluidPlan(state:FluidState,model:FluidModel,hero:FluidHero,modifier:Modifier,calendarAccess:()->Unit,activityAccess:()->Unit,edit:PageEdit?=null) {
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val widgets=state.config.planWidgets
    SideEffect { edit?.bind(widgets,with(density){16.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().onGloballyPositioned{edit?.viewport=it.boundsInRoot()}.testTag("fluid-plan")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FluidHeading("Plan",fluidDate(state.now,"EEEE, MMM d"))
            WidgetGrid(widgets,state.config,edit,16.dp) { kind -> ExpandableWidget(kind,state,model,hero,calendarAccess,activityAccess) }
        }
    }
}
