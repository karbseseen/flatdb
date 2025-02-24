package flatdb

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier


class Field(val declaration: KSPropertyDeclaration, val type: KSType) {
	constructor(declaration: KSPropertyDeclaration) : this(declaration, declaration.type.aliasResolve())
	val name get() = declaration.simpleNameStr
	val typeClass get() = type.declaration as KSClassDeclaration
	override fun hashCode() = type.hashCode()
	override fun equals(other: Any?) = type == (other as? Field)?.type
}


private val baseFieldTypes = arrayOf(IntField::class.qualifiedName!!, IntPartField::class.qualifiedName!!)
private tailrec fun isField(cls: KSClassDeclaration): Boolean =
	if (baseFieldTypes.contains(cls.qualifiedNameStr)) true
	else if (cls.modifiers.contains(Modifier.VALUE))
		isField(cls.getDeclaredProperties().first().type.aliasResolve().declaration as KSClassDeclaration)
	else false

private fun getFields(struct: KSClassDeclaration) = run {
	val fields = ArrayList<Field>()
	for (field in struct.getDeclaredProperties()) {
		val structField = Field(field)
		if (isField(structField.typeClass)) fields += structField
	}
	fields.toTypedArray()
}

class StructsFields {
	private val map = HashMap<KSClassDeclaration, Array<Field>>()
	operator fun get(struct: KSClassDeclaration) = map.computeIfAbsent(struct, ::getFields)
}
