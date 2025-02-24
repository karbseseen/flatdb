package flatdb

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
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

	val arrays = run {
		val fields = LinkedHashSet<Field>()
		for (property in db.getDeclaredProperties()) {
			val baseField = Field(property)
			if (baseField.typeClass.qualifiedNameStr != FlatArray::class.qualifiedName) break
			val structType = baseField.type.arguments[0].type?.aliasResolve()
				?: throw ClassNotFoundException("Couldn't get FlatArray type for " + property.qualifiedNameStr)
			val structClass = (structType.declaration as? KSClassDeclaration)
				?.takeIf { it.classKind == ClassKind.OBJECT }
				?: throw ClassCastException(structType.declaration.qualifiedNameStr + " must be an object")
			val field = Field(property, structType)
			if (!fields.add(field))
				throw Exception("Found multiple occurrences of FlatArray<" + structClass.qualifiedNameStr + "> in " + db.qualifiedNameStr)
		}
		fields.toList()
	}

	override val imports get() = listOf(
		*arrays.map { it.typeClass.qualifiedNameStr }.toTypedArray(),
		Ref::class.qualifiedName!!,
		FlatDb::class.qualifiedName!!,
	)


	enum class Modifier { Public, ProtectedSet, Protected }
	val KSDeclaration.modifier get() = run {
		for (annotation in annotations)
			when (annotation.annotationType.aliasResolve().declaration.qualifiedNameStr) {
				Public		::class.qualifiedName -> return@run Modifier.Public
				ProtectedSet::class.qualifiedName -> return@run Modifier.ProtectedSet
				Protected	::class.qualifiedName -> return@run Modifier.Protected
			}
		null
	}

	override fun write(out: BufferedWriter) = with (out) {
		var first = true
		val dbModifier by lazy { db.modifier ?: throw TypeCastException("Not found annotation for " + db.qualifiedNameStr) }
		writeln("sealed class " + db.simpleNameStr + "Base : FlatDb() {")
		for (array in arrays) {
			if (first) first = false else writeln()
			val arrayName = array.name
			val type = array.typeClass.simpleNameStr
			val arrayModifier by lazy { array.declaration.modifier ?: array.typeClass.modifier ?: dbModifier }
			writeln("\tprivate val $arrayName = FlatArray($type)")
			for (field in structFields[array.typeClass]) {
				val name = field.name
				val modifier = field.declaration.modifier ?: arrayModifier
				val varModifier = if (modifier == Modifier.Protected) "protected " else ""
				val setModifier = if (modifier == Modifier.ProtectedSet) "protected " else ""
				writeln("\t${varModifier}var Ref<$type>.$name")
				writeln("\t\t@JvmName(\"${type}_$name\") get() = $type.$name.getValue(this, $arrayName)")
				writeln("\t\t@JvmName(\"${type}_$name\") ${setModifier}set(value) { $type.$name.setValue(this, $arrayName, value) }")
			}
		}
		writeln("}")
	}
}
