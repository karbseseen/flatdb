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

	protected fun <F: FlatField<*>> field(factory: (Int) -> F) =
		if (bitSize % 32 != 0) fieldSizeException(32)
		else factory(bitSize / 32).also { bitSize += 32 }
	protected fun <F: FlatField<*>> field(bits: Int, factory: (Int, Int) -> F) =
		if (bits > remainingBits) fieldSizeException(bits)
		else factory(bitSize, bitSize + bits).also { bitSize += bits }

	protected fun int() = field(::IntField)
	protected fun int(bits: Int) = field(bits, ::IntPartField)
	protected fun bool() = field(1, ::BoolPartField)
	protected fun str() = field(::StrField)
	protected fun str(bits: Int) = field(bits, ::StrPartField)
	protected fun <R: FlatStruct> ref() = field { RefField<R>(it) }
	protected fun <R: FlatStruct> ref(bits: Int) = field(bits) { o,s -> RefPartField<R>(o,s) }
}
