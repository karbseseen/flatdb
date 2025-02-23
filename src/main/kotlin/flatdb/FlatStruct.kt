package flatdb

/*
sealed class FlatField(val offset: Int, val size: Int) {
	fun <T : FlatStruct> get(ref: Ref<T>, array: FlatArray<T>) =

}
class IntField(offset: Int, size: Int) : FlatField(offset, size)
class StrField(offset: Int, size: Int) : FlatField(offset, size)
class RefField<T>(offset: Int, size: Int) : FlatField(offset, size)
class BoolField(offset: Int, size: Int) : FlatField(offset, size)*/

abstract class FlatStruct {

	var size = 0; private set
	var usedBits = 0; private set(value) {
		field = value
		size = (value + 31) / 32
	}

	private val remainingBits get() = 32 - usedBits % 32

	private fun fieldSizeException(fieldSize: Int): Nothing =
		throw Exception("Invalid field size of $fieldSize while remaining bit count is $remainingBits")
	private fun <T> addField(field: (Int) -> T) = run {
		if (usedBits % 32 != 0) fieldSizeException(32)
		field(usedBits / 32).also { usedBits += 32 }
	}
	private fun <T> addPartField(bits: Int, field: (Int, Int) -> T) = run {
		if (bits > remainingBits) fieldSizeException(bits)
		field(usedBits, usedBits + bits).also { usedBits += bits }
	}

	protected fun int() 							= addField(::IntField)
	protected fun int(size: Int)					= addPartField(size, ::IntPartField)
	protected fun <R : FlatStruct> ref()			= RefField<R>(int())
	protected fun <R : FlatStruct> ref(size: Int)	= RefPartField<R>(int(size))
	protected fun bool() 							= addPartField(1, ::BoolPartField)
}
