package flatdb


interface FlatField<S: FlatStruct, V> {
	fun getValue(ref: Ref<S>, array: FlatArray<S>): V
	fun setValue(ref: Ref<S>, array: FlatArray<S>, value: V)
}
abstract class FlatFieldInner<S: FlatStruct, V> internal constructor() {
	protected abstract fun getInner(ref: Ref<S>, array: FlatArray<S>): Int
	protected abstract fun setInner(ref: Ref<S>, array: FlatArray<S>, value: Int)
}

interface FlatRefField<S: FlatStruct, R: FlatStruct> : FlatField<S, Ref<R>> { val refStruct: R }


abstract class FullField<S: FlatStruct, V>(val intOffset: Int) : FlatField<S, V>, FlatFieldInner<S, V>() {
	override fun getInner(ref: Ref<S>, array: FlatArray<S>) =
		array.data[ref.offset + intOffset]
	override fun setInner(ref: Ref<S>, array: FlatArray<S>, value: Int) {
		array.data[ref.offset + intOffset] = value
	}
}
abstract class PartField<S: FlatStruct, V>(bitOffset: Int, bitSize: Int) : FlatField<S, V>, FlatFieldInner<S, V>() {
	val intOffset = bitOffset / 32
	val bitOffset = bitOffset % 32
	val mask = ((1 shl bitSize) - 1) shl this.bitOffset
	override fun getInner(ref: Ref<S>, array: FlatArray<S>) =
		array.data[ref.offset + intOffset] and mask ushr bitOffset
	override fun setInner(ref: Ref<S>, array: FlatArray<S>, value: Int) {
		val offset = ref.offset + intOffset
		array.data[offset] = (array.data[offset] and mask.inv()) or (value shl bitOffset)
	}
}


open class IntField<S: FlatStruct> internal constructor(intOffset: Int) :
	FullField<S, Int>(intOffset)
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = getInner(ref, array)
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: Int) = setInner(ref, array, value)
}
open class IntPartField<S: FlatStruct> internal constructor(bitOffset: Int, bitSize: Int) :
	PartField<S, Int>(bitOffset, bitSize)
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = getInner(ref, array)
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: Int) = setInner(ref, array, value)
}

class BoolPartField<S: FlatStruct> internal constructor(bitOffset: Int, bitSize: Int) :
	PartField<S, Boolean>(bitOffset, bitSize)
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = getInner(ref, array) != 0
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: Boolean) = setInner(ref, array, if (value) 1 else 0)
}

class RefField<S: FlatStruct, R: FlatStruct> internal constructor(intOffset: Int, override val refStruct: R) :
	FullField<S, Ref<R>>(intOffset), FlatRefField<S, R>
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = Ref<R>(getInner(ref, array))
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: Ref<R>) = setInner(ref, array, value.offset)
}
class RefPartField<S: FlatStruct, R: FlatStruct> internal constructor(bitOffset: Int, bitSize: Int, override val refStruct: R) :
	PartField<S, Ref<R>>(bitOffset, bitSize), FlatRefField<S, R>
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = Ref<R>(getInner(ref, array))
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: Ref<R>) = setInner(ref, array, value.offset)
}

class StrField<S: FlatStruct> internal constructor(intOffset: Int) :
	FullField<S, StrRef>(intOffset)
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = StrRef(getInner(ref, array))
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: StrRef) = setInner(ref, array, value.offset)
}
class StrPartField<S: FlatStruct> internal constructor(bitOffset: Int, bitSize: Int) :
	PartField<S, StrRef>(bitOffset, bitSize)
{
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = StrRef(getInner(ref, array))
	override fun setValue(ref: Ref<S>, array: FlatArray<S>, value: StrRef) = setInner(ref, array, value.offset)
}
