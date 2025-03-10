package flatdb

import com.google.devtools.ksp.symbol.*
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.InvalidClassException
import kotlin.reflect.KClass


fun BufferedWriter.writeln(line: String = "") = write("$line\n")

val KSDeclaration.simpleNameStr get() = simpleName.asString()
val KSDeclaration.qualifiedNameStr get() = qualifiedName?.asString()
	?: throw InvalidClassException("Couldn't get qualified name for $simpleNameStr")
val KSDeclaration.file get() = containingFile
	?: throw FileNotFoundException("Couldn't find containing file for $qualifiedNameStr")

val KSFile.simpleName get() = if (fileName.endsWith(".kt")) fileName.substring(0, fileName.length - 3) + "Gen"
	else throw FileNotFoundException("$fileName doesn't end with \".kt\"")

val KSType.classDeclaration get() = declaration as KSClassDeclaration

fun KSTypeReference.aliasResolve(): KSType = run {
	val resolved = resolve()
	val declaration = resolved.declaration
	when (declaration) {
		is KSClassDeclaration -> resolved
		is KSTypeAlias -> declaration.type.aliasResolve()
		else -> {
			val name = declaration.qualifiedName ?: declaration.simpleName
			throw ClassNotFoundException("Can't resolve type " + name.asString())
		}
	}
}
