package flatdb

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration


class Processor(
	private val options: Map<String, String>,
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
): SymbolProcessor {

	val structFields = StructsFields()
	val writers = ArrayList<Writer>()

	fun process(symbols: Sequence<KSAnnotated>) = symbols.forEach { symbolClass ->
		if (symbolClass !is KSClassDeclaration) return@forEach

		for (parentType in symbolClass.superTypes) {
			val parentClass = parentType.aliasResolve().declaration
			if (parentClass is KSClassDeclaration) {
				if (parentClass.qualifiedName?.asString() == FlatStruct::class.qualifiedName) return@forEach
				else break
			}
		}

		val dbWriter = DbWriter(symbolClass, structFields)
		if (dbWriter.arrays.isEmpty())
			logger.warn("Nothing to do with " + symbolClass.qualifiedNameStr + ", no FlatArray fields was found", symbolClass)
		writers += dbWriter
		writers += dbWriter.arrays.map { StructWriter(it.typeClass) }
	}

	override fun process(resolver: Resolver) = emptyList<KSAnnotated>().also {
		process(resolver.getSymbolsWithAnnotation(Public::class.qualifiedName!!))
		process(resolver.getSymbolsWithAnnotation(ProtectedSet::class.qualifiedName!!))
		process(resolver.getSymbolsWithAnnotation(Protected::class.qualifiedName!!))
	}

	override fun finish() = writers.write(codeGenerator)
}

@AutoService(SymbolProcessorProvider::class)
class Provider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment) =
		Processor(environment.options, environment.codeGenerator, environment.logger)
}