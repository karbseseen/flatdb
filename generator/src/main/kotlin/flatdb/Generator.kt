package flatdb

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import java.io.BufferedWriter
import java.io.InvalidObjectException
import javax.naming.InvalidNameException


val KSDeclaration.fullName: String get() =
	qualifiedName?.asString()
	?: parentDeclaration?.let { it.fullName + '_' + simpleName.asString() }
	?: throw InvalidNameException("Couldn't get full name for " + simpleName.asString())

fun BufferedWriter.writeln(line: String) = write("$line\n")
fun List<String>.mkString(separator: String) =
	if (isEmpty()) ""
	else {
		val it = iterator()
		val sb = StringBuilder()
		sb.append(it.next())
		for (value in it) sb.append(separator).append(value)
		sb.toString()
	}


/*class ArrayOccurrence(val dbClass: KSClassDeclaration, val structClass: KSClassDeclaration) {
	override fun hashCode() = dbClass.fullName.hashCode() xor structClass.fullName.hashCode()
	override fun equals(other: Any?) = other is ArrayOccurrence && dbClass == other.dbClass && structClass == other.structClass
}*/

class Processor(
	private val options: Map<String, String>,
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
): SymbolProcessor {

	fun processDb(dbClass: KSClassDeclaration) {
		val usedStructs = HashSet<KSClassDeclaration>()
		dbClass.declarations.forEach {
			if (it !is KSPropertyDeclaration) return@forEach
			if (it.type.resolve().declaration.fullName != FlatArray::class.java.name) return@forEach
			val structType = it.type.element?.typeArguments?.get(0)?.type?.resolve()?.declaration ?: return@forEach
			val structClass = (structType as? KSClassDeclaration)
				?.takeIf { it.classKind == ClassKind.OBJECT }
				?: throw ClassCastException(it.fullName + " is not an object")
			if (!usedStructs.add(structClass))
				throw Exception("Found multiple occurrences of FlatArray<" + structClass.fullName + "> in " + dbClass.fullName)
		}
		if (usedStructs.isEmpty())
			logger.warn("Nothing to do with " + dbClass.fullName + ", no FlatArray fields was found", dbClass)

		val base = dbClass.superTypes

		val packageName = dbClass.packageName.asString()
	}

	override fun process(resolver: Resolver) = emptyList<KSAnnotated>().also {
		for (symbol in resolver.getSymbolsWithAnnotation(FlatDb.Generate::class.qualifiedName!!)) {
			val dbClass = symbol as? KSClassDeclaration
				?: throw InvalidObjectException("@FlatDb.Generate must be applied to a class, interface or object")
			processDb(dbClass)
		}
	}


	override fun finish() {

		/*codeGenerator.createNewFile(Dependencies.ALL_FILES, "", "generated").bufferedWriter().use {
			it.writeln("object AnnotatedPrint {")
			it.writeln("\tfun printAnnotated() {")
			it.writeln("\t\tprintln(\"" + annotated.mkString(", ") + "\")")
			it.writeln("\t}")
			it.writeln("}")
		}*/
	}

}

@AutoService(SymbolProcessorProvider::class)
class Provider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment) =
		Processor(environment.options, environment.codeGenerator, environment.logger)
}