package hu.szileemester.adamazon.domain

/**
 * An icon is stored in the same text field as an emoji: either the emoji itself, or "img:{id}"
 * pointing at a photo sticker in the group's icon library.
 */
object IconRef {
    private const val PREFIX = "img:"

    fun of(imageId: String) = PREFIX + imageId

    fun isImage(icon: String) = icon.startsWith(PREFIX)

    /** The sticker id, or null for a plain emoji. */
    fun imageId(icon: String): String? = icon.takeIf(::isImage)?.removePrefix(PREFIX)?.ifEmpty { null }
}

/** A photo sticker of the group (WebP with transparency). */
data class GroupIcon(val id: String, val name: String, val image: ByteArray, val createdAt: Long = 0) {
    override fun equals(other: Any?): Boolean =
        other is GroupIcon && id == other.id && name == other.name && image.contentEquals(other.image)

    override fun hashCode(): Int = id.hashCode()
}
