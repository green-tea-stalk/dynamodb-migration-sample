plugins {
    `java`
    alias(libs.plugins.spotless)
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // AWS SDK for Java v1 (DynamoDBMapper)
    implementation(libs.aws.sdk.v1.dynamodb)

    // AWS SDK for Java v2 (DynamoDB & DynamoDbEnhancedClient)
    implementation(platform(libs.aws.sdk.v2.bom))
    implementation(libs.aws.sdk.v2.dynamodb)
    implementation(libs.aws.sdk.v2.dynamodb.enhanced)
    implementation(libs.aws.sdk.v2.url.connection.client)

    // Jackson (JSON Token serialization)
    implementation(libs.jackson.databind)

    // Logging
    implementation(libs.slf4j.api)
    testImplementation(libs.slf4j.simple)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        eclipse()
        removeUnusedImports()
        leadingTabsToSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
