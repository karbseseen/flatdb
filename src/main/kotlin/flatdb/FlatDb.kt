package flatdb

import flatdb.util.decodeInt
import flatdb.util.encodeInt
import java.lang.reflect.ParameterizedType
import kotlin.math.max


abstract class FlatDb {
	private var arrays: HashMap<Class<*>, FlatArray<*>>? = HashMap()
	private var canCreate = true
	protected fun <S : FlatStruct> FlatArray(struct: S) =
		arrays?.let { arrays ->
			(arrays.remove(struct.javaClass) as FlatArray<S>?)?.also {
				canCreate = false
				if (arrays.isEmpty()) this.arrays = null
			} ?: if (canCreate) flatdb.FlatArray(struct).also { arrays += struct.javaClass to it } else null
		} ?: throw ClassNotFoundException("Can't get FlatArray<" + struct.javaClass.name + ">")


	class Allocator internal constructor(existingData: CharArray, initialCapacity: Int) :
		FlatString.Allocator<Ref<FlatString.Companion>>
	{
		private class Data(val bytes: CharArray, val actualSize: Int)
		private val prevData = arrayListOf(Data(existingData, existingData.size))
		private var dataOffset = existingData.size
		private var data = CharArray(max(initialCapacity, 1))
		private var begin = 0
		private var end = 0

		override fun put(char: Char) {
			if (end == data.size) {
				prevData += Data(data, begin)
				dataOffset += begin
				data = data.copyInto(CharArray(data.size * 2), 0, begin, end)
				end -= begin
				begin = 0
			}
			data[end++] = char
		}
		override fun get(needSave: (FlatString) -> Boolean) = run {
			val view = FlatString(data, begin, end - begin)
			if (needSave(view)) Ref<FlatString.Companion>(dataOffset + end).also {
				encodeInt(view.length, ::put)
				begin = end
			}
			else Ref<FlatString.Companion>(-1).also { end = begin }
		}

		fun flatten() = run {
			val allBytes = CharArray(dataOffset + end)
			prevData.fold(0) { offset, data ->
				data.bytes.copyInto(allBytes, offset, 0, data.actualSize)
				offset + data.actualSize
			}
			data.copyInto(allBytes, dataOffset, 0, end)
		}
	}

	private var strings = CharArray(0)
	fun createAllocator(initialCapacity: Int = 1024 * 16) = Allocator(strings, initialCapacity)
	fun setData(allocator: Allocator) { strings = allocator.flatten() }

	fun Ref<FlatString.Companion>.get() = run {
		var offset = this.offset
		val length = decodeInt { strings[offset++] }
		FlatString(strings, this.offset - length, length)
	}
}
