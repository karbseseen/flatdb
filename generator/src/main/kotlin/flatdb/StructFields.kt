package flatdb

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration


private val fieldTypes = arrayOf(
	IntField::class.qualifiedName!!,
	IntPartField::class.qualifiedName!!,
	RefField::class.qualifiedName!!,
	RefPartField::class.qualifiedName!!,
	BoolPartField::class.qualifiedName!!,
)

private fun getFields(struct: KSClassDeclaration) = run {
	val fields = ArrayList<Field>()
	for (field in struct.getDeclaredProperties()) {
		val fieldType = field.type.aliasResolve().declaration as? KSClassDeclaration ?: break
		if (fieldTypes.contains(fieldType.qualifiedNameStr)) fields += Field(struct, field.simpleNameStr)
	}
	fields.toTypedArray()
}

class StructsFields {
	private val map = HashMap<KSClassDeclaration, Array<Field>>()
	operator fun get(struct: KSClassDeclaration) = map.computeIfAbsent(struct, ::getFields)
}
