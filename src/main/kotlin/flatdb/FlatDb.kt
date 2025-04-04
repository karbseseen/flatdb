package flatdb

import java.io.File
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
	class Allocator internal constructor(initialCapacity: Int, offset: Int) :
		FlatString.Allocator.Base<DbString>(initialCapacity)
	{
		private val history = ArrayList<CharArray>()

		private fun applyOffset(offset: Int) {
			begin = 3
			end += 3
			data.offset = offset
		}

		init { applyOffset(offset) }

		override fun expandArray() {
			val newOffset = data.offset + begin - 3
			history += data
			super.expandArray()
			applyOffset(newOffset)
		}
		override val view get() = DbString(data, begin, currentLength)
		override fun save(view: DbString) = run {
			data[begin - 1] = currentLength.toChar()
			ensureHaveSpace()
			end++
			super.save(view)
		}

		internal fun flatten() = CharArray(data.offset + begin - 3).also { allData ->
			history += data
			history.forEach { it.copyInto(allData, it.offset, 2, min(it.size - 2, allData.size - it.offset)) }

			val last = view
			history.clear()
			begin = 3
			end = 3
			data.offset = allData.size
			put(last)
		}
	}

	private var strings = CharArray(0)
	fun createAllocator(initialCapacity: Int = 1024 * 16) = Allocator(initialCapacity, strings.size)
	fun setData(allocator: Allocator) { strings = strings.copyInto(allocator.flatten()) }

	val StrRef.size get() = strings[offset - 1].code
	fun StrRef.get() = DbString(strings, offset, size)
}
