package flatdb


interface FlatFieldView<S: FlatStruct, V> {
	fun getValue(ref: Ref<S>, array: FlatArray<S>): V
}

class RangeField<S: FlatStruct, R: FlatStruct> internal constructor(
	val struct: S,
	val field: FlatRefField<S, R>,
) : FlatFieldView<S, Ref.Range<R>> {
	override fun getValue(ref: Ref<S>, array: FlatArray<S>) = Ref.Range(
		field.getValue(ref, array),
		field.getValue(ref + struct.size, array),
		struct.size,
	)
}
