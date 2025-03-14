package flatdb.util

import flatdb.FlatArray
import flatdb.FlatDb
import flatdb.FlatStruct
import flatdb.Ref
import kotlin.collections.iterator


object IndexStruct : FlatStruct() { val indexRef = int() }
class IndexArray : IndexArrayBase() { val value = FlatArray(IndexStruct) }

/*Generated*/
val Ref<IndexStruct>.next @JvmName("IndexStruct_next") get() = Ref<IndexStruct>(offset + IndexStruct.size)
val Ref<IndexStruct>.prev @JvmName("IndexStruct_prev") get() = Ref<IndexStruct>(offset - IndexStruct.size)

sealed class IndexArrayBase : FlatDb() {
	private val value = FlatArray(IndexStruct)
	var Ref<IndexStruct>.indexRef
		@JvmName("IndexStruct_indexRef") get() = IndexStruct.indexRef.getValue(this, value)
		@JvmName("IndexStruct_indexRef") set(v) { IndexStruct.indexRef.setValue(this, value, v) }
}
/*Generated*/


inline fun <I : FlatStruct, S : FlatStruct, D : FlatStruct> radixSort(
	indexArray: FlatArray<I>,
	srcArray: FlatArray<S>,
	dstArray: FlatArray<D>,
	indexGet: (Ref<I>) -> Ref<D>,
	indexSet: (Ref<I>, Ref<D>) -> Unit,
	getter: (Ref<S>) -> Ref<I>,
	mapper: (Ref<S>, Ref<D>) -> Unit,
) {
	for (ref in srcArray) {
		val index = getter(ref)
		indexSet(index, indexGet(index) + dstArray.itemSize)
	}

	var sum = dstArray.add(srcArray.size).begin
	for (ref in indexArray) {
		sum += indexGet(ref).offset
		indexSet(ref, sum)
	}

	for (src in srcArray.reverseIterator()) {
		val index = getter(src)
		val dst = indexGet(index) - dstArray.itemSize
		indexSet(index, dst)
		mapper(src, dst)
	}
}

inline fun <S : FlatStruct, D : FlatStruct> radixSort(
	indexNum: Int,
	srcArray: FlatArray<S>,
	dstArray: FlatArray<D>,
	getter: (Ref<S>) -> Int,
	mapper: (Ref<S>, Ref<D>) -> Unit,
) = with(IndexArray()) {
	radixSort(
		value.apply { add(indexNum) },
		srcArray,
		dstArray,
		{ Ref(it.indexRef) },
		{ ref, value -> ref.indexRef = value.offset },
		{ Ref(getter(it)) },
		mapper
	)
}

inline fun <S: FlatStruct> FlatArray<S>.radixSortInPlace(indexNum: Int, getter: (Ref<S>) -> Int) {
	val dstArray = FlatArray(struct).also { it.add(size) }
	radixSort(indexNum, this, dstArray, getter) { src, dst -> this.copyItem(dstArray, src, dst) }
	dstArray.shareData(this)
}
