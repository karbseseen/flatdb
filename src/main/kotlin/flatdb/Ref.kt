package flatdb

@JvmInline value class Ref<T>(val offset: Int) {
	operator fun plus(inc: Int) = Ref<T>(offset + inc)
	operator fun minus(dec: Int) = Ref<T>(offset - dec)

	class Range<T>(val begin: Ref<T>, val end: Ref<T>, val itemSize: Int) : AbstractCollection<Ref<T>>() {
		override val size get() = (end.offset - begin.offset) / itemSize
		override fun iterator() = object : Iterator<Ref<T>> {
			var current = begin
			override fun hasNext() = current.offset < end.offset
			override fun next() = current.also { current += itemSize }
		}
	}
}
