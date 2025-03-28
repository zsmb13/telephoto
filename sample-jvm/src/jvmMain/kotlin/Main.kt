import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import jdk.jfr.internal.OldObjectSample.emit
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import telephoto.sample_jvm.generated.resources.Res

private object FloatAnimSaver : Saver<Animatable<Float, AnimationVector1D>, Float> {
  override fun restore(value: Float): Animatable<Float, AnimationVector1D>? = Animatable(value)
  override fun SaverScope.save(value: Animatable<Float, AnimationVector1D>): Float? = value.value
}

class Svg(svgBytes: ByteArray) {
  private val svg = SVGDOM(Data.makeFromBytes(svgBytes))

  val width: Float get() = svg.root?.width?.value ?: 0f
  val height: Float get() = svg.root?.height?.value ?: 0f

  fun renderTo(scope: DrawScope) {
    scope.drawIntoCanvas { canvas ->
      svg.render(canvas.nativeCanvas)
    }
  }
}

/**
 * Converts an offset containing x and y percentage locations within the SVG (0f..1f)
 * into real offset values from the middle of the SVG image.
 */
private fun Offset.asSvgOffset(svg: Svg) = Offset(
  svg.width / 2 - svg.width * this.x,
  svg.height / 2 - svg.height * this.y
)

fun main() = singleWindowApplication(
  state = WindowState(width = 800.dp, height = 600.dp),
) {
  Box(Modifier.fillMaxSize()) {
    val svg = produceState<Svg?>(null) {
      @OptIn(ExperimentalResourceApi::class)
      this.value = Svg(Res.readBytes("files/ground-floor.svg"))
    }.value

    if (svg != null) {
      Map(
        svg = svg,
        initialZoom = 2f,
        initialOffset = Offset(0.34f, 0.78f).asSvgOffset(svg) // Hall D2
      )
    }
  }
}

@Composable
private fun Map(
  svg: Svg,
  modifier: Modifier = Modifier,
  initialZoom: Float = 1f,
  initialOffset: Offset = Offset.Zero,
  zoomRange: ClosedFloatingPointRange<Float> = 0.5f..5f,
  interactive: Boolean = true,
) {
  val scale = rememberSaveable(saver = FloatAnimSaver) { Animatable(initialZoom) }
  val offsetX = rememberSaveable(saver = FloatAnimSaver) { Animatable(initialOffset.x) }
  val offsetY = rememberSaveable(saver = FloatAnimSaver) { Animatable(initialOffset.y) }

  val scope = rememberCoroutineScope()

  val validOffsetX = (-svg.width * 0.5f)..(svg.height * 0.5f)
  val validOffsetY = (-svg.height * 0.5f)..(svg.height * 0.5f)

  val interactiveModifiers = if (!interactive) {
    Modifier
  } else {
    Modifier
      .transformable(rememberTransformableState { zoomChange, panChange, _ ->
        if (!interactive) return@rememberTransformableState

        scope.launch {
          scale.snapTo((scale.value * zoomChange).coerceIn(zoomRange))
          offsetX.snapTo((offsetX.value + panChange.x / scale.value).coerceIn(validOffsetX))
          offsetY.snapTo((offsetY.value + panChange.y / scale.value).coerceIn(validOffsetY))
        }
      })
      .pointerInput(Unit) {
        if (!interactive) return@pointerInput

        detectTapGestures(
          onDoubleTap = { tapOffset ->
            val spec = tween<Float>(500, easing = EaseOutCubic)

            if (scale.value >= zoomRange.endInclusive - 0.1f) {
              scope.launch { scale.animateTo(initialZoom, spec) }
            } else {
              val newScale = (scale.value * 2f).coerceIn(zoomRange)

              val newOffsetX = (offsetX.value + (size.width / 2 - tapOffset.x) / 2 / scale.value)
                .coerceIn(validOffsetX)
              val newOffsetY = (offsetY.value + (size.height / 2 - tapOffset.y) / 2 / scale.value)
                .coerceIn(validOffsetY)

              scope.launch {
                async { scale.animateTo(newScale, spec) }
                async { offsetX.animateTo(newOffsetX, spec) }
                async { offsetY.animateTo(newOffsetY, spec) }
              }
            }
          }
        )
      }
  }

  Canvas(
    modifier
      .clipToBounds()
      .fillMaxSize()
      .then(interactiveModifiers)
  ) {
    translate(
      left = offsetX.value + (size.width - svg.width) / 2,
      top = offsetY.value + (size.height - svg.height) / 2,
    ) {
      scale(
        scale = scale.value,
        pivot = Offset(svg.width / 2 - offsetX.value, svg.height / 2 - offsetY.value),
      ) {
        svg.renderTo(this)
      }
    }
  }
}
