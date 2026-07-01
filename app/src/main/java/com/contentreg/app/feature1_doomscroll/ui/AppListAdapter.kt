package com.contentreg.app.feature1_doomscroll.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.contentreg.app.R

/** M1.4 — one installed, launchable app the user can mark as counting toward the budget. */
data class AppRow(
    val packageName: String,
    val label: String,
    val icon: Drawable?,   // Bug #5: null on construction; loaded lazily by the adapter
    val checked: Boolean,
)

/**
 * M1.4 — list of installed apps with a checkbox each. Selection state is owned by the activity;
 * the adapter just renders rows and reports toggles via [onToggle].
 *
 * Bug #5: icons are not loaded in [loadInstalledApps] (which blocked the spinner for ~4s).
 * Instead, each [AppViewHolder.bind] posts a deferred load via [View.post]; the result is cached
 * in [iconCache] so re-binds (e.g. scroll back) are instant. A tag check prevents a recycled
 * view from applying an icon that arrived late for a prior row.
 */
class AppListAdapter(
    private var rows: List<AppRow>,
    private val onToggle: (packageName: String, checked: Boolean) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private val iconCache = HashMap<String, Drawable>()

    fun submit(newRows: List<AppRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)
        private val checkbox: CheckBox = itemView.findViewById(R.id.appCheckbox)

        fun bind(row: AppRow) {
            // Clear any stale image from recycling, tag the view so late-arriving loads can detect
            // if the row has been replaced before the post() callback fires.
            icon.setImageDrawable(null)
            icon.tag = row.packageName

            val cached = iconCache[row.packageName]
            if (cached != null) {
                icon.setImageDrawable(cached)
            } else {
                icon.post {
                    if (icon.tag == row.packageName) {
                        runCatching {
                            icon.context.packageManager.getApplicationIcon(row.packageName)
                        }.getOrNull()?.let { d ->
                            iconCache[row.packageName] = d
                            icon.setImageDrawable(d)
                        }
                    }
                }
            }

            label.text = row.label
            checkbox.isChecked = row.checked
            itemView.setOnClickListener {
                val newChecked = !checkbox.isChecked
                checkbox.isChecked = newChecked
                onToggle(row.packageName, newChecked)
            }
        }
    }
}
