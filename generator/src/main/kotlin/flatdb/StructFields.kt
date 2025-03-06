package flatdb

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.*


open class Field(val declaration: KSPropertyDeclaration, val type: KSType) {
	constructor(declaration: KSPropertyDeclaration) : this(declaration, declaration.type.aliasResolve())
	val name get() = declaration.simpleNameStr
	val modifier by lazy { declaration.modifier }
	val typeClass get() = type.classDeclaration

	override fun hashCode() = typeClass.qualifiedNameStr.hashCode()
	override fun equals(other: Any?) = typeClass.qualifiedNameStr == (other as? Field)?.typeClass?.qualifiedNameStr
}

class StructField(declaration: KSPropertyDeclaration) : Field(declaration) {
	val rangeName = run {
		declaration.getter?.getFromAnnotation { annotation, name ->
			if (name != Range::class.qualifiedName) null
			else annotation.arguments.find { it.name?.asString() == "name" }?.value as? String
		}
	}
	val rangeClass = rangeName?.let {
		val className = typeClass.qualifiedNameStr
		if (className != RefField::class.qualifiedName && className != RefPartField::class.qualifiedName)
			throw ClassCastException("@Range applied to " + declaration.qualifiedNameStr + " which is not a Ref field")
		else type.arguments.firstOrNull()?.type?.aliasResolve()?.classDeclaration
			?: throw TypeCastException("Couldn't resolve " + declaration.qualifiedNameStr + " Ref type")
	}
}

private val baseFieldTypes = arrayOf(IntField::class.qualifiedName!!, IntPartField::class.qualifiedName!!)
private tailrec fun isField(cls: KSClassDeclaration): Boolean =
	if (baseFieldTypes.contains(cls.qualifiedNameStr)) true
	else if (cls.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.VALUE))
		isField(cls.getDeclaredProperties().first().type.aliasResolve().declaration as KSClassDeclaration)
	else false

class StructFields(struct: KSClassDeclaration) {
	val fields = struct.getDeclaredProperties().map(::StructField).filter { isField(it.typeClass) }
	val modifier by lazy { struct.modifier }
}

class StructsFields {
	private val map = HashMap<KSClassDeclaration, StructFields>()
	operator fun get(struct: KSClassDeclaration) = map.computeIfAbsent(struct, ::StructFields)
}
