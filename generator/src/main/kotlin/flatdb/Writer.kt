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
			if (writers.any { it is DbWriter })
				out.writeln("@RequiresOptIn @Retention(AnnotationRetention.BINARY) private annotation class Private\n")
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
		Ref::class.qualifiedName!!,
		FlatDb::class.qualifiedName!!,
		FlatArray::class.qualifiedName!!,
	)

	override fun write(out: BufferedWriter) = with (out) {
		val dbName = db.simpleNameStr
		val dbAccessName = db.simpleNameStr + "Access"
		val dbModifier by lazy { db.modifier }
		val dbClassModifier = if (db.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.INTERNAL)) "internal " else ""
		writeln("${dbClassModifier}class $dbAccessName @Private constructor(val db: $dbName) {")
		for (array in arrays) {
			val arrayName = array.name
			val type = array.typeClass.callName
			val jvmType = type.replace(".", "")
			val valueArg = if (arrayName == "value") "v" else "value"

			val arrayModifier = array.declaration.modifier
			val struct = structFields[array.typeClass]
			writeln()
			for (field in struct.fields) {
				val name = field.name
				val nameAnnotation = "@JvmName(\"${jvmType}_$name\")"
				val modifier = arrayModifier ?: field.declaration.modifier ?: dbModifier ?: struct.modifier ?: Modifier.Public

				val varModifier = (if (!modifier.onlySet) modifier.value else "") + "inline"
				val varDeclaration = if (field.isView) "val" else "var"
				writeln("\t$varModifier $varDeclaration Ref<$type>.$name")
				writeln("\t\t$nameAnnotation get() = $type.$name.getValue(this, db.$arrayName)")

				if (field.isView) continue
				val setModifier = if (modifier.onlySet) modifier.value else ""
				writeln("\t\t$nameAnnotation ${setModifier}set($valueArg) = $type.$name.setValue(this, db.$arrayName, $valueArg)")
			}
		}
		writeln("}")
		writeln("@OptIn(Private::class) ${dbClassModifier}fun <R> $dbName.access(block: $dbAccessName.() -> R) = block($dbAccessName(this))")
	}
}
