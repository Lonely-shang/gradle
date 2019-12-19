dependencies {

    implementation(project(":configuration"))
    implementation(project(":build"))

    if (System.getProperty("kotlin-eap") != null) {
        println("**** USING EAP KOTLIN VERSION")
        api(kotlin("gradle-plugin", "1.3.70-eap-16"))
        api(kotlin("stdlib-jdk8", "1.3.70-eap-16"))
        api(kotlin("reflect", "1.3.70-eap-16"))
        api(kotlin("compiler-embeddable", "1.3.70-eap-16"))
    } else {
        api(kotlin("gradle-plugin"))
        api(kotlin("stdlib-jdk8"))
        api(kotlin("reflect"))
        api(kotlin("compiler-embeddable"))
    }

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.4.1")
    implementation("com.gradle.publish:plugin-publish-plugin:0.10.0")

    implementation("com.thoughtworks.qdox:qdox:2.0-M9")
    implementation("org.ow2.asm:asm:7.1")

    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
}
