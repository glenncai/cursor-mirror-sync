plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.glenncai.cursormirrorsync"
version = "1.0.0"

    repositories {
        mavenCentral()
        maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    intellij {
        version.set("2023.3")
        downloadSources.set(true)
        instrumentCode.set(false)
        pluginName.set("cursor-mirror-sync")
    }

    dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.10.1")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    
    patchPluginXml {
        version.set("${project.version}")
        sinceBuild.set("233")
        untilBuild.set(provider{null})
    }

    buildPlugin {
        archiveBaseName.set("cursor-mirror-sync")
        archiveVersion.set(project.version.toString())
    }

    runIde {
        jvmArgs("-Xmx2g", "--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
    
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    
    test {
        useJUnitPlatform()
    }
}
