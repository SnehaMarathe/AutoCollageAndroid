package com.example.collage

import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel

class CollageViewModel : ViewModel() {

    val selectedTemplate = mutableStateOf(CollageTemplates.all.first())
    val slotUris = mutableStateListOf<Uri?>()
    val slotTransforms = mutableStateListOf<SlotTransform>()

    val spacingPx = mutableStateOf(14f)
    val cornerRadiusPx = mutableStateOf(26f)

    private val thumbCache = LruCache<String, ImageBitmap>(80)

    init { setTemplate(selectedTemplate.value) }

    fun setTemplate(t: CollageTemplate) {
        selectedTemplate.value = t
        slotUris.clear()
        slotTransforms.clear()
        repeat(t.slots.size) {
            slotUris.add(null)
            slotTransforms.add(SlotTransform())
        }
    }

    fun setSlotUri(index: Int, uri: Uri) {
        if (index in 0 until slotUris.size) {
            slotUris[index] = uri
            slotTransforms[index] = SlotTransform()
        }
    }

    fun setSlotTransform(index: Int, transform: SlotTransform) {
        if (index in 0 until slotTransforms.size) slotTransforms[index] = transform
    }

    fun getCachedThumb(uri: Uri) = thumbCache.get(uri.toString())
    fun putCachedThumb(uri: Uri, bmp: ImageBitmap) { thumbCache.put(uri.toString(), bmp) }
}
