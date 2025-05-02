package flatdb.util

import kotlin.math.max


class IntArrayList(initialCapacity: Int = 1024) : AbstractMutableList<Int>() {

	private var data = IntArray(max(initialCapacity, 1))
	override var size = 0; private set

	override fun get(index: Int) = data[index]
	override fun set(index: Int, element: Int) = data[index].also { data[index] = element }

	fun resize(newSize: Int) {
		if (data.size < newSize) {
			val actualNewSize = 1 shl (32 - (newSize - 1).countLeadingZeroBits())
			data = IntArray(actualNewSize) { if (it < size) data[it] else 0 }
		}
		size = newSize
	}

	override fun add(element: Int) = true.also {
		if (size == data.size) {
			data = IntArray(data.size * 2) { if (it < size) data[it] else 0 }
		}
		data[size++] = element
	}

	override fun add(index: Int, element: Int) {
		if (size == data.size) {
			data = IntArray(data.size * 2) {
				if (it < index) data[it]
				else if (it == index) element
				else if (it <= size) data[it - 1]
				else 0
			}
		} else {
			for (i in size downTo index + 1)
				data[i] = data[i - 1]
			data[index] = element
		}
		size++
	}

	override fun removeAt(index: Int) = data[index].also {
		for (i in index until size-1)
			data[i] = data[i + 1]
		size--
	}

	override fun clear() { size = 0 }

	fun sort() = java.util.Arrays.sort(data, 0, size)
}