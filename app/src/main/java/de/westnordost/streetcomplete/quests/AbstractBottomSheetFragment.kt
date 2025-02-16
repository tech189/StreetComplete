package de.westnordost.streetcomplete.quests


import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.ktx.updateMargins
import de.westnordost.streetcomplete.view.RoundRectOutlineProvider
import de.westnordost.streetcomplete.view.insets_animation.respectSystemInsets
import kotlinx.android.synthetic.main.fragment_quest_answer.*
import kotlin.math.min

/** Abstract base class for (quest) bottom sheets
 *
 * Note: The AbstractBottomSheetFragment currently assumes that it will be inflated with the views
 * that are in fragment_quest_answer by any subclass!
 * */
abstract class AbstractBottomSheetFragment : Fragment(), IsCloseableBottomSheet {

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    private var minBottomInset = Int.MAX_VALUE

    private val mainHandler = Handler(Looper.getMainLooper())

    // overridable by child classes
    open val defaultExpanded = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomSheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // not immediately because this is called during layout change (view.getTop() == 0)
            mainHandler.post { updateCloseButtonVisibility() }
        }

        closeButton.setOnClickListener { activity?.onBackPressed() }

        minBottomInset = Int.MAX_VALUE
        view.respectSystemInsets {
            scrollViewChild.updatePadding(bottom = it.bottom)
            bottomSheetContainer.updateMargins(top = it.top, left = it.left, right = it.right)
            okButton.updateMargins(bottom = it.bottom + 8f.toPx(context).toInt())

            // expanding bottom sheet when keyboard is opened
            if (minBottomInset < it.bottom) expand()
            minBottomInset = min(it.bottom, minBottomInset)
        }

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        val cornerRadius = resources.getDimension(R.dimen.speech_bubble_rounded_corner_radius)
        val margin = resources.getDimensionPixelSize(R.dimen.horizontal_speech_bubble_margin)
        val topMargin = -resources.getDimensionPixelSize(R.dimen.quest_form_speech_bubble_top_margin)
        speechBubbleTitleContainer.outlineProvider = RoundRectOutlineProvider(
            cornerRadius, margin, topMargin, margin, margin
        )

        speechbubbleContentContainer.outlineProvider = RoundRectOutlineProvider(
            cornerRadius, margin, margin, margin, margin
        )

        speechBubbleTitleContainer.setOnClickListener {
            bottomSheetBehavior.apply {
                if (state == STATE_EXPANDED)
                    state = STATE_COLLAPSED
                else if (state == STATE_COLLAPSED)
                    state = STATE_EXPANDED
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updateCloseButtonVisibility()
            }
        })

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || defaultExpanded) {
            expand()
        }

        if (savedInstanceState == null) {
            speechBubbleTitleContainer.startAnimation(
                AnimationUtils.loadAnimation(context, R.anim.inflate_title_bubble)
            )

            speechbubbleContentContainer.startAnimation(
                AnimationUtils.loadAnimation(context, R.anim.inflate_answer_bubble)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // I need to do everything myself... (AppCompactActivity only does this after calling this
        // method. Genius!)
        resources.updateConfiguration(newConfig, resources.displayMetrics)

        bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.quest_form_peekHeight)
        bottomSheetContainer?.let {
            it.updateLayoutParams { width = resources.getDimensionPixelSize(R.dimen.quest_form_width) }
        }
    }

    protected fun expand() {
        bottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun updateCloseButtonVisibility() {
        // this is called asynchronously. It may happen that the activity is already gone when this
        // method is finally called
        closeButton?.isInvisible = (bottomSheet?.top ?: 0) > 0
    }

    @UiThread override fun onClickMapAt(position: LatLon, clickAreaSizeInMeters: Double): Boolean {
        return false
    }

    /** Request to close the form through user interaction (back button, clicked other quest,..),
     * requires user confirmation if any changes have been made  */
    @UiThread override fun onClickClose(onConfirmed: () -> Unit) {
        if (!isRejectingClose()) {
            onDiscard()
            onConfirmed()
        } else {
            activity?.let {
                AlertDialog.Builder(it)
                    .setMessage(R.string.confirmation_discard_title)
                    .setPositiveButton(R.string.confirmation_discard_positive) { _, _ ->
                        onDiscard()
                        onConfirmed()
                    }
                    .setNegativeButton(R.string.confirmation_discard_negative, null)
                    .show()
            }
        }
    }

    /** returns whether this form should not be closeable without confirmation */
    open fun isRejectingClose(): Boolean = false

    protected open fun onDiscard() {}
}
