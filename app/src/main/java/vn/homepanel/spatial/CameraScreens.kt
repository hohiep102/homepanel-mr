package vn.homepanel.spatial

import kotlin.math.min

const val SCREEN_WIDTH = 1.6f
const val SCREEN_HEIGHT = .9f

/**
 * Which placed cameras get one of the few live screens. [wanted] is nearest first; a screen keeps its
 * camera while that camera stays wanted, so its stream does not restart when another one comes closer.
 */
fun assignScreens(current: List<String?>, wanted: List<String>): List<String?> {
    val chosen = wanted.take(current.size).toSet()
    val kept = current.map { it?.takeIf { id -> id in chosen } }
    val free = chosen.filter { it !in kept }.iterator()
    return kept.map { it ?: if (free.hasNext()) free.next() else null }
}

/** A camera screen is always 16:9; its size is set by its width. */
fun screenHeight(width: Float) = width * SCREEN_HEIGHT / SCREEN_WIDTH

/** The largest 16:9 screen inside a scanned object's face, e.g. a TV, centred on it. */
fun screenInFace(face: FittedFrame): FittedFrame {
    val width = min(face.width, face.height * SCREEN_WIDTH / SCREEN_HEIGHT)
    return face.copy(width = width, height = screenHeight(width))
}

/** Uniform scale that fits a 16:9 screen inside a placed frame without stretching the picture. */
fun screenScale(width: Float, height: Float) = min(width / SCREEN_WIDTH, height / SCREEN_HEIGHT)
