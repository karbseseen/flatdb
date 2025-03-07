package flatdb

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Public
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Protected {
	@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Set
}
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Internal {
	@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Set
}

@Target(AnnotationTarget.CLASS) annotation class Generate

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY) annotation class Range(val name: String)
