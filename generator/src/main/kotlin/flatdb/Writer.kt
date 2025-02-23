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
	override val imports get() = listOf(struct.qualifiedNameStr, Ref::class.qualifiedName!!)
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
		for (field in db.getDeclaredProperties()) {
			val resolvedType = field.type.aliasResolve()
			if (resolvedType.declaration.qualifiedNameStr != FlatArray::class.qualifiedName) break
			val structType = resolvedType.arguments[0].type?.aliasResolve()?.declaration
				?: throw ClassNotFoundException("Couldn't get FlatArray type for " + field.qualifiedNameStr)
			val structClass = (structType as? KSClassDeclaration)
				?.takeIf { it.classKind == ClassKind.OBJECT }
				?: throw ClassCastException(structType.qualifiedNameStr + " must be an object")
			val value = Field(structClass, field.simpleNameStr)
			if (!fields.add(value))
				throw Exception("Found multiple occurrences of FlatArray<" + structClass.qualifiedNameStr + "> in " + db.qualifiedNameStr)
		}
		fields.toList()
	}

	override val imports get() = listOf(
		*arrays.map { it.type.qualifiedNameStr }.toTypedArray(),
		Ref::class.qualifiedName!!,
		FlatDb::class.qualifiedName!!,
	)

	fun BufferedWriter.writeStruct(array: Field) {
		val arrayName = array.name
		writeln("\tprivate val $arrayName = FlatArray(${array.type.simpleNameStr})")
		for (field in structFields[array.type]) {
			val type = field.type.simpleNameStr
			val name = field.name
			writeln("\tvar Ref<$type>.$name")
			writeln("\t\t@JvmName(\"${type}_$name\") get() = $type.$name.getValue(this, $arrayName)")
			writeln("\t\t@JvmName(\"${type}_$name\") set(value) { $type.$name.setValue(this, $arrayName, value) }")
		}
	}

	override fun write(out: BufferedWriter) = with (out) {
		writeln("abstract class " + db.simpleNameStr + "Base : FlatDb() {")
		var first = true
		for (array in arrays) {
			if (first) first = false else writeln()
			writeStruct(array)
		}
		writeln("}")
	}
}