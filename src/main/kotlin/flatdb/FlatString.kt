package flatdb


class FlatString(
	private val data: ByteArray,
	private val begin: Int,
	override val length: Int,
) : CharSequence {

	override fun get(index: Int) = data[begin + index].toInt().toChar()
	override fun subSequence(startIndex: Int, endIndex: Int) =
		FlatString(data, begin + startIndex, endIndex - startIndex)

	private var hash = 0
	override fun hashCode() = run {
		if (hash == 0)
			for (index in begin until begin + length)
				hash = hash * 31 + (data[index].toInt() and 0xff)
		hash
	}
	override fun equals(other: Any?) =
		other is FlatString &&
		length == other.length &&
		(0 until length).all { index -> data[begin + index] == other.data[other.begin + index]  }
	override fun toString() = String(data, begin, length)

	interface Allocator<T> {
		fun put(byte: Int)
		fun get(needSave: (FlatString) -> Boolean): T

		class Simple(initialCapacity: Int = 1024 * 4) : Allocator<FlatString> {
			private var data = ByteArray(initialCapacity)
			private var begin = 0
			private var end = 0
			val currentLength get() = end - begin

			override fun put(byte: Int) {
				if (end == data.size) data = ByteArray(data.size * 2).also {
					val (oldBegin, oldEnd) = (begin to end).also { begin = 0; end = 0 }
					for (i in oldBegin until oldEnd) it[end++] = data[i]
				}
				data[end++] = byte.toByte()
			}

			private fun save() { begin = end  }			//Is valid forever
			private fun clear() { end = begin  }		//Is valid until next put
			private val stringView get() = FlatString(data, begin, currentLength)
			fun constString() = stringView.also { save() }
			fun tempString() = stringView.also { clear() }
			override fun get(needSave: (FlatString) -> Boolean) = stringView.also { if (needSave(it)) save() else clear() }
			override fun toString() = stringView.toString()
		}
	}


}
