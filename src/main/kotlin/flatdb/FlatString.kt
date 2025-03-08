package flatdb

import flatdb.FlatString.Allocator.Distinct


class FlatString(
	private val data: CharArray,
	private val begin: Int,
	override val length: Int,
) : CharSequence {
	companion object : FlatStruct()

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

	interface Allocator<T> {
		fun put(char: Char)
		val stringView: FlatString
		/**Is valid forever*/
		fun save(view: FlatString): T
		/**Is valid forever*/
		fun constString() = save(stringView)
		/**Is valid until next put*/
		fun clear()
		/**Is valid until next put*/
		fun tempString() = stringView.also { clear() }

		class Simple(initialCapacity: Int = 1024 * 4) : Allocator<FlatString> {
			private var data = CharArray(initialCapacity)
			private var begin = 0
			private var end = 0
			val currentLength get() = end - begin

			override fun put(char: Char) {
				if (end == data.size) data = CharArray(data.size * 2).also {
					val (oldBegin, oldEnd) = (begin to end).also { begin = 0; end = 0 }
					for (i in oldBegin until oldEnd) it[end++] = data[i]
				}
				data[end++] = char
			}

			override val stringView get() = FlatString(data, begin, currentLength)
			override fun save(view: FlatString) = view.also { begin = end  }
			override fun clear() { end = begin  }

		}

		class Distinct<T, A : Allocator<T>> internal constructor(val allocator: A) : Allocator<T> {
			val values = HashMap<FlatString, T>()
			override fun put(char: Char) = allocator.put(char)
			override val stringView get() = allocator.stringView
			override fun save(view: FlatString) =
				values.compute(stringView) { _,value -> value?.also { allocator.clear() } ?: allocator.save(view) }!!
			override fun clear() = allocator.clear()
		}
	}

}

fun <T, A: FlatString.Allocator<T>> A.distinct() = Distinct(this)
