package flatdb


class IntField internal constructor(val intOffset: Int) {
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = array.data[ref.offset + intOffset]
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: Int) { array.data[ref.offset + intOffset] = value }
}
class IntPartField internal constructor(bitOffset: Int, bitSize: Int) {
	val intOffset = bitOffset / 32
	val bitOffset = bitOffset % 32
	val mask = ((1 shl bitSize) - 1) ushr bitOffset
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = array.data[ref.offset + intOffset] and mask ushr bitOffset
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: Int) {
		val offset = ref.offset + intOffset
		array.data[offset] = (array.data[offset] and mask.inv()) or (value shl bitOffset)
	}
}

@JvmInline value class BoolPartField internal constructor(private val field: IntPartField) {
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = field.getValue(ref, array) != 0
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: Boolean) { field.setValue(ref, array, if (value) 1 else 0) }
}

@JvmInline value class RefField<R : FlatStruct> internal constructor(private val field: IntField) {
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = Ref<R>(field.getValue(ref, array))
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: Ref<R>) { field.setValue(ref, array, value.offset) }
}
@JvmInline value class RefPartField<R : FlatStruct> internal constructor(private val field: IntPartField) {
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = Ref<R>(field.getValue(ref, array))
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: Ref<R>) { field.setValue(ref, array, value.offset) }
}

@JvmInline value class StrField internal constructor(private val field: IntField) {
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = StrRef(field.getValue(ref, array))
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: StrRef) { field.setValue(ref, array, value.offset) }
}
@JvmInline value class StrPartField internal constructor(private val field: IntPartField) {
	fun <S : FlatStruct> getValue(ref: Ref<S>, array: FlatArray<S>) = StrRef(field.getValue(ref, array))
	fun <S : FlatStruct> setValue(ref: Ref<S>, array: FlatArray<S>, value: StrRef) { field.setValue(ref, array, value.offset) }
}
