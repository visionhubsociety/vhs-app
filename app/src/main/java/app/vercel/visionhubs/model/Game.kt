package app.vercel.visionhubs.model

import androidx.compose.runtime.Immutable

@Immutable
data class LinkObject(
    val label: String = "",
    val url: String = ""
)

@Immutable
data class Game(
    val id: String = "",
    val name: String = "",
    val desc: String = "",
    val category: String = "Geral",
    val banner: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val linkObjects: List<LinkObject> = emptyList()
)
