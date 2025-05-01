@file:OptIn(Private::class)
package flatdb

@RequiresOptIn(message = "Only to be used in FlatStruct")
@Retention(AnnotationRetention.BINARY)
private annotation class Private


abstract class FlatStruct {
	override fun toString(): String = javaClass.simpleName

	var size = 0; private set
	var bitSize = 0; internal set(value) {
		field = value
		size = (value + 31) / 32
	}
	@Private val genericRanges = ArrayList<RangeField<*,*>>()
}
@Suppress("UNCHECKED_CAST")
val <S: FlatStruct> S.ranges get() = genericRanges as ArrayList<RangeField<S, FlatStruct>>


private val FlatStruct.remainingBits get() = 32 - bitSize % 32

private fun FlatStruct.fieldSizeException(fieldSize: Int): Nothing =
	throw Exception("Invalid field size of $fieldSize while remaining bit count is $remainingBits")

fun <S: FlatStruct, F: FlatField<S, *>> S.field(factory: (Int) -> F) =
	if (bitSize % 32 != 0) fieldSizeException(32)
	else factory(bitSize / 32).also { bitSize += 32 }
fun <S: FlatStruct, F: FlatField<S, *>> S.field(bits: Int, factory: (Int, Int) -> F) =
	if (bits > remainingBits) fieldSizeException(bits)
	else factory(bitSize, bitSize + bits).also { bitSize += bits }

fun <S: FlatStruct> S.int() = field(::IntField)
fun <S: FlatStruct> S.int(bits: Int) = field(bits, ::IntPartField)
fun <S: FlatStruct> S.bool() = field(1, ::BoolPartField)
fun <S: FlatStruct> S.str() = field(::StrField)
fun <S: FlatStruct> S.str(bits: Int) = field(bits, ::StrPartField)
fun <S: FlatStruct, R: FlatStruct> S.ref(ref: R) = field { RefField(it, ref) }
fun <S: FlatStruct, R: FlatStruct> S.ref(ref: R, bits: Int) = field(bits) { o,s -> RefPartField(o, s, ref) }

fun <S: FlatStruct, R: FlatStruct> S.range(field: FlatRefField<S, R>) =
	RangeField(this, field).also { genericRanges += it }
