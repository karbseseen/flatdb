package flatdb

import kotlin.math.max


open class FlatString(
	protected val data: CharArray,
	protected val begin: Int,
	final override val length: Int,
) : CharSequence {
	override fun get(index: Int) = data[begin + index]
	override fun subSequence(startIndex: Int, endIndex: Int) =
		FlatString(data, begin + startIndex, endIndex - startIndex)

	private var hash = 0
	override fun hashCode() = run {
		if (hash == 0)
			for (index in begin until begin + length)
				hash = hash * 31 + data[index].code
		hash
	}
	override fun equals(other: Any?) =
		other is FlatString &&
		length == other.length &&
		(0 until length).all { index -> data[begin + index] == other.data[other.begin + index] }
	override fun toString() = String(data, begin, length)

	interface Allocator<S: FlatString> {
		val currentLength: Int

		fun put(char: Char)
		fun put(chars: CharSequence) = chars.forEach(::put)

		val view: S
		/**Is valid forever*/
		fun save(view: S): S
		/**Is valid forever*/
		fun string() = save(view)
		/**Is valid until next put*/
		fun clear()
		/**Is valid until next put*/
		fun tempString() = view.also { clear() }

		fun distinct() = Distinct(this)

		class Simple(initialCapacity: Int = 1024 * 2) : Allocator<FlatString> {
			private var data = CharArray(max(initialCapacity, 1))
			private var begin = 0
			private var end = 0
			override val currentLength get() = end - begin

			override fun put(char: Char) {
				if (end == data.size) {
					data = data.copyInto(CharArray(data.size * 2), 0, begin, end)
					end -= begin
					begin = 0
				}
				data[end++] = char
			}
			override val view get() = FlatString(data, begin, end - begin)
			override fun save(view: FlatString) = view.also { begin = end }
			override fun clear() { end = begin }
		}

		class Distinct<S: FlatString> internal constructor(val base: Allocator<S>) : Allocator<S> {
			val values = HashMap<S, S>()
			override val currentLength get() = base.currentLength
			override fun put(char: Char) = base.put(char)
			override val view get() = base.view
			override fun save(view: S) =
				values[view]?.also { base.clear() } ?: base.save(view).also { values[it] = it }
			override fun clear() = base.clear()
		}
	}
}
