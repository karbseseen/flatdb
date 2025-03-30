package flatdb

import flatdb.util.IntArrayList
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream


class FlatArray<S : FlatStruct>(val struct: S) : Sequence<Ref<S>> {
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

	fun copyItem(destination: FlatArray<S>, src: Ref<S>, dst: Ref<S>) {
		for (index in 0 until itemSize)
			destination.data[dst.offset + index] = data[src.offset + index]
	}
	fun shareData(destination: FlatArray<S>) { destination.data = data }

	fun save(output: OutputStream) {
		val dataOutput = DataOutputStream(output)
		data.forEach(dataOutput::writeInt)
	}
	fun load(input: InputStream) {
		val dataInput = DataInputStream(input)
		while (true)
			data += try { dataInput.readInt() } catch (_: Throwable) { break }
	}
}
