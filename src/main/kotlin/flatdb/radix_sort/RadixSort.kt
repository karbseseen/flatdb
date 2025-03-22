package flatdb.radix_sort

import flatdb.FlatArray
import flatdb.FlatDb
import flatdb.FlatStruct
import flatdb.Ref
import java.lang.Math.pow
import kotlin.collections.iterator
import kotlin.countLeadingZeroBits
import kotlin.math.pow
import kotlin.math.sqrt


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


enum class IterationNum { Auto, One, Two, Three }


object RawRadixSort {
	class PreSort<S: FlatStruct>(val initialData: FlatArray<S>, val shift: Int)

	inline operator fun <I : FlatStruct, S : FlatStruct, D : FlatStruct> invoke(
		indexArray: FlatArray<I>,
		srcArray: FlatArray<S>,
		dstArray: FlatArray<D>,
		indexGet: (Ref<I>) -> Ref<D>,
		indexSet: (Ref<I>, Ref<D>) -> Unit,
		get: (Ref<S>) -> Ref<I>,
		map: (Ref<S>, Ref<D>) -> Unit,
	) {
		for (ref in srcArray) {
			val index = get(ref)
			indexSet(index, indexGet(index) + dstArray.itemSize)
		}

		var sum = dstArray.add(srcArray.size).begin
		for (ref in indexArray) {
			sum += indexGet(ref).offset
			indexSet(ref, sum)
		}

		for (src in srcArray.reverseIterator()) {
			val index = get(src)
			val dst = indexGet(index) - dstArray.itemSize
			indexSet(index, dst)
			map(src, dst)
		}
	}

	inline fun <S : FlatStruct, D : FlatStruct> to(
		indexNum: Int,
		srcArray: FlatArray<S>,
		dstArray: FlatArray<D>,
		get: (Ref<S>) -> Int,
		map: (Ref<S>, Ref<D>) -> Unit,
	) = with(IndexArray()) {
		invoke(
			value.apply { add(indexNum) },
			srcArray,
			dstArray,
			{ Ref(it.indexRef) },
			{ ref, value -> ref.indexRef = value.offset },
			{ Ref(get(it)) },
			map,
		)
	}

	inline fun <S : FlatStruct> inPlace(
		indexNum: Int,
		array: FlatArray<S>,
		get: (Ref<S>) -> Int,
	) {
		if (indexNum <= 1) return
		val dstArray = FlatArray(array.struct)
		to(indexNum, array, dstArray, get) { src, dst -> array.copyItem(dstArray, src, dst) }
		dstArray.shareData(array)
	}

	inline fun <S : FlatStruct> presort(
		srcArray: FlatArray<S>,
		indexNum: Int,
		iterationNum: IterationNum,
		get: (Ref<S>) -> Int,
	) = run {
		val bitNum = { i: Int -> 32 - i.countLeadingZeroBits() }
		val roundDivide = { a: Int, b: Int -> (a + (b - 1) / 2) / b }

		val iterationNum = when (iterationNum) {
			IterationNum.One -> 1
			IterationNum.Two -> 2
			IterationNum.Three -> 3
			IterationNum.Auto -> {
				val arraySize = srcArray.size.toDouble()
				val indexNum = indexNum.toDouble()
				val complexity1 = arraySize + indexNum
				val complexity2 = (arraySize + sqrt(indexNum)) * 2
				val complexity3 = (arraySize + indexNum.pow(1.0 / 3)) * 2

				if (complexity1 < complexity2) 1
				else if (complexity2 < complexity3) 2
				else 3
			}
		}

		val initialData = FlatArray(srcArray.struct).also { srcArray.shareData(it) }
		val bitNum1: Int
		val bitNum2: Int

		if (iterationNum >= 2) {
			bitNum1 = roundDivide(bitNum(indexNum), iterationNum)
			val indexNum1 = 1 shl roundDivide(bitNum(indexNum), iterationNum)
			inPlace(indexNum1, srcArray) { get(it) and (indexNum1 - 1) }
		} else bitNum1 = 0

		if (iterationNum == 3) {
			bitNum2 = roundDivide(bitNum(indexNum ushr bitNum1), 2)
			val indexNum2 = 1 shl bitNum2
			inPlace(indexNum2, srcArray) { get(it) ushr bitNum1 and (indexNum2 - 1) }
		} else bitNum2 = 0

		PreSort(initialData, bitNum1 + bitNum2)
	}

}

inline fun <S: FlatStruct, D: FlatStruct> FlatArray<S>.radixSortTo(
	dstArray: FlatArray<D>,
	indexNum: Int = Int.MAX_VALUE,
	iterationNum: IterationNum = IterationNum.Auto,
	get: (Ref<S>) -> Int,
	map: (Ref<S>, Ref<D>) -> Unit,
) {
	val presort = RawRadixSort.presort(this, indexNum, iterationNum, get)
	RawRadixSort.to(indexNum ushr presort.shift, this, dstArray, get, map)
	presort.initialData.shareData(this)
}

inline fun <S: FlatStruct> FlatArray<S>.radixSortInPlace(
	indexNum: Int = Int.MAX_VALUE,
	iterationNum: IterationNum = IterationNum.Auto,
	get: (Ref<S>) -> Int,
) {
	val presort = RawRadixSort.presort(this, indexNum, iterationNum, get)
	RawRadixSort.inPlace(indexNum ushr presort.shift, this, get)
}
