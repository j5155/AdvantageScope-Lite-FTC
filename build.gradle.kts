plugins {
	id("dev.frozenmilk.android-library") version "10.1.1-0.1.3"
	id("dev.frozenmilk.publish") version "0.0.4"
	id("dev.frozenmilk.doc") version "0.0.4"
}

android.namespace = "page.j5155.RRScopeLite"

// Most FTC libraries will want the following
ftc {
	kotlin // if you don't want to use kotlin, remove this

	sdk {
		RobotCore
		FtcCommon {
			configurationNames += "testImplementation"
		}
		RobotServer
	}
}

dependencies {
	implementation("org.zeroturnaround:zt-zip:1.17") {
		exclude("org.slf4j") // everything breaks without this and I don't know why.
	}
}

publishing {
	publications {
		register<MavenPublication>("release") {
			groupId = "page.j5155"
			artifactId = "RRScopeLite"

			artifact(dairyDoc.dokkaHtmlJar)
			artifact(dairyDoc.dokkaJavadocJar)

			afterEvaluate {
				from(components["release"])
			}
		}
	}
}
