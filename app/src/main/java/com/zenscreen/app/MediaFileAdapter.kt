package com.zenscreen.app

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zenscreen.app.databinding.ItemMediaFileBinding

class MediaFileAdapter(
    private val onDelete: (MediaFile) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : RecyclerView.Adapter<MediaFileAdapter.VH>() {

    private val items = mutableListOf<MediaFile>()
    private val selected = mutableSetOf<String>()

    fun submitList(list: List<MediaFile>) {
        items.clear()
        items.addAll(list)
        selected.retainAll(items.map { it.file.name }.toSet())
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun getItems(): List<MediaFile> = items.toList()

    fun getSelectedNames(): Set<String> = selected.toSet()

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun moveItem(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    inner class VH(val binding: ItemMediaFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(mf: MediaFile) {
            binding.fileTypeIcon.setImageResource(when (mf.type) {
                MediaType.PDF   -> R.drawable.ic_description
                MediaType.IMAGE -> R.drawable.ic_image
                MediaType.VIDEO -> R.drawable.ic_movie
            })
            val sizeKb = "%.1f KB".format(mf.file.length() / 1024f)
            binding.fileName.text = "${mf.file.name}  ($sizeKb)"

            binding.fileCheckbox.setOnCheckedChangeListener(null)
            binding.fileCheckbox.isChecked = mf.file.name in selected
            binding.fileCheckbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(mf.file.name) else selected.remove(mf.file.name)
                onSelectionChanged(selected.size)
            }

            binding.btnDeleteFile.setOnClickListener { onDelete(mf) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMediaFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size
}
