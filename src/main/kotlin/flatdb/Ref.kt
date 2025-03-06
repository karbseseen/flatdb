package flatdb

@JvmInline value class Ref<S: FlatStruct>(val offset: Int) {
	operator fun plus(inc: Int) = Ref<S>(offset + inc)
	operator fun minus(dec: Int) = Ref<S>(offset - dec)

	class Range<S : FlatStruct>(val begin: Ref<S>, val end: Ref<S>, val itemSize: Int) : AbstractCollection<Ref<S>>() {
		override val size get() = (end.offset - begin.offset) / itemSize
		override fun iterator() = object : Iterator<Ref<S>> {
			var current = begin
			override fun hasNext() = current.offset < end.offset
			override fun next() = current.also { current += itemSize }
		}
	}
}
