package com.example.launcher.fluid

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import com.example.launcher.ui.Glyph
import com.example.launcher.R

data class GlassScene(val size: Size = Size(1080f, 2400f), val dark: Boolean = false, val frost: ImageBitmap? = null, val wallpaper:String="Original")
val LocalGlass = staticCompositionLocalOf { GlassScene() }
object F {
    val purple = Color(0xFF8051FF)
    val lilac = Color(0xFFB99BFF)
    val ink @Composable get() = if (LocalGlass.current.dark) Color(0xFFF3F0FF) else Color(0xFF211B3D)
    val muted @Composable get() = if (LocalGlass.current.dark) Color(0xFFBCB5D6) else Color(0xFF68627D)
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val font = FontFamily(
        Font(R.font.manrope, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
        Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    )
    @Composable fun text(size: Int = 14, bold: Boolean = false, muted: Boolean = false) = TextStyle(fontFamily = font,
        fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = if (muted) F.muted else ink)
}

@Composable fun FluidTheme(config: FluidConfig, content: @Composable () -> Unit) {
    val dark = config.mode == "Dark" || config.mode == "Auto" && isSystemInDarkTheme()
    var dimensions by remember { mutableStateOf(Size(1080f,2400f)) }
    val view = LocalView.current; val activity = LocalContext.current as? Activity
    SideEffect { activity?.let { WindowCompat.getInsetsController(it.window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    var frosted by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(dimensions, dark, config.homeWallpaper) {
        frosted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { renderFrost(GlassScene(dimensions, dark, wallpaper=config.homeWallpaper)) }
    }
    CompositionLocalProvider(LocalGlass provides GlassScene(dimensions, dark, frosted,config.homeWallpaper)) {
        val colors = if (dark) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colors.copy(primary = F.purple, onPrimary = Color.White, background = if (dark) Color(0xFF201A34) else Color(0xFFE8E3F3),
            surface = if (dark) Color(0xFF312842) else Color(0xFFF3EFFC), onSurface = F.ink, onBackground = F.ink),
            typography = Typography(bodyLarge = F.text(), bodyMedium = F.text(), titleLarge = F.text(24, true))) {
            Box(Modifier.fillMaxSize().onSizeChanged { dimensions = Size(it.width.toFloat(),it.height.toFloat()) }) { content() }
        }
    }
}

/** Analytic wallpaper shared by the root and frosted panes, sampled in window coordinates.
 * No screenshot readback, bitmap allocation per frame, or blur of foreground text. */
internal fun DrawScope.wallpaper(scene: GlassScene) {
    val w = scene.size.width; val h = scene.size.height
    val dark = scene.dark
    if(scene.wallpaper=="Lavender") {
        drawRect(Color(0xFFCACCD8),size=scene.size)
        val center=Offset(w*.18f,h*.76f)
        scale(1f,1.45f,pivot=center) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB5A6F0),Color(0xFFB8ACF0),Color(0x00B8ACF0)),center,w*1.02f),w*1.02f,center)
        }
        if(dark)drawRect(Color(0xFF151124).copy(alpha=.58f),size=scene.size)
        return
    }
    drawRect(Brush.linearGradient(if (dark) listOf(Color(0xFF373147),Color(0xFF231C38),Color(0xFF4B386E)) else listOf(Color(0xFFD7DDDA),Color(0xFFEAECE7),Color(0xFFEED9C6)), end = Offset(w,h)), size = scene.size)
    val wave = Path().apply { moveTo(-w*.2f,h*.86f); cubicTo(w*.08f,h*.5f,w*.44f,h*.28f,w*1.2f,h*.22f); lineTo(w*1.2f,h*1.2f); lineTo(-w*.2f,h*1.2f); close() }
    drawPath(wave, Brush.linearGradient(if (dark) listOf(Color(0xFF9B6BA9),Color(0xFF574889),Color(0xFF322844)) else listOf(Color(0xFFF3F0E8),Color(0xFFDCE4DF),Color(0xFFF0D7BC)), Offset(w*.9f,h*.22f), Offset(w*.15f,h*.99f)))
    val edge = Path().apply { moveTo(-w*.2f,h*.86f); cubicTo(w*.08f,h*.5f,w*.44f,h*.28f,w*1.2f,h*.22f) }
    drawPath(edge, Color.White.copy(alpha = if (dark) .2f else .64f), style = Stroke(2.dp.toPx()))
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) .06f else .24f), Color.Transparent), center = Offset(w*.95f,h*.86f), radius = w*.85f), w*.85f, Offset(w*.95f,h*.86f))
}
@Composable fun FluidWallpaper(modifier: Modifier = Modifier) {
    val scene = LocalGlass.current
    Canvas(modifier.fillMaxSize()) { wallpaper(scene) }
}

fun Modifier.glass(radius: Dp = 28.dp, elevated: Boolean = false, strength: Float = 1f, shadowed: Boolean = true): Modifier = composed {
    val scene = LocalGlass.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(radius)
    this.onGloballyPositioned { val p = it.positionInWindow(); if (p != origin) origin = p }
        .shadow(if (!shadowed) 0.dp else if (elevated) 22.dp else 12.dp, shape, ambientColor = Color(0xFF18202D).copy(alpha = .20f), spotColor = Color(0xFF18202D).copy(alpha = .30f))
        .clip(shape).drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val veil = if (scene.dark) Color(0xFF141C29).copy(alpha = .32f*strength) else Color(0xFF62707F).copy(alpha = .14f*strength)
            val reflection = Brush.linearGradient(
                0f to Color.White.copy(alpha=if(scene.dark).24f else .42f),
                .30f to Color.White.copy(alpha=if(scene.dark).08f else .13f),
                .52f to Color.Transparent,
                .76f to Color(0xFF182435).copy(alpha=if(scene.dark).18f else .09f),
                1f to Color.White.copy(alpha=if(scene.dark).09f else .20f),
                start=Offset.Zero,end=Offset(size.width*.85f,size.height))
            val highlight = Brush.linearGradient(
                0f to Color.White.copy(alpha=.92f),
                .28f to Color.White.copy(alpha=.26f),
                .55f to Color.White.copy(alpha=.04f),
                .82f to Color.White.copy(alpha=.20f),
                1f to Color.White.copy(alpha=.62f),start=Offset.Zero,end=Offset(size.width,size.height))
            onDrawBehind {
                // A cached, blurred wallpaper sample refracts beneath crisp foreground content.
                translate(-origin.x + 1.dp.toPx(), -origin.y + 2.dp.toPx()) {
                    val frost = scene.frost
                    if (frost == null) wallpaper(scene)
                    else drawImage(frost, dstSize = IntSize(scene.size.width.toInt(), scene.size.height.toInt()), filterQuality = FilterQuality.Medium)
                }
                drawRect(veil)
                drawRect(reflection)
                // Broad reflected light and a shaded lower edge give the pane thickness.
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=if(scene.dark).12f else .22f),Color.Transparent),
                    center=Offset(size.width*.12f,-size.height*.18f),radius=size.maxDimension*.85f))
                drawRect(Brush.verticalGradient(0f to Color.Transparent,.78f to Color.Transparent,1f to Color(0xFF172333).copy(alpha=if(scene.dark).16f else .08f)))
                drawOutline(outline,Color.White.copy(alpha=if(scene.dark).035f else .08f),style=Stroke(5.dp.toPx()))
                drawOutline(outline, highlight, style = Stroke(1.2.dp.toPx()))
            }
        }
}

@Composable fun Modifier.softPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .95f else 1f, spring(.72f, 520f), label = "Glass press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
@Composable fun GlassButton(label: String, modifier: Modifier = Modifier, primary: Boolean = false, glyph: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val base = modifier.softPress(interaction).heightIn(min = 44.dp)
    Row((if (primary) base.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(F.lilac,F.purple))) else base.glass(24.dp))
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        glyph?.let { Glyph(it, if (primary) Color.White else F.purple, Modifier.size(18.dp)) }
        Text(label, style = F.text(12, true), color = if (primary) Color.White else F.ink)
    }
}
@Composable fun GlassIcon(glyph: String, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(44.dp).softPress(interaction).then(if (primary) Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(F.lilac,F.purple))) else Modifier.glass(50.dp))
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Glyph(glyph, if (primary) Color.White else F.ink, Modifier.size(20.dp))
    }
}
@Composable fun FluidHeading(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = F.text(28,true)); subtitle?.let { Text(it, style = F.text(12, muted = true), modifier = Modifier.padding(top=4.dp)) } }
        action?.invoke()
    }
}
