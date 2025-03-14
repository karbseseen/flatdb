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

	protected fun int() =
		if (bitSize % 32 != 0) fieldSizeException(32)
		else IntField(bitSize / 32).also { bitSize += 32 }
	protected fun int(bits: Int) =
		if (bits > remainingBits) fieldSizeException(bits)
		else IntPartField(bitSize, bitSize + bits).also { bitSize += bits }

	protected fun bool() 							= BoolPartField(int(1))
	protected fun <R : FlatStruct> ref()			= RefField<R>(int())
	protected fun <R : FlatStruct> ref(bits: Int)	= RefPartField<R>(int(bits))
	protected fun str()								= StrField(int())
	protected fun str(bits: Int)					= StrPartField(int(bits))
}
