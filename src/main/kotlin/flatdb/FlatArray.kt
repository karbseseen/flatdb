package flatdb

import flatdb.util.IntArrayList

class FlatArray<S : FlatStruct> private constructor(val itemSize: Int) {
	constructor(struct: S) : this(struct.size)
	internal val data = IntArrayList()
	val size get() = data.size / itemSize

	val endRef get() = Ref<S>(data.size)
	fun add() = endRef.also { data.resize(data.size + itemSize) }
	fun add(count: Int) = run {
		val begin = endRef
		data.resize(data.size + count * itemSize)
		Ref.Range(begin, endRef, itemSize)
	}
	fun removeLast() = data.resize(data.size - itemSize)
}
