package flatdb

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import kotlin.reflect.KClass


enum class Modifier(val annotation: KClass<*>, val value: String, val onlySet: Boolean) {
	Public		(flatdb.Public::class, "", onlySet = false),
	ProtectedSet(flatdb.Protected.Set::class,	"protected ",	onlySet = true),
	Protected	(flatdb.Protected::class,		"protected ",	onlySet = false),
	InternalSet	(flatdb.Internal.Set::class,	"internal ",	onlySet = true),
	Internal	(flatdb.Internal::class,		"internal ",	onlySet = false);
	companion object { val byAnnotation = entries.associateBy { it.annotation.qualifiedName } }
}

fun <T> KSAnnotated.getFromAnnotation(getter: (KSAnnotation, String) -> T?) = run {
	for (annotation in annotations)
		getter(annotation, annotation.annotationType.aliasResolve().declaration.qualifiedNameStr)
			?.let { return@run it }
	null
}

private val KSClassDeclaration.isInternal: Boolean get() =
	this.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.INTERNAL) ||
	(parentDeclaration as? KSClassDeclaration)?.isInternal == true

val KSDeclaration.modifier get() =
	getFromAnnotation { _, name -> Modifier.byAnnotation[name] }
	?: Modifier.Internal.takeIf { (this as? KSClassDeclaration)?.isInternal == true }