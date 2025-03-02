package flatdb.util

fun encodeInt(value: Int, writeByte: (Char) -> Unit) = run {
	var i = value
	do {
		if (i < 0x80) writeByte(i.toChar())
		else writeByte((i and 0x7f).toChar())
		i = i ushr 7
	} while (i != 0)
}

fun decodeInt(readByte: () -> Char): Int = run {
	var i = 0
	var readValue: Int
	do {
		readValue = readByte().code
		i = (i shl 7) or (readValue and 0x7f)
	} while (readValue and 0x80 != 0)
	i
}