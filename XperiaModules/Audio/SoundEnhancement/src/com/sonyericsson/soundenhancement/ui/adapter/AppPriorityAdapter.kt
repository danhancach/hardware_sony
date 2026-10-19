package com.sonyericsson.soundenhancement.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.backend.PriorityAppItem

// Card Position trong nhom the Expressive
enum class CardPosition {
    TOP, CENTER, BOTTOM, SINGLE
}

sealed class PriorityListItem {
    data class Header(val titleRes: Int) : PriorityListItem()
    data class App(
        val appItem: PriorityAppItem,
        val cardPosition: CardPosition
    ) : PriorityListItem()
}

// Adapter hien thi danh sach ung dung uu tien hieu ung theo nhom the Expressive
class AppPriorityAdapter(
    private var allApps: List<PriorityAppItem>,
    private val onToggle: (PriorityAppItem, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP = 1
    }

    private var currentQuery = ""
    private var displayItems = listOf<PriorityListItem>()

    init {
        rebuildDisplayItems()
    }

    fun updateList(newList: List<PriorityAppItem>) {
        allApps = newList
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        currentQuery = query
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    private fun rebuildDisplayItems() {
        val filtered = if (currentQuery.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(currentQuery, ignoreCase = true) ||
                        it.packageName.contains(currentQuery, ignoreCase = true)
            }
        }

        val presetList = filtered.filter { it.isPreset }
        val userList = filtered.filter { !it.isPreset }

        val items = mutableListOf<PriorityListItem>()

        // 1. Nhom ung dung ho tro san (Preset supported apps)
        if (presetList.isNotEmpty()) {
            items.add(PriorityListItem.Header(R.string.sound_enhance_priority_preset_header))
            val size = presetList.size
            presetList.forEachIndexed { index, app ->
                val pos = when {
                    size == 1 -> CardPosition.SINGLE
                    index == 0 -> CardPosition.TOP
                    index == size - 1 -> CardPosition.BOTTOM
                    else -> CardPosition.CENTER
                }
                items.add(PriorityListItem.App(app, pos))
            }
        }

        // 2. Nhom ung dung do nguoi dung them / cai dat (User added apps)
        if (userList.isNotEmpty()) {
            items.add(PriorityListItem.Header(R.string.sound_enhance_priority_user_header))
            val size = userList.size
            userList.forEachIndexed { index, app ->
                val pos = when {
                    size == 1 -> CardPosition.SINGLE
                    index == 0 -> CardPosition.TOP
                    index == size - 1 -> CardPosition.BOTTOM
                    else -> CardPosition.CENTER
                }
                items.add(PriorityListItem.App(app, pos))
            }
        }

        displayItems = items
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is PriorityListItem.Header -> VIEW_TYPE_HEADER
            is PriorityListItem.App -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_priority_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_priority_app, parent, false)
            AppViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is PriorityListItem.Header -> {
                (holder as HeaderViewHolder).title.setText(item.titleRes)
            }
            is PriorityListItem.App -> {
                val appHolder = holder as AppViewHolder
                val app = item.appItem

                appHolder.appName.text = app.appName
                appHolder.appPackage.text = app.packageName
                if (app.icon != null) {
                    appHolder.appIcon.setImageDrawable(app.icon)
                } else {
                    appHolder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                }
                appHolder.switchPriority.isChecked = app.isEnabled

                // Gan drawable bo goc tuong ung voi vi tri the trong nhom
                val bgRes = when (item.cardPosition) {
                    CardPosition.TOP -> R.drawable.sound_preference_bg_top
                    CardPosition.CENTER -> R.drawable.sound_preference_bg_center
                    CardPosition.BOTTOM -> R.drawable.sound_preference_bg_bottom
                    CardPosition.SINGLE -> R.drawable.sound_preference_bg_single
                }
                appHolder.container.setBackgroundResource(bgRes)

                // Khoang cach: 2dp giua cac the trong nhom, 12dp o the cuoi nhom
                val lp = appHolder.container.layoutParams as? MarginLayoutParams
                if (lp != null) {
                    val bottomMarginDp = if (item.cardPosition == CardPosition.BOTTOM || item.cardPosition == CardPosition.SINGLE) 12 else 2
                    val bottomMarginPx = (bottomMarginDp * holder.itemView.context.resources.displayMetrics.density).toInt()
                    if (lp.bottomMargin != bottomMarginPx) {
                        lp.bottomMargin = bottomMarginPx
                        appHolder.container.layoutParams = lp
                    }
                }

                appHolder.itemView.setOnClickListener {
                    val newState = !appHolder.switchPriority.isChecked
                    appHolder.switchPriority.isChecked = newState
                    app.isEnabled = newState
                    onToggle(app, newState)
                }
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tv_header_title)
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: View = itemView.findViewById(R.id.row_app_container)
        val appIcon: ImageView = itemView.findViewById(R.id.img_app_icon)
        val appName: TextView = itemView.findViewById(R.id.tv_app_name)
        val appPackage: TextView = itemView.findViewById(R.id.tv_app_package)
        val switchPriority: MaterialSwitch = itemView.findViewById(R.id.switch_app_priority)
    }
}
