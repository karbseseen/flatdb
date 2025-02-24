package flatdb

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.InvalidObjectException


class Processor(
	private val options: Map<String, String>,
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
): SymbolProcessor {

	val structFields = StructsFields()
	val writers = ArrayList<Writer>()

	override fun process(resolver: Resolver) = emptyList<KSAnnotated>().also {
		for (symbol in resolver.getSymbolsWithAnnotation(FlatDb.Generate::class.qualifiedName!!)) {
			val dbClass = symbol as? KSClassDeclaration
				?: throw InvalidObjectException("@FlatDb.Generate must be applied to a class, interface or object")
			val dbWriter = DbWriter(dbClass, structFields)
			if (dbWriter.arrays.isEmpty())
				logger.warn("Nothing to do with " + dbClass.qualifiedNameStr + ", no FlatArray fields was found", dbClass)
			writers += dbWriter
			writers += dbWriter.arrays.map { StructWriter(it.typeClass) }
		}
	}

	override fun finish() = writers.write(codeGenerator)
}

@AutoService(SymbolProcessorProvider::class)
class Provider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment) =
		Processor(environment.options, environment.codeGenerator, environment.logger)
}