package com.kroegerama.views

import android.animation.LayoutTransition
import android.content.Context
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate


class DraggableLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
    }

    private val isHorizontal get() = orientation == HORIZONTAL
    private val scrollPadding get() = if (isHorizontal) (scrollListener ?: this).width / 5f else (scrollListener ?: this).height / 5f

    private var isDraggingOuter = false
    private var scrollTimer: Timer? = null

    var scrollListener: ViewGroup? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rootView?.setOnDragListener { _, event ->
            internalDragEvent(event, false)
            true
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        initDragHandling(child)
    }

    private fun initDragHandling(view: View) {
        if (view.hasHandling) {
            return
        }
        view.setOnLongClickListener { startDrag(view);true }
        view.hasHandling = true
    }

    private fun startDrag(view: View) {
        val shadow = View.DragShadowBuilder(view)
        ViewCompat.startDragAndDrop(view, null, shadow, view, 0)
    }

    private fun handleDragStart(view: View) {
        view.visibility = View.INVISIBLE
    }

    private fun handleDragLocation(view: View, pos: Float) {
        val sOffs = if (isDraggingOuter) {
            0
        } else {
            scrollListener?.let { if (isHorizontal) it.scrollX else it.scrollY } ?: 0
        }
        val vOffs = if (isDraggingOuter) {
            scrollListener?.let { if (isHorizontal) it.scrollX else it.scrollY } ?: 0
        } else {
            0
        }

        handleScrolling(pos - sOffs)
        swapViews(view, pos + vOffs)
    }

    private fun handleDragEnd(view: View) {
        view.visibility = View.VISIBLE
        scrollTimer?.cancel()
    }

    private fun swapViews(view: View, pos: Float) {
        val ownIdx = indexOfChild(view)
        val targetIdx = getIndexAtPos(pos)
        if (targetIdx == -1 || ownIdx == -1 || targetIdx == ownIdx) {
            return
        }
        if (isInLayout || layoutTransition.isRunning) {
            return
        }
        synchronized(this) {
            removeView(view)
            addView(view, targetIdx)
        }
    }

    private fun getIndexAtPos(pos: Float): Int {
        if (childCount == 0) {
            return -1
        }

        val lastIdx = childCount - 1
        getChildAt(lastIdx).let { last ->
            val end = if (isHorizontal) last.right else last.bottom
            if (pos > end) {
                return lastIdx
            }
        }

        forEach {
            val start = if (isHorizontal) left else top
            val end = if (isHorizontal) right else bottom
            val po = pos + (if (isHorizontal) width else height) / 2
            if (start <= po && end >= po) {
                return indexOfChild(this)
            }
        }
        return -1
    }

    override fun onDragEvent(event: DragEvent): Boolean {
        internalDragEvent(event, true)
        return true
    }

    private fun internalDragEvent(event: DragEvent, ownEvent: Boolean) {
        val view = event.localState as? View ?: return
        val pos = if (isHorizontal) event.x else event.y
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> handleDragStart(view)
            DragEvent.ACTION_DRAG_LOCATION -> handleDragLocation(view, pos)
            DragEvent.ACTION_DRAG_ENDED -> handleDragEnd(view)
            DragEvent.ACTION_DRAG_EXITED -> if (ownEvent) {
                isDraggingOuter = true
            }
            DragEvent.ACTION_DRAG_ENTERED -> if (ownEvent) {
                isDraggingOuter = false
            }
        }
        //INNEN: DROP, END -> DROP
        //AUßEN: END
    }

    private fun handleScrolling(pos: Float) {
        scrollListener?.let { scroller ->
            val end = if (isHorizontal) scroller.right - scrollPadding else scroller.bottom - scrollPadding
            when {
                pos < scrollPadding -> setScrollSpeed(-(scrollPadding - pos))
                pos > end -> setScrollSpeed(scrollPadding - (end - pos))
                else -> setScrollSpeed(0f)
            }
        }
    }

    private fun setScrollSpeed(speed: Float) {
        scrollTimer?.cancel()
        scrollTimer = null
        if (speed == 0f) {
            return
        }
        scrollListener?.let { scroller ->
            scrollTimer = Timer().apply {
                scheduleAtFixedRate(0, 1000 / 20) {
                    val x = if (isHorizontal) (speed / 5f).toInt() else 0
                    val y = if (isHorizontal) 0 else (speed / 5f).toInt()
                    scroller.smoothScrollBy(x, y)
                }
            }
        }
    }

    private fun View.smoothScrollBy(x: Int, y: Int) {
        when (this) {
            is ScrollView -> this.smoothScrollBy(x, y)
            is HorizontalScrollView -> this.smoothScrollBy(x, y)
        }
    }

    private var View.hasHandling
        get() = getTag(KEY_HAS_HANDLING) != null
        set(_) {
            setTag(KEY_HAS_HANDLING, true)
        }

    private inline fun forEach(block: View.() -> Unit) = (0 until childCount).map { getChildAt(it) }.forEach(block)

    companion object {
        private const val TAG = "HorizontalDragContainer"
        private const val KEY_HAS_HANDLING = 0x13378008 + 5
    }
}