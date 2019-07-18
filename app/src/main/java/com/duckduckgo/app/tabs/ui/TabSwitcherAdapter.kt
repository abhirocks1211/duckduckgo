/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.tabs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.updateMargins
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.image.GlideApp
import com.duckduckgo.app.global.image.GlideRequests
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.show
import com.duckduckgo.app.global.view.toPx
import com.duckduckgo.app.tabs.model.TabEntity
import com.google.android.material.card.MaterialCardView
import kotlinx.android.synthetic.main.item_tab.view.*
import timber.log.Timber
import java.io.File

private const val DIFF_KEY_TITLE = "title"
private const val DIFF_KEY_PREVIEW = "previewImage"
private const val DIFF_KEY_VIEWED = "viewed"

class TabSwitcherAdapter(private val itemClickListener: TabSwitchedListener, private val webViewPreviewPersister: WebViewPreviewPersister) :
    ListAdapter<TabEntity, TabSwitcherAdapter.TabViewHolder>(TabEntityDiffCallback()) {

    private var selectedTab: TabEntity? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(R.layout.item_tab, parent, false) as MaterialCardView

        return TabViewHolder(
            root = root,
            favicon = root.favicon,
            tabPreview = root.tabPreview,
            tabPreviewPlaceholder = root.tabPreviewPlaceholder,
            title = root.title,
            close = root.close,
            cardContentsContainer = root.cardContentsContainer
        )
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val context = holder.root.context

        val tab = getItem(position)
        holder.title.text = tab.displayTitle(context)
        //holder.tabUnread.visibility = if (tab.viewed) View.INVISIBLE else View.VISIBLE

        if (tab.tabId == selectedTab?.tabId) {
            holder.root.strokeWidth = 2.toPx()
            val marginSize = holder.root.strokeWidth + 2.toPx()
            updateMargin(holder, marginSize)
        } else {
            holder.root.strokeWidth = 0
            updateMargin(holder, 0)
        }

        val glide = GlideApp.with(holder.root)

        glide.load(tab.favicon())
            .placeholder(R.drawable.ic_globe_gray_16dp)
            .error(R.drawable.ic_globe_gray_16dp)
            .into(holder.favicon)


        loadTabPreviewImage(tab, glide, holder, initialisePreviews = true)

        //ViewCompat.setTransitionName(holder.root, tab.tabId)
        attachClickListeners(holder, tab)

        holder.root

    }

    private fun updateMargin(holder: TabViewHolder, marginSize: Int) {
        val params = holder.cardContentsContainer.layoutParams as ViewGroup.MarginLayoutParams
        params.updateMargins(left = marginSize, top = marginSize, right = marginSize, bottom = marginSize)
        holder.cardContentsContainer.layoutParams = params
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        Timber.i("Found ${payloads.size} payloads")

        for (payload in payloads) {
            val bundle = payload as Bundle

            for (key: String in bundle.keySet()) {
                Timber.i("Need an update, as $key changed")
            }

            bundle[DIFF_KEY_PREVIEW]?.let {
                loadTabPreviewImage(getItem(position), GlideApp.with(holder.root), holder, initialisePreviews = false)
            }

            bundle[DIFF_KEY_TITLE]?.let {
                holder.title.text = it as String
            }

            bundle[DIFF_KEY_VIEWED]?.let {
                Timber.w("TODO: viewed status changed")
            }
        }
    }

    private fun loadTabPreviewImage(tab: TabEntity, glide: GlideRequests, holder: TabViewHolder, initialisePreviews: Boolean) {
        if (initialisePreviews) {
            initialiseWebViewPreviewImages(holder)
        }

        val previewFile = tab.tabPreviewFile ?: return
        val cachedWebViewPreview = File(webViewPreviewPersister.fullPathForFile(previewFile))
        if (!cachedWebViewPreview.exists()) {
            return
        }

        holder.tabPreview.show()
        glide.load(cachedWebViewPreview)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .listener(WebViewPreviewGlideListener(holder))
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.tabPreview)
    }

    private fun initialiseWebViewPreviewImages(holder: TabViewHolder) {
        holder.tabPreviewPlaceholder.show()
        holder.tabPreview.gone()
    }

    private fun attachClickListeners(holder: TabViewHolder, tab: TabEntity) {
        holder.root.setOnClickListener {
            itemClickListener.onTabSelected(tab)
        }
        holder.close.setOnClickListener {
            itemClickListener.onTabDeleted(tab)
        }
    }

    fun updateData(data: List<TabEntity>?, selectedTab: TabEntity?) {
        if (data == null) return

        submitList(data)
        this.selectedTab = selectedTab
    }

    fun getTab(position: Int): TabEntity = getItem(position)

    fun adapterPositionForTab(tabId: String?): Int {
        if (tabId == null) return -1
        Timber.i("Finding adapter position for $tabId from a list of ${currentList.size} tabs")
        return currentList.indexOfFirst { it.tabId == tabId }
    }

    interface TabSwitchedListener {
        fun onNewTabRequested()
        fun onTabSelected(tab: TabEntity)
        fun onTabDeleted(tab: TabEntity)
    }

    data class TabViewHolder(
        val root: MaterialCardView,
        val favicon: ImageView,
        val tabPreview: ImageView,
        val tabPreviewPlaceholder: ImageView,
        val title: TextView,
        val close: ImageView/*,
        val tabUnread: View*/,
        val cardContentsContainer: ViewGroup
    ) : ViewHolder(root)

    companion object {

        @DrawableRes
        private const val SELECTED_BACKGROUND = R.drawable.tab_background_selected
        @DrawableRes
        private const val DEFAULT_BACKGROUND = R.drawable.tab_background

    }

}

class TabEntityDiffCallback : DiffUtil.ItemCallback<TabEntity>() {

    override fun areItemsTheSame(oldItem: TabEntity, newItem: TabEntity): Boolean {
        return oldItem.tabId == newItem.tabId
    }

    override fun areContentsTheSame(oldItem: TabEntity, newItem: TabEntity): Boolean {
        return !previewImageChanged(oldItem, newItem) &&
                !viewStatusChanged(oldItem, newItem) &&
                !titleChanged(oldItem, newItem)
    }

    private fun titleChanged(oldItem: TabEntity, newItem: TabEntity) =
        oldItem.title != newItem.title

    private fun viewStatusChanged(oldItem: TabEntity, newItem: TabEntity) =
        oldItem.viewed != newItem.viewed

    private fun previewImageChanged(oldItem: TabEntity, newItem: TabEntity): Boolean {
        return oldItem.tabPreviewFile != newItem.tabPreviewFile
    }

    override fun getChangePayload(oldItem: TabEntity, newItem: TabEntity): Bundle {
        Timber.i("Detected a difference in $oldItem and $newItem")

        val diffBundle = Bundle()

        if (titleChanged(oldItem, newItem)) {
            diffBundle.putString(DIFF_KEY_TITLE, newItem.title)
        }

        if (viewStatusChanged(oldItem, newItem)) {
            diffBundle.putBoolean(DIFF_KEY_VIEWED, newItem.viewed)
        }

        if (previewImageChanged(oldItem, newItem)) {
            diffBundle.putString(DIFF_KEY_PREVIEW, newItem.tabPreviewFile)
        }

        return diffBundle
    }

}
