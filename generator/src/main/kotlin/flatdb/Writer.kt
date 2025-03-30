package flatdb

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.BufferedWriter


interface Writer {
	val declaration: KSClassDeclaration
	val imports: List<String>
	fun write(out: BufferedWriter)
}

fun CodeGenerator.write(writers: Iterable<Writer>) {
	for ((file, writers) in writers.distinct().groupBy { it.declaration.file })
		createNewFile(Dependencies(true, file), file.packageName.asString(), file.simpleName)
		.bufferedWriter()
		.use { out ->
			file.packageName.asString().takeIf { it.isNotEmpty() }?.let { packageName -> out.writeln("package $packageName\n") }
			writers.flatMap { it.imports }.distinct().forEach { out.writeln("import $it") }
			out.writeln()
			writers
				.sortedBy { it.declaration.simpleNameStr }
				.sortedBy { (it.declaration.location as? FileLocation)?.lineNumber ?: -1 }
				.forEach { out.writeln(); it.write(out) }
		}
}

class StructWriter(val struct: KSClassDeclaration, val structFields: StructsFields) : Writer {
	override val declaration get() = struct
	override val imports get() = listOf(Ref::class.qualifiedName!!)
	override fun write(out: BufferedWriter) = with (out) {
		val def = structFields[struct].modifier?.takeIf { it == Modifier.Internal }?.value.orEmpty() + "val"
		val type = struct.callName
		val jvmType = type.replace(".", "")
		writeln("$def Ref<$type>.index @JvmName(\"${jvmType}_index\") get() = offset / $type.size")
		writeln("$def Ref<$type>.nextRef @JvmName(\"${jvmType}_next\") get() = Ref<$type>(offset + $type.size)")
		writeln("$def Ref<$type>.prevRef @JvmName(\"${jvmType}_prev\") get() = Ref<$type>(offset - $type.size)")
	}
	override fun hashCode() = struct.hashCode()
	override fun equals(other: Any?) = other is StructWriter && struct == other.struct
}

class DbWriter(val db: KSClassDeclaration, val structFields: StructsFields) : Writer {
	override val declaration get() = db
	override fun hashCode() = db.hashCode()
	override fun equals(other: Any?) = other is DbWriter && db == other.db

	val arrays = LinkedHashSet<Field>().also { arrays ->
		for (property in db.getDeclaredProperties()) {
			val flatArrayType = property.type.aliasResolve()
				.takeIf { it.declaration.qualifiedNameStr == FlatArray::class.qualifiedName }
				?: continue
			val structType = flatArrayType.arguments[0].type?.aliasResolve()
				?: throw ClassNotFoundException("Couldn't get FlatArray type for " + property.qualifiedNameStr)
			val array = Field(property, structType)
				.takeIf { it.typeClass.classKind == ClassKind.OBJECT }
				?: throw ClassCastException(structType.declaration.qualifiedNameStr + " must be an object")
			if (!arrays.add(array))
				throw Exception("Found multiple occurrences of FlatArray<" + array.typeClass.qualifiedNameStr + "> in " + db.qualifiedNameStr)
		}
	}.toList()

	override val imports get() = listOf(
		*arrays.map { it.typeClass.import }.toTypedArray(),
		*arrays.flatMap { structFields[it.typeClass].fields }.mapNotNull { it.rangeClass?.import }.toTypedArray(),
		Ref::class.qualifiedName!!,
		FlatDb::class.qualifiedName!!,
		FlatArray::class.qualifiedName!!,
	)

	override fun write(out: BufferedWriter) = with (out) {
		val arrayByStruct by lazy { arrays.associateBy { it.typeClass.qualifiedNameStr } }
		val rangeArrays = ArrayList<Field>()
		val dbModifier by lazy { db.modifier }
		writeln("sealed class " + db.simpleNameStr + "Base : FlatDb() {")
		writeln("\tprivate val actualThis = this as " + db.simpleNameStr)
		writeln("\toverride val allArrays get() = arrayOf(" + arrays.joinToString(", ") { it.name } + ")")
		for (array in arrays) {
			val rangeFields = ArrayList<StructField>()

			val arrayName = array.name
			val type = array.typeClass.callName
			val jvmType = type.replace(".", "")
			val valueArg = if (arrayName == "value") "v" else "value"

			val arrayModifier = array.declaration.modifier
			val struct = structFields[array.typeClass]
			val rangeLines = ArrayList<String>()
			writeln()
			writeln("\tprivate val $arrayName get() = actualThis.$arrayName")
			for (field in struct.fields) {
				val name = field.name
				val modifier = arrayModifier ?: field.declaration.modifier ?: dbModifier ?: struct.modifier ?: Modifier.Public
				val varModifier = if (!modifier.onlySet) modifier.value else ""
				val setModifier = if ( modifier.onlySet) modifier.value else ""
				writeln("\t${varModifier}var Ref<$type>.$name")
				writeln("\t\t@JvmName(\"${jvmType}_$name\") get() = $type.$name.getValue(this, $arrayName)")
				writeln("\t\t@JvmName(\"${jvmType}_$name\") ${setModifier}set($valueArg) { $type.$name.setValue(this, $arrayName, $valueArg) }")
				field.rangeName?.let { rangeName ->
					rangeFields += field
					val rangeType = field.rangeClass?.callName
					rangeLines += "\t${varModifier}val Ref<$type>.$rangeName"
					rangeLines += "\t\t@JvmName(\"${jvmType}_$rangeName\") get() = Ref.Range($name, nextRef.$name, ${rangeType}.size)"
				}
			}

			for (rangeLine in rangeLines) writeln(rangeLine)

			if (rangeFields.isNotEmpty()) {
				rangeArrays += array
				val modifier = arrayModifier ?: dbModifier ?: struct.modifier ?: Modifier.Public
				writeln("\t@JvmName(\"${jvmType}_endRanges\") ${modifier.value}fun FlatArray<$type>.endRanges() = validEndRef.let {")
				for (field in rangeFields) {
					val rangeClass = field.rangeClass
					val refArrayName = rangeClass?.let { arrayByStruct[rangeClass.qualifiedNameStr]?.name }
						?: throw ClassNotFoundException(
							"Not found referenced class " + (rangeClass?.qualifiedNameStr?:"???") +
							" for range field " + field.declaration.qualifiedNameStr)
					writeln("\t\tit.${field.name} = Ref($refArrayName.size)")
				}
				writeln("\t}")
			}
		}

		if (rangeArrays.isNotEmpty()) {
			val modifier = if (dbModifier == Modifier.Protected || dbModifier == Modifier.ProtectedSet) "protected " else ""
			writeln()
			writeln("\t${modifier}fun endAllRanges() {")
			for (array in rangeArrays) writeln("\t\t${array.name}.endRanges()")
			writeln("\t}")
		}

		writeln("}")
	}
}
