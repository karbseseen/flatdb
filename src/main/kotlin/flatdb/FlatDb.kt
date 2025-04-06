package flatdb

import java.io.File
import kotlin.math.max
import kotlin.math.min


private var CharArray.offset
	get() = this[0].code or (this[1].code shl 16)
	set(value) { this[0] = value.toChar(); this[1] = (value shr 16).toChar() }

abstract class FlatDb {
	abstract val allArrays: Array<FlatArray<*>>

	fun save(path: File) {
		for (array in allArrays)
			File(path, array.struct.javaClass.simpleName).outputStream().use(array::save)
	}
	fun load(path: File) {
		for (array in allArrays)
			File(path, array.struct.javaClass.simpleName).inputStream().use(array::load)
	}

	class DbString internal constructor(data: CharArray, begin: Int, length: Int) : FlatString(data, begin, length) {
		val ref get() = StrRef(data.offset + begin)
	}
	class Allocator internal constructor(initialCapacity: Int, offset: Int) : FlatString.Allocator<DbString> {
		private val history = ArrayList<CharArray>()
		private var data = CharArray(max(initialCapacity, 3))
		private var begin = 3
		private var end = 3
		override val currentLength get() = end - begin

		init { data.offset = offset - 2 }

		private fun ensureHaveSpace() {
			if (end >= data.size) {
				history += data
				data = CharArray(data.size * 2).also {
					it.offset = data.offset + begin - 3
					if (begin < end) data.copyInto(it, 3, begin, end)
				}
				end = end - begin + 3
				begin = 3
			}
		}

		override fun put(char: Char) {
			ensureHaveSpace()
			data[end++] = char
		}
		override val view get() = DbString(data, begin, end - begin)
		override fun save(view: DbString) = view.also {
			data[begin - 1] = view.length.toChar()
			begin = ++end
			ensureHaveSpace()
		}
		override fun clear() { end = begin }

		internal fun flatten() = CharArray(data.offset + begin - 1).also { allData ->
			history += data
			data = CharArray(max(data.size / 2, 3)).apply { offset = allData.size - 2 }
			history += data
			history.zipWithNext { current, next ->
				System.arraycopy(current, 2, allData, current.offset + 2, next.offset - current.offset)
			}

			val last = view
			begin = 3
			end = 3
			put(last)
			history.clear()
		}
	}

	private var strings = CharArray(0)
	fun createAllocator(initialCapacity: Int = 1024 * 16) = Allocator(initialCapacity, strings.size)
	fun setData(allocator: Allocator) { strings = strings.copyInto(allocator.flatten()) }

	val StrRef.size get() = strings[offset - 1].code
	val StrRef.value get() = FlatString(strings, offset, size)
}
