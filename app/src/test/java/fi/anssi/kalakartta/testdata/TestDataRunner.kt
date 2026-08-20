package fi.anssi.kalakartta.testdata

import org.junit.Ignore
import org.junit.Test

/**
 * JUnit-wrapper TestDataGeneratorin ajamiseksi Gradlesta.
 * Tätä ei ajeta normaalisti (merkitty @Ignore), vaan vain kun halutaan generoida dataa.
 */
class TestDataRunner {

    @Test
    fun runGenerator() {
        val configPath = System.getProperty("generatorConfig") ?: "src/test/resources/test-data-config.json"
        TestDataGenerator.main(arrayOf(configPath))
    }
}
