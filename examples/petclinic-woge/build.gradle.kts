plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
}

val upstream = file(providers.gradleProperty("petclinicSource").get())
val wogeVersion: String by project

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
springBoot.mainClass.set("org.springframework.samples.petclinic.PetClinicApplication")

sourceSets.main {
    java.srcDir(upstream.resolve("src/main/java"))
    resources.srcDir(upstream.resolve("src/main/resources"))
}

repositories {
    exclusiveContent {
        forRepository { maven { url = uri(providers.gradleProperty("wogeRepository").get()) } }
        filter { includeGroup("dev.woge") }
    }
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("javax.cache:cache-api")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api")
    implementation("dev.woge:woge-spring-boot-starter:$wogeVersion")
    implementation("dev.woge:woge-spring-mvc:$wogeVersion")
    runtimeOnly("org.webjars:webjars-locator-lite:1.1.3")
    runtimeOnly("org.webjars.npm:bootstrap:5.3.8")
    runtimeOnly("org.webjars.npm:font-awesome:4.7.0")
    runtimeOnly("com.github.ben-manes.caffeine:caffeine")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jsoup:jsoup:1.23.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    val visitAction = "@{/owners/{ownerId}/pets/{petId}/visits/new(ownerId=\${owner.id},petId=\${pet.id})}"
    inputs.property("petclinicVisitAction", visitAction)
    filesMatching("templates/pets/createOrUpdateVisitForm.html") {
        // Thymeleaf invokes Spring's hidden-field processor for a form with th:action.
        filter { line: String ->
            line.replace("th:object=\"\${visit}\"", "th:object=\"\${visit}\" th:action=\"$visitAction\"")
        }
    }

    from(upstream.resolve("LICENSE.txt")) {
        into("META-INF")
        rename { "LICENSE-petclinic.txt" }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootRun {
    args("--server.address=127.0.0.1")
}
