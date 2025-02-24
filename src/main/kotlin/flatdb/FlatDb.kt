package flatdb


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