package flatdb

abstract class FlatStruct {
	var size = 0; private set
	var bitSize = 0; private set(value) {
		field = value
		size = (value + 31) / 32
	}
	private val remainingBits get() = 32 - bitSize % 32

	private fun fieldSizeException(fieldSize: Int): Nothing =
		throw Exception("Invalid field size of $fieldSize while remaining bit count is $remainingBits")
	private fun <T> addField(field: (Int) -> T) = run {
		if (bitSize % 32 != 0) fieldSizeException(32)
		field(bitSize / 32).also { bitSize += 32 }
	}
	private fun <T> addPartField(bits: Int, field: (Int, Int) -> T) = run {
		if (bits > remainingBits) fieldSizeException(bits)
		field(bitSize, bitSize + bits).also { bitSize += bits }
	}

	protected fun int() 							= addField(::IntField)
	protected fun int(size: Int)					= addPartField(size, ::IntPartField)
	protected fun bool() 							= addPartField(1, ::BoolPartField)
	protected fun <R : FlatStruct> ref()			= RefField<R>(int())
	protected fun <R : FlatStruct> ref(size: Int)	= RefPartField<R>(int(size))
	protected fun str()								= ref<FlatString.Companion>()
	protected fun str(size: Int)					= ref<FlatString.Companion>(size)
}
