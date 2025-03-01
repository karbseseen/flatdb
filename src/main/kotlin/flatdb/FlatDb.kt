package flatdb

import flatdb.util.decodeInt
import flatdb.util.encodeInt
import kotlin.math.max


abstract class FlatDb {
	private var arrays: HashMap<Class<*>, FlatArray<*>>? = HashMap()
	private var canCreate = true
	protected fun <S : FlatStruct> FlatArray(struct: S) =
		(arrays?.remove(struct.javaClass) as FlatArray<S>?)?.also {
			canCreate = false
			if (arrays!!.isEmpty()) arrays = null
		} ?: if (canCreate) flatdb.FlatArray(struct).also { arrays!! += struct.javaClass to it }
			else throw ClassNotFoundException("Can't get FlatArray<" + struct.javaClass.name + ">")
}

abstract class FlatStringDb : FlatDb() {

	class Allocator internal constructor(existingData: ByteArray, initialCapacity: Int) :
		FlatString.Allocator<Ref<FlatString>>
	{
		private class Data(val bytes: ByteArray, val actualSize: Int)
		private val prevData = arrayListOf(Data(existingData, existingData.size))
		private var dataOffset = existingData.size
		private var data = ByteArray(max(initialCapacity, 1))
		private var begin = 0
		private var end = 0

		override fun put(byte: Int) {
			if (end == data.size) {
				prevData += Data(data, begin)
				dataOffset += begin
				data = data.copyInto(ByteArray(data.size * 2), 0, begin, end)
				end -= begin
				begin = 0
			}
			data[end++] = byte.toByte()
		}
		override fun get(needSave: (FlatString) -> Boolean) = run {
			val view = FlatString(data, begin, end - begin)
			if (needSave(view)) Ref<FlatString>(dataOffset + end).also {
				encodeInt(view.length, ::put)
				begin = end
			}
			else Ref<FlatString>(-1).also { end = begin }
		}

		fun flatten() = run {
			val allBytes = ByteArray(dataOffset + end)
			prevData.fold(0) { offset, data ->
				data.bytes.copyInto(allBytes, offset, 0, data.actualSize)
				offset + data.actualSize
			}
			data.copyInto(allBytes, dataOffset, 0, end)
		}
	}

	private var strings = ByteArray(0)
	fun createAllocator(initialCapacity: Int = 1024 * 16) = Allocator(strings, initialCapacity)
	fun setData(allocator: Allocator) { strings = allocator.flatten() }

	fun Ref<FlatString>.get() = run {
		var offset = this.offset
		val length = decodeInt { strings[offset++].toInt() }
		FlatString(strings, this.offset - length, length, )
	}

}
