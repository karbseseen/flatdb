package flatdb

class FlatArray<T> internal constructor(val itemSize: Int) {
	internal val data = IntArrayList()
	val size get() = data.size / itemSize

	val endRef get() = Ref<T>(data.size)
	fun add() = endRef.also { data.resize(data.size + itemSize) }
	fun add(count: Int) = run {
		val begin = endRef
		data.resize(data.size + count * itemSize)
		Ref.Range(begin, endRef, itemSize)
	}
	fun removeLast() = data.resize(data.size - itemSize)
}
/*
typealias ArrayFactory<T> = () -> FlatArray<T>
object FlatArrays {
	private val arrayFactories = HashMap<Class<*>, ArrayFactory<*>>()

	fun <T> putArrayFactory(factoryClass: Class<T>, factory: ArrayFactory<T>) { arrayFactories += factoryClass to factory }
	inline fun <reified T> putArrayFactory(noinline factory: ArrayFactory<T>) = putArrayFactory(T::class.java, factory)

	fun <T> get(factoryClass: Class<T>) = (arrayFactories[factoryClass] as ArrayFactory<T>?)?.let { it() }
		?: throw ClassNotFoundException("Factory for FlatArray<" + factoryClass.name + "> wasn't found")
	inline fun <reified T> get() = get(T::class.java)
}
*/