package flatdb

import flatdb.util.IntArrayList
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread


class FlatArray<S : FlatStruct>(val struct: S, val db: FlatDb) : Sequence<Ref<S>> {
	internal var data = IntArrayList()
	val itemSize = struct.size
	val size get() = data.size / itemSize

	operator fun get(index: Int) = Ref<S>(index * itemSize)

	fun add() = endRef.also { data.resize(data.size + itemSize) }
	fun add(count: Int) = run {
		val begin = endRef
		data.resize(data.size + count * itemSize)
		Ref.Range(begin, endRef, itemSize)
	}
	fun removeLast() = data.resize(data.size - itemSize)
	fun clear() = data.clear()

	val endRef get() = Ref<S>(data.size)
	val validEndRef get() = add().also { removeLast() }

	override fun iterator() = object : Iterator<Ref<S>> {
		var ref = Ref<S>(0)
		override fun hasNext() = ref.offset < endRef.offset
		override fun next() = ref.also { ref += itemSize }
	}
	fun reverseIterator() = object : Iterator<Ref<S>> {
		var ref = endRef
		override fun hasNext() = ref.offset > 0
		override fun next() = (ref - itemSize).also { ref = it }
	}


	fun endRanges() {
		val end = validEndRef
		for (rangeField in struct.ranges) {
			val field = rangeField.field
			val array = db.allArrays.find { it.struct == field.refStruct }
				?: throw NoSuchFieldException("Couldn't find array for " + field.refStruct)
			field.setValue(end, this, Ref(array.endRef.offset))
		}
	}


	fun copyItem(destination: FlatArray<S>, src: Ref<S>, dst: Ref<S>) {
		for (index in 0 until itemSize)
			destination.data[dst.offset + index] = data[src.offset + index]
	}
	fun shareData(destination: FlatArray<S>) { destination.data = data }


	private val defaultFileName get() = struct.javaClass.simpleName + ".bin"

	fun save(output: OutputStream) {
		val dataOutput = DataOutputStream(output)
		data.forEach(dataOutput::writeInt)
		/*val bytes = ByteArray(4)
		for (i in data) {
			bytes[0] = i.toByte()
			bytes[1] = (i shr 8).toByte()
			bytes[2] = (i shr 16).toByte()
			bytes[3] = (i shr 24).toByte()
			output.write(bytes)
		}*/
	}
	fun save(path: File) = save(File(path, defaultFileName).outputStream().buffered())

	fun load(input: InputStream) {
		val dataInput = DataInputStream(input)
		while (true)
			data += try { dataInput.readInt() } catch (_: Throwable) { break }
	}
	fun load(path: File) = load(File(path, defaultFileName).inputStream().buffered())
}
