package flatdb


abstract class FlatField<V> internal constructor() {
	protected abstract fun getInner(ref: Ref<*>, array: FlatArray<*>): Int
	protected abstract fun setInner(ref: Ref<*>, array: FlatArray<*>, value: Int)

	abstract fun getValue(ref: Ref<*>, array: FlatArray<*>): V
	abstract fun setValue(ref: Ref<*>, array: FlatArray<*>, value: V)
}


abstract class FullField<V>(val intOffset: Int) : FlatField<V>() {
	override fun getInner(ref: Ref<*>, array: FlatArray<*>) =
		array.data[ref.offset + intOffset]
	override fun setInner(ref: Ref<*>, array: FlatArray<*>, value: Int) {
		array.data[ref.offset + intOffset] = value
	}
}
abstract class PartField<V>(bitOffset: Int, bitSize: Int) : FlatField<V>() {
	val intOffset = bitOffset / 32
	val bitOffset = bitOffset % 32
	val mask = ((1 shl bitSize) - 1) ushr bitOffset
	override fun getInner(ref: Ref<*>, array: FlatArray<*>) =
		array.data[ref.offset + intOffset] and mask ushr bitOffset
	override fun setInner(ref: Ref<*>, array: FlatArray<*>, value: Int) {
		val offset = ref.offset + intOffset
		array.data[offset] = (array.data[offset] and mask.inv()) or (value shl bitOffset)
	}
}


open class IntField internal constructor(intOffset: Int) : FullField<Int>(intOffset) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = getInner(ref, array)
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: Int) = setInner(ref, array, value)
}
open class IntPartField internal constructor(bitOffset: Int, bitSize: Int) : PartField<Int>(bitOffset, bitSize) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = getInner(ref, array)
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: Int) = setInner(ref, array, value)
}

class BoolPartField internal constructor(bitOffset: Int, bitSize: Int) : PartField<Boolean>(bitOffset, bitSize) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = getInner(ref, array) != 0
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: Boolean) = setInner(ref, array, if (value) 1 else 0)
}

class RefField<R: FlatStruct> internal constructor(intOffset: Int) : FullField<Ref<R>>(intOffset) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = Ref<R>(getInner(ref, array))
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: Ref<R>) = setInner(ref, array, value.offset)
}
class RefPartField<R: FlatStruct> internal constructor(bitOffset: Int, bitSize: Int) : PartField<Ref<R>>(bitOffset, bitSize) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = Ref<R>(getInner(ref, array))
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: Ref<R>) = setInner(ref, array, value.offset)
}

class StrField internal constructor(intOffset: Int) : FullField<StrRef>(intOffset) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = StrRef(getInner(ref, array))
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: StrRef) = setInner(ref, array, value.offset)
}
class StrPartField internal constructor(bitOffset: Int, bitSize: Int) : PartField<StrRef>(bitOffset, bitSize) {
	override fun getValue(ref: Ref<*>, array: FlatArray<*>) = StrRef(getInner(ref, array))
	override fun setValue(ref: Ref<*>, array: FlatArray<*>, value: StrRef) = setInner(ref, array, value.offset)
}
