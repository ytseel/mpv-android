package `is`.xyz.mpv

import `is`.xyz.mpv.databinding.DialogTrackBinding
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView

internal typealias Listener = (MPVView.Track, Boolean) -> Unit

/** Primary subtitles only. The separate live sync control lives on the video overlay. */
internal class SubTrackDialog(private val player: MPVView) {
    private lateinit var binding: DialogTrackBinding
    private var tracks = listOf<MPVView.Track>()
    private var selectedMpvId = -1

    var listener: Listener? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        binding = DialogTrackBinding.inflate(layoutInflater)
        binding.list.adapter = CustomAdapter(this)
        refresh()
        Utils.handleInsetsAsPadding(binding.root)
        return binding.root
    }

    fun refresh() {
        tracks = player.tracks.getValue(TRACK_TYPE)
        selectedMpvId = player.sid
        binding.list.adapter?.notifyDataSetChanged()
        val index = tracks.indexOfFirst { it.mpvId == selectedMpvId }
        if (index >= 0) binding.list.scrollToPosition(index)
        binding.list.post { binding.list.parent?.requestLayout() }
    }

    private fun clickItem(position: Int) {
        tracks.getOrNull(position)?.let { listener?.invoke(it, false) }
    }

    class CustomAdapter(private val parent: SubTrackDialog) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {
        class ViewHolder(private val parent: SubTrackDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: CheckedTextView =
                ViewCompat.requireViewById(view, android.R.id.text1)

            init {
                view.setOnClickListener {
                    val index = bindingAdapterPosition
                    if (index != RecyclerView.NO_POSITION) parent.clickItem(index)
                }
            }

            fun bind(track: MPVView.Track, selected: Boolean) {
                textView.text = track.name
                textView.isChecked = selected
                textView.isEnabled = true
            }
        }

        override fun onCreateViewHolder(parentView: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parentView.context)
                .inflate(R.layout.dialog_track_item, parentView, false)
            return ViewHolder(parent, view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = parent.tracks[position]
            holder.bind(track, track.mpvId == parent.selectedMpvId)
        }

        override fun getItemCount() = parent.tracks.size
    }

    companion object {
        const val TRACK_TYPE = "sub"
    }
}
