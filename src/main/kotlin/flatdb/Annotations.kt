package flatdb

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Public
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class ProtectedSet
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY) annotation class Protected

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY) annotation class Range(val name: String)
