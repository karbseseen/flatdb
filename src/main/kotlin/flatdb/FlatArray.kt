package flatdb

import flatdb.util.IntArrayList

class FlatArray<S : FlatStruct>(val struct: S) {
	internal val data = IntArrayList()
	val itemSize = struct.size
	val size get() = data.size / itemSize

	fun add() = endRef.also { data.resize(data.size + itemSize) }
	fun add(count: Int) = run {
		val begin = endRef
		data.resize(data.size + count * itemSize)
		Ref.Range(begin, endRef, itemSize)
	}
	fun removeLast() = data.resize(data.size - itemSize)

	val endRef get() = Ref<S>(data.size)
	val validEndRef get() = add().also { removeLast() }
}
