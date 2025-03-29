plugins {
	kotlin("jvm") version "2.1.0"
	id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

dependencies {
	implementation(project(":flatdb"))
	implementation("com.google.devtools.ksp:symbol-processing-api:2.1.0-1.0.29")
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	ksp("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")
}
