package flatdb

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*


open class Field(val declaration: KSPropertyDeclaration, val type: KSType) {
	val name get() = declaration.simpleNameStr
	val typeClass get() = type.classDeclaration
	override fun hashCode() = typeClass.qualifiedNameStr.hashCode()
	override fun equals(other: Any?) = typeClass.qualifiedNameStr == (other as? Field)?.typeClass?.qualifiedNameStr
}

class StructField(
	declaration: KSPropertyDeclaration,
	type: KSType,
	val isView: Boolean,
) : Field(declaration, type)

class StructFields(struct: KSClassDeclaration, types: StructsFields) {
	val fields = struct.getDeclaredProperties().mapNotNull { property ->
		val type = property.type.aliasResolve()
		if		(types.fieldType.isAssignableFrom(type))	StructField(property, type, false)
		else if	(types.fieldViewType.isAssignableFrom(type))StructField(property, type, true)
		else null
	}.toList()
	val modifier by lazy { struct.modifier }
}

@OptIn(KspExperimental::class) class StructsFields(resolver: Resolver) {
	val fieldType		= resolver.getKotlinClassByName(FlatField::class.qualifiedName!!)!!.asStarProjectedType()
	val fieldViewType	= resolver.getKotlinClassByName(FlatFieldView::class.qualifiedName!!)!!.asStarProjectedType()
	private val map = HashMap<KSClassDeclaration, StructFields>()
	operator fun get(struct: KSClassDeclaration) = map.computeIfAbsent(struct) { StructFields(it, this) }
}
