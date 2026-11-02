package com.example.launcher.fluid

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*

/** A focused daily page: agenda, tasks, shortcuts, and the people the user starred. */
@Composable internal fun FluidPlan(state:FluidState,model:FluidModel,modifier:Modifier,calendarAccess:()->Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom=100.dp).testTag("fluid-plan"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FluidHeading("Plan",fluidDate(state.now,"EEEE, MMM d"))
        Column(Modifier.fillMaxWidth().glass(30.dp,true).padding(20.dp)) { WidgetBody(FluidWidget.AGENDA,state,model,calendarAccess=calendarAccess) }
        Text("Tasks",style=F.text(11,muted=true),modifier=Modifier.padding(start=8.dp))
        Column(Modifier.fillMaxWidth().glass(30.dp).padding(20.dp)) { WidgetBody(FluidWidget.TASKS,state,model) }
        Column(Modifier.fillMaxWidth().glass(30.dp).padding(20.dp)) { QuickActions(state,model) }
        Column(Modifier.fillMaxWidth().glass(30.dp).padding(20.dp)) { FavoritesWidget(state,model) }
    }
}
