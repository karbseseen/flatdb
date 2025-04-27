package flatdb

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*


open class Field(val declaration: KSPropertyDeclaration, val type: KSType) {
	constructor(declaration: KSPropertyDeclaration) : this(declaration, declaration.type.aliasResolve())
	val name get() = declaration.simpleNameStr
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

class StructFields(struct: KSClassDeclaration, fieldType: KSType) {
	val fields = struct.getDeclaredProperties()
		.map(::StructField)
		.filter { fieldType.isAssignableFrom(it.type) }
	val modifier by lazy { struct.modifier }
}

class StructsFields(resolver: Resolver) {
	@OptIn(KspExperimental::class)
	private val fieldType = resolver.getKotlinClassByName(FlatField::class.qualifiedName!!)!!.asStarProjectedType()
	private val map = HashMap<KSClassDeclaration, StructFields>()
	operator fun get(struct: KSClassDeclaration) = map.computeIfAbsent(struct) { StructFields(it, fieldType) }
}
