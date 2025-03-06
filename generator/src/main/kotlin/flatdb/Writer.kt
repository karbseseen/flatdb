package flatdb

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSType
import java.io.BufferedWriter


interface Writer {
	val declaration: KSClassDeclaration
	val imports: List<String>
	fun write(out: BufferedWriter)
}

fun Iterable<Writer>.write(codeGenerator: CodeGenerator) {
	for ((file, writers) in distinct().groupBy { it.declaration.file }) codeGenerator
		.createNewFile(Dependencies(true, file), file.packageName.asString(), file.simpleName)
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

class StructWriter(val struct: KSClassDeclaration) : Writer {
	override val declaration get() = struct
	override val imports get() = listOf(Ref::class.qualifiedName!!)
	override fun write(out: BufferedWriter) = with (out) {
		val structType = struct.simpleNameStr
		writeln("val Ref<$structType>.next @JvmName(\"${structType}_next\") get() = Ref<$structType>(offset + $structType.size)")
		writeln("val Ref<$structType>.prev @JvmName(\"${structType}_prev\") get() = Ref<$structType>(offset - $structType.size)")
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
				?: break
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
		*arrays.map { it.typeClass.qualifiedNameStr }.toTypedArray(),
		*arrays.flatMap { structFields[it.typeClass].fields }.mapNotNull { it.rangeClass?.qualifiedNameStr }.toTypedArray(),
		Ref::class.qualifiedName!!,
		FlatDb::class.qualifiedName!!,
		FlatArray::class.qualifiedName!!,
	)

	override fun write(out: BufferedWriter) = with (out) {
		val arrayByStruct by lazy { arrays.associateBy { it.typeClass.qualifiedNameStr } }
		val rangeArrays = ArrayList<Field>()
		val dbModifier by lazy { db.modifier }
		var first = true
		writeln("sealed class " + db.simpleNameStr + "Base : FlatDb() {")
		for (array in arrays) {
			val rangeFields = ArrayList<StructField>()
			val arrayName = array.name
			val type = array.typeClass.simpleNameStr
			val arrayModifier = array.declaration.modifier
			val struct = structFields[array.typeClass]
			val rangeLines = ArrayList<String>()
			if (first) first = false else writeln()
			writeln("\tprivate val $arrayName = FlatArray($type)")
			for (field in struct.fields) {
				val name = field.name
				val modifier = arrayModifier ?: field.declaration.modifier ?: dbModifier ?: struct.modifier
				val varModifier = if (modifier == Modifier.Protected) "protected " else ""
				val setModifier = if (modifier == Modifier.ProtectedSet) "protected " else ""
				writeln("\t${varModifier}var Ref<$type>.$name")
				writeln("\t\t@JvmName(\"${type}_$name\") get() = $type.$name.getValue(this, $arrayName)")
				writeln("\t\t@JvmName(\"${type}_$name\") ${setModifier}set(value) { $type.$name.setValue(this, $arrayName, value) }")
				field.rangeName?.let { rangeName ->
					rangeFields += field
					val rangeType = field.rangeClass?.qualifiedNameStr
					rangeLines += "\t${varModifier}val Ref<$type>.$rangeName"
					rangeLines += "\t\t@JvmName(\"${type}_$rangeName\") get() = Ref.Range($name, next.$name, ${rangeType}.size)"
				}
			}

			for (rangeLine in rangeLines) writeln(rangeLine)

			if (rangeFields.isNotEmpty()) {
				rangeArrays += array
				val modifier = arrayModifier ?: dbModifier ?: struct.modifier
				val funModifier = if (modifier == Modifier.Protected || modifier == Modifier.ProtectedSet) "protected " else ""
				writeln("\t${funModifier}fun FlatArray<$type>.endRanges() = $arrayName.validEndRef.let {")
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
