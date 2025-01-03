plugins {
	id("dev.frozenmilk.android-library") version "10.1.1-0.1.3"
	id("dev.frozenmilk.publish") version "0.0.4"
	id("dev.frozenmilk.doc") version "0.0.4"
}

// TODO: modify
android.namespace = "com.example.library"

// Most FTC libraries will want the following
ftc {
	kotlin // if you don't want to use kotlin, remove this

	sdk {
		RobotCore
		FtcCommon {
			configurationNames += "testImplementation"
		}
	}
}

publishing {
	publications {
		register<MavenPublication>("release") {
			// TODO: modify
			groupId = "com.example"
			// TODO: modify
			artifactId = "Library"

			artifact(dairyDoc.dokkaHtmlJar)
			artifact(dairyDoc.dokkaJavadocJar)

			afterEvaluate {
				from(components["release"])
			}
		}
	}
}
