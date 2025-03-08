package flatdb

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import kotlin.reflect.KClass


class Processor(
	private val options: Map<String, String>,
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
): SymbolProcessor {

	val structFields = StructsFields()
	val writers = ArrayList<Writer>()

	fun process(symbolClass: KSAnnotated) {
		if (symbolClass !is KSClassDeclaration) return

		for (parentType in symbolClass.superTypes) {
			val parentClass = parentType.aliasResolve().declaration
			if (parentClass is KSClassDeclaration) {
				if (parentClass.qualifiedName?.asString() == FlatStruct::class.qualifiedName) return
				else break
			}
		}

		val dbWriter = DbWriter(symbolClass, structFields)
		if (dbWriter.arrays.isEmpty())
			logger.warn("Nothing to do with " + symbolClass.qualifiedNameStr + ", no FlatArray fields was found", symbolClass)
		writers += dbWriter
		writers += dbWriter.arrays.map { StructWriter(it.typeClass, structFields) }
	}

	fun process(resolver: Resolver, annotation: KClass<*>): Unit =
		resolver.getSymbolsWithAnnotation(annotation.qualifiedName!!).forEach(::process)

	override fun process(resolver: Resolver) = emptyList<KSAnnotated>().also {
		for (modifier in Modifier.entries)
			process(resolver, modifier.annotation)
		process(resolver, Generate::class)
	}

	override fun finish() = codeGenerator.write(writers)
}

@AutoService(SymbolProcessorProvider::class)
class Provider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment) =
		Processor(environment.options, environment.codeGenerator, environment.logger)
}