package com.reactnativemenu

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.AbsListView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.views.view.ReactViewGroup
import com.reactnativemenu.ThemeHelper.appcompat
import com.reactnativemenu.ThemeHelper.color
import com.reactnativemenu.ThemeHelper.isMaterial3Expressive
import com.reactnativemenu.ThemeHelper.isNight
import com.reactnativemenu.ThemeHelper.material3
import com.reactnativemenu.ThemeHelper.material3Expressive
import java.lang.reflect.Field


class MenuView(private val mContext: ReactContext) : ReactViewGroup(mContext) {
  private lateinit var mActions: ReadableArray
  private var uiKit = "auto"
  private var themeVariant = "system"
  private var mIsAnchoredToRight = false
  private var mThemedContext: Context = context
  private var mPopup: MenuPopupHelper? = null
  private var mPendingSubactions: ReadableArray? = null
  private var mHighlightSelected = false
  private var mIsMenuDisplayed = false
  private var mIsOnLongPress = false
  private var mGestureDetector: GestureDetector
  private var mHitSlopRect: Rect? = null
    set(value) {
      field = value
      updateTouchDelegate()
    }

  init {
    mGestureDetector = GestureDetector(mContext, object : GestureDetector.SimpleOnGestureListener() {
      override fun onLongPress(e: MotionEvent) {
        if (!mIsOnLongPress) {
          return
        }
        prepareMenu()
      }

      override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (!mIsOnLongPress) {
          prepareMenu()
        }
        return true
      }
    })
  }

  fun show() {
    prepareMenu()
  }

  override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
    super.addView(child, index, params)
    // Make child views trigger menu when clicked via TalkBack
    child?.let {
      it.isClickable = true
      it.isFocusable = true
      it.setOnClickListener {
        performClick()
      }
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    return true
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    mGestureDetector.onTouchEvent(ev)
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    if (!mIsOnLongPress) {
      prepareMenu()
    }
    return true
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    updateTouchDelegate()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    updateTouchDelegate()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    if (mIsMenuDisplayed) {
      mPendingSubactions = null
      mPopup?.dismiss()
    }
  }

  fun setActions(actions: ReadableArray) {
    mActions = actions
  }

  fun setIsAnchoredToRight(isAnchoredToRight: Boolean) {
    if (mIsAnchoredToRight == isAnchoredToRight) {
      return
    }
    mIsAnchoredToRight = isAnchoredToRight
  }

  fun setIsOpenOnLongPress(isLongPress: Boolean) {
    mIsOnLongPress = isLongPress
  }

  fun setUiKit(uiKit: String?) {
    this.uiKit = uiKit ?: "auto"

    setStyle()
  }

  fun setThemeVariant(themeVariant: String?) {
    this.themeVariant = themeVariant ?: "system"

    setStyle()
  }

  private val getActionsCount: Int
    get() = mActions.size()

  private fun prepareMenuItem(menuItem: MenuItem, config: ReadableMap?) {
    val titleColor = when (config != null && config.hasKey("titleColor") && !config.isNull("titleColor")) {
      true -> config.getInt("titleColor")
      else -> null
    }
    val imageName = when (config != null && config.hasKey("image") && !config.isNull("image")) {
      true -> config.getString("image")
      else -> null
    }
    val imageColor = when (config != null && config.hasKey("imageColor") && !config.isNull("imageColor")) {
      true -> config.getInt("imageColor")
      else -> null
    }
    val attributes = when (config != null && config.hasKey("attributes") && !config.isNull(("attributes"))) {
      true -> config.getMap("attributes")
      else -> null
    }
    val menuState = config?.getString("state")

    if (titleColor != null) {
      menuItem.title = getMenuItemTextWithColor(menuItem.title.toString(), titleColor)
    }

    if (imageName != null) {
      val resourceId: Int = getDrawableIdWithName(imageName)
      if (resourceId != 0) {
        val icon = resources.getDrawable(resourceId, context.theme)
        if (imageColor != null) {
          icon.setTintList(ColorStateList.valueOf(imageColor))
        }
        menuItem.icon = icon
      }
    }

    if (attributes != null) {
      // actions.attributes.disabled
      val disabled = when (attributes.hasKey("disabled") && !attributes.isNull("disabled")) {
        true -> attributes.getBoolean("disabled")
        else -> false
      }
      menuItem.isEnabled = !disabled
      if (!menuItem.isEnabled) {
        val disabledColor = 0x77888888
        menuItem.title = getMenuItemTextWithColor(menuItem.title.toString(), disabledColor)
        if (imageName != null) {
          val icon = menuItem.icon
          icon?.setTintList(ColorStateList.valueOf(disabledColor))
          menuItem.icon = icon
        }
      }

      // actions.attributes.hidden
      val hidden = when (attributes.hasKey("hidden") && !attributes.isNull("hidden")) {
        true -> attributes.getBoolean("hidden")
        else -> false
      }
      menuItem.isVisible = !hidden

      // actions.attributes.destructive
      val destructive = when (attributes.hasKey("destructive") && !attributes.isNull("destructive")) {
        true -> attributes.getBoolean("destructive")
        else -> false
      }
      if (destructive) {
        menuItem.title = getMenuItemTextWithColor(menuItem.title.toString(), Color.RED)
        if (imageName != null) {
          val icon = menuItem.icon
          icon?.setTintList(ColorStateList.valueOf(Color.RED))
          menuItem.icon = icon
        }
      }
    }

    // Handle menuState for checkable items
    when (menuState) {
      "on", "off" -> if (mHighlightSelected) {
        menuItem.isCheckable = false
        if (menuState == "on" && titleColor == null && menuItem.isEnabled) {
          val selectedColor = color(mThemedContext, "colorOnTertiaryContainer")
          if (selectedColor != null) {
            menuItem.title = getMenuItemTextWithColor(menuItem.title.toString(), selectedColor)
          }
        }
      } else {
        menuItem.isCheckable = true
        menuItem.isChecked = menuState == "on"
      }
      else -> menuItem.isCheckable = false
    }
  }

  private fun prepareMenu() {
    if (getActionsCount > 0) {
      mIsMenuDisplayed = true
      val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(mContext, id)
      val surfaceId: Int = UIManagerHelper.getSurfaceId(this)
      dispatcher?.dispatchEvent(MenuOnOpenEvent(surfaceId, id, id))
      showMenu(mActions)
    }
  }

  private fun showMenu(actions: ReadableArray) {
    val menu = MenuBuilder(mThemedContext)
    val selectedPositions = mutableListOf<Int>()
    var position = 0
    var i = 0
    while (i < actions.size()) {
      if (!actions.isNull(i)) {
        val item = actions.getMap(i)
        val subactions = when (item != null && item.hasKey("subactions") && !item.isNull("subactions")) {
          true -> item.getArray("subactions")
          else -> null
        }
        val menuItem = when (subactions != null) {
          true -> menu.addSubMenu(Menu.NONE, Menu.NONE, i, item?.getString("title")).item
          else -> menu.add(Menu.NONE, Menu.NONE, i, item?.getString("title"))
        }
        prepareMenuItem(menuItem, item)
        if (menuItem.isVisible) {
          if (mHighlightSelected && item?.getString("state") == "on") {
            selectedPositions.add(position)
          }
          position++
        }
        menuItem.setOnMenuItemClickListener {
          if (subactions != null) {
            mPendingSubactions = subactions
          } else {
            mIsMenuDisplayed = false
            if (!actions.isNull(it.order)) {
              val selectedItem = actions.getMap(it.order)
              val dispatcher =
                UIManagerHelper.getEventDispatcherForReactTag(mContext, id)
              val surfaceId: Int = UIManagerHelper.getSurfaceId(this)
              dispatcher?.dispatchEvent(
                MenuOnPressActionEvent(surfaceId, id, selectedItem?.getString("id"), id)
              )
            }
          }
          true
        }
      }
      i++
    }

    val popup = MenuPopupHelper(mThemedContext, menu, this)
    popup.gravity = when (mIsAnchoredToRight) {
      true -> Gravity.RIGHT
      false -> Gravity.LEFT
    }
    popup.setForceShowIcon(true)
    popup.setOnDismissListener {
      val subactions = mPendingSubactions
      if (subactions != null) {
        mPendingSubactions = null
        showMenu(subactions)
        return@setOnDismissListener
      }
      mIsMenuDisplayed = false
      val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(mContext, id)
      val surfaceId: Int = UIManagerHelper.getSurfaceId(this)
      dispatcher?.dispatchEvent(MenuOnCloseEvent(surfaceId, id, id))
    }
    mPopup = popup
    popup.show()

    if (selectedPositions.isNotEmpty()) {
      popup.listView?.let { listView ->
        listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        selectedPositions.forEach { listView.setItemChecked(it, true) }
      }
    }
  }

  private fun updateTouchDelegate() {
    post {
      val hitRect = Rect()
      getHitRect(hitRect)

      mHitSlopRect?.let {
        hitRect.left -= it.left
        hitRect.top -= it.top
        hitRect.right += it.right
        hitRect.bottom += it.bottom
      }

      (parent as? ViewGroup)?.let {
        it.touchDelegate = TouchDelegate(hitRect, this)
      }
    }
  }

  private fun getDrawableIdWithName(name: String): Int {
    val appResources: Resources = context.resources
    var resourceId = appResources.getIdentifier(name, "drawable", context.packageName)
    if (resourceId == 0) {
      // If drawable is not present in app's resources, check system's resources
      resourceId = getResId(name, android.R.drawable::class.java)
    }
    return resourceId
  }

  private fun getResId(resName: String?, c: Class<*>): Int {
    return try {
      val idField: Field = c.getDeclaredField(resName!!)
      idField.getInt(idField)
    } catch (e: Exception) {
      e.printStackTrace()
      0
    }
  }

  private fun getMenuItemTextWithColor(text: String, color: Int): SpannableStringBuilder {
    val textWithColor = SpannableStringBuilder()
    textWithColor.append(text)
    textWithColor.setSpan(ForegroundColorSpan(color),
      0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return textWithColor
  }

  private fun setStyle() {
    val dark = when (this.themeVariant) {
      "dark" -> true
      "light" -> false
      else -> isNight(context)
    }

    val expressive = when (this.uiKit) {
      "material3-expressive" -> material3Expressive(dark)
      "material3", "appcompat" -> 0
      else -> if (isMaterial3Expressive(context)) material3Expressive(dark) else 0
    }

    val theme = when (this.uiKit) {
      "appcompat" -> appcompat(dark)
      else -> expressive.takeIf { it != 0 } ?: material3(dark).takeIf { it != 0 } ?: appcompat(dark)
    }

    val themedCtx = ContextThemeWrapper(context, theme)

    if (expressive != 0) {
      themedCtx.theme.applyStyle(R.style.ThemeOverlay_RNMenu_Material3Expressive, true)
    }

    mThemedContext = themedCtx
    mHighlightSelected = expressive != 0
  }
}
